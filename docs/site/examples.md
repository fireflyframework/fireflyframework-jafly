# Examples and tutorials

![Examples and tutorials](assets/banners/examples.svg)

Follow one customer application from submission to a stored history and coordinated review. Each project runs on its own so its dependencies and failure behavior are visible.

## Follow the learning path

| Step | Project | What to observe |
| --- | --- | --- |
| 1. Accept an application | [Your first service](getting-started.md) | Submit over HTTP, read through QueryBus, reject invalid input, and return 404 for an unknown ID |
| 2. Retain its history | [Onboarding event store](guides/onboarding-event-store.md) | Append and reconstruct typed events in PostgreSQL, reject a stale writer, and roll back an invalid batch |
| 3. Coordinate a review | [Onboarding coordination](guides/onboarding-coordination.md) | Exercise Saga compensation, TCC confirmation/cancellation, and ordered Workflow steps with controlled providers |

The HTTP project uses an application-owned in-memory repository. The event-store project uses a real PostgreSQL Testcontainers database. The coordination project uses the real framework engines with in-memory provider implementations. They demonstrate separate boundaries; no project silently turns the others into a distributed transaction.

The source and test locations are:

- [`docs/examples/getting-started`](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/docs/examples/getting-started)
- [`docs/examples/onboarding-event-store`](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/docs/examples/onboarding-event-store)
- [`docs/examples/onboarding-coordination`](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/docs/examples/onboarding-coordination)

## Run the projects

Use JDK 25 and install the framework first. Docker is required for the full framework tests and the PostgreSQL event-store example:

```sh
./mvnw -B -ntp install
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml clean verify
./mvnw -B -ntp -f docs/examples/onboarding-event-store/pom.xml clean verify
./mvnw -B -ntp -f docs/examples/onboarding-coordination/pom.xml clean verify
```

Use the individual guides for the HTTP run command and expected responses. Do not supply production credentials or customer data to these examples.

## Extend one capability

Choose the next requirement from the [capability journeys](index.md#find-the-layer-your-service-needs). The journeys explain the smallest component choice and link to its configuration and test sources.

| Requirement | Entry point | Setup boundary |
| --- | --- | --- |
| Protect customer records | [Security and identity](capabilities/security-and-identity.md) | Trusted issuer, keys, policy, and service-specific access tests |
| Call another service | [External services](capabilities/external-services.md) | Provider endpoint, credentials, and repeat/failure policy |
| Publish or receive integration events | [Event-driven integration](capabilities/event-driven-integration.md) | Transport or webhook configuration and delivery tests |
| Add persistent state or documents | [Domain and data](capabilities/domain-and-data.md) | Schema, store, retention, and access configuration |
| Inspect runtime outcomes | [Service operations](capabilities/service-operations.md) | Endpoint exposure, exporters, and operational backend |

The [complete client example](modules/client-complete-example.md) and [Agentic bridge samples](modules/agentic-bridge-samples.md) are additional applications with their own setup. A compile or library test does not establish access to a real provider. Check each example's README before starting services or supplying credentials.

## Read reference fragments in context

Module guides also contain scoped API fragments. They show a contract or integration point and identify the application-owned collaborators needed around it. Use the matching source version when copying a fragment; a snippet from a provider guide is not automatically part of the runnable onboarding service.

The [Java by example book](https://github.com/fireflyframework/fireflyframework-java-book) is a separate repository. Match its sample dependency train to the framework version you are using.
