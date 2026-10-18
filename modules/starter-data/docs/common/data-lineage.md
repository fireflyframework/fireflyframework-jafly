# Data Lineage

When data flows through multiple enrichment providers, transformation steps, and collection jobs, it can be surprisingly difficult to answer a simple question: *where did this value come from?* Data lineage is the practice of recording that history -- tracking every operation that touched a data record, who performed it, when it happened, and what changed.

This matters for several practical reasons. During debugging, lineage lets you pinpoint exactly which enricher introduced a bad value instead of guessing across a dozen possible sources. For compliance and audit purposes, many regulated industries require a clear provenance trail showing how data was produced and modified. And for day-to-day observability, lineage records can be correlated with distributed traces to give you a complete picture of data flow through your system.

In `fireflyframework-starter-data`, lineage tracking is built around a simple port-and-adapter pattern. You interact with a `LineageTracker` interface to record and query lineage events, and the framework provides a default in-memory implementation out of the box. For production use, you swap in your own implementation backed by a database, event log, or graph store -- the rest of your code stays the same.

This document provides a comprehensive guide to the data lineage tracking framework in `fireflyframework-starter-data`, covering the `LineageTracker` port, `LineageRecord` model, the default in-memory implementation, and guidance for production deployments.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [LineageRecord Fields](#lineagerecord-fields)
- [InMemoryLineageTracker](#inmemorylineagetracker)
- [Recording Lineage Programmatically](#recording-lineage-programmatically)
- [Querying Lineage](#querying-lineage)
- [Creating a Custom LineageTracker](#creating-a-custom-lineagetracker)
- [Integration with DataEnricher](#integration-with-dataenricher)
- [Best Practices](#best-practices)

---

## Overview

Data lineage tracking records the provenance chain of data entities as they flow through enrichments, transformations, and job collections. This enables:

- **Auditability** -- full trace of how data was produced and modified
- **Debugging** -- identify which operator introduced an error
- **Compliance** -- satisfy regulatory requirements for data governance
- **Observability** -- correlate data operations with distributed traces

The framework is built around two core abstractions:

- **LineageTracker** -- a port interface defining how lineage records are stored and queried
- **LineageRecord** -- an immutable value object capturing a single step in the provenance chain

---

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                  LineageTracker (Port)                  │
│                                                        │
│   record(LineageRecord) -> Mono<Void>                  │
│   getLineage(entityId) -> Flux<LineageRecord>          │
│   getLineageByOperator(operatorId) -> Flux<...>        │
│                                                        │
│   ┌──────────────────────────────────────────────┐     │
│   │           Implementations                    │     │
│   │  ┌─────────────────────┐ ┌────────────────┐  │     │
│   │  │ InMemoryLineage-    │ │  CustomTracker  │  │     │
│   │  │ Tracker (default)   │ │ (your impl)     │  │     │
│   │  └─────────────────────┘ └────────────────┘  │     │
│   └──────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────┘

   Data flows through:
   Enricher/Transformer/Job  ──record()──>  LineageTracker
                                              │
   Audit / Query Service  <──getLineage()─────┘
```

**Key classes:**

| Class | Package | Role |
|---|---|---|
| `LineageTracker` | `o.f.data.lineage` | Port interface for recording and querying lineage |
| `LineageRecord` | `o.f.data.lineage` | Immutable record of a single provenance step |
| `InMemoryLineageTracker` | `o.f.data.lineage` | Default in-memory implementation |
| `DataLineageAutoConfiguration` | `o.f.data.config` | Auto-configures the tracker from Spring properties |

---

## Configuration

Data lineage is **disabled by default**. You must explicitly enable it:

```yaml
firefly:
  data:
    lineage:
      enabled: true    # default: false (must be explicitly enabled)
```

When enabled, `DataLineageAutoConfiguration` creates an `InMemoryLineageTracker` bean unless you provide your own `LineageTracker` bean (via `@ConditionalOnMissingBean`).

To disable lineage tracking (the default):

```yaml
firefly:
  data:
    lineage:
      enabled: false
```

> **See also:** [Configuration Guide](configuration.md) for the full property reference.

---

## LineageRecord Fields

Each `LineageRecord` captures a single step in the provenance chain of a data entity. It is built using the Lombok `@Builder` pattern.

| Field | Type | Description |
|---|---|---|
| `recordId` | `String` | Unique identifier for this lineage record (UUID) |
| `entityId` | `String` | Business entity identifier whose provenance is being tracked |
| `sourceSystem` | `String` | Name of the source system or provider that produced this record |
| `operation` | `String` | Type of operation: `ENRICHMENT`, `TRANSFORMATION`, `JOB_COLLECTION`, etc. |
| `operatorId` | `String` | Identifier of the operator that performed the operation (enricher name, job name) |
| `timestamp` | `Instant` | When this lineage record was created |
| `inputHash` | `String` | Hash of the input data before the operation was applied |
| `outputHash` | `String` | Hash of the output data after the operation was applied |
| `traceId` | `String` | OpenTelemetry trace ID for distributed tracing correlation |
| `metadata` | `Map<String, Object>` | Additional metadata associated with this lineage record |

### Building a LineageRecord

```java
import org.fireflyframework.data.lineage.LineageRecord;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

LineageRecord record = LineageRecord.builder()
    .recordId(UUID.randomUUID().toString())
    .entityId("customer-12345")
    .sourceSystem("clearbit")
    .operation("ENRICHMENT")
    .operatorId("company-enricher")
    .timestamp(Instant.now())
    .inputHash("sha256:abc123")
    .outputHash("sha256:def456")
    .traceId("4bf92f3577b34da6a3ce929d0e0e4736")
    .metadata(Map.of(
        "fieldsEnriched", 5,
        "provider", "clearbit-v2"
    ))
    .build();
```

---

## InMemoryLineageTracker

The default `InMemoryLineageTracker` is a thread-safe implementation that stores lineage records in memory using a `ConcurrentHashMap<String, List<LineageRecord>>`, where each entity's records are backed by a `CopyOnWriteArrayList`.

### Characteristics

| Property | Value |
|---|---|
| Thread safety | Fully thread-safe via `ConcurrentHashMap` + `CopyOnWriteArrayList` |
| Persistence | None -- data is lost on application restart |
| Scalability | Single-instance only; not shared across replicas |
| Query support | By entity ID or by operator ID |
| Suitable for | Development, testing, single-instance deployments |

### How It Works

```java
// Storing: records are grouped by entityId
lineageByEntity
    .computeIfAbsent(record.getEntityId(), key -> new CopyOnWriteArrayList<>())
    .add(record);

// Querying by entity: direct lookup
Flux<LineageRecord> records = lineageByEntity.getOrDefault(entityId, List.of());

// Querying by operator: scans all records across all entities
Flux.fromIterable(lineageByEntity.values())
    .flatMapIterable(records -> records)
    .filter(record -> operatorId.equals(record.getOperatorId()));
```

### Limitations

- **No persistence** -- all records are lost when the application stops
- **Memory growth** -- records accumulate indefinitely; no eviction policy
- **Single-instance** -- records are not shared across application replicas
- **No indexing** -- operator queries require a full scan

For production environments, implement a persistent `LineageTracker` backed by a database, event log, or graph database.

---

## Recording Lineage Programmatically

Inject the `LineageTracker` and call `record()`:

```java
@Service
public class EnrichmentService {

    private final LineageTracker lineageTracker;

    public EnrichmentService(LineageTracker lineageTracker) {
        this.lineageTracker = lineageTracker;
    }

    public Mono<Customer> enrichWithLineage(Customer customer, String enricherName) {
        String inputHash = computeHash(customer);

        return enrichCustomer(customer)
            .flatMap(enriched -> {
                LineageRecord record = LineageRecord.builder()
                    .recordId(UUID.randomUUID().toString())
                    .entityId(customer.getId())
                    .sourceSystem("internal")
                    .operation("ENRICHMENT")
                    .operatorId(enricherName)
                    .timestamp(Instant.now())
                    .inputHash(inputHash)
                    .outputHash(computeHash(enriched))
                    .build();

                return lineageTracker.record(record)
                    .thenReturn(enriched);
            });
    }
}
```

---

## Querying Lineage

The `LineageTracker` interface provides two query methods, both returning reactive `Flux` streams.

### Query by Entity

Retrieve the complete provenance chain for a specific business entity:

```java
lineageTracker.getLineage("customer-12345")
    .subscribe(record ->
        log.info("Step: {} by {} at {}",
            record.getOperation(),
            record.getOperatorId(),
            record.getTimestamp())
    );
```

### Query by Operator

Retrieve all lineage records produced by a specific operator, across all entities:

```java
lineageTracker.getLineageByOperator("company-enricher")
    .subscribe(record ->
        log.info("Enriched entity {} at {}",
            record.getEntityId(),
            record.getTimestamp())
    );
```

### Example: Building a Lineage Trail

```java
@RestController
@RequestMapping("/api/v1/lineage")
public class LineageController {

    private final LineageTracker lineageTracker;

    @GetMapping("/{entityId}")
    public Flux<LineageRecord> getEntityLineage(@PathVariable String entityId) {
        return lineageTracker.getLineage(entityId);
    }

    @GetMapping("/operator/{operatorId}")
    public Flux<LineageRecord> getOperatorLineage(@PathVariable String operatorId) {
        return lineageTracker.getLineageByOperator(operatorId);
    }
}
```

---

## Creating a Custom LineageTracker

For production deployments, implement the `LineageTracker` interface with a persistent store. The interface defines three methods:

```java
public interface LineageTracker {
    Mono<Void> record(LineageRecord record);
    Flux<LineageRecord> getLineage(String entityId);
    Flux<LineageRecord> getLineageByOperator(String operatorId);
}
```

### Example: R2DBC-backed LineageTracker

```java
@Component
public class R2dbcLineageTracker implements LineageTracker {

    private final DatabaseClient databaseClient;

    public R2dbcLineageTracker(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> record(LineageRecord record) {
        return databaseClient.sql("""
                INSERT INTO data_lineage
                  (record_id, entity_id, source_system, operation,
                   operator_id, timestamp, input_hash, output_hash, trace_id)
                VALUES (:recordId, :entityId, :sourceSystem, :operation,
                        :operatorId, :timestamp, :inputHash, :outputHash, :traceId)
                """)
            .bind("recordId", record.getRecordId())
            .bind("entityId", record.getEntityId())
            .bind("sourceSystem", record.getSourceSystem())
            .bind("operation", record.getOperation())
            .bind("operatorId", record.getOperatorId())
            .bind("timestamp", record.getTimestamp())
            .bind("inputHash", record.getInputHash())
            .bind("outputHash", record.getOutputHash())
            .bind("traceId", record.getTraceId())
            .then();
    }

    @Override
    public Flux<LineageRecord> getLineage(String entityId) {
        return databaseClient.sql(
                "SELECT * FROM data_lineage WHERE entity_id = :entityId ORDER BY timestamp")
            .bind("entityId", entityId)
            .map(this::mapRow)
            .all();
    }

    @Override
    public Flux<LineageRecord> getLineageByOperator(String operatorId) {
        return databaseClient.sql(
                "SELECT * FROM data_lineage WHERE operator_id = :operatorId ORDER BY timestamp")
            .bind("operatorId", operatorId)
            .map(this::mapRow)
            .all();
    }

    private LineageRecord mapRow(Row row) {
        return LineageRecord.builder()
            .recordId(row.get("record_id", String.class))
            .entityId(row.get("entity_id", String.class))
            .sourceSystem(row.get("source_system", String.class))
            .operation(row.get("operation", String.class))
            .operatorId(row.get("operator_id", String.class))
            .timestamp(row.get("timestamp", Instant.class))
            .inputHash(row.get("input_hash", String.class))
            .outputHash(row.get("output_hash", String.class))
            .traceId(row.get("trace_id", String.class))
            .build();
    }
}
```

When you provide your own `LineageTracker` bean, the auto-configuration's `InMemoryLineageTracker` is automatically skipped due to `@ConditionalOnMissingBean`.

---

## Integration with DataEnricher

When lineage tracking is enabled, the enrichment pipeline can automatically record lineage for each enrichment operation. A common pattern is to wrap the enrichment call with lineage recording:

```java
@Service
public class LineageAwareEnricher {

    private final DataEnricher enricher;
    private final LineageTracker lineageTracker;

    public LineageAwareEnricher(DataEnricher enricher, LineageTracker lineageTracker) {
        this.enricher = enricher;
        this.lineageTracker = lineageTracker;
    }

    public <T> Mono<T> enrichWithTracking(T data, String entityId) {
        String inputHash = Integer.toHexString(data.hashCode());

        return enricher.enrich(data)
            .flatMap(enriched -> {
                LineageRecord record = LineageRecord.builder()
                    .recordId(UUID.randomUUID().toString())
                    .entityId(entityId)
                    .sourceSystem(enricher.getProviderName())
                    .operation("ENRICHMENT")
                    .operatorId(enricher.getName())
                    .timestamp(Instant.now())
                    .inputHash(inputHash)
                    .outputHash(Integer.toHexString(enriched.hashCode()))
                    .build();

                return lineageTracker.record(record)
                    .thenReturn(enriched);
            });
    }
}
```

---

## Best Practices

1. **Enable lineage explicitly.** Lineage tracking is opt-in (`matchIfMissing = false`) because it adds overhead. Enable it only when you need auditability or governance.

2. **Implement a persistent tracker for production.** The `InMemoryLineageTracker` is suitable for development and testing. Production deployments should use a database-backed (relational, graph, or event log) implementation to ensure lineage survives restarts and is shared across replicas.

3. **Always set `entityId` and `operatorId`.** These are the primary query dimensions. Consistent entity IDs enable reconstructing the full provenance chain; consistent operator IDs enable tracing all operations from a given enricher or job.

4. **Include input/output hashes.** Hashes enable detecting whether an operation actually changed the data. This is critical for auditing and change detection.

5. **Correlate with distributed tracing.** Populate the `traceId` field with the current OpenTelemetry trace ID so lineage records can be correlated with infrastructure-level traces.

6. **Use metadata for domain context.** Store enrichment provider names, field counts, error details, or any domain-specific information in the `metadata` map rather than extending the `LineageRecord` class.

7. **Index operator queries in your persistent store.** The `getLineageByOperator` method requires scanning all records when using the in-memory implementation. Ensure your production tracker has proper indexes on `operator_id`.

8. **Consider retention policies.** Lineage data grows over time. Implement TTL-based cleanup or archival in your persistent tracker to manage storage.

> **See also:** [Data Quality](data-quality.md) | [Data Transformation](data-transformation.md) | [GenAI Bridge](genai-bridge.md)
