# Team-wide architecture

![Team-wide architecture](../assets/banners/capability-team-wide-architecture.svg)

Several teams may build onboarding, review, and document services. Share compatible dependencies and useful contracts while letting each service choose its own business behavior and providers.

## Start with dependency management

Use the [framework parent or independent BOM](../maven.md). Both align Jafly dependency versions. The parent also provides build management; the BOM lets an existing parent keep that responsibility. Neither adds every framework library to the application.

The runnable [onboarding service](../getting-started.md) and [event-store example](../guides/onboarding-event-store.md) have their own `0.1.0-SNAPSHOT` application versions while selecting the framework train through their parent. Use their POMs as concrete examples of that separation.

## Choose a shared convention

| Team need | Candidate |
| --- | --- |
| Common exception vocabulary | [Kernel](../modules/kernel.md) |
| Application request context and process support | [Application starter](../modules/starter-application.md) |
| Domain-service dependency set | [Domain starter](../modules/starter-domain.md) |
| Data-operation services and contracts | [Data starter](../modules/starter-data.md) |
| Core infrastructure conventions | [Core starter](../modules/starter-core.md) |
| Additional Jakarta validation constraints | [Validators](../modules/validators.md) |
| Host-controlled extension points | [Plugin API](../modules/plugin-api.md), then [plugin core](../modules/plugin-core.md) when loading plugins |

Choose a starter after reading its dependency set and wiring requirements. A starter is not a substitute for deciding which layer the service owns. A small service can declare individual libraries, as the onboarding tutorial does.

## Keep the shared boundary testable

Follow the [Maven guide](../maven.md) to inspect the effective POM and dependency tree. The repository's [management contract tests](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/tests/test_migration_contract.py) check the parent/BOM catalog and consumer-version separation.

For a team convention, create a consumer application test that starts the chosen dependency set and runs a representative operation. The onboarding examples show that pattern for HTTP/CQRS, coordination, and PostgreSQL event history. They do not prove that every combination of starters and providers works in a deployment.

Use a plugin boundary only when the host needs extensions with a defined lifecycle and contract. A normal Spring component is simpler when the code ships and changes with the application.

## Share policy deliberately

Keep issuer settings, provider credentials, database schemas, and business permissions under the service's deployment and ownership model. A shared BOM aligns versions; it does not choose who may read an application or how a failed review is recovered.

Return to [business operations](business-operations.md) to build a first operation, or use the [module catalog](../modules/index.md) to inspect a concrete dependency before adopting it across services.
