# Domain and data

![Domain and data](../assets/banners/capability-domain-and-data.svg)

An accepted onboarding application must survive a restart. Decide whether the service needs its current record, the history behind that record, or both. Keep documents and short-lived cached results under their own storage policies.

## Choose the data model first

| Requirement | Start with |
| --- | --- |
| Query and update current relational state | [R2DBC](../modules/r2dbc.md) with Spring Data and an application-owned schema |
| Reconstruct state from domain events | [Event sourcing](../modules/eventsourcing.md) and the runnable PostgreSQL example |
| Cache repeatable lookups | [Cache](../modules/cache.md), then a distributed provider only when needed |
| Store supporting documents | [ECM contracts](../modules/ecm.md) and a selected storage adapter |
| Request a document signature | A selected [ECM signature adapter](../modules/ecm.md) |

A cache is not the durable source of an application record. A blob store does not define the application's approval history. Choose each component for the state it owns.

## Reconstruct one application

Follow [Keep an onboarding history](../guides/onboarding-event-store.md). Its separate project uses `R2dbcEventStoreImpl` with a real PostgreSQL container:

1. Submit an application and append `ApplicationSubmitted`.
2. Reload a fresh aggregate from its typed event history.
3. Approve it and append `ApplicationApproved` with the expected stored version.
4. Reload again and check the reviewer, status, and history version.

The [integration test](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/docs/examples/onboarding-event-store/src/test/java/com/example/onboarding/OnboardingEventStoreTest.java) also rejects a stale writer and verifies that PostgreSQL rejects an invalid event without leaving a partial append. The guide states the exact connection-factory and transaction-manager setup used for that result.

## Set up the chosen store

For relational records, supply the connection factory, driver, migrations, repositories, and transaction boundary your application needs. The R2DBC helpers do not create the onboarding schema or decide its retention rules.

The cache library has a local Caffeine default. Redis, PostgreSQL, Hazelcast, and JCache adapters have separate setup requirements. Decide what may be stale, how entries expire, and which event invalidates them before caching application status.

For documents, choose [S3 storage](../modules/ecm-storage-aws.md) or [Azure Blob storage](../modules/ecm-storage-azure.md) only after defining access control and retention. A signature adapter requires its provider account and workflow; the event-store tests do not verify those services.

Current-state persistence is enough when history replay adds no value to the service. If event sourcing is appropriate, plan event schema evolution and customer-data handling before treating the log as a permanent record.

Next, add [business coordination](business-coordination.md) when approval spans several operations with different recovery rules.
