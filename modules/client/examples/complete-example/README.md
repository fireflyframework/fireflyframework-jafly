![Jafly. Common Client - Complete Example](../../../../docs/site/assets/modules/client-complete-example.svg)

# Common Client - Complete Example

Explore concrete REST, GraphQL, OAuth2, upload, and WebSocket client service examples.

Use this example application to explore the client APIs. Install the client dependency first, then build the example separately from its POM. External sample endpoints and OAuth2 settings must be supplied for the scenario you run.

[Module guide](../../../../docs/site/modules/client-complete-example.md) · [Jafly README](../../../../README.md) · [Module catalog](../../../../docs/site/modules/index.md)

## In the onboarding service

This separate application explores client features beyond the onboarding tutorial. Its sample service types and remote endpoints are examples; adapt the selected protocol pattern to the verification partner rather than copying every integration.

## Installation

[Installation and version availability](../../../../docs/site/installation.md) explains source builds and published releases.

This is a runnable example, not a dependency-installation recommendation. Build instructions are provided below.

## Quick start

This is a runnable companion application with its own configuration. Configure the external endpoints/providers used by the selected scenario before running it.

### 1. Build the Project

```bash
./mvnw -pl :complete-example -am verify
```

### 2. Run the Application

```bash
./mvnw -f modules/client/examples/complete-example/pom.xml spring-boot:run
```

The application will start on `http://localhost:8080`.

### 3. Access Health Checks

```bash
curl http://localhost:8080/actuator/health
```

### 4. Access Metrics

```bash
curl http://localhost:8080/actuator/metrics
```

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`UserService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/examples/complete-example/src/main/java/org/fireflyframework/example/service/UserService.java) | Example service demonstrating REST client usage with all features: - Circuit breaker - Retry with exponential backoff - Request/response logging - Error handling |
| [`GraphQLExampleService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/examples/complete-example/src/main/java/org/fireflyframework/example/service/GraphQLExampleService.java) | Example service demonstrating GraphQL client usage with: - Query caching - Automatic retry - Error handling - Variables support |
| [`OAuth2ExampleService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/examples/complete-example/src/main/java/org/fireflyframework/example/service/OAuth2ExampleService.java) | Example service demonstrating OAuth2 client usage with: - Multi-scope token caching - Automatic token refresh - Retry with exponential backoff - ID token support |
| [`FileUploadExampleService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/examples/complete-example/src/main/java/org/fireflyframework/example/service/FileUploadExampleService.java) | Example service demonstrating multipart upload with: - Progress tracking - Chunked uploads for large files - Parallel uploads - File validation - Compression |
| [`WebSocketExampleService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/examples/complete-example/src/main/java/org/fireflyframework/example/service/WebSocketExampleService.java) | Example service demonstrating WebSocket client usage with: - Automatic reconnection - Heartbeat/ping-pong - Message queuing - Binary message support |

## Continue the application

Use the [Client guide](../../../../docs/site/modules/client.md) for the next part of this integration. ## Build and validation

Build the client dependency first, then build the standalone example:

```bash
./mvnw -pl :fireflyframework-client -am install
./mvnw -f modules/client/examples/complete-example/pom.xml verify
```

Supply the external service settings in the example resources before running its application. The example has no authored `src/test/java` suite.

Configuration details and source references: [module guide](../../../../docs/site/modules/client-complete-example.md).
