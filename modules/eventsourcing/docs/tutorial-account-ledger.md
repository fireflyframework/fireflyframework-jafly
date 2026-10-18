# Tutorial: Account Ledger

This tutorial walks through a complete event-sourced application using the `AccountLedger` aggregate from the library's test suite. Every code snippet below is based on the actual source files located under `src/test/java/org/fireflyframework/eventsourcing/examples/ledger/`.

## What We Are Building

An account ledger that tracks financial transactions for bank accounts. The ledger supports:

- Opening accounts with an initial deposit
- Depositing and withdrawing money
- Freezing and unfreezing accounts
- Closing accounts
- Time-travel queries (reconstructing state at any past moment)
- Snapshot-based performance optimization

## Prerequisites

Complete the [Quick Start](quick-start.md) guide first. You should have a Spring Boot 3.x project with R2DBC, Flyway, and the `fireflyframework-eventsourcing` dependency configured.

## Step 1: Define Domain Events

The `AccountLedger` aggregate uses six domain events. Each event extends `AbstractDomainEvent` and carries the `@DomainEvent` annotation that links it to a `@JsonTypeName` for polymorphic serialization.

### AccountOpenedEvent

```java
@DomainEvent("account.opened")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountOpenedEvent extends AbstractDomainEvent {
    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal initialDeposit;
    private String currency;
}
```

### MoneyDepositedEvent

```java
@DomainEvent("money.deposited")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyDepositedEvent extends AbstractDomainEvent {
    private BigDecimal amount;
    private String source;
    private String reference;
    private String depositedBy;
}
```

### MoneyWithdrawnEvent

```java
@DomainEvent("money.withdrawn")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyWithdrawnEvent extends AbstractDomainEvent {
    private BigDecimal amount;
    private String destination;
    private String reference;
    private String withdrawnBy;
}
```

### AccountFrozenEvent

```java
@DomainEvent("account.frozen")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountFrozenEvent extends AbstractDomainEvent {
    private String reason;
    private String frozenBy;
}
```

### AccountUnfrozenEvent

```java
@DomainEvent("account.unfrozen")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountUnfrozenEvent extends AbstractDomainEvent {
    private String reason;
    private String unfrozenBy;
}
```

### AccountClosedEvent

```java
@DomainEvent("account.closed")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosedEvent extends AbstractDomainEvent {
    private String reason;
    private BigDecimal finalBalance;
    private String closedBy;
}
```

All events share metadata infrastructure from `AbstractDomainEvent`: `aggregateId`, `eventTimestamp`, `metadata` map, `eventVersion`, and builder helpers for `correlationId`, `causationId`, `userId`, and `source`.

## Step 2: Create the Aggregate

The `AccountLedger` aggregate extends `AggregateRoot` and contains two types of methods:

1. **Command methods** -- validate business rules and call `applyChange(event)`.
2. **Event handler methods** -- named `on`, accept a single event parameter, and update internal state. They can be `private`.

```java
@Getter
public class AccountLedger extends AggregateRoot {

    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private boolean frozen;
    private boolean closed;
    private Instant openedAt;
    private Instant lastTransactionAt;

    // Constructor for loading from event store
    public AccountLedger(UUID id) {
        super(id, "AccountLedger");
        this.balance = BigDecimal.ZERO;
    }

    // Constructor for creating a new account (command)
    public AccountLedger(UUID id, String accountNumber, String accountType,
                         UUID customerId, BigDecimal initialDeposit, String currency) {
        super(id, "AccountLedger");
        validateAccountOpening(accountNumber, accountType, customerId, initialDeposit, currency);

        applyChange(AccountOpenedEvent.builder()
                .aggregateId(id)
                .accountNumber(accountNumber)
                .accountType(accountType)
                .customerId(customerId)
                .initialDeposit(initialDeposit)
                .currency(currency)
                .build());
    }
```

### Command Methods

Each command method validates business rules before generating an event:

```java
    public void deposit(BigDecimal amount, String source, String reference, String depositedBy) {
        if (closed) {
            throw new AccountClosedException("Cannot deposit to closed account: " + accountNumber);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Deposit amount must be positive: " + amount);
        }

        applyChange(MoneyDepositedEvent.builder()
                .aggregateId(getId())
                .amount(amount)
                .source(source)
                .reference(reference)
                .depositedBy(depositedBy)
                .build());
    }

    public void withdraw(BigDecimal amount, String destination, String reference, String withdrawnBy) {
        if (closed) {
            throw new AccountClosedException("Cannot withdraw from closed account: " + accountNumber);
        }
        if (frozen) {
            throw new AccountFrozenException("Cannot withdraw from frozen account: " + accountNumber);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Withdrawal amount must be positive: " + amount);
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: balance=" + balance + ", requested=" + amount);
        }

        applyChange(MoneyWithdrawnEvent.builder()
                .aggregateId(getId())
                .amount(amount)
                .destination(destination)
                .reference(reference)
                .withdrawnBy(withdrawnBy)
                .build());
    }

    public void freeze(String reason, String frozenBy) {
        if (closed) {
            throw new AccountClosedException("Cannot freeze closed account: " + accountNumber);
        }
        if (frozen) {
            throw new IllegalStateException("Account is already frozen: " + accountNumber);
        }

        applyChange(AccountFrozenEvent.builder()
                .aggregateId(getId())
                .reason(reason)
                .frozenBy(frozenBy)
                .build());
    }

    public void unfreeze(String reason, String unfrozenBy) {
        if (!frozen) {
            throw new IllegalStateException("Account is not frozen: " + accountNumber);
        }

        applyChange(AccountUnfrozenEvent.builder()
                .aggregateId(getId())
                .reason(reason)
                .unfrozenBy(unfrozenBy)
                .build());
    }

    public void close(String reason, String closedBy) {
        if (closed) {
            throw new IllegalStateException("Account is already closed: " + accountNumber);
        }

        applyChange(AccountClosedEvent.builder()
                .aggregateId(getId())
                .reason(reason)
                .finalBalance(balance)
                .closedBy(closedBy)
                .build());
    }
```

### Event Handlers

Event handlers only update internal state. They are called both when applying new events and when replaying historical events from the event store.

```java
    private void on(AccountOpenedEvent event) {
        this.accountNumber = event.getAccountNumber();
        this.accountType = event.getAccountType();
        this.customerId = event.getCustomerId();
        this.balance = event.getInitialDeposit();
        this.currency = event.getCurrency();
        this.frozen = false;
        this.closed = false;
        this.openedAt = event.getEventTimestamp();
        this.lastTransactionAt = event.getEventTimestamp();
    }

    private void on(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.getAmount());
        this.lastTransactionAt = event.getEventTimestamp();
    }

    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
        this.lastTransactionAt = event.getEventTimestamp();
    }

    private void on(AccountFrozenEvent event) {
        this.frozen = true;
    }

    private void on(AccountUnfrozenEvent event) {
        this.frozen = false;
    }

    private void on(AccountClosedEvent event) {
        this.closed = true;
    }
}
```

`AggregateRoot` resolves event handlers by reflection: it looks for a method named `on` that accepts the exact event class as its single parameter. This is why handler methods can be `private`.

## Step 3: Add Snapshot Support

Snapshots capture aggregate state at a specific version so that you can skip replaying all events from the beginning.

### The Snapshot Class

```java
@Getter
public class AccountLedgerSnapshot extends AbstractSnapshot {

    private final String accountNumber;
    private final String accountType;
    private final UUID customerId;
    private final BigDecimal balance;
    private final String currency;
    private final boolean frozen;
    private final boolean closed;
    private final Instant openedAt;
    private final Instant lastTransactionAt;

    public AccountLedgerSnapshot(UUID aggregateId, long version, Instant createdAt,
                                 String accountNumber, String accountType, UUID customerId,
                                 BigDecimal balance, String currency,
                                 boolean frozen, boolean closed,
                                 Instant openedAt, Instant lastTransactionAt) {
        super(aggregateId, version, createdAt);
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.customerId = customerId;
        this.balance = balance;
        this.currency = currency;
        this.frozen = frozen;
        this.closed = closed;
        this.openedAt = openedAt;
        this.lastTransactionAt = lastTransactionAt;
    }

    @Override
    public String getSnapshotType() {
        return "AccountLedger";
    }
}
```

`AbstractSnapshot` provides the common fields `aggregateId`, `version`, and `createdAt`. Your snapshot class adds the domain-specific state fields.

### Creating and Restoring Snapshots

Add these methods to the `AccountLedger` aggregate:

```java
    public AccountLedgerSnapshot createSnapshot() {
        return new AccountLedgerSnapshot(
                getId(),
                getCurrentVersion(),
                Instant.now(),
                accountNumber, accountType, customerId,
                balance, currency, frozen, closed,
                openedAt, lastTransactionAt
        );
    }

    public static AccountLedger fromSnapshot(AccountLedgerSnapshot snapshot) {
        AccountLedger account = new AccountLedger(snapshot.getAggregateId());
        account.accountNumber = snapshot.getAccountNumber();
        account.accountType = snapshot.getAccountType();
        account.customerId = snapshot.getCustomerId();
        account.balance = snapshot.getBalance();
        account.currency = snapshot.getCurrency();
        account.frozen = snapshot.isFrozen();
        account.closed = snapshot.isClosed();
        account.openedAt = snapshot.getOpenedAt();
        account.lastTransactionAt = snapshot.getLastTransactionAt();
        account.setCurrentVersion(snapshot.getVersion());
        return account;
    }
```

`setCurrentVersion` is a `protected` method on `AggregateRoot` intended for snapshot restoration. It sets the version without generating events.

## Step 4: Build the Service Layer

The service layer orchestrates loading aggregates, executing commands, and persisting events. It uses `EventStore` for event persistence and `SnapshotStore` for snapshots.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLedgerService {

    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;
```

### Creating a New Account

```java
    @EventSourcingTransactional
    public Mono<AccountLedger> openAccount(String accountNumber, String accountType,
                                           UUID customerId, BigDecimal initialDeposit,
                                           String currency) {
        UUID accountId = UUID.randomUUID();

        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setUserId(customerId.toString());

        return Mono.fromCallable(() -> new AccountLedger(
                        accountId, accountNumber, accountType,
                        customerId, initialDeposit, currency
                ))
                .flatMap(account -> eventStore.appendEvents(
                                accountId,
                                "AccountLedger",
                                account.getUncommittedEvents(),
                                -1L
                        )
                        .doOnSuccess(stream -> account.markEventsAsCommitted())
                        .thenReturn(account)
                );
    }
```

The `expectedVersion` is `-1L` because this is a new aggregate. The database function `COALESCE(MAX(aggregate_version), -1)` returns `-1` when no events exist for the aggregate, so `-1L` matches the "no events yet" state.

### Updating an Existing Account

```java
    @EventSourcingTransactional(retryOnConcurrencyConflict = true, maxRetries = 3)
    public Mono<AccountLedger> deposit(UUID accountId, BigDecimal amount,
                                       String description, String reference, String userId) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setUserId(userId);
        EventSourcingLoggingContext.setOperation("deposit");

        return loadAccount(accountId)
                .doOnNext(account -> account.deposit(amount, description, reference, userId))
                .flatMap(this::saveAccount);
    }
```

The `retryOnConcurrencyConflict = true` attribute enables automatic retry with exponential backoff when a `ConcurrencyException` occurs (another transaction modified the same aggregate between load and save).

### Loading with Snapshot Optimization

The `loadAccount` method tries to load a snapshot first. If a snapshot exists, it restores the aggregate from the snapshot and then replays only the events that occurred after the snapshot version.

```java
    private Mono<AccountLedger> loadAccount(UUID accountId) {
        return snapshotStore.loadLatestSnapshot(accountId, "AccountLedger")
                .cast(AccountLedgerSnapshot.class)
                .flatMap(snapshot -> loadAccountFromSnapshot(accountId, snapshot))
                .switchIfEmpty(loadAccountFromEvents(accountId));
    }

    private Mono<AccountLedger> loadAccountFromSnapshot(UUID accountId,
                                                         AccountLedgerSnapshot snapshot) {
        return eventStore.loadEventStream(accountId, "AccountLedger", snapshot.getVersion())
                .map(stream -> {
                    AccountLedger account = AccountLedger.fromSnapshot(snapshot);
                    account.loadFromHistory(stream.getEvents());
                    return account;
                });
    }

    private Mono<AccountLedger> loadAccountFromEvents(UUID accountId) {
        return eventStore.loadEventStream(accountId, "AccountLedger")
                .map(stream -> {
                    AccountLedger account = new AccountLedger(accountId);
                    account.loadFromHistory(stream.getEvents());
                    return account;
                });
    }
```

### Saving Events

The `saveAccount` method computes the expected version as `currentVersion - uncommittedEventCount`. This gives the version the aggregate was at before the new events were applied.

```java
    private Mono<AccountLedger> saveAccount(AccountLedger account) {
        return eventStore.appendEvents(
                        account.getId(),
                        "AccountLedger",
                        account.getUncommittedEvents(),
                        account.getCurrentVersion() - account.getUncommittedEventCount()
                )
                .doOnSuccess(stream -> account.markEventsAsCommitted())
                .thenReturn(account);
    }
```

### Time-Travel Query

One of the key advantages of event sourcing is the ability to reconstruct state at any past moment:

```java
    public Mono<AccountLedger> getAccountAtTime(UUID accountId, Instant pointInTime) {
        return eventStore.loadEventStream(accountId, "AccountLedger")
                .map(stream -> {
                    AccountLedger account = new AccountLedger(accountId);
                    List<StoredEventEnvelope> filteredEvents = stream.getEvents().stream()
                            .filter(envelope -> !envelope.getCreatedAt().isAfter(pointInTime))
                            .collect(Collectors.toList());
                    account.loadFromHistory(filteredEvents);
                    return account;
                });
    }
```

### Creating a Snapshot

```java
    public Mono<Void> createSnapshot(UUID accountId) {
        return loadAccount(accountId)
                .flatMap(account -> {
                    AccountLedgerSnapshot snapshot = account.createSnapshot();
                    return snapshotStore.saveSnapshot(snapshot);
                });
    }
```

The `SnapshotStore` performs an UPSERT because the `snapshots` table has a composite primary key of `(aggregate_id, aggregate_type)`. Only one snapshot per aggregate is stored.

## Step 5: Structured Logging

The service uses `EventSourcingLoggingContext` to set MDC values before each operation. This enriches log output with contextual information for distributed tracing and debugging.

```java
EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
EventSourcingLoggingContext.setUserId(userId);
EventSourcingLoggingContext.setOperation("deposit");
```

With a JSON log formatter, this produces structured log entries:

```json
{
  "message": "Processing deposit: accountId=..., amount=500.00",
  "aggregateId": "abc-123",
  "aggregateType": "AccountLedger",
  "userId": "user-456",
  "operation": "deposit"
}
```

See the [API Reference](api-reference.md) for the full list of 16 MDC keys available on `EventSourcingLoggingContext`.

## Step 6: Integration Test

Here is a Testcontainers-based integration test that exercises the full stack.

```java
@SpringBootTest
@Testcontainers
class AccountLedgerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":"
                        + postgres.getFirstMappedPort() + "/testdb");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    AccountLedgerService service;

    @Test
    void fullAccountLifecycle() {
        UUID customerId = UUID.randomUUID();

        // Open account
        AccountLedger account = service.openAccount(
                "ACC-001", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        ).block();

        assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(account.getCurrentVersion()).isEqualTo(0L);

        UUID accountId = account.getId();

        // Deposit
        account = service.deposit(
                accountId, BigDecimal.valueOf(500.00),
                "Wire Transfer", "REF-001", "user-1"
        ).block();

        assertThat(account.getBalance()).isEqualByComparingTo("1500.00");

        // Withdraw
        account = service.withdraw(
                accountId, BigDecimal.valueOf(200.00),
                "ATM", "ATM-001", "user-1"
        ).block();

        assertThat(account.getBalance()).isEqualByComparingTo("1300.00");

        // Freeze
        account = service.freezeAccount(accountId, "Suspicious activity", "admin-1").block();
        assertThat(account.isFrozen()).isTrue();

        // Withdrawals should be rejected while frozen
        assertThatThrownBy(() ->
                service.withdraw(accountId, BigDecimal.valueOf(100.00), "ATM", "ATM-002", "user-1").block()
        ).hasCauseInstanceOf(AccountFrozenException.class);

        // Unfreeze
        account = service.unfreezeAccount(accountId, "Investigation cleared", "admin-1").block();
        assertThat(account.isFrozen()).isFalse();

        // Create snapshot
        service.createSnapshot(accountId).block();

        // Load from snapshot (transparent to the caller)
        account = service.getAccount(accountId).block();
        assertThat(account.getBalance()).isEqualByComparingTo("1300.00");

        // Close account
        account = service.closeAccount(accountId, "Customer request", "admin-1").block();
        assertThat(account.isClosed()).isTrue();
    }
}
```

## Version Semantics Summary

| State | Version | Events |
|-------|---------|--------|
| New aggregate (before any events) | `-1` | 0 |
| After `AccountOpenedEvent` | `0` | 1 |
| After `MoneyDepositedEvent` | `1` | 2 |
| After `MoneyWithdrawnEvent` | `2` | 3 |
| After `AccountFrozenEvent` | `3` | 4 |

When calling `eventStore.appendEvents(id, type, events, expectedVersion)`:
- Use `-1L` for a brand-new aggregate (no events in the database yet).
- Use `currentVersion - uncommittedEventCount` for an existing aggregate.

## Next Steps

- [Testing](testing.md) -- more testing strategies and CI configuration
- [Database Schema](database-schema.md) -- understand the tables and indexes
- [Optional Enhancements](optional-enhancements.md) -- circuit breakers, metrics, health checks
- [Configuration](configuration.md) -- tune snapshot thresholds, publisher settings, and more
