![Jafly. Callback Management Platform - Web](../assets/modules/callbacks-web.svg)

# Callback Management Platform - Web

Expose the callback-management HTTP API and its Spring Boot application.

Run CallbackManagementApplication with the database and messaging configuration expected by callbacks-core. Use the configuration, subscription, domain, and execution controllers through the published API contract.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-callbacks-web` | `jar` | [modules/callbacks/fireflyframework-callbacks-web](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/callbacks/fireflyframework-callbacks-web) |

Part of [External services](index.md#external-services).

## In the onboarding service

Operate this service if onboarding partners manage callbacks through HTTP. Configure its database/messaging backend, register the partner domain, and inspect execution records to distinguish registration from delivery.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-web</artifactId>
    <version>26.10.20</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Install the selected service and its reactor prerequisites, configure its database and messaging settings, and start its actual Boot application from the root:

```bash
./mvnw -pl :fireflyframework-callbacks-web -am install
./mvnw -f modules/callbacks/fireflyframework-callbacks-web/pom.xml spring-boot:run
```

The controllers expose `/api/v1/authorized-domains`, `/api/v1/callback-configurations`, `/api/v1/event-subscriptions`, and `/api/v1/callback-executions`. Create/verify the destination domain, create its callback configuration, and register subscriptions before expecting event routing to deliver callbacks. See the [platform guide](callbacks.md) for request examples; use the execution routes to inspect outcomes rather than assuming registration proves delivery.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`CallbackManagementApplication`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/CallbackManagementApplication.java) | Defines `main` operations. |
| [`CallbackConfigurationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/controller/CallbackConfigurationController.java) | REST controller for callback configuration management. |
| [`AuthorizedDomainController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/controller/AuthorizedDomainController.java) | REST controller for authorized domain management. |
| [`EventSubscriptionController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/controller/EventSubscriptionController.java) | REST controller for event subscription management. |
| [`CallbackExecutionController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/controller/CallbackExecutionController.java) | REST controller for callback execution history. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Callbacks Library](callbacks.md) | Group the outbound callback platform: configuration, domain authorization, subscriptions, execution tracking, and dispatch. |
| [Callback Management Platform - Core](callbacks-core.md) | Route subscribed events and dispatch outbound HTTP callbacks with signing, retries, circuit breaking, and execution tracking. |
| [Web](web.md) | Handle WebFlux errors, request metadata, idempotency, CORS, and sensitive-data masking consistently. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |
| [EDA - Kafka](eda-kafka.md) | Connect the EDA publishing and consuming abstractions to Apache Kafka. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-callbacks-web -am verify
./mvnw -pl :fireflyframework-callbacks-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [CallbackManagementEndToEndTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/test/java/org/fireflyframework/callbacks/web/CallbackManagementEndToEndTest.java).
- [SpringContextTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/test/java/org/fireflyframework/callbacks/web/SpringContextTest.java).
- [CallbackEndToEndIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/test/java/org/fireflyframework/callbacks/web/integration/CallbackEndToEndIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

## Test fixture

The `shouldListCallbackConfigurations` test demonstrates one concrete behavior. Excerpt from [CallbackEndToEndIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/test/java/org/fireflyframework/callbacks/web/integration/CallbackEndToEndIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
@Order(4)
void shouldListCallbackConfigurations() {
    // Use filter endpoint with empty filter to get all
    Map<String, Object> filterRequest = new HashMap<>();
    filterRequest.put("page", 0);
    filterRequest.put("size", 10);

    webTestClient.post()
            .uri("/api/v1/callback-configurations/filter")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(filterRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content[?(@.name == 'Test Webhook')]").exists();
}
```

## Continue the application

Use the [Callback Management Platform - SDK guide](callbacks-sdk.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java).
- [application.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/resources/application.yml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
