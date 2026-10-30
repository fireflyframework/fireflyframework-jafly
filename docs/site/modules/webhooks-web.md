![Jafly. Webhook Management Platform - Web Application](../assets/modules/webhooks-web.svg)

# Webhook Management Platform - Web Application

Expose the reactive incoming webhook endpoint and runnable ingestion application.

Run WebhookManagementApplication with the transport/cache settings required by the core. The controller validates provider and request data, records metrics, and delegates to the processing service; provider-specific processing belongs downstream.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-webhooks-web` | `jar` | [modules/webhooks/fireflyframework-webhooks-web](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/webhooks/fireflyframework-webhooks-web) |

Part of [Event-driven integration](index.md#event-driven-integration).

## In the onboarding service

Expose the verification-provider ingestion endpoint through this service after its transport and request settings are configured. A successful ingestion response does not mean the onboarding worker has completed the update.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-web</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Build the ingestion service, configure the core transport/cache settings, and run its actual application:

```bash
./mvnw -pl :fireflyframework-webhooks-web -am install
./mvnw -f modules/webhooks/fireflyframework-webhooks-web/pom.xml spring-boot:run
```

`WebhookController` is rooted at `/api/v1/webhook`. It receives a provider-specific path, validates the incoming provider/payload/request, records metrics, and delegates to `WebhookProcessingService`. Follow the [platform ingestion example](webhooks.md) for a concrete request and the processor guide for the consumer/signature/idempotency side of the flow. Configure the service's security and destination transport before exposing an ingestion endpoint.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`WebhookController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-web/src/main/java/org/fireflyframework/webhooks/web/controllers/WebhookController.java) | REST controller for receiving webhooks from multiple providers. |
| [`WebhookManagementApplication`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-web/src/main/java/org/fireflyframework/webhooks/web/WebhookManagementApplication.java) | Defines `main` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Webhooks Library](webhooks.md) | Group incoming webhook contracts, ingestion logic, HTTP service, processing extensions, and generated SDK. |
| [Webhook Management Platform - Core](webhooks-core.md) | Validate, enrich, batch/compress, and publish incoming webhook events with retry, rate-limit, and dead-letter support. |
| [Webhook Management Platform - Interfaces](webhooks-interfaces.md) | Share the provider-neutral inbound webhook request and response DTOs. |
| [Web](web.md) | Handle WebFlux errors, request metadata, idempotency, CORS, and sensitive-data masking consistently. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |
| [EDA - Kafka](eda-kafka.md) | Connect the EDA publishing and consuming abstractions to Apache Kafka. |
| [Cache - Redis](cache-redis.md) | Back the shared cache abstraction with Redis and reactive serialization. |
| [Webhook Processor Framework](webhooks-processor.md) | Implement provider-specific webhook processors, listeners, signature checks, and idempotency behavior. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-webhooks-web -am verify
./mvnw -pl :fireflyframework-webhooks-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [WebhookIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-web/src/test/java/org/fireflyframework/webhooks/integration/WebhookIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

## API reference excerpt

Contract excerpt from [WebhookController.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-web/src/main/java/org/fireflyframework/webhooks/web/controllers/WebhookController.java). This fragment needs the full type and its imports.

```java
public class WebhookController {
    private final WebhookProcessingService webhookProcessingService;
    private final WebhookValidator webhookValidator;
    private final WebhookMetricsService metricsService;
    private final org.fireflyframework.webhooks.core.ratelimit.WebhookRateLimitService rateLimitService;
    private final WebhookMetadataEnrichmentService metadataEnrichmentService;
            value = "/{providerName}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
            summary = "Receive webhook from any provider",
            description = "Universal endpoint that accepts webhooks from any provider. " +
                    "The webhook payload is stored AS-IS and published to a message queue for processing."
    )
            responseCode = "202",
            description = "Webhook accepted and queued for processing",
            content = @Content(schema = @Schema(implementation = WebhookResponseDTO.class))
    )
            responseCode = "400",
            description = "Invalid webhook payload"
    )
            responseCode = "500",
            description = "Internal server error"
    )
```

## Continue the application

Use the [Webhook Processor Framework guide](webhooks-processor.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-web/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/webhooks/fireflyframework-webhooks-web/src/main/java).
- [application.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-web/src/main/resources/application.yml).
- [logback-spring.xml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-web/src/main/resources/logback-spring.xml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
