# Your first service

![Get started with jafly](assets/banners/getting-started.svg)

Build a customer onboarding service that accepts an application and lets the caller retrieve it. Begin with a WebFlux route, add a command to submit the application, then use a query to read it back.

The service stores applications in memory, so restarting it clears the data. It needs no database, message broker, or cloud credentials. Authentication, duplicate-submission handling, and durable storage remain application responsibilities and are outside this example.

## Prepare the libraries

Use JDK 25 and the checked-in Maven 3.9.16 wrapper. Install CQRS and its reactor prerequisites for this tutorial:

```sh
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -B -ntp -pl :fireflyframework-cqrs -am install
```

This builds seven framework projects and runs their configured tests without Docker. The full framework build also tests database and broker adapters and needs Docker; see [installation](installation.md).

The complete application is in [`docs/examples/getting-started`](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/docs/examples/getting-started). See [installation](installation.md) for prerequisites and choosing a locally installed or verified registry version.

## Define the application

The framework parent manages dependency and build versions. The application keeps its own version, `0.1.0-SNAPSHOT`. It declares WebFlux, validation, CQRS, and Lombok, with Spring Boot test support and Reactor Test for the tests. Lombok has `provided` scope; the parent configures its annotation processor.

`pom.xml`:

```xml
--8<-- "docs/examples/getting-started/pom.xml"
```

Start Spring Boot from `src/main/java/com/example/jafly/OnboardingServiceApplication.java`:

```java
--8<-- "docs/examples/getting-started/src/main/java/com/example/jafly/OnboardingServiceApplication.java"
```

Spring scans components in `com.example.jafly` and its child packages. The controllers, handlers, and repository live there.

## Begin with HTTP

The existing `GET /hello` route checks that Spring Boot and WebFlux are serving requests. The onboarding controller adds `POST /applications` for submission and `GET /applications/{id}` for retrieval:

```java
--8<-- "docs/examples/getting-started/src/main/java/com/example/jafly/ApplicationsController.java"
```

The controller validates input and translates results into HTTP responses. It sends typed work through `CommandBus` and `QueryBus`; it does not instantiate either handler. `@RequiredArgsConstructor` generates a constructor for the two `final` dependencies, which Spring injects automatically. The handlers use the same pattern for their repository dependency.

## Define the application record

An onboarding application has an ID, applicant name, and email address:

```java
--8<-- "docs/examples/getting-started/src/main/java/com/example/jafly/OnboardingApplication.java"
```

The application owns its storage. This repository uses an in-memory map for the tutorial:

```java
--8<-- "docs/examples/getting-started/src/main/java/com/example/jafly/InMemoryApplicationRepository.java"
```

This repository does not persist across restarts or coordinate several service instances. Replace it with storage that fits your service before relying on durable records.

## Submit an application with a command

`SubmitApplication` describes the input and result type. Its command ID stays the same throughout execution:

```java
--8<-- "docs/examples/getting-started/src/main/java/com/example/jafly/SubmitApplication.java"
```

`@CommandHandlerComponent` registers the handler as a Spring component. The handler creates the application record and saves it through the application repository:

```java
--8<-- "docs/examples/getting-started/src/main/java/com/example/jafly/SubmitApplicationHandler.java"
```

The bus finds the handler and runs the command pipeline. Annotation-based validation needs a Jakarta `Validator` bean, supplied here by the validation starter. A command ID alone does not make duplicate submissions idempotent.

This example defines no authorization policy. `Command.authorize()` allows by default. A protected operation needs an authorization rule and a trusted identity context supplied by the service.

## Read an application with a query

The caller uses the ID returned by submission to ask for the stored application:

```java
--8<-- "docs/examples/getting-started/src/main/java/com/example/jafly/FindApplication.java"
```

The query handler reads from the same repository. It does not change the application:

```java
--8<-- "docs/examples/getting-started/src/main/java/com/example/jafly/FindApplicationHandler.java"
```

Command and query handlers separate the write operation from the read operation. Both use one in-memory store here. CQRS does not require separate databases, and this example does not enable a query cache or publish events.

## Run the service

From the framework checkout:

```sh
./mvnw -f docs/examples/getting-started/pom.xml spring-boot:run
```

In another terminal, check the native HTTP route:

```sh
curl http://localhost:8080/hello
```

Expected body:

```text
Hello from jafly
```

Submit an application:

```sh
curl -i -X POST http://localhost:8080/applications \
  -H 'Content-Type: application/json' \
  -d '{"applicantName":"Ada Lovelace","email":"ada@example.com"}'
```

The response has status `201 Created`, a `Location` header such as `/applications/<id>`, and a JSON body containing `id`, `applicantName`, and `email`. Copy the returned ID into the next request:

```sh
APPLICATION_ID='paste-returned-id-here'
curl "http://localhost:8080/applications/$APPLICATION_ID"
```

The read returns the same application with status `200 OK`. An unknown UUID returns `404 Not Found`. A blank applicant name or invalid email returns `400 Bad Request` on submission. Stop the service with `Ctrl+C` when finished.

## Test the complete path

The example tests the command and query handlers and runs requests against the Spring Boot application on a random port. It checks submission and retrieval, validation errors, and a missing application.

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

The test source lives beside the application in [`src/test/java/com/example/jafly`](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/docs/examples/getting-started/src/test/java/com/example/jafly).

## Choose the next capability

The runnable service ends here. For a larger onboarding process, choose the next dependency for a specific requirement:

- Add [security and identity](modules/security-api.md) before accepting protected customer data.
- Read [CQRS](modules/cqrs.md) for execution context and the conditional event/cache bridges.
- Choose [EDA](modules/eda.md) and a transport if another service needs an application-submitted event.
- Use [orchestration](modules/orchestration.md) if onboarding requires coordinated checks with recovery or compensating steps.

These integrations need their own configuration and tests. Continue with the [architecture guide](architecture.md) to see their boundaries around the command path.
