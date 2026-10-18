# Documentation Index

## Learning Path

Read the documents in this order to go from "what is event sourcing?" to "I can build production systems with it."

| # | Document | Description |
|---|----------|-------------|
| 1 | [Event Sourcing Explained](event-sourcing-explained.md) | What event sourcing is, why it matters, when to use it, when not to |
| 2 | [Quick Start](quick-start.md) | Step-by-step guide: dependency, configuration, events, aggregate, service |
| 3 | [Architecture](architecture.md) | System layers, auto-configuration chain, event persistence and loading flows |
| 4 | [API Reference](api-reference.md) | Every public interface and class with method signatures and defaults |
| 5 | [Configuration](configuration.md) | All `firefly.eventsourcing.*` properties with types and defaults |
| 6 | [Database Schema](database-schema.md) | Flyway migrations V1-V8, table definitions, indexes, triggers, views |
| 7 | [Testing](testing.md) | Unit testing aggregates, integration testing with Testcontainers, projection testing |
| 8 | [Account Ledger Tutorial](tutorial-account-ledger.md) | Complete working example with events, aggregate, service, projection, snapshot |
| 9 | [Optional Enhancements](optional-enhancements.md) | Circuit breakers, metrics, health indicators, structured logging, multi-tenancy, upcasting |

## Key Packages

| Package | Purpose |
|---------|---------|
| `o.f.eventsourcing.domain` | `Event`, `AbstractDomainEvent`, `StoredEventEnvelope`, `EventStream` |
| `o.f.eventsourcing.aggregate` | `AggregateRoot`, `EventHandlerException` |
| `o.f.eventsourcing.annotation` | `@DomainEvent`, `@EventSourcingTransactional` |
| `o.f.eventsourcing.store` | `EventStore`, `ConcurrencyException`, `EventStoreException` |
| `o.f.eventsourcing.store.r2dbc` | `R2dbcEventStore`, `EventEntity`, `GenericEvent` |
| `o.f.eventsourcing.snapshot` | `Snapshot`, `AbstractSnapshot`, `SnapshotStore`, `SnapshotTrigger` |
| `o.f.eventsourcing.snapshot.r2dbc` | `R2dbcSnapshotStore` |
| `o.f.eventsourcing.config` | Auto-configuration classes, `EventSourcingProperties`, `EventTypeRegistry` |
| `o.f.eventsourcing.publisher` | `EventSourcingPublisher` |
| `o.f.eventsourcing.outbox` | `EventOutboxService`, `EventOutboxProcessor`, `EventOutboxRepository` |
| `o.f.eventsourcing.projection` | `ProjectionService`, `ProjectionHealth`, `ProjectionMetrics` |
| `o.f.eventsourcing.transaction` | `EventSourcingTransactionalAspect` |
| `o.f.eventsourcing.logging` | `EventSourcingLoggingContext` (16 MDC keys) |
| `o.f.eventsourcing.monitoring` | `EventStoreMetrics` |
| `o.f.eventsourcing.health` | Health indicators for EventStore, Outbox, Snapshot, Projection |
| `o.f.eventsourcing.resilience` | `CircuitBreakerAutoConfiguration` |
| `o.f.eventsourcing.multitenancy` | `TenantContext`, `MultiTenancyAutoConfiguration` |
| `o.f.eventsourcing.upcasting` | `EventUpcaster`, `EventUpcastingService` |
