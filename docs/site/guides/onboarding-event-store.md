# Keep an onboarding history

![Onboarding event store](../assets/banners/onboarding-event-store.svg)

The [first service](../getting-started.md) keeps customer applications in memory. To retain how an application reached its current state, store its domain events and reconstruct the application from that history.

In this separate runnable project, a customer submits an application and a reviewer approves it. PostgreSQL stores `ApplicationSubmitted` and `ApplicationApproved`. Reloading those events rebuilds the applicant details, approval state, and aggregate version.

The complete project is [`docs/examples/onboarding-event-store`](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/docs/examples/onboarding-event-store). It tests the persistence layer directly, using a real PostgreSQL container. It does not expose an HTTP API.

<picture>
  <source media="(max-width: 600px)" srcset="../../assets/diagrams/event-store-narrow.svg">
  <img class="jafly-diagram" src="../../assets/diagrams/event-store.svg" alt="The aggregate produces events. R2dbcEventStoreImpl appends them with an expected version in PostgreSQL. Reading the ordered, typed history reconstructs a fresh aggregate.">
</picture>

## Run the project

Use JDK 25 and a running Docker engine. Install the framework libraries from the checkout, then run the standalone project:

```sh
./mvnw -B -ntp install
./mvnw -B -ntp -f docs/examples/onboarding-event-store/pom.xml clean verify
```

Testcontainers creates a PostgreSQL 15 container and initializes its `events` table. The four tests exercise the real event store; Docker must be available. There are no cloud credentials or external broker settings to supply.

The POM keeps the application's version separate from the framework version and declares Lombok as a provided dependency:

```xml
--8<-- "docs/examples/onboarding-event-store/pom.xml"
```

## Record what happened

`ApplicationSubmitted` carries the customer details at submission. Its timestamp is fixed when the event is created:

```java
--8<-- "docs/examples/onboarding-event-store/src/main/java/com/example/onboarding/ApplicationSubmitted.java"
```

`ApplicationApproved` records the reviewer responsible for the approval:

```java
--8<-- "docs/examples/onboarding-event-store/src/main/java/com/example/onboarding/ApplicationApproved.java"
```

Both implement Jafly's `Event` contract. `@DomainEvent` supplies the event name. The test configures Jackson's subtype registration explicitly so loading an event returns its concrete Java type. Persisted event names and fields form a compatibility contract; plan their evolution before changing them.

These records contain customer names and email addresses. A deployed service needs its own access controls, retention policy, and protection for stored personal data.

## Put state rules in the aggregate

The aggregate permits submission from `DRAFT` and approval from `SUBMITTED`. Each operation applies a new event. The `on` methods update state during both a new operation and replay:

```java
--8<-- "docs/examples/onboarding-event-store/src/main/java/com/example/onboarding/OnboardingAggregate.java"
```

A new aggregate starts at version `-1`. Submission produces version `0`; approval produces version `1`. These are aggregate history positions. The event schema version is a separate concept.

The aggregate checks required text and allowed transitions. The service must still validate HTTP input, identify the reviewer, and authorize the operation before calling it.

## Save and reconstruct the application

`OnboardingEventService` belongs to this example. It connects the aggregate to the framework's `EventStore` interface:

```java
--8<-- "docs/examples/onboarding-event-store/src/main/java/com/example/onboarding/OnboardingEventService.java"
```

Saving sends only uncommitted events. The expected stored version is the current aggregate version minus its pending event count. The service clears pending events after the append succeeds.

Loading creates a new aggregate and passes its ordered history to `loadFromHistory`. An unknown ID returns an empty `Mono`. The test verifies that the reloaded object is a different instance with the same applicant details and the correct approval state.


## Configure the PostgreSQL boundary

The test creates one `PostgresqlConnectionFactory` and uses it for `DatabaseClient`, `R2dbcEntityTemplate`, `R2dbcTransactionManager`, and `TransactionalOperator`. Those objects are passed to `R2dbcEventStoreImpl`. The test also registers the concrete event types with its Jackson mapper.

Its SQL fixture follows the event store's table contract:

```sql
--8<-- "docs/examples/onboarding-event-store/src/test/resources/events.sql"
```

The unique `(aggregate_id, aggregate_version)` constraint protects history positions. Event data and metadata use `TEXT`, matching the module's initial PostgreSQL migration. `event_type` has a 255-character limit.

This example initializes a disposable test schema. A deployed application must manage its database migrations and indexes. Review the module's [migration resources](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eventsourcing/src/main/resources/db/migration) before configuring additional features.

## Test success and failure

The integration test covers four outcomes:

| Scenario | Expected result |
| --- | --- |
| Submit, reload, approve, reload | Applicant data survives reconstruction; status is `APPROVED`; stored version is `1` |
| Two reviewers load version `0` | The first approval commits; the stale writer receives `ConcurrencyException`; its event is not stored |
| One event exceeds the SQL event-type limit | PostgreSQL returns SQLSTATE `22001`; the append leaves no event rows |
| Approve before submission or load an unknown ID | The invalid transition is rejected; the unknown application stays absent |

```java
--8<-- "docs/examples/onboarding-event-store/src/test/java/com/example/onboarding/OnboardingEventStoreTest.java"
```

After a version conflict, reload the history and reconsider the requested operation against its current state. Repeating the old write blindly can violate a business rule even if a later database append succeeds.

## Keep the transaction claim precise

The rollback test proves that a failed batch append leaves no event rows when this store uses the configured PostgreSQL connection factory and transaction manager. It does not prove atomicity with a separate database, HTTP call, or message broker.

No outbox service or broker publisher is configured in this project. The event-sourcing module has optional outbox support, but enabling and testing that path is separate work. This example also uses no snapshots or projection worker: replay rebuilds the aggregate itself.

Continue with [event sourcing](../modules/eventsourcing.md) for the module's other APIs, or [business coordination](../modules/orchestration.md) when onboarding spans several operations with recovery or compensating steps.
