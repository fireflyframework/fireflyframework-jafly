# Accept a customer application

![Customer onboarding](../../site/assets/banners/getting-started.svg)

This service accepts an application over HTTP, validates the applicant's details, and dispatches a command to an application-owned in-memory repository. A query handler retrieves the stored application. Constructor injection uses Lombok's `@RequiredArgsConstructor` with final dependencies.

Use JDK 25. From the framework checkout, install the example's prerequisites and run its tests:

```sh
./mvnw -B -ntp -pl :fireflyframework-cqrs -am install
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml clean verify
```

The eight tests cover the native `/hello` route, command/query behavior, application submission and retrieval, invalid input, and an unknown application. These tests explicitly select the local Caffeine cache, so running the test suite needs no database, broker, or Docker engine. Running the application with the framework default PostgreSQL cache requires the PostgreSQL adapter and configured database; see [PostgreSQL cache configuration](../../site/modules/cache-postgres.md#configuration-and-wiring). The application-owned records disappear when the application stops.

Read [your first service](../../site/getting-started.md) for the HTTP commands, expected responses, complete source, and next steps. Event history and coordinated review have separate runnable examples alongside this project.
