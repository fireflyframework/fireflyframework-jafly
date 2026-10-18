# API Reference

## Event Interface

`org.fireflyframework.eventsourcing.domain.Event`

The root interface for all domain events. Decorated with `@JsonTypeInfo(use=Id.NAME, property="eventType")` for polymorphic serialization.

```java
public interface Event {
    UUID getAggregateId();

    // Default: reads from @DomainEvent annotation; throws IllegalStateException if missing
    default String getEventType() { ... }

    // Default: returns Map.of()
    default Map<String, Object> getMetadata() { ... }

    // Default: returns Instant.now()
    default Instant getEventTimestamp() { ... }

    // Default: returns 1
    default int getEventVersion() { ... }
}
```

## AbstractDomainEvent

`org.fireflyframework.eventsourcing.domain.AbstractDomainEvent`

Abstract base class implementing `Event` with Lombok `@SuperBuilder`, `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`.

**Fields:**

| Field | Type | Default |
|-------|------|---------|
| `aggregateId` | `UUID` | `null` |
| `eventTimestamp` | `Instant` | Lazy-initialized to `Instant.now()` |
| `metadata` | `Map<String, Object>` | Lazy-initialized to `new HashMap<>()` |
| `eventVersion` | `int` | `0` (returns `1` via getter if `<= 0`) |

**Instance methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `addMetadata(String key, Object value)` | `AbstractDomainEvent` | Adds a metadata entry, returns `this` |
| `addMetadata(Map<String, Object>)` | `AbstractDomainEvent` | Adds multiple entries, returns `this` |

**Builder helpers** (available on all subclass builders):

| Builder Method | Metadata Key |
|---------------|-------------|
| `.correlationId(String)` | `"correlationId"` |
| `.causationId(String)` | `"causationId"` |
| `.userId(String)` | `"userId"` |
| `.source(String)` | `"source"` |
| `.addMetadata(String, Object)` | Custom key |

## AggregateRoot

`org.fireflyframework.eventsourcing.aggregate.AggregateRoot`

Abstract base class for event-sourced aggregates. Annotated with `@Getter` and `@Slf4j`.

**Constructor:**

```java
protected AggregateRoot(UUID id, String aggregateType)
```

- `id` must not be null
- `aggregateType` must not be null or blank
- Sets `version = -1L`

**Fields:**

| Field | Type | Access | Initial |
|-------|------|--------|---------|
| `id` | `UUID` | public (getter) | Constructor param |
| `aggregateType` | `String` | public (getter) | Constructor param |
| `version` | `long` | package (getter via Lombok) | `-1` |
| `uncommittedEvents` | `List<Event>` | public (unmodifiable getter) | Empty list |
| `deleted` | `boolean` | public (getter) | `false` |

**Methods:**

| Method | Visibility | Signature | Description |
|--------|-----------|-----------|-------------|
| `applyChange` | `protected` | `void applyChange(Event event)` | Validates aggregate ID match, adds to uncommitted list, calls event handler, increments version |
| `loadFromHistory` | `public` | `void loadFromHistory(List<StoredEventEnvelope> events)` | Validates all events match aggregate, applies each, sets version to last envelope's version, clears uncommitted |
| `getUncommittedEvents` | `public` | `List<Event> getUncommittedEvents()` | Returns unmodifiable view |
| `markEventsAsCommitted` | `public` | `void markEventsAsCommitted()` | Clears uncommitted list |
| `hasUncommittedEvents` | `public` | `boolean hasUncommittedEvents()` | True if uncommitted list is non-empty |
| `getUncommittedEventCount` | `public` | `int getUncommittedEventCount()` | Size of uncommitted list |
| `getCurrentVersion` | `public` | `long getCurrentVersion()` | Returns current version |
| `setCurrentVersion` | `protected` | `void setCurrentVersion(long version)` | For snapshot restoration only |
| `markAsDeleted` | `protected` | `void markAsDeleted()` | Sets `deleted = true` |

**Version semantics:**

- New aggregate (no events): version = `-1`
- After first `applyChange`: version = `0`
- After Nth `applyChange`: version = `N-1`

When calling `appendEvents` for a **new** aggregate, pass `expectedVersion = -1L`.

## EventStore Interface

`org.fireflyframework.eventsourcing.store.EventStore`

All methods return Project Reactor types (`Mono` or `Flux`).

```java
// Append events with metadata
Mono<EventStream> appendEvents(UUID aggregateId, String aggregateType,
                                List<Event> events, long expectedVersion,
                                Map<String, Object> metadata);

// Append events without metadata (delegates to above with Map.of())
default Mono<EventStream> appendEvents(UUID aggregateId, String aggregateType,
                                        List<Event> events, long expectedVersion);

// Load complete event stream
Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType);

// Load events from a specific version (inclusive)
Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType, long fromVersion);

// Load events in a version range (both inclusive)
Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType,
                                   long fromVersion, long toVersion);

// Get current aggregate version (-1 if not found, per R2dbcEventStore)
Mono<Long> getAggregateVersion(UUID aggregateId, String aggregateType);

// Check if aggregate exists (version >= 0)
Mono<Boolean> aggregateExists(UUID aggregateId, String aggregateType);

// Stream all events from global sequence 0
Flux<StoredEventEnvelope> streamAllEvents();

// Stream events from a global sequence (inclusive)
Flux<StoredEventEnvelope> streamAllEvents(long fromSequence);

// Stream events by type
Flux<StoredEventEnvelope> streamEventsByType(List<String> eventTypes);

// Stream events by aggregate type
Flux<StoredEventEnvelope> streamEventsByAggregateType(List<String> aggregateTypes);

// Stream events in a time range (both inclusive)
Flux<StoredEventEnvelope> streamEventsByTimeRange(Instant from, Instant to);

// Stream events matching metadata criteria (PostgreSQL JSONB containment)
Flux<StoredEventEnvelope> streamEventsByMetadata(Map<String, Object> metadataCriteria);

// Get current max global sequence (0 if empty)
Mono<Long> getCurrentGlobalSequence();

// Health check
Mono<Boolean> isHealthy();

// Statistics (total events, total aggregates, current global sequence)
Mono<EventStoreStatistics> getStatistics();
```

## SnapshotStore Interface

`org.fireflyframework.eventsourcing.snapshot.SnapshotStore`

```java
Mono<Void> saveSnapshot(Snapshot snapshot);
Mono<Snapshot> loadLatestSnapshot(UUID aggregateId, String snapshotType);
Mono<Snapshot> loadSnapshotAtOrBeforeVersion(UUID aggregateId, String snapshotType, long maxVersion);
Mono<Snapshot> loadSnapshotAtVersion(UUID aggregateId, String snapshotType, long version);
Mono<Boolean> snapshotExists(UUID aggregateId, String snapshotType);
Mono<Long> getLatestSnapshotVersion(UUID aggregateId, String snapshotType);
Mono<Void> deleteSnapshot(UUID aggregateId, String snapshotType, long version);
Mono<Void> deleteAllSnapshots(UUID aggregateId, String snapshotType);
Mono<Long> deleteSnapshotsOlderThan(Instant olderThan);
default Mono<Long> deleteSnapshotsOlderThan(int days);
Mono<Long> keepLatestSnapshots(UUID aggregateId, String snapshotType, int keepCount);
Flux<Snapshot> listSnapshots(UUID aggregateId, String snapshotType);
Flux<Snapshot> listSnapshots(UUID aggregateId, String snapshotType, long fromVersion, long toVersion);
Mono<Long> countSnapshots(UUID aggregateId, String snapshotType);
Mono<SnapshotStatistics> getStatistics();
Mono<Boolean> isHealthy();
Mono<Void> optimize();
```

## Snapshot Interface

`org.fireflyframework.eventsourcing.snapshot.Snapshot`

```java
UUID getAggregateId();
String getSnapshotType();
long getVersion();
Instant getCreatedAt();
default int getSnapshotVersion() { return 1; }
default String getReason() { return null; }
default Long getSizeBytes() { return null; }
default boolean isOlderThan(int days) { ... }
default boolean isForVersion(long version) { ... }
default boolean isNewerThan(long version) { ... }
```

## AbstractSnapshot

`org.fireflyframework.eventsourcing.snapshot.AbstractSnapshot`

Implements `Snapshot`. Provides fields for `aggregateId`, `version`, `createdAt`, `reason`, and `sizeBytes`.

Constructors:
- `AbstractSnapshot(UUID aggregateId, long version, Instant createdAt)`
- `AbstractSnapshot(UUID aggregateId, long version, Instant createdAt, String reason, Long sizeBytes)`

Subclasses must implement `getSnapshotType()`.

## StoredEventEnvelope

`org.fireflyframework.eventsourcing.domain.StoredEventEnvelope`

Wraps a domain event with storage metadata. Uses Lombok `@Data`, `@Builder`, `@Jacksonized`.

| Field | Type | Description |
|-------|------|-------------|
| `eventId` | `UUID` | Unique envelope ID |
| `event` | `Event` | The domain event |
| `aggregateId` | `UUID` | Denormalized for query performance |
| `aggregateType` | `String` | Aggregate type |
| `aggregateVersion` | `long` | Version within aggregate stream |
| `globalSequence` | `long` | Global ordering number |
| `eventType` | `String` | Event type identifier |
| `createdAt` | `Instant` | Persistence timestamp |
| `metadata` | `Map<String, Object>` | Additional metadata (default: `Map.of()`) |

**Factory methods:**

```java
static StoredEventEnvelope of(Event event, String aggregateType,
                               long aggregateVersion, long globalSequence,
                               Map<String, Object> metadata);
static StoredEventEnvelope of(Event event, String aggregateType,
                               long aggregateVersion, long globalSequence);
```

**Convenience methods:**

```java
Object getMetadataValue(String key);
<T> T getMetadataValue(String key, Class<T> type);
String getCorrelationId();    // reads metadata key "correlationId"
String getCausationId();      // reads metadata key "causationId"
String getUserId();           // reads metadata key "userId"
```

## EventStream

`org.fireflyframework.eventsourcing.domain.EventStream`

Uses Lombok `@Data`, `@Builder`.

| Field | Type | Default |
|-------|------|---------|
| `aggregateId` | `UUID` | |
| `aggregateType` | `String` | |
| `currentVersion` | `long` | |
| `fromVersion` | `long` | `0L` |
| `events` | `List<StoredEventEnvelope>` | |

**Methods:**

```java
boolean isEmpty();
int size();
StoredEventEnvelope getFirstEvent();
StoredEventEnvelope getLastEvent();
List<StoredEventEnvelope> getEventsFromVersion(long fromVersion);
List<StoredEventEnvelope> getEventsToVersion(long toVersion);
List<StoredEventEnvelope> getEventsInRange(long fromVersion, long toVersion);

static EventStream empty(UUID aggregateId, String aggregateType);
static EventStream of(UUID aggregateId, String aggregateType, List<StoredEventEnvelope> events);
```

## @DomainEvent Annotation

`org.fireflyframework.eventsourcing.annotation.DomainEvent`

Target: `TYPE`. Retention: `RUNTIME`.

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | `String` | (required) | Event type ID, aliased to `@JsonTypeName.value` |
| `description` | `String` | `""` | Documentation |
| `version` | `int` | `1` | Schema version |
| `publishable` | `boolean` | `true` | Publish to external systems |
| `tags` | `String[]` | `{}` | Categorization tags |

## @EventSourcingTransactional Annotation

`org.fireflyframework.eventsourcing.annotation.EventSourcingTransactional`

Target: `METHOD`, `TYPE`. Retention: `RUNTIME`.

| Attribute | Type | Default |
|-----------|------|---------|
| `propagation` | `Propagation` | `REQUIRED` |
| `publishEvents` | `boolean` | `true` |
| `retryOnConcurrencyConflict` | `boolean` | `false` |
| `maxRetries` | `int` | `3` |
| `retryDelay` | `long` | `100` (ms) |
| `timeout` | `int` | `-1` (seconds, -1 = none) |
| `readOnly` | `boolean` | `false` |
| `isolation` | `Isolation` | `DEFAULT` |
| `rollbackFor` | `Class<? extends Throwable>[]` | `{}` |
| `rollbackForClassName` | `String[]` | `{}` |
| `noRollbackFor` | `Class<? extends Throwable>[]` | `{}` |
| `noRollbackForClassName` | `String[]` | `{}` |
| `transactionManager` | `String` | `""` |

**Propagation enum values:** `REQUIRED`, `REQUIRES_NEW`, `MANDATORY`, `NEVER`, `SUPPORTS`, `NOT_SUPPORTED`

**Isolation enum values:** `DEFAULT`, `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`

## EventTypeRegistry

`org.fireflyframework.eventsourcing.config.EventTypeRegistry`

```java
// Triggered at ApplicationReadyEvent -- scans classpath automatically
@EventListener(ApplicationReadyEvent.class)
void registerEventTypes();

// Manual registration (reads @JsonTypeName annotation)
void registerEventType(Class<? extends Event> eventClass);

// Manual registration with custom type name
void registerEventType(Class<? extends Event> eventClass, String typeName);

// Get all registered type names
Set<String> getRegisteredEventTypes();
```

Scan packages are read from `firefly.eventsourcing.event-scan-packages` (default: `"org.fireflyframework"`).

## EventOutboxService

`org.fireflyframework.eventsourcing.outbox.EventOutboxService`

```java
Mono<EventOutboxEntity> saveToOutbox(StoredEventEnvelope envelope);
Mono<EventOutboxEntity> saveToOutbox(StoredEventEnvelope envelope, int priority, int maxRetries);
Mono<Long> processPendingEntries(int batchSize);
Mono<Long> processRetryEntries(int batchSize);
Flux<EventOutboxEntity> getDeadLetterEntries();
Mono<Long> cleanupCompletedEntries(int olderThanDays);
Mono<OutboxStatistics> getStatistics();
```

`OutboxStatistics` is a record: `(long pendingCount, long processingCount, long completedCount, long failedCount, long deadLetterCount)`

## ProjectionService

`org.fireflyframework.eventsourcing.projection.ProjectionService<T>`

Abstract class for building read model projections. Constructor takes `MeterRegistry`.

| Method | Visibility | Returns | Description |
|--------|-----------|---------|-------------|
| `handleEvent(StoredEventEnvelope)` | `public abstract` | `Mono<Void>` | Process a single event |
| `getCurrentPosition()` | `public abstract` | `Mono<Long>` | Current global sequence position |
| `updatePosition(long)` | `public abstract` | `Mono<Void>` | Save new position |
| `getProjectionName()` | `public abstract` | `String` | Projection identifier |
| `clearProjectionData()` | `protected abstract` | `Mono<Void>` | Clear read model data |
| `getLatestGlobalSequenceFromEventStore()` | `protected abstract` | `Mono<Long>` | For health checks |
| `processBatch(Flux<StoredEventEnvelope>)` | `public` | `Mono<Void>` | Process batch with atomic position update |
| `processIndividually(Flux<StoredEventEnvelope>)` | `public` | `Mono<Void>` | Process events one by one |
| `resetProjection()` | `public` | `Mono<Void>` | Clear data and reset position to 0 |
| `getHealth(long)` | `public` | `Mono<ProjectionHealth>` | Health with lag calculation |
| `checkHealth()` | `public` | `Mono<ProjectionHealth>` | Full health check |
| `onProjectionReset()` | `protected` | `Mono<Void>` | Hook after reset (default: empty) |
| `getMaxAllowedLag()` | `protected` | `long` | Max lag for healthy status (default: 1000) |
| `handleEventType(envelope, type, handler)` | `protected` | `Mono<Void>` | Helper for type filtering |

## EventUpcaster Interface

`org.fireflyframework.eventsourcing.upcasting.EventUpcaster`

```java
boolean canUpcast(String eventType, int eventVersion);
Event upcast(Event event);
default int getTargetVersion() { return 2; }
default int getPriority() { return 0; }   // higher = runs first
```

## TenantContext

`org.fireflyframework.eventsourcing.multitenancy.TenantContext`

Static utility for reactive tenant isolation.

```java
static Mono<String> getCurrentTenantId();          // reads from Reactor context, default "default"
static String getCurrentTenantIdOrDefault();        // blocking version
static Function<Context, Context> withTenantId(String tenantId);  // for .contextWrite(...)
static Mono<Boolean> hasTenantId();
static Function<Context, Context> clear();
static String getDefaultTenant();                   // returns "default"
```

## EventSourcingLoggingContext

`org.fireflyframework.eventsourcing.logging.EventSourcingLoggingContext`

Utility class (`@UtilityClass`) for MDC context management. All methods are static.

**MDC Keys (16 total):**

`correlationId`, `causationId`, `aggregateId`, `aggregateType`, `eventType`, `tenantId`, `userId`, `operation`, `duration`, `version`, `globalSequence`, `outboxId`, `status`, `retryCount`, `priority`, `destination`

**Key methods:**

| Method | Description |
|--------|-------------|
| `setCorrelationId(String)` | Set correlation ID |
| `setCausationId(String)` | Set causation ID |
| `getOrGenerateCorrelationId()` | Get or auto-generate UUID |
| `setAggregateContext(UUID, String)` | Set aggregate ID and type |
| `setAggregateContext(UUID, String, long)` | Set aggregate ID, type, and version |
| `setEventType(String)` | Set event type |
| `setTenantId(String)` | Set tenant ID |
| `setUserId(String)` | Set user ID |
| `setOperation(String)` | Set operation name |
| `setDuration(long)` | Set duration in ms |
| `clearAll()` | Remove all 16 keys |
| `clearAggregateContext()` | Remove aggregate ID, type, version |
| `clearOutboxContext()` | Remove outbox ID, status, retry count, priority |
| `withMdcContext(Mono<T>)` | Write current MDC to Reactor context for propagation |
| `mdcContextPropagation()` | Returns a function that wraps Monos with MDC context |
