# Data Transformation

Data rarely arrives from external providers in exactly the shape your application needs. Field names differ between providers (one calls it `cmp_name`, another calls it `companyName`), values need normalization, and derived fields like "company size tier" must be computed from raw numbers. Handling all of this inline quickly turns your enrichment logic into a tangled mess of ad-hoc mappings.

The data transformation framework in `fireflyframework-starter-data` addresses this by giving you a composable pipeline of small, single-purpose transformation steps. Each step is a function that takes data in and produces data out, and you chain them together so the output of one step feeds into the next. Because each step is a separate unit, it is easy to test in isolation, reorder, or swap out without affecting the rest of the pipeline.

Transformers are fully reactive (they return `Mono<T>`), which means a step can do simple synchronous work like renaming fields or trimming strings, but it can also perform asynchronous operations like looking up a reference value from an external service. A shared `TransformContext` carries request-scoped metadata -- such as tenant ID and request ID -- through every step, so transformers can make context-aware decisions without relying on thread-local state.

This document provides a comprehensive guide to the data transformation framework in `fireflyframework-starter-data`, covering the `DataTransformer` functional interface, `TransformationChain` composition, built-in transformers, and integration patterns.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [DataTransformer Interface](#datatransformer-interface)
- [TransformContext](#transformcontext)
- [TransformationChain](#transformationchain)
- [Built-in Transformers](#built-in-transformers)
- [Creating Custom Transformers](#creating-custom-transformers)
- [Example: Post-Enrichment Normalization Pipeline](#example-post-enrichment-normalization-pipeline)
- [Integration with Enrichment Flow](#integration-with-enrichment-flow)
- [Best Practices](#best-practices)

---

## Overview

The data transformation framework provides a composable, reactive pipeline for reshaping data as it flows through the system. It is designed around two core abstractions:

- **DataTransformer** -- a functional interface representing a single transformation step
- **TransformationChain** -- a composable chain that threads transformers sequentially, piping the output of each step into the input of the next

Transformers operate on arbitrary types and are fully reactive, returning `Mono<T>` results. This makes them suitable for both synchronous field mapping and asynchronous operations (e.g., looking up a reference value from an external service).

---

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                TransformationChain<S, T>                │
│                                                        │
│   then(transformer) -> chain   (fluent composition)    │
│   execute(source, context) -> Mono<T>                  │
│                                                        │
│   ┌──────────────────────────────────────────────┐     │
│   │     Step 1                Step 2              │     │
│   │  DataTransformer<A,B>  DataTransformer<B,C>   │     │
│   │       │                      │                │     │
│   │   transform(A, ctx)     transform(B, ctx)     │     │
│   │       │                      │                │     │
│   │       └──── flatMap ─────────┘                │     │
│   └──────────────────────────────────────────────┘     │
│                                                        │
│   TransformContext carries request-scoped metadata      │
└────────────────────────────────────────────────────────┘
```

**Key classes:**

| Class | Package | Role |
|---|---|---|
| `DataTransformer<S, T>` | `o.f.data.transform` | Functional interface for a single transformation |
| `TransformationChain<S, T>` | `o.f.data.transform` | Composable chain of transformers |
| `TransformContext` | `o.f.data.transform` | Request-scoped metadata passed to every step |
| `FieldMappingTransformer` | `o.f.data.transform.transformers` | Renames map fields according to a mapping |
| `ComputedFieldTransformer` | `o.f.data.transform.transformers` | Adds a computed field to a map |

---

## DataTransformer Interface

`DataTransformer<S, T>` is a `@FunctionalInterface` with a single method:

```java
@FunctionalInterface
public interface DataTransformer<S, T> {

    Mono<T> transform(S source, TransformContext context);
}
```

**Type parameters:**

- `S` -- the source (input) type
- `T` -- the target (output) type

Because it is a functional interface, you can implement transformers as lambda expressions:

```java
DataTransformer<String, String> upperCase = (source, context) ->
    Mono.just(source.toUpperCase());
```

Or as full classes for more complex transformations:

```java
public class TrimTransformer implements DataTransformer<Map<String, Object>, Map<String, Object>> {
    @Override
    public Mono<Map<String, Object>> transform(Map<String, Object> source, TransformContext context) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.replaceAll((key, value) ->
            value instanceof String s ? s.trim() : value
        );
        return Mono.just(result);
    }
}
```

---

## TransformContext

The `TransformContext` is an immutable context object carried through every step of a `TransformationChain`. It provides request-scoped metadata that transformers can read to adjust their behavior.

### Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `requestId` | `String` | -- | Identifier of the originating request |
| `tenantId` | `UUID` | -- | Tenant identifier for multi-tenant isolation |
| `metadata` | `Map<String, Object>` | empty `HashMap` | Extensible map for transformer-specific state |
| `startTime` | `Instant` | `Instant.now()` | When the transformation pipeline started |

### Creating a TransformContext

```java
TransformContext context = TransformContext.builder()
    .requestId("req-abc-123")
    .tenantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
    .metadata(Map.of("source", "enrichment-pipeline", "version", "2.1"))
    .build();
```

### Using Context in Transformers

Transformers can read context to make conditional decisions:

```java
DataTransformer<Map<String, Object>, Map<String, Object>> tenantAware =
    (source, context) -> {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put("tenantId", context.getTenantId().toString());
        result.put("processedAt", context.getStartTime().toString());
        return Mono.just(result);
    };
```

---

## TransformationChain

The `TransformationChain<S, T>` is a composable pipeline that applies `DataTransformer` instances sequentially. Each step's output becomes the next step's input.

### Creating and Building a Chain

```java
TransformationChain<Map<String, Object>, Map<String, Object>> chain =
    TransformationChain.<Map<String, Object>, Map<String, Object>>create()
        .then(new FieldMappingTransformer(Map.of("old_name", "newName")))
        .then(new ComputedFieldTransformer("fullName",
            m -> m.get("first") + " " + m.get("last")));
```

### Executing a Chain

```java
TransformContext context = TransformContext.builder()
    .requestId("req-001")
    .build();

chain.execute(inputMap, context)
    .subscribe(result -> System.out.println("Transformed: " + result));
```

### Key Behaviors

- **Empty chain:** If no transformers are added, `execute()` returns the source value unchanged.
- **Sequential execution:** Transformers are applied in the order they are added via `then()`.
- **Reactive threading:** Each step is connected via `flatMap`, allowing async transformers.
- **Size inspection:** Call `chain.size()` to get the number of transformers in the chain.

```java
TransformationChain<String, String> empty = TransformationChain.create();
assert empty.size() == 0;

// Empty chain passes through the source
empty.execute("hello", context)
    .subscribe(result -> assert result.equals("hello"));
```

---

## Built-in Transformers

The framework ships two built-in transformers in the `o.f.data.transform.transformers` package. Both operate on `Map<String, Object>`, which is the common representation for loosely-typed enrichment data.

### FieldMappingTransformer

Renames fields in a map according to a source-to-target mapping. Fields not listed in the mapping are passed through unchanged. Fields listed in the mapping but absent from the source are ignored.

```java
Map<String, String> mappings = Map.of(
    "first_name", "firstName",
    "last_name", "lastName",
    "e_mail", "email"
);

FieldMappingTransformer transformer = new FieldMappingTransformer(mappings);
```

**Example:**

```java
Map<String, Object> input = new LinkedHashMap<>();
input.put("first_name", "Jane");
input.put("last_name", "Doe");
input.put("age", 30);

transformer.transform(input, context).subscribe(result -> {
    // result = {"firstName": "Jane", "lastName": "Doe", "age": 30}
});
```

**Behavior details:**
- The original source map is not mutated; a new `LinkedHashMap` is created
- Renamed keys replace their source keys (the old key is removed)
- Non-mapped keys are retained as-is

### ComputedFieldTransformer

Adds a new field to the map by applying a computation function to the current map state. If the field already exists, it is overwritten.

```java
ComputedFieldTransformer transformer = new ComputedFieldTransformer(
    "fullName",
    m -> m.get("firstName") + " " + m.get("lastName")
);
```

**Example:**

```java
Map<String, Object> input = Map.of("firstName", "Jane", "lastName", "Doe");

transformer.transform(input, context).subscribe(result -> {
    // result = {"firstName": "Jane", "lastName": "Doe", "fullName": "Jane Doe"}
});
```

**Behavior details:**
- The computation function receives an unmodifiable view of the source map
- The result is stored in a new `LinkedHashMap` to avoid mutating the source
- The computation can return any value type (stored as `Object`)

---

## Creating Custom Transformers

### As a Lambda

For simple, inline transformations:

```java
DataTransformer<Map<String, Object>, Map<String, Object>> trimStrings =
    (source, ctx) -> {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.replaceAll((k, v) -> v instanceof String s ? s.trim() : v);
        return Mono.just(result);
    };
```

### As a Class

For reusable, testable transformations:

```java
public class DefaultValueTransformer
        implements DataTransformer<Map<String, Object>, Map<String, Object>> {

    private final Map<String, Object> defaults;

    public DefaultValueTransformer(Map<String, Object> defaults) {
        this.defaults = defaults;
    }

    @Override
    public Mono<Map<String, Object>> transform(
            Map<String, Object> source, TransformContext context) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new LinkedHashMap<>(source);
            defaults.forEach(result::putIfAbsent);
            return result;
        });
    }
}
```

### As an Async Transformer

For transformations that require external lookups:

```java
public class GeocodingTransformer
        implements DataTransformer<Map<String, Object>, Map<String, Object>> {

    private final GeocodingService geocodingService;

    public GeocodingTransformer(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @Override
    public Mono<Map<String, Object>> transform(
            Map<String, Object> source, TransformContext context) {
        String address = (String) source.get("address");
        if (address == null) {
            return Mono.just(source);
        }

        return geocodingService.geocode(address)
            .map(coords -> {
                Map<String, Object> result = new LinkedHashMap<>(source);
                result.put("latitude", coords.getLat());
                result.put("longitude", coords.getLng());
                return result;
            });
    }
}
```

---

## Example: Post-Enrichment Normalization Pipeline

A common use case is normalizing data after enrichment, where field names from external providers need to be standardized and computed fields need to be derived.

```java
@Configuration
public class NormalizationConfig {

    @Bean
    public TransformationChain<Map<String, Object>, Map<String, Object>> normalizationChain() {
        return TransformationChain.<Map<String, Object>, Map<String, Object>>create()
            // Step 1: Rename provider-specific field names to canonical names
            .then(new FieldMappingTransformer(Map.of(
                "cmp_name", "companyName",
                "cmp_domain", "domain",
                "emp_count", "employeeCount",
                "annual_rev", "annualRevenue"
            )))
            // Step 2: Add computed fields
            .then(new ComputedFieldTransformer("companySize", m -> {
                Object count = m.get("employeeCount");
                if (count instanceof Number n) {
                    int employees = n.intValue();
                    if (employees < 50) return "SMALL";
                    if (employees < 500) return "MEDIUM";
                    return "LARGE";
                }
                return "UNKNOWN";
            }))
            // Step 3: Add a computed revenue tier
            .then(new ComputedFieldTransformer("revenueTier", m -> {
                Object rev = m.get("annualRevenue");
                if (rev instanceof Number n) {
                    double revenue = n.doubleValue();
                    if (revenue < 1_000_000) return "SMB";
                    if (revenue < 100_000_000) return "MID_MARKET";
                    return "ENTERPRISE";
                }
                return "UNKNOWN";
            }))
            // Step 4: Trim all string values
            .then((source, ctx) -> {
                Map<String, Object> result = new LinkedHashMap<>(source);
                result.replaceAll((k, v) ->
                    v instanceof String s ? s.trim() : v);
                return Mono.just(result);
            });
    }
}
```

**Using the chain:**

```java
@Service
public class EnrichmentNormalizer {

    private final TransformationChain<Map<String, Object>, Map<String, Object>> normalizationChain;

    public Mono<Map<String, Object>> normalize(Map<String, Object> enrichedData) {
        TransformContext context = TransformContext.builder()
            .requestId(UUID.randomUUID().toString())
            .build();

        return normalizationChain.execute(enrichedData, context);
    }
}
```

---

## Integration with Enrichment Flow

Transformation chains are typically applied after enrichment to standardize the output before persistence or downstream processing.

```java
@Service
public class EnrichAndTransformPipeline {

    private final DataEnricher enricher;
    private final TransformationChain<Map<String, Object>, Map<String, Object>> chain;
    private final DataQualityEngine qualityEngine;

    public Mono<Map<String, Object>> process(Map<String, Object> rawData) {
        TransformContext context = TransformContext.builder()
            .requestId(UUID.randomUUID().toString())
            .build();

        return enricher.enrich(rawData)                        // 1. Enrich
            .flatMap(enriched -> chain.execute(enriched, context))  // 2. Transform
            .flatMap(transformed ->                                // 3. Validate
                qualityEngine.evaluate(transformed, QualityStrategy.COLLECT_ALL)
                    .flatMap(report -> {
                        if (report.isPassed()) {
                            return Mono.just(transformed);
                        }
                        return Mono.error(new QualityException(report));
                    })
            );
    }
}
```

This pattern creates a three-stage pipeline: **Enrich -> Transform -> Validate**.

---

## Best Practices

1. **Prefer immutability.** Always create a new map in your transformer rather than mutating the source. Both built-in transformers follow this pattern using `new LinkedHashMap<>(source)`.

2. **Keep transformers single-purpose.** Each transformer should do one thing: rename fields, compute a value, trim strings, etc. Compose multiple small transformers via `TransformationChain.then()`.

3. **Use TransformContext for cross-cutting concerns.** Pass request IDs, tenant IDs, and pipeline metadata through the context rather than embedding them in the data. Transformers can read the context to make conditional decisions.

4. **Order matters in chains.** Transformers execute in the order they are added. Place field renames before computed fields so the computation functions can reference the canonical field names.

5. **Leverage the functional interface.** For one-off transformations, use lambda expressions instead of creating a dedicated class. Save classes for transformers that need constructor parameters or complex state.

6. **Handle nulls gracefully.** Transformers should account for missing fields. Check for null values before operating on map entries to avoid `NullPointerException` in the reactive pipeline.

7. **Test chains in isolation.** Since `TransformationChain.execute()` returns a `Mono`, you can test chains with `StepVerifier` from Project Reactor's test module.

8. **Combine with quality gates.** Apply a `DataQualityEngine` evaluation after a transformation chain to catch any data issues introduced during transformation.

> **See also:** [Data Quality](data-quality.md) | [Data Lineage](data-lineage.md) | [GenAI Bridge](genai-bridge.md)
