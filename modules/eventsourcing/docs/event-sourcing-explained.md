# Event Sourcing Explained

## What Is Event Sourcing?

Event sourcing is an architectural pattern where state changes are captured as a sequence of immutable events. Instead of storing only the current state of an entity (the traditional CRUD approach), you store every change that has ever happened to it.

The current state of any entity is derived by replaying its event history from the beginning (or from a snapshot).

**Example: A bank account**

Traditional CRUD stores a single row:

```
| account_id | balance | status |
|------------|---------|--------|
| abc-123    | 1150.00 | ACTIVE |
```

Event sourcing stores the history:

```
1. AccountOpened   { initialDeposit: 1000.00 }
2. MoneyDeposited  { amount: 250.00 }
3. MoneyWithdrawn  { amount: 100.00 }
```

Replaying these three events produces `balance = 1150.00`. But you also know *how* you got there.

## Traditional CRUD vs Event Sourcing

| Concern | CRUD | Event Sourcing |
|---------|------|----------------|
| What is stored | Current state | Every state change (event) |
| Audit trail | Must be built separately | Built in -- the event log *is* the audit trail |
| Temporal queries | Not possible without extra work | Replay events up to any point in time |
| Schema changes | ALTER TABLE, migrate data | Old events remain unchanged; use upcasting |
| Debugging | "Why is balance 1150?" -- unknown | Replay events to see exactly what happened |
| Storage | One row per entity | One row per event (more storage, but append-only) |
| Concurrency | Last-write-wins or pessimistic locks | Optimistic concurrency via version numbers |
| Integration | Polling or CDC for change propagation | Events are the integration mechanism |

## Key Concepts

### Events

An event is an immutable fact that something happened in the past. Events are named in past tense:

- `AccountOpened` (not `OpenAccount`)
- `MoneyDeposited` (not `DepositMoney`)
- `OrderShipped` (not `ShipOrder`)

Events carry all the data needed to describe what happened. In this library, events implement the `Event` interface and are annotated with `@DomainEvent`:

```java
@DomainEvent("money.deposited")
@SuperBuilder @Getter @NoArgsConstructor @AllArgsConstructor
public class MoneyDepositedEvent extends AbstractDomainEvent {
    private BigDecimal amount;
    private String source;
}
```

### Aggregates

An aggregate is a cluster of domain objects treated as a single unit for data changes. In event sourcing, the aggregate:

1. **Receives commands** (method calls with business intent)
2. **Validates business rules** (throws exceptions if rules are violated)
3. **Produces events** (calls `applyChange(event)`)
4. **Updates its own state** from events (via `on(SomeEvent)` handler methods)

The aggregate never modifies its state directly -- all state changes go through events.

```java
public class Account extends AggregateRoot {
    private BigDecimal balance;

    public void withdraw(BigDecimal amount) {
        // 1. Validate business rules
        if (balance.compareTo(amount) < 0)
            throw new InsufficientFundsException(...);

        // 2. Produce event (state change is NOT done here)
        applyChange(MoneyWithdrawnEvent.builder()
            .aggregateId(getId()).amount(amount).build());
    }

    // 3. State is updated here, in the event handler
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
    }
}
```

### Event Store

The event store is the persistence layer that stores events and provides retrieval capabilities. It guarantees:

- **Atomicity** -- all events from a single command are saved together or not at all
- **Ordering** -- events within an aggregate are ordered by version
- **Global ordering** -- all events across all aggregates have a global sequence number
- **Optimistic concurrency** -- concurrent writes to the same aggregate are detected via version checking

In this library, the `EventStore` interface provides reactive methods backed by R2DBC.

### Projections (Read Models)

Since the event store is optimized for writes (append-only), reads require a different strategy. A **projection** listens to events and builds a read model optimized for queries.

For example, the event store might contain thousands of `MoneyDeposited` and `MoneyWithdrawn` events. A projection builds a single `account_balance` row that can be queried instantly.

Projections are eventually consistent -- they update asynchronously after events are committed.

### Snapshots

For aggregates with many events, replaying the entire history on every load becomes slow. A **snapshot** captures the aggregate state at a specific version so that only events after the snapshot need to be replayed.

```
Without snapshot: replay 10,000 events (slow)
With snapshot at version 9,900: load snapshot + replay 100 events (fast)
```

## When to Use Event Sourcing

Event sourcing is a good fit when:

- **Audit trails are required** -- financial systems, healthcare, compliance
- **You need temporal queries** -- "what was the state at time T?"
- **Business events are the natural model** -- transaction ledgers, order processing
- **You need reliable integration** -- events drive downstream systems
- **Complex business rules benefit from replaying history** -- fraud detection, analytics

## When NOT to Use Event Sourcing

Event sourcing adds complexity. Avoid it when:

- **Simple CRUD is sufficient** -- user profiles, settings, basic content management
- **You need ad-hoc queries across entities** -- event sourcing optimizes for single-aggregate reads; cross-aggregate queries require projections
- **The team is unfamiliar with the pattern** -- the learning curve is real
- **Storage cost is a primary concern** -- event sourcing stores more data than CRUD
- **Events are not a natural model** -- if your domain is purely about current state without meaningful history

## Next Steps

- [Quick Start](quick-start.md) -- build your first event-sourced application
- [Architecture](architecture.md) -- understand the system design
- [Account Ledger Tutorial](tutorial-account-ledger.md) -- see a complete working example
