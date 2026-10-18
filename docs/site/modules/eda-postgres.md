![Jafly. EDA - PostgreSQL](../assets/modules/eda-postgres.svg)

# EDA - PostgreSQL

Deliver EDA events through a PostgreSQL outbox with LISTEN/NOTIFY support.

Use this transport when PostgreSQL is the messaging boundary. Apply the documented schema and configure publisher/consumer database access; distinguish persistence of the outbox row from successful consumer processing.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-eda-postgres` | `jar` | [modules/eda-postgres](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eda-postgres) |

Part of [Event-driven integration](index.md#event-driven-integration).

## In the onboarding service

A PostgreSQL delivery table can carry application lifecycle events without a separate broker. Its publisher connection is independent of the application write, so recovery must cover a saved application whose event was not published.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda-postgres</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

The outbox here is a persisted delivery queue, not an automatic atomic business-write/event-write boundary. `PostgresEventPublisher` obtains a separate connection for each publication. Design application recovery/idempotency for the case where a business write succeeds and event publication fails; use an explicitly transaction-bound outbox implementation when both writes must commit together.

1. Add the dependency (see above). The PostgreSQL transport beans light up only when you enable them.

2. Enable and configure the PostgreSQL transport in `application.yml`:

```yaml
firefly:
  eda:
    publishers:
      enabled: true
      postgres:
        default:
          enabled: true
          host: localhost
          port: 5432
          database: firefly
          username: firefly
          password: firefly
          schema: public
          outbox-table: firefly_eda_outbox
          default-destination: events
          auto-create-schema: true   # creates the outbox table, indexes, NOTIFY function & trigger
    consumer:
      enabled: true
      postgres:
        default:
          enabled: true
          host: localhost
          port: 5432
          database: firefly
          username: firefly
          password: firefly
          channels: events            # used only when no @EventListener is discovered
          polling-interval: PT30S     # NOTIFY fallback; PT0S to disable
```

3. Publish events with the framework's transport-agnostic annotation. The method result is written to the outbox and dispatched via `NOTIFY`:

```java
@Service
public class PaymentService {

    @PublishResult(publisherType = PublisherType.POSTGRES,
                   destination = "payments",
                   eventType = "payment.completed")
    public Mono<PaymentCompleted> complete(PaymentCommand cmd) {
        return process(cmd); // returned value is inserted into the outbox for "payments"
    }
}
```

4. Consume events by annotating a handler. The consumer derives the channel automatically and `LISTEN`s for it:

```java
@Component
public class PaymentListener {

    @EventListener(destinations = "payments",
                   eventTypes = "payment.completed",
                   consumerType = PublisherType.POSTGRES)
    public Mono<Void> onPaymentCompleted(PaymentCompleted event) {
        log.info("Received payment: {}", event.id());
        return Mono.empty();
    }
}
```

No hand-written SQL, no `spring.r2dbc.*`, and no manual `LISTEN`/`NOTIFY` plumbing. The adapter owns the outbox schema, the dedicated connection factories, and the channel mapping.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

Property-based wiring conditions found in this module:

- `prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyEdaPostgresConsumerAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/config/FireflyEdaPostgresConsumerAutoConfiguration.java).
- `prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyEdaPostgresPublisherAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/config/FireflyEdaPostgresPublisherAutoConfiguration.java).
- `prefix = "firefly.eda.consumer", name = "enabled", havingValue = "true"`. [PostgresEventConsumer.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/consumer/postgres/PostgresEventConsumer.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`PostgresEventPublisher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/publisher/postgres/PostgresEventPublisher.java) | Persists events in a PostgreSQL delivery table and dispatches through `LISTEN`/`NOTIFY`; publication uses its own database connection. |
| [`PostgresEventConsumer`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/consumer/postgres/PostgresEventConsumer.java) | PostgreSQL implementation of `EventConsumer`. |
| [`FireflyEdaPostgresConsumerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/config/FireflyEdaPostgresConsumerAutoConfiguration.java) | Defines `FireflyEdaPostgresConsumerAutoConfiguration`, `fireflyEdaPostgresConsumerConnectionFactory` operations. |
| [`FireflyEdaPostgresPublisherAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/config/FireflyEdaPostgresPublisherAutoConfiguration.java) | Defines `FireflyEdaPostgresPublisherAutoConfiguration`, `fireflyEdaPostgresPublisherConnectionFactory`, `initialiseSchema`, `executeAll` operations. |
| [`PostgresChannelMapper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/publisher/postgres/PostgresChannelMapper.java) | Maps arbitrary destination strings (e.g., `"order-events"`, `"user.created"`) to deterministic, valid PostgreSQL channel names suitable for use with `LISTEN`/`NOTIFY`. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eda-postgres -am verify
./mvnw -pl :fireflyframework-eda-postgres -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [PostgresIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/test/java/org/fireflyframework/eda/integration/PostgresIntegrationTest.java).
- [PostgresChannelMapperTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/test/java/org/fireflyframework/eda/publisher/postgres/PostgresChannelMapperTest.java).
- [BaseIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/test/java/org/fireflyframework/eda/testconfig/BaseIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `publisherIdentifiesItself` test demonstrates one concrete behavior. Excerpt from [PostgresIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/test/java/org/fireflyframework/eda/integration/PostgresIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
@DisplayName("publisher exposes POSTGRES type and remains healthy")
void publisherIdentifiesItself() {
    if (publisher == null) {
        return;
    }
    assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.POSTGRES);
    assertThat(publisher.isAvailable()).isTrue();
    StepVerifier.create(publisher.getHealth())
            .assertNext(health -> {
                assertThat(health.getPublisherType()).isEqualTo(PublisherType.POSTGRES);
                assertThat(health.isAvailable()).isTrue();
                assertThat(health.getStatus()).isEqualTo("UP");
            })
            .verifyComplete();
}
```

## Detailed reference

### How It Works

```
@PublishResult ──▶ PostgresEventPublisher
                      │  INSERT INTO firefly_eda_outbox (... status='PENDING')
                      ▼
            ┌──────────────────────────┐
            │   firefly_eda_outbox      │  AFTER INSERT trigger
            │   (delivery queue)        │  ──▶ pg_notify(channel, id)
            └──────────────────────────┘
                      │ NOTIFY (id only)            │ polling fallback
                      ▼                             ▼  (PENDING rows, attempts < max)
            PostgresEventConsumer ── LISTEN ── drains row, dispatches to @EventListener
                      │ success ──▶ status='PROCESSED'
                      │ failure ──▶ attempts++  (DEAD_LETTER once attempts ≥ max-attempts)
```

The outbox table (created automatically when `auto-create-schema: true`) carries the event `payload` (`BYTEA`), `headers` (`JSONB`), the resolved `channel`, `event_type`, `event_class`, and lifecycle columns (`status`, `attempts`, `error_message`, `created_at`, `processed_at`, `failed_at`). Only the row id travels over `NOTIFY`, so payloads are not bound by PostgreSQL's `NOTIFY` size limit and survive consumer restarts.

### Configuration

All configuration lives under `firefly.eda.*` and is bound from the core `EdaProperties`. Connections are keyed by an id (`default` shown); add more keys for multiple connections. The adapter deliberately ignores `spring.r2dbc.*`.

```yaml
firefly:
  eda:
    enabled: true                      # master EDA switch (default true)
    default-publisher-type: AUTO       # AUTO resolves POSTGRES from config when enabled

    publishers:
      enabled: false                   # global publisher switch (must be true to publish)
      postgres:
        default:
          enabled: false               # enable this PostgreSQL publisher connection
          host: localhost
          port: 5432
          database:                    # database name (no default)
          username:                    # database user (no default)
          password:                    # database password (no default)
          schema: public               # schema holding the outbox table
          outbox-table: firefly_eda_outbox
          default-destination: events  # used when publish() is called without a destination
          auto-create-schema: true     # create table/indexes/function/trigger at startup
          max-pool-size: 10            # publisher R2DBC pool size
          properties: {}               # extra R2DBC connection options

    consumer:
      enabled: false                   # global consumer switch (must be true to consume)
      postgres:
        default:
          enabled: false               # enable this PostgreSQL consumer connection
          host: localhost
          port: 5432
          database:
          username:
          password:
          schema: public
          outbox-table: firefly_eda_outbox
          channels: events             # comma-separated; used only when no @EventListener found
          polling-interval: PT30S      # NOTIFY fallback poll; PT0S disables polling
          max-attempts: 3              # attempts before a row goes to DEAD_LETTER
          batch-size: 50               # rows drained per poll cycle
          max-pool-size: 5             # query pool size (LISTEN uses a separate long-lived conn)
          properties: {}
```

#### Publisher properties (`firefly.eda.publishers.postgres.<id>.*`)

| Property | Default | Description |
|---|---|---|
| `enabled` | `false` | Enables this PostgreSQL publisher connection. A bean is only created when this is `true`, `firefly.eda.publishers.enabled` is `true`, and `host` is non-empty. |
| `host` | `localhost` | PostgreSQL host. |
| `port` | `5432` | PostgreSQL port. |
| `database` / `username` / `password` | _(none)_ | Connection credentials. |
| `schema` | `public` | Schema that holds the outbox table. |
| `outbox-table` | `firefly_eda_outbox` | Outbox table name. |
| `default-destination` | `events` | Destination used when `publish()` is called without one. |
| `auto-create-schema` | `true` | When `true`, idempotently creates the outbox table, indexes, `firefly_eda_notify_event()` function, and `AFTER INSERT` trigger at startup. Set `false` to manage schema via Flyway/Liquibase. |
| `max-pool-size` | `10` | Maximum size of the publisher's R2DBC connection pool. |
| `properties` | `{}` | Additional R2DBC connection options. |

#### Consumer properties (`firefly.eda.consumer.postgres.<id>.*`)

| Property | Default | Description |
|---|---|---|
| `enabled` | `false` | Enables this PostgreSQL consumer connection (requires `firefly.eda.consumer.enabled: true` and a non-empty `host`). |
| `host` / `port` | `localhost` / `5432` | PostgreSQL host and port. |
| `database` / `username` / `password` | _(none)_ | Connection credentials. |
| `schema` / `outbox-table` | `public` / `firefly_eda_outbox` | Where the outbox table lives. |
| `channels` | `events` | Comma-separated channels to `LISTEN` on only when no `@EventListener` annotations are discovered; discovered listeners take precedence. |
| `polling-interval` | `PT30S` | Periodic `NOTIFY` fallback poll for `PENDING` rows. Set to `PT0S` to disable polling and rely solely on `NOTIFY`. |
| `max-attempts` | `3` | Delivery attempts before a row is moved to `DEAD_LETTER`. |
| `batch-size` | `50` | Maximum rows drained per poll cycle. |
| `max-pool-size` | `5` | Query (SELECT/UPDATE) pool size. The `LISTEN` connection is long-lived and separate from this pool. |
| `properties` | `{}` | Additional R2DBC connection options. |

## Continue the application

Use the [Event Sourcing guide](eventsourcing.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eda-postgres/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
