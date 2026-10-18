![Jafly. Common Event Sourcing Library](../../docs/site/assets/modules/eventsourcing.svg)

# Common Event Sourcing Library

Persist domain events, rehydrate aggregates, and manage snapshots through reactive event-store contracts.

Define events and an aggregate, then use EventStore rather than writing the aggregate state as the source of truth. Pay attention to expected versions and concurrency failures when appending events. Configure the backing store and its schema before starting the service.

[Module guide](../../docs/site/modules/eventsourcing.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

If the product needs application history as the source of truth, model submitted and reviewed events and rebuild state from that history. This changes the persistence design; it is more than replacing the tutorial map with a repository.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eventsourcing</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

Follow [Persist onboarding application history](../../docs/site/guides/onboarding-event-store.md) for the complete tested extension to the onboarding service. The API fragments below illustrate individual contracts.

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

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

Flyway runs the bundled V1-V8 migrations on startup to create the `events`, `snapshots`, `event_outbox`, and `projection_positions` tables.

## Configuration

The `firefly.eventsourcing.projection` namespace is bound by [EventSourcingProjectionProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eventsourcing/src/main/java/org/fireflyframework/eventsourcing/config/EventSourcingProjectionProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.eventsourcing.projection.batch-processing` | `BatchProcessing` | `new BatchProcessing()` |
| `firefly.eventsourcing.projection.health-check` | `HealthCheck` | `new HealthCheck()` |
| `firefly.eventsourcing.projection.retry` | `Retry` | `new Retry()` |
| `firefly.eventsourcing.projection.metrics` | `Metrics` | `new Metrics()` |

The `firefly.eventsourcing` namespace is bound by [EventSourcingProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eventsourcing/src/main/java/org/fireflyframework/eventsourcing/config/EventSourcingProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.eventsourcing.enabled` | `boolean` | `true` |
| `firefly.eventsourcing.store` | `EventStore` | `new EventStore()` |
| `firefly.eventsourcing.snapshot` | `Snapshot` | `new Snapshot()` |
| `firefly.eventsourcing.publisher` | `Publisher` | `new Publisher()` |
| `firefly.eventsourcing.performance` | `Performance` | `new Performance()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`EventStore`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eventsourcing/src/main/java/org/fireflyframework/eventsourcing/store/EventStore.java) | Main interface for event storage and retrieval in the event sourcing system. |
| [`AggregateRoot`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eventsourcing/src/main/java/org/fireflyframework/eventsourcing/aggregate/AggregateRoot.java) | Base class for event-sourced aggregates that provides the foundation for implementing Domain-Driven Design aggregates with Event Sourcing. |
| [`DomainEvent`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eventsourcing/src/main/java/org/fireflyframework/eventsourcing/annotation/DomainEvent.java) | Marks a class as a domain event and specifies its event type identifier. |
| [`SnapshotStore`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eventsourcing/src/main/java/org/fireflyframework/eventsourcing/snapshot/SnapshotStore.java) | Interface for snapshot storage and retrieval operations. |
| [`EventSourcingProjectionProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eventsourcing/src/main/java/org/fireflyframework/eventsourcing/config/EventSourcingProjectionProperties.java) | Configuration properties for Event Sourcing projections. |

## Continue the application

Use the [Orchestration guide](../../docs/site/modules/orchestration.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eventsourcing -am verify
./mvnw -pl :fireflyframework-eventsourcing -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/eventsourcing.md).
