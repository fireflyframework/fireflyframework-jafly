![Jafly. EDA - PostgreSQL](../../docs/site/assets/modules/eda-postgres.svg)

# EDA - PostgreSQL

Deliver EDA events through a PostgreSQL outbox with LISTEN/NOTIFY support.

Use this transport when PostgreSQL is the messaging boundary. Apply the documented schema and configure publisher/consumer database access; distinguish persistence of the outbox row from successful consumer processing.

[Module guide](../../docs/site/modules/eda-postgres.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A PostgreSQL delivery table can carry application lifecycle events without a separate broker. The default dedicated publisher pool is independent of an application write. Atomic business and event writes require the exact same connection factory and an owner transaction.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda-postgres</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

The publisher appends through Spring Data on `fireflyEdaPostgresPublisherConnectionFactory`. When that exact connection factory backs the owner's reactive transaction, the append shares its connection and commit, rollback, or cancellation. To couple a Core business write and its event, provide that named bean as the owner's exact connection factory and publish inside its transaction. Completion of the insert does not commit an enclosing transaction. The default dedicated transport pool remains separate from other business connection factories; recovery and idempotency are still required across those boundaries.

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
          password: ${EDA_POSTGRES_PASSWORD}
          schema: public
          outbox-table: firefly_eda_outbox
          default-destination: events
          auto-create-schema: false  # operator provisions the publisher schema and delivery protocol
    consumer:
      enabled: true
      postgres:
        default:
          enabled: true
          host: localhost
          port: 5432
          database: firefly
          username: firefly
          password: ${EDA_POSTGRES_PASSWORD}
          channels: events            # unioned with registered PostgreSQL listener destinations
          polling-interval: PT30S     # positive polling is mandatory; notifications only wake it
          delivery-mode: ANNOTATED
          max-pool-size: 10
          lease-duration: PT30S
          handler-timeout: PT30S
          query-timeout: PT5S
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

5. Register trusted payload types explicitly. This bean uses application-owned class tokens; stored `event_class` and header strings never load classes:

```java
@Bean
PostgresEventDecoder postgresEventDecoder(MessageSerializer serializer) {
    return new RegisteredPostgresEventDecoder(serializer, Map.of(
        PaymentCompleted.class.getName(), PaymentCompleted.class,
        "payment.completed", PaymentCompleted.class));
}
```

Import `PostgresEventDecoder` from `org.fireflyframework.eda.consumer.postgres`, `RegisteredPostgresEventDecoder` from its `impl` package, and `MessageSerializer` from `org.fireflyframework.eda.serialization`. An unknown class identifier fails decoding even if another event type is registered. `Object.class` decoding requires an explicit registration.

6. Install the reviewed delivery protocol below and explicitly activate the worker through `PostgresEventConsumer.start()` or a processing subscription to `consume()`. Compose and await the returned publisher in the application's lifecycle. A configured/enabled bean is not a running or ready worker; this block adds no automatic lifecycle activation.

Application handlers use the event port. The technical adapter owns channel mapping and its named resources; schema installation remains an operator action.

## Configuration

Configuration binds through the shared `EdaProperties` class under `firefly.eda.consumer.postgres.default`. Only the selected, enabled PostgreSQL consumer validates its runtime bounds. Polling must be positive whole milliseconds; notification payloads carry no delivery authority. Concurrency is 1–100, batch size 1–1024, pool maximum 1–100, and query timeout 1–60 whole seconds. The handler timeout cannot exceed the lease. Defaults are a 30-second lease and handler timeout, one-second retry/recovery delay, capped 30-second LISTEN recovery backoff, and five-second connect, acquire, query, and shutdown budgets.

`fireflyEdaPostgresConsumerQueryConnectionFactory` is a lazy, named, non-default bounded query pool. `fireflyEdaPostgresConsumerConnectionFactory` supplies separate raw LISTEN connections. Neither replaces a Core business factory, template, repository, or transaction manager. An explicitly supplied query factory must be independent and compatible with the repository's ownership contract. The framework closes only its own query pool when the context closes; `stop()` preserves that pool for a later start and never disposes an external factory.

The original five-argument `PostgresEventConsumer` constructor remains link-compatible, but starting that legacy construction now fails before resource access because it lacks the mandatory repository and decoder. Migrate direct callers to the additive seven-argument constructor or the configured bean. Missing decoder configuration fails selected-consumer bean creation; missing or incompatible protocol fails `start()` before admission.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`PostgresEventPublisher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/publisher/postgres/PostgresEventPublisher.java) | Persists events in a PostgreSQL delivery table and dispatches through `LISTEN`/`NOTIFY`; publication uses Spring Data and joins an owner transaction on its configured connection factory. |
| [`PostgresEventConsumer`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/consumer/postgres/PostgresEventConsumer.java) | PostgreSQL implementation of `EventConsumer`. |
| [`FireflyEdaPostgresConsumerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/config/FireflyEdaPostgresConsumerAutoConfiguration.java) | Defines `FireflyEdaPostgresConsumerAutoConfiguration`, `fireflyEdaPostgresConsumerConnectionFactory` operations. |
| [`FireflyEdaPostgresPublisherAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/config/FireflyEdaPostgresPublisherAutoConfiguration.java) | Defines `FireflyEdaPostgresPublisherAutoConfiguration`, `fireflyEdaPostgresPublisherConnectionFactory`, `initialiseSchema`, `executeAll` operations. |
| [`PostgresChannelMapper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-postgres/src/main/java/org/fireflyframework/eda/publisher/postgres/PostgresChannelMapper.java) | Maps arbitrary destination strings (e.g., `"order-events"`, `"user.created"`) to deterministic, valid PostgreSQL channel names suitable for use with `LISTEN`/`NOTIFY`. |

## Fenced delivery repository

`PostgresDeliveryRepository` provides bounded claims, fenced acknowledgments and retries, dead-letter transitions, and protocol verification through Spring Data. `SpringDataPostgresDeliveryRepository` accepts an explicit independent technical connection factory, schema, table, and a transaction timeout of 1–60 whole seconds. Each operation uses its own `REQUIRES_NEW` transaction and completes connection release before returning a result. An outer transaction does not own a claim. A release error after commit withholds the result; the durable claim then recovers through lease expiry.

Supply the underlying independent factory explicitly. Transaction-aware proxies, single-connection factories, scoped factories, and their supported infrastructure aliases are rejected before acquisition or scoped target resolution. Custom connection-reusing or close-suppressing wrappers are unsupported. The repository neither looks up nor disposes a default/business pool.

`PostgresEventConsumer` uses this repository for every claim and durable transition. A bounded/coalesced poll and notification loop shares concurrency permits; a committed batch is released from the query connection before handlers run. Query or LISTEN failures recover with bounded delays. Stop interrupts waiting handlers and bounds cleanup; no lease is renewed. Transport delivery remains at least once, and Core operation journals/retry keys remain authoritative for business effects.

The default `ANNOTATED` mode waits for the strict listener outcome before fenced ACK/retry/dead-letter handling. `consume()` subscribers in this mode are read-only observers; missing observer demand cannot block handlers or ACK rows. `MANUAL` mode permits exactly one processing subscriber, claims only against positive demand, does not invoke annotations, and exposes cold fenced `acknowledge()`/`reject()` callbacks. Cancelling, timing out, or losing a subscriber never substitutes an ACK for missing processing. An already submitted mutation can have an unknown outcome if its result is lost. Stale callbacks fail rather than acknowledging a newer claim. A callback failure can leave the row leased until expiry; the runtime does not infer a successful transition from a lost result.

The independent group repository described below implements the same delivery port and can be supplied explicitly. The default bean still selects the global repository; first-class group selection and deployment cutover remain separate integration work. This block adds no automatic migration or group registration, lease renewal, retention deletion, or production-readiness guarantee.

Before admitting workers, stop old consumers and review a one-time migration:

```java
import org.fireflyframework.eda.consumer.postgres.PostgresDeliverySchema;

String migration = PostgresDeliverySchema.installationSql("public", "firefly_eda_outbox");
```

Apply the reviewed script through the operator's deployment process, then call `verifyProtocol()` before starting workers. It adds four lease columns, two indexes, and a version marker without rewriting existing IDs, payloads, attempts, or terminal states. A repeated installation fails atomically. The legacy publisher's schema initializer does not install this protocol. Mixed old/new workers are unsupported; verify permissions, retained Core journals/retry keys, and cutover separately. Protocol verification rejects incompatible metadata or RLS rather than changing policies or grants.

Claims contain an immutable mapped row, claim-time attempt limit, UUID token, monotonic generation, and database lease deadline. A request snapshots 1–256 channels, a batch limit of 1–1024, a positive attempt limit, and a whole-millisecond lease of up to 30 days. Retry delay permits zero through 30 days. Missing or impossible result counts fail. `ALREADY_APPLIED` is limited to the identical terminal action and claim; repeated retry rejection is `STALE` and does not reschedule or increment again.

Locks precede database clock evaluation. A transaction-local `pg_catalog` search path protects technical type/function/operator resolution while every target relation remains quoted and qualified; the caller's path is restored at transaction completion. See [PostgreSQL's schema search path rules](https://www.postgresql.org/docs/16/ddl-schemas.html). Stored event class names remain inert strings; registered-type decoding belongs to the consuming runtime.

## Independent durable consumer groups

The group adapter implements the same `PostgresDeliveryRepository` port with a group identity bound to its constructor. Use the exact dedicated technical query ConnectionFactory. The raw LISTEN connection remains separate; notifications wake workers, while durable rows and repeated polling determine delivery. Runtime/default bean selection is a separate integration gate.

Render `PostgresConsumerGroupSchema.installationSql(ConsumerGroupTables)` and apply that one-time migration through the reviewed deployment process after the v1 outbox protocol. It creates a registry, independent delivery state and a redrive journal; it does not alter existing outbox events. Readiness checks catalog shape, identity/foreign keys, defaults, collations, markers, RLS and the fixed journal function/trigger enablement. No migration or group registration runs at startup.

First registration requires `registerGroup(subscription, RetainedReplayPolicy.ALL_RETAINED)`. This explicitly replays **every retained subscribed event**, including globally processed and dead-lettered events, into a new group. Stop or isolate old global workers and purge jobs before cutover, verify retained data and application-owned Core idempotency, and obtain explicit replay consent. Existing global statuses, payloads, counters and terminal results remain unchanged. The group subscription and initial attempt policy are immutable; conflicting configuration refuses. Repeated identical registration preserves state.

Each claim materializes a bounded batch of missing `(group,event)` rows through a fresh anti-join. There is no ID/time high-water cursor: a later smaller-ID commit remains discoverable. Same-group workers compete for leases; different groups keep separate status, attempts, retry deadline and dead-letter state. Query transactions commit and release before a handler receives a claim. Row counts and timeouts are bounded; query-plan/large-history progress qualification remains necessary. Delivery is at least once, with no strict global FIFO or exactly-once business-effect promise.

`redrive(command)` requires an expected dead-letter token/generation and an explicit positive extra attempt budget, no larger than the registered policy per command. It extends the persisted ceiling while preserving attempts and generation, records the prior failure and supplied actor/reason, and schedules the next ordinary claim. Counters and exhausted generations are never reset or repaired. A repeated identical command does not reschedule; a reused command identity with different intent refuses. Callers must enforce operator authorization before this API. Stored actor/reason are supplied technical provenance; their presence alone does not authenticate an operator.

The manual journal guards reject UPDATE, DELETE and TRUNCATE and remain enabled in replication mode. Readiness verifies their exact fixed function and ALWAYS-trigger definitions. Owner ALTER remains outside this protection; regulatory/WORM qualification is separate. No retention deletion, privilege grant, lease renewal, automatic redrive or default group adoption is provided. Qualify the exact manual migration, PostgreSQL concurrency/guard behavior, runtime integration and deployment cutover before adoption.

## Continue the application

Use the [Event Sourcing guide](../../docs/site/modules/eventsourcing.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eda-postgres -am verify
./mvnw -pl :fireflyframework-eda-postgres -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/eda-postgres.md).
