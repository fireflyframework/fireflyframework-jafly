# Testing

This guide covers testing strategies for applications built with `fireflyframework-eventsourcing`.

## Unit Testing Aggregates

Aggregates are pure domain objects. They do not depend on Spring, databases, or any external infrastructure. This makes them straightforward to test with plain JUnit.

### Creating a New Aggregate

```java
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AccountLedgerTest {

    @Test
    void shouldCreateAccountWithInitialDeposit() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        AccountLedger account = new AccountLedger(
                accountId, "ACC-001", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        );

        // State was derived from the AccountOpenedEvent
        assertThat(account.getAccountNumber()).isEqualTo("ACC-001");
        assertThat(account.getAccountType()).isEqualTo("CHECKING");
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(account.getCurrency()).isEqualTo("USD");
        assertThat(account.isFrozen()).isFalse();
        assertThat(account.isClosed()).isFalse();

        // One uncommitted event was produced
        assertThat(account.getUncommittedEvents()).hasSize(1);
        assertThat(account.getUncommittedEvents().get(0)).isInstanceOf(AccountOpenedEvent.class);

        // Version starts at -1 and is incremented once by applyChange
        assertThat(account.getCurrentVersion()).isEqualTo(0L);
    }
}
```

Key points about version semantics:
- `AggregateRoot` initializes `version` to `-1`.
- Each call to `applyChange(event)` increments the version by 1.
- After one event is applied (the creation event), the version is `0`.

### Testing Command Methods

```java
@Test
void shouldDepositMoney() {
    AccountLedger account = createAccount(BigDecimal.valueOf(500.00));

    account.deposit(BigDecimal.valueOf(200.00), "Wire Transfer", "REF-001", "user-1");

    assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(700.00));
    assertThat(account.getUncommittedEvents()).hasSize(2); // AccountOpened + MoneyDeposited
    assertThat(account.getCurrentVersion()).isEqualTo(1L);
}

@Test
void shouldRejectWithdrawalFromFrozenAccount() {
    AccountLedger account = createAccount(BigDecimal.valueOf(1000.00));
    account.freeze("Suspicious activity", "admin-1");

    assertThatThrownBy(() ->
            account.withdraw(BigDecimal.valueOf(100.00), "ATM", "REF-002", "user-1")
    ).isInstanceOf(AccountFrozenException.class);
}

@Test
void shouldRejectOverdraft() {
    AccountLedger account = createAccount(BigDecimal.valueOf(100.00));

    assertThatThrownBy(() ->
            account.withdraw(BigDecimal.valueOf(200.00), "ATM", "REF-003", "user-1")
    ).isInstanceOf(InsufficientFundsException.class);
}

private AccountLedger createAccount(BigDecimal initialDeposit) {
    return new AccountLedger(
            UUID.randomUUID(), "ACC-001", "CHECKING",
            UUID.randomUUID(), initialDeposit, "USD"
    );
}
```

### Testing Event Replay

Verify that an aggregate can be reconstructed from a sequence of stored events.

```java
@Test
void shouldReplayEventsToRebuildState() {
    UUID accountId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Instant now = Instant.now();

    // Build stored event envelopes as they would come from the database
    List<StoredEventEnvelope> history = List.of(
            StoredEventEnvelope.of(
                    UUID.randomUUID(), accountId, "AccountLedger", 0L, 1L,
                    "account.opened", now,
                    AccountOpenedEvent.builder()
                            .aggregateId(accountId)
                            .accountNumber("ACC-001")
                            .accountType("CHECKING")
                            .customerId(customerId)
                            .initialDeposit(BigDecimal.valueOf(1000.00))
                            .currency("USD")
                            .build(),
                    null
            ),
            StoredEventEnvelope.of(
                    UUID.randomUUID(), accountId, "AccountLedger", 1L, 2L,
                    "money.deposited", now,
                    MoneyDepositedEvent.builder()
                            .aggregateId(accountId)
                            .amount(BigDecimal.valueOf(500.00))
                            .source("Wire Transfer")
                            .reference("REF-001")
                            .depositedBy("user-1")
                            .build(),
                    null
            )
    );

    // Reconstruct from history
    AccountLedger account = new AccountLedger(accountId);
    account.loadFromHistory(history);

    assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
    assertThat(account.getCurrentVersion()).isEqualTo(1L);
    assertThat(account.getUncommittedEvents()).isEmpty();
}
```

### Testing Snapshots

```java
@Test
void shouldCreateAndRestoreFromSnapshot() {
    AccountLedger original = createAccount(BigDecimal.valueOf(1000.00));
    original.deposit(BigDecimal.valueOf(500.00), "Wire", "REF-001", "user-1");

    // Create snapshot
    AccountLedgerSnapshot snapshot = original.createSnapshot();
    assertThat(snapshot.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
    assertThat(snapshot.getVersion()).isEqualTo(1L);
    assertThat(snapshot.getSnapshotType()).isEqualTo("AccountLedger");

    // Restore from snapshot
    AccountLedger restored = AccountLedger.fromSnapshot(snapshot);
    assertThat(restored.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
    assertThat(restored.getCurrentVersion()).isEqualTo(1L);
    assertThat(restored.getAccountNumber()).isEqualTo(original.getAccountNumber());
}
```

## Testing with H2 (In-Memory Database)

For fast integration tests that do not require PostgreSQL, you can use H2 with R2DBC. The library provides a minimal test schema at `src/test/resources/db/test-schema.sql`.

### Test Schema

The test schema creates the projection-related tables that H2 needs:

```sql
-- projection_positions: checkpoint table for ProjectionService
CREATE TABLE IF NOT EXISTS projection_positions (
    projection_name VARCHAR(255) NOT NULL,
    position BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_projection_positions PRIMARY KEY (projection_name)
);

-- account_balance_projections: example read model table
CREATE TABLE IF NOT EXISTS account_balance_projections (
    id BIGSERIAL NOT NULL,
    account_id UUID NOT NULL,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_balance_projections PRIMARY KEY (id),
    CONSTRAINT uk_account_balance_projections_account_id UNIQUE (account_id)
);
```

### H2 Test Configuration

```yaml
# src/test/resources/application-test.yml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1
    username: sa
    password:

  sql:
    init:
      mode: always
      schema-locations: classpath:db/test-schema.sql

firefly:
  eventsourcing:
    enabled: true
    event-scan-packages: "com.example.myapp"
    store:
      type: r2dbc
    snapshot:
      enabled: false
    publisher:
      enabled: false
```

### H2 Limitations

H2 does not support several PostgreSQL features used in the production migrations:

- **BIGSERIAL** -- H2 uses `IDENTITY` or `AUTO_INCREMENT` instead
- **Partial indexes** (e.g., `WHERE status = 'PENDING'`)
- **Materialized views**
- **Custom triggers and functions** (PL/pgSQL)
- **`gen_random_uuid()`**
- **Table partitioning**

For tests that need the full `events`, `snapshots`, or `event_outbox` tables, you must either create H2-compatible versions of those tables in your test schema or use Testcontainers with a real PostgreSQL instance.

## Integration Testing with Testcontainers

Testcontainers provides a real PostgreSQL instance for your tests. This is the recommended approach for testing the full event sourcing stack including Flyway migrations, concurrency, and outbox processing.

### Dependencies

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>r2dbc</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### Testcontainers Configuration

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
        // R2DBC URL (for the reactive event store)
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":"
                        + postgres.getFirstMappedPort() + "/testdb");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        // JDBC URL (for Flyway migrations -- Flyway does not support R2DBC)
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }
}
```

### Full Integration Test Example

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
    private EventStore eventStore;

    @Autowired
    private SnapshotStore snapshotStore;

    @Test
    void shouldPersistAndReloadAggregate() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // Create aggregate and persist events
        AccountLedger account = new AccountLedger(
                accountId, "ACC-001", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        );

        eventStore.appendEvents(
                accountId, "AccountLedger",
                account.getUncommittedEvents(), -1L
        ).block();

        account.markEventsAsCommitted();

        // Reload from event store
        EventStream stream = eventStore.loadEventStream(accountId, "AccountLedger").block();
        AccountLedger reloaded = new AccountLedger(accountId);
        reloaded.loadFromHistory(stream.getEvents());

        assertThat(reloaded.getAccountNumber()).isEqualTo("ACC-001");
        assertThat(reloaded.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(reloaded.getCurrentVersion()).isEqualTo(0L);
    }

    @Test
    void shouldDetectConcurrencyConflict() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // Create account
        AccountLedger account = new AccountLedger(
                accountId, "ACC-002", "SAVINGS", customerId,
                BigDecimal.valueOf(500.00), "USD"
        );
        eventStore.appendEvents(
                accountId, "AccountLedger",
                account.getUncommittedEvents(), -1L
        ).block();
        account.markEventsAsCommitted();

        // Two concurrent loads -- both see version 0
        AccountLedger instance1 = loadAccount(accountId);
        AccountLedger instance2 = loadAccount(accountId);

        // First write succeeds (expectedVersion = 0)
        instance1.deposit(BigDecimal.valueOf(100.00), "Wire", "REF-001", "user-1");
        eventStore.appendEvents(
                accountId, "AccountLedger",
                instance1.getUncommittedEvents(), 0L
        ).block();

        // Second write fails -- the version in the database is now 1, but we expected 0
        instance2.deposit(BigDecimal.valueOf(200.00), "Wire", "REF-002", "user-2");
        assertThatThrownBy(() ->
                eventStore.appendEvents(
                        accountId, "AccountLedger",
                        instance2.getUncommittedEvents(), 0L
                ).block()
        ).hasCauseInstanceOf(ConcurrencyException.class);
    }

    @Test
    void shouldSaveAndLoadSnapshot() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // Create account with several transactions
        AccountLedger account = new AccountLedger(
                accountId, "ACC-003", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        );
        account.deposit(BigDecimal.valueOf(200.00), "Wire", "REF-001", "user-1");
        account.deposit(BigDecimal.valueOf(300.00), "Wire", "REF-002", "user-1");

        eventStore.appendEvents(
                accountId, "AccountLedger",
                account.getUncommittedEvents(), -1L
        ).block();
        account.markEventsAsCommitted();

        // Save snapshot at version 2
        AccountLedgerSnapshot snapshot = account.createSnapshot();
        snapshotStore.saveSnapshot(snapshot).block();

        // Load snapshot
        AccountLedgerSnapshot loaded = (AccountLedgerSnapshot) snapshotStore
                .loadLatestSnapshot(accountId, "AccountLedger").block();

        assertThat(loaded).isNotNull();
        assertThat(loaded.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
        assertThat(loaded.getVersion()).isEqualTo(2L);
    }

    private AccountLedger loadAccount(UUID accountId) {
        EventStream stream = eventStore.loadEventStream(accountId, "AccountLedger").block();
        AccountLedger account = new AccountLedger(accountId);
        account.loadFromHistory(stream.getEvents());
        return account;
    }
}
```

## Projection Testing

Projection tests verify that your read model is updated correctly as events flow through the system.

```java
@SpringBootTest
@Testcontainers
class AccountBalanceProjectionTest {

    // ... Testcontainers setup (same as above) ...

    @Autowired
    private AccountBalanceProjection projection;

    @Autowired
    private EventStore eventStore;

    @Test
    void shouldUpdateBalanceOnDeposit() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // Persist events
        AccountLedger account = new AccountLedger(
                accountId, "ACC-001", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        );
        account.deposit(BigDecimal.valueOf(500.00), "Wire", "REF-001", "user-1");

        eventStore.appendEvents(
                accountId, "AccountLedger",
                account.getUncommittedEvents(), -1L
        ).block();

        // Process events through the projection
        projection.processBatch(2).block();

        // Verify the read model
        Long position = projection.getCurrentPosition().block();
        assertThat(position).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void shouldResetProjection() {
        projection.resetProjection().block();

        Long position = projection.getCurrentPosition().block();
        assertThat(position).isEqualTo(0L);
    }
}
```

## Reactive Testing with StepVerifier

Since all store operations return `Mono` or `Flux`, use Reactor's `StepVerifier` for precise reactive assertions.

```java
import reactor.test.StepVerifier;

@Test
void shouldAppendEventsReactively() {
    UUID accountId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();

    AccountLedger account = new AccountLedger(
            accountId, "ACC-001", "CHECKING", customerId,
            BigDecimal.valueOf(1000.00), "USD"
    );

    StepVerifier.create(
            eventStore.appendEvents(
                    accountId, "AccountLedger",
                    account.getUncommittedEvents(), -1L
            )
    )
    .assertNext(stream -> {
        assertThat(stream.getCurrentVersion()).isEqualTo(0L);
        assertThat(stream.getEvents()).hasSize(1);
    })
    .verifyComplete();
}

@Test
void shouldStreamAllEventsReactively() {
    StepVerifier.create(
            eventStore.streamAllEvents(0L)
                    .take(10)
    )
    .thenConsumeWhile(envelope -> {
        assertThat(envelope.getGlobalSequence()).isPositive();
        assertThat(envelope.getEvent()).isNotNull();
        return true;
    })
    .verifyComplete();
}
```

## CI Configuration

The project uses Java 21 for compilation and testing. Below is a GitHub Actions workflow that runs tests with Testcontainers.

```yaml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Run tests
        run: mvn verify -B

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: target/surefire-reports/
```

Testcontainers starts a PostgreSQL Docker container automatically during the test run. The `ubuntu-latest` runner includes Docker, so no additional setup is needed.

### Maven Surefire Configuration

Ensure your `pom.xml` configures Surefire for Java 21:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>--add-opens java.base/java.lang.reflect=ALL-UNNAMED</argLine>
    </configuration>
</plugin>
```

The `--add-opens` flag is needed because `AggregateRoot` uses reflection to invoke private event handler methods.

## Test Organization

A recommended test structure:

```
src/test/java/
  com/example/myapp/
    aggregate/
      TaskTest.java                  # Unit tests for aggregate logic
    events/
      TaskCreatedEventTest.java      # Serialization round-trip tests
    service/
      TaskServiceIntegrationTest.java # Testcontainers integration tests
    projection/
      TaskProjectionTest.java        # Projection update tests
src/test/resources/
  application-test.yml               # Test configuration
  db/
    test-schema.sql                  # H2-compatible schema for fast tests
```

## Next Steps

- [Tutorial: Account Ledger](tutorial-account-ledger.md) -- a complete working example
- [Configuration](configuration.md) -- tune test-specific properties
- [Optional Enhancements](optional-enhancements.md) -- circuit breakers, metrics, health checks
