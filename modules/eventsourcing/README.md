# Firefly Framework - Event Sourcing

[![CI](https://github.com/fireflyframework/fireflyframework-eventsourcing/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-eventsourcing/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive event sourcing for Spring Boot — store domain events as the source of truth with an R2DBC event store, optimistic concurrency, snapshots, transactional outbox, and projections.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Version Semantics](#version-semantics)
- [Configuration](#configuration)
- [Database Schema](#database-schema)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-eventsourcing` is a reactive Event Sourcing library for Spring Boot. Instead of persisting only the current state of an entity, an event-sourced application records every state change as an immutable domain event. The full history of events becomes the source of truth: the current state of any aggregate is derived by replaying its events. This unlocks complete audit trails, temporal ("as-of") queries, reliable integration via published events, and trivial debugging of how an entity reached its current state.

The library is built entirely on Project Reactor and R2DBC, so reads and writes are non-blocking end to end. At its core is the reactive `EventStore` SPI backed by an R2DBC implementation (`R2dbcEventStore`) that provides atomic appends, optimistic concurrency control via aggregate versioning, and global-order event streaming for projections. Around this core the module layers production concerns: a snapshot store to cap replay cost, a transactional outbox for reliable event publishing, read-model projections with checkpointing, and operational hooks for metrics, health, structured logging, resilience, and multi-tenancy.

Within the Firefly Framework, this module sits in the domain/persistence layer and complements the CQRS and orchestration modules. It depends on `fireflyframework-kernel` (shared exceptions and abstractions), `fireflyframework-r2dbc` (reactive database access and utilities), and `fireflyframework-eda` for outbound event publishing — the outbox relays committed events through the EDA abstraction, and the publisher transport is selected with `firefly.eventsourcing.publisher.type` (mapping to the EDA `PublisherType`, e.g. `AUTO`, `KAFKA`, `RABBITMQ`). `fireflyframework-observability` provides metrics, tracing, and health primitives, and `fireflyframework-cache` is an optional dependency used to cache snapshots. Everything is wired automatically through Spring Boot auto-configuration, so adding the dependency and pointing it at an R2DBC datasource is enough to start storing events.

## Features

**Core**
- `AggregateRoot` base class with reflection-based event-handler dispatch (`on(...)` methods)
- Reactive `EventStore` SPI backed by R2DBC (`R2dbcEventStore`); PostgreSQL, MySQL, and H2 supported
- Optimistic concurrency control via per-aggregate versioning (`expectedVersion` on `appendEvents`)
- `@DomainEvent` annotation for declarative event-type registration (bridges to Jackson `@JsonTypeName`)
- `AbstractDomainEvent` with builder pattern and metadata helpers (correlationId, causationId, userId, source)
- `StoredEventEnvelope` wraps domain events with storage metadata (global sequence, created timestamp)
- `EventStream` with query helpers (`getEventsFromVersion`, `getEventsInRange`, `isEmpty`, `size`)
- Rich global-order querying: `streamAllEvents`, `streamEventsByType`, `streamEventsByAggregateType`, `streamEventsByTimeRange`, `streamEventsByMetadata`

**Persistence**
- Flyway-managed schema with 8 migrations (V1–V8) for events, snapshots, outbox, and projection tables
- `BIGSERIAL` global sequence assigned by the database — the INSERT excludes `global_sequence`
- `TEXT` columns for `event_data` and `metadata` (database-agnostic, not JSONB)
- Snapshot store with UPSERT semantics — PK is `(aggregate_id, aggregate_type)`, one snapshot per aggregate
- Transactional Outbox pattern (`EventOutboxService` / `EventOutboxProcessor`) for reliable publishing with exponential-backoff retry

**Projections & evolution**
- `ProjectionService<T>` base class for read-model projections with batch or per-event processing
- Projection checkpoint tracking (`projection_positions`) for resume-after-restart and lag-based health
- Event upcasting for schema evolution via the `EventUpcaster` SPI (`canUpcast` / `upcast`, priority-ordered)

**Operations**
- `@EventSourcingTransactional` with configurable propagation, isolation, retry, and timeout
- Auto-configuration chain (9 conditional configuration classes) — see the imports file below
- Health indicators: EventStore, Outbox, Snapshot, Projection
- Micrometer metrics via `EventStoreMetrics` and `ProjectionMetrics` (timers, counters, gauges)
- Structured logging with MDC keys and reactive context propagation
- Circuit breakers (eventStore, outbox, projection) via Resilience4j (off by default)
- Multi-tenancy via `TenantContext` (off by default)

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- PostgreSQL (recommended) or any R2DBC-compatible database (MySQL, H2). The PostgreSQL, MySQL, and H2 R2DBC drivers are optional dependencies — add the one you use to the classpath.

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eventsourcing</artifactId>
    <!-- Version is managed by the Firefly BOM / parent — omit when inheriting it -->
</dependency>
```

If you inherit the Firefly parent (or import the BOM), the version is managed for you:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.05.08</version>
</parent>
```

Add the R2DBC driver for your database, e.g. PostgreSQL:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
</dependency>
```

## Quick Start

### 1. Define domain events

```java
@DomainEvent("order.placed")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedEvent extends AbstractDomainEvent {
    private String productId;
    private int quantity;
    private BigDecimal totalPrice;
}
```

### 2. Create an aggregate

```java
public class Order extends AggregateRoot {

    private String productId;
    private int quantity;
    private BigDecimal totalPrice;

    // Constructor for loading from the event store
    public Order(UUID id) {
        super(id, "Order");
    }

    // Constructor for creating a new order (command)
    public Order(UUID id, String productId, int quantity, BigDecimal totalPrice) {
        super(id, "Order");
        applyChange(OrderPlacedEvent.builder()
                .aggregateId(id)
                .productId(productId)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build());
    }

    // Event handler — updates state only, no validation
    private void on(OrderPlacedEvent event) {
        this.productId = event.getProductId();
        this.quantity = event.getQuantity();
        this.totalPrice = event.getTotalPrice();
    }
}
```

### 3. Persist and load via the EventStore

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final EventStore eventStore;

    public Mono<Order> placeOrder(String productId, int qty, BigDecimal price) {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, productId, qty, price);

        return eventStore.appendEvents(
                orderId, "Order", order.getUncommittedEvents(), -1L) // -1 = new aggregate
            .doOnSuccess(stream -> order.markEventsAsCommitted())
            .thenReturn(order);
    }

    public Mono<Order> getOrder(UUID orderId) {
        return eventStore.loadEventStream(orderId, "Order")
            .map(stream -> {
                Order order = new Order(orderId);
                order.loadFromHistory(stream.getEvents());
                return order;
            });
    }
}
```

### 4. Point it at a database

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: user
    password: pass

firefly:
  eventsourcing:
    enabled: true
    event-scan-packages: "com.example.myapp"  # where @DomainEvent classes live
```

Flyway runs the bundled V1–V8 migrations on startup to create the `events`, `snapshots`, `event_outbox`, and `projection_positions` tables.

## How It Works

### Command flow (write)

```
Command --> Aggregate --> [validate] --> Event(s) --> EventStore.appendEvents()
                                                         |
                                              +----------+-----------+
                                              |                      |
                                       events table            event_outbox
                                     (BIGSERIAL seq)          (if publisher
                                                                configured)
```

### Read flow (query)

```
EventStore.loadEventStream() --> StoredEventEnvelope[] --> aggregate.loadFromHistory()
                                                               |
                                                        Aggregate (current state)
```

### Auto-configuration chain

Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
R2dbcBeansAutoConfiguration
EventStoreAutoConfiguration
SnapshotAutoConfiguration
EventSourcingAutoConfiguration
EventSourcingProjectionAutoConfiguration
EventSourcingHealthAutoConfiguration
EventSourcingMetricsAutoConfiguration
CircuitBreakerAutoConfiguration        (resilience, off by default)
MultiTenancyAutoConfiguration          (off by default)
```

## Version Semantics

| State                  | `AggregateRoot.version` | `expectedVersion` for `appendEvents` |
|------------------------|:-----------------------:|:------------------------------------:|
| New (no events yet)    | -1                      | -1                                   |
| After 1st event        | 0                       | 0 (for next append)                  |
| After Nth event        | N-1                     | N-1 (for next append)                |

The aggregate version starts at **-1** and increments with each event applied via `applyChange()`. When calling `appendEvents`, pass the aggregate's current version as `expectedVersion` for optimistic concurrency control; a mismatch raises `ConcurrencyException`.

## Configuration

All properties live under the `firefly.eventsourcing.*` prefix (bound by `EventSourcingProperties` and `EventSourcingProjectionProperties`). The block below shows the real keys with their defaults:

```yaml
firefly:
  eventsourcing:
    enabled: true                         # master switch
    event-scan-packages: org.fireflyframework  # packages scanned for @DomainEvent
    store:
      type: r2dbc                         # event store backend
      batch-size: 100
      connection-timeout: 30s
      query-timeout: 30s
      validate-schemas: true
      max-events-per-load: 1000
    snapshot:
      enabled: true
      threshold: 50                       # snapshot after N events
      check-interval: 5m
      keep-count: 3
      max-age: 30d
      store-type: same                    # same | cache | ...
    publisher:
      enabled: true
      type: AUTO                          # AUTO | KAFKA | RABBITMQ ... (EDA PublisherType)
      destination-prefix: events
      async: true
      batch-size: 10
      publish-timeout: 10s
      continue-on-failure: true
      retry:
        enabled: true
        max-attempts: 3
        initial-delay: 1s
        max-delay: 10s
        backoff-multiplier: 2.0
    performance:
      buffer-size: 1000
      metrics-enabled: true
      health-checks-enabled: true
      tracing-enabled: true
      statistics-interval: 1m
      circuit-breaker:
        enabled: false                    # Resilience4j, opt-in
        failure-rate-threshold: 50.0
        minimum-number-of-calls: 10
        sliding-window-size: 60s
        wait-duration-in-open-state: 30s
    projection:
      batch-processing:
        default-batch-size: 100
        default-interval: 5s
        max-batch-size: 1000
        min-interval: 100ms
      health-check:
        timeout: 5s
        max-acceptable-lag: 1000          # events behind before "unhealthy"
        include-details: true
        fail-on-unhealthy-projection: true
      retry:
        default-max-attempts: 3
        default-delay: 1s
        max-delay: 5m
        backoff-multiplier: 2.0
      metrics:
        enabled: true
        include-projection-tags: true
        track-event-processing-time: true
        enable-export: true
```

Key properties:

- `firefly.eventsourcing.enabled` — master switch for the whole library.
- `firefly.eventsourcing.event-scan-packages` — packages scanned for `@DomainEvent` types so events deserialize to the correct class (default `org.fireflyframework`; set to your app's base package).
- `firefly.eventsourcing.snapshot.threshold` — number of events after which a snapshot is written to cap replay cost.
- `firefly.eventsourcing.publisher.type` — selects the EDA transport for outbox publishing (`AUTO` picks the configured EDA provider; e.g. `KAFKA`, `RABBITMQ`).
- `firefly.eventsourcing.performance.circuit-breaker.enabled` and `multi-tenancy` are off by default; enable them only when you need the corresponding resilience or tenant-isolation behavior.

## Database Schema

The library ships 8 Flyway migrations (`V1`–`V8`). Key tables:

| Table                  | Purpose                                | Primary key                       |
|------------------------|----------------------------------------|-----------------------------------|
| `events`               | Append-only event log                  | `event_id` (UUID)                 |
| `snapshots`            | Aggregate state cache                   | `(aggregate_id, aggregate_type)`  |
| `event_outbox`         | Transactional outbox for publishing     | `outbox_id` (UUID)                |
| `projection_positions` | Projection checkpoint tracking          | `projection_name`                 |

The `events` table uses `BIGSERIAL` for `global_sequence` — the database auto-assigns sequence numbers, so the INSERT statement omits that column. `event_data` and `metadata` are `TEXT` (database-agnostic), not JSONB. See [docs/database-schema.md](docs/database-schema.md) for full details.

## Documentation

- Framework docs hub & module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- In-repo guides under [`docs/`](docs/):
  - [Event Sourcing Explained](docs/event-sourcing-explained.md) — concepts and comparison with CRUD
  - [Quick Start](docs/quick-start.md) — step-by-step setup
  - [Architecture](docs/architecture.md) — layers, auto-configuration, event flows
  - [API Reference](docs/api-reference.md) — interfaces and classes
  - [Configuration](docs/configuration.md) — every property with defaults
  - [Database Schema](docs/database-schema.md) — migrations, tables, indexes, triggers
  - [Testing](docs/testing.md) — unit, integration, and projection testing
  - [Account Ledger Tutorial](docs/tutorial-account-ledger.md) — complete worked example
  - [Optional Enhancements](docs/optional-enhancements.md) — circuit breakers, metrics, multi-tenancy, upcasting

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
