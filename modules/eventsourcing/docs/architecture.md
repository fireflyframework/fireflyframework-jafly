# Architecture

## System Layers

```
+--------------------------------------------------+
|                Application Layer                 |
|  Services, Controllers, Command Handlers         |
+--------------------------------------------------+
          |                          |
          v                          v
+----------------------+    +------------------------+
|   Domain Layer       |    | Infrastructure Layer   |
|                      |    |                        |
| Event                |    | R2dbcEventStore        |
| AbstractDomainEvent  |    | R2dbcSnapshotStore     |
| AggregateRoot        |    | EventTypeRegistry      |
| @DomainEvent         |    | EventOutboxService     |
| StoredEventEnvelope  |    | EventSourcingPublisher |
| EventStream          |    +------------------------+
+----------------------+             |
          |                          v
          |             +--------------------------------------+
          |             | Transaction Layer                    |
          |             |                                      |
          |             | @EventSourcingTransactional          |
          |             | EventSourcingTransactionalAspect     |
          |             | TransactionalOperator                |
          |             +--------------------------------------+
          |                          |
          v                          v
+--------------------------------------------------+
|              PostgreSQL (R2DBC)                  |
|  events | snapshots | event_outbox | projections |
+--------------------------------------------------+
```

## Domain Layer

### Event Interface

The root abstraction for all domain events. Provides default implementations that read from `@DomainEvent` annotation:

- `getAggregateId()` -- required, identifies the owning aggregate
- `getEventType()` -- default reads from `@DomainEvent` annotation; throws if annotation is missing
- `getMetadata()` -- default returns empty map
- `getEventTimestamp()` -- default returns `Instant.now()`
- `getEventVersion()` -- default returns `1`

Decorated with `@JsonTypeInfo` for polymorphic serialization using the `eventType` property.

### AbstractDomainEvent

Base class that implements `Event` with Lombok `@SuperBuilder` support. Provides:

- Fields: `aggregateId`, `eventTimestamp`, `metadata`, `eventVersion`
- Fluent metadata API: `addMetadata(key, value)`, `addMetadata(map)`
- Builder helpers: `.correlationId(...)`, `.causationId(...)`, `.userId(...)`, `.source(...)`

### @DomainEvent Annotation

Declares a class as a domain event with attributes:

| Attribute | Type | Default | Purpose |
|-----------|------|---------|---------|
| `value` | `String` | (required) | Event type identifier, aliased to `@JsonTypeName.value` |
| `description` | `String` | `""` | Human-readable description |
| `version` | `int` | `1` | Schema version for evolution |
| `publishable` | `boolean` | `true` | Whether to publish to external systems |
| `tags` | `String[]` | `{}` | Categorization tags |

### AggregateRoot

Base class for event-sourced aggregates. Fields:

| Field | Type | Initial Value |
|-------|------|---------------|
| `id` | `UUID` | Set in constructor |
| `aggregateType` | `String` | Set in constructor |
| `version` | `long` | `-1` |
| `uncommittedEvents` | `List<Event>` | Empty list |
| `deleted` | `boolean` | `false` |

Key methods:

| Method | Visibility | Purpose |
|--------|-----------|---------|
| `applyChange(Event)` | `protected` | Adds event to uncommitted list, calls handler, increments version |
| `loadFromHistory(List<StoredEventEnvelope>)` | `public` | Replays events to rebuild state, sets version, clears uncommitted |
| `getUncommittedEvents()` | `public` | Returns unmodifiable list of events not yet persisted |
| `markEventsAsCommitted()` | `public` | Clears the uncommitted list after successful persistence |
| `getCurrentVersion()` | `public` | Returns current version |
| `setCurrentVersion(long)` | `protected` | For snapshot restoration only |
| `markAsDeleted()` | `protected` | Soft delete |

Event handler resolution (in the private `applyEvent` method):
1. Look for a method named `on` accepting the exact event class
2. Look for a method named `on` + event class simple name (e.g., `onMoneyWithdrawnEvent`)
3. Look for any method named `on` with a parameter assignable from the event class
4. Throw `EventHandlerException` if no handler is found

## Infrastructure Layer

### R2dbcEventStore

Implements `EventStore` using Spring R2DBC `DatabaseClient`. Key behaviors:

- **appendEvents**: Wraps the operation in `TransactionalOperator.transactional()`. First checks concurrency by comparing `expectedVersion` with the current max `aggregate_version`. Then inserts events using a SQL INSERT that excludes `global_sequence` (the DB assigns it via `BIGSERIAL`). If an `EventOutboxService` is present (`@Autowired(required = false)`), saves events to the outbox in the same transaction.
- **loadEventStream**: Queries events ordered by `aggregate_version ASC` and maps rows to `StoredEventEnvelope` objects.
- **getAggregateVersion**: Returns `COALESCE(MAX(aggregate_version), -1)` -- returns `-1` for non-existent aggregates.
- **streamAllEvents**: Streams events ordered by `global_sequence ASC`.

JSON data is bound as `String` (TEXT columns), not as JSONB. This makes the store database-agnostic.

### R2dbcSnapshotStore

Implements `SnapshotStore` using `DatabaseClient`. The `snapshots` table has a composite PK of `(aggregate_id, aggregate_type)`, meaning only one snapshot per aggregate is stored (UPSERT on save).

### EventTypeRegistry

Scans the classpath at `ApplicationReadyEvent` for classes that implement `Event` and are annotated with `@JsonTypeName`. Registers them with Jackson's `ObjectMapper` as `NamedType` for polymorphic deserialization.

Scan packages are configured via `firefly.eventsourcing.event-scan-packages` (default: `"org.fireflyframework"`). Multiple packages can be comma-separated.

Manual registration is also available:
- `registerEventType(Class<? extends Event>)` -- reads type name from `@JsonTypeName`
- `registerEventType(Class<? extends Event>, String)` -- uses a custom type name

### EventSourcingPublisher

Bridges event sourcing to the EDA messaging infrastructure (`fireflyframework-eda`). Uses `EventPublisherFactory` to obtain a publisher and routes events to destinations using the pattern `{prefix}.{eventType}` or custom mappings.

### EventOutboxService

Implements the Transactional Outbox pattern. Events are saved to the `event_outbox` table in the same database transaction as the event store write. A background processor (`EventOutboxProcessor`) polls for pending entries and publishes them via `EventSourcingPublisher`.

Features:
- Priority-based processing (1=highest, 10=lowest)
- Exponential backoff retry
- Dead letter queue for permanently failed entries
- Cleanup of completed entries

## Transaction Layer

### @EventSourcingTransactional

Annotation for transactional event sourcing operations. Attributes:

| Attribute | Type | Default |
|-----------|------|---------|
| `propagation` | `Propagation` | `REQUIRED` |
| `publishEvents` | `boolean` | `true` |
| `retryOnConcurrencyConflict` | `boolean` | `false` |
| `maxRetries` | `int` | `3` |
| `retryDelay` | `long` | `100` (ms, exponential backoff) |
| `timeout` | `int` | `-1` (no timeout, in seconds) |
| `readOnly` | `boolean` | `false` |
| `isolation` | `Isolation` | `DEFAULT` |
| `rollbackFor` | `Class<?>[]` | `{}` |
| `noRollbackFor` | `Class<?>[]` | `{}` |
| `transactionManager` | `String` | `""` |

`Propagation` enum: `REQUIRED`, `REQUIRES_NEW`, `MANDATORY`, `NEVER`, `SUPPORTS`, `NOT_SUPPORTED`

`Isolation` enum: `DEFAULT`, `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`

### EventSourcingTransactionalAspect

AOP aspect that intercepts methods annotated with `@EventSourcingTransactional`. Creates a `ReactiveTransactionManager`-backed transaction and publishes events via `EventSourcingPublisher` after successful commit.

## Auto-Configuration Chain

The library has 9 auto-configuration classes. They are loaded via Spring Boot's `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

| Order | Class | Condition | Creates |
|-------|-------|-----------|---------|
| 1 | `R2dbcBeansAutoConfiguration` | `@ConditionalOnClass(ConnectionFactory, R2dbcEntityTemplate)` | `DatabaseClient`, `R2dbcEntityTemplate` (fallbacks) |
| 2 | `EventStoreAutoConfiguration` | `@AutoConfigureAfter(R2dbcBeansAutoConfiguration)`, `@ConditionalOnBean(DatabaseClient, R2dbcEntityTemplate, ConnectionFactory)` | `R2dbcEventStore` |
| 3 | `SnapshotAutoConfiguration` | `firefly.eventsourcing.snapshot.enabled=true` (default) | `R2dbcSnapshotStore`, `SnapshotTrigger` |
| 4 | `EventSourcingAutoConfiguration` | `firefly.eventsourcing.enabled=true` (default) | `EventSourcingPublisher`, `ObjectMapper`, `EventStoreMetrics`, `EventTypeRegistry`, `EventSourcingTransactionalAspect`, `EventUpcastingService`, `EventOutboxService`, `EventOutboxProcessor` |
| 5 | `EventSourcingJacksonConfiguration` | `@ConditionalOnMissingBean(ObjectMapper)` | `ObjectMapper` with JavaTimeModule, polymorphic Event handling |
| 6 | `EventSourcingHealthAutoConfiguration` | `@ConditionalOnClass(HealthIndicator)` | `EventStoreHealthIndicator`, `OutboxHealthIndicator`, `SnapshotStoreHealthIndicator`, `ProjectionHealthIndicator` |
| 7 | `EventSourcingMetricsAutoConfiguration` | `@ConditionalOnClass(MeterRegistry)` | `EventStoreMetrics` |
| 8 | `EventSourcingProjectionAutoConfiguration` | `@ConditionalOnClass(ProjectionService, MeterRegistry)` | `ProjectionHealthIndicator` |
| 9 | `CircuitBreakerAutoConfiguration` | `@ConditionalOnClass(CircuitBreaker)`, `firefly.eventsourcing.resilience.circuit-breaker.enabled=true` (default: **false**) | `eventStoreCircuitBreaker`, `outboxCircuitBreaker`, `projectionCircuitBreaker` |

Additionally, `MultiTenancyAutoConfiguration` loads when `firefly.eventsourcing.multitenancy.enabled=true` (default: **false**).

## Event Persistence Flow

```
1. Service calls eventStore.appendEvents(aggregateId, type, events, expectedVersion)
2. TransactionalOperator begins R2DBC transaction
3. checkConcurrency: SELECT COALESCE(MAX(aggregate_version), -1) WHERE aggregate_id AND aggregate_type
4. If currentVersion != expectedVersion --> throw ConcurrencyException, rollback
5. createEventEnvelopes: assign aggregate_version = baseVersion + index + 1, globalSequence = 0 (placeholder)
6. insertEvents: INSERT INTO events (event_id, aggregate_id, aggregate_type, aggregate_version,
                                     event_type, event_data, metadata, created_at)
   NOTE: global_sequence is NOT in the INSERT -- the database BIGSERIAL assigns it
7. saveToOutboxIfEnabled: if EventOutboxService is wired, save each envelope to event_outbox
8. Transaction commits
9. EventSourcingTransactionalAspect publishes events via EventSourcingPublisher (post-commit)
```

## Event Loading/Replay Flow

```
1. Service calls eventStore.loadEventStream(aggregateId, type)
2. SELECT ... FROM events WHERE aggregate_id AND aggregate_type ORDER BY aggregate_version ASC
3. Each row is mapped to StoredEventEnvelope (event_data TEXT is deserialized via ObjectMapper)
4. EventStream.of() wraps the list with version metadata
5. Service creates empty aggregate: new MyAggregate(id) -- version starts at -1
6. aggregate.loadFromHistory(stream.getEvents()):
   a. Validates all events belong to this aggregate (matching ID and type)
   b. For each envelope, calls applyEvent(envelope.getEvent()) via reflection
   c. Sets version = envelope.getAggregateVersion()
   d. Clears uncommittedEvents
7. Aggregate is now at current state with correct version
```
