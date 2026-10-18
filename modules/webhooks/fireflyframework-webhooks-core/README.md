![Jafly. Webhook Management Platform - Core](../../../docs/site/assets/modules/webhooks-core.svg)

# Webhook Management Platform - Core

Validate, enrich, batch/compress, and publish incoming webhook events with retry, rate-limit, and dead-letter support.

Pass a WebhookEventDTO to WebhookProcessingService after request validation. Configure provider-specific limits and retry/batching settings consciously; the security property defaults constrain payloads and accepted request types.

[Module guide](../../../docs/site/modules/webhooks-core.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Validate/enrich the inbound verification-provider request and publish its configured event. Receiving a webhook is distinct from updating the onboarding application; the worker supplies that business operation.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-core</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Construct a provider-neutral `WebhookEventDTO`, validate the actual HTTP request with `WebhookValidator`, and pass the DTO to the injected processing service:

```java
Mono<WebhookResponseDTO> outcome = processingService.processWebhook(event);
```

`processingService` is `org.fireflyframework.webhooks.core.services.WebhookProcessingService`; use the exact package link in the API table. `event` carries provider name, JSON payload, event identifier, and request metadata. The [web controller](../../../docs/site/modules/webhooks-web.md) is the reference for the complete validation/metrics/delegation order. Configure the destination transport before expecting processing to publish an event, and choose batching/compression/retry settings from the source-backed namespaces below.

## Configuration

The `firefly.webhooks.batching` namespace is bound by [BatchingProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/config/BatchingProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.webhooks.batching.enabled` | `boolean` | `false` |
| `firefly.webhooks.batching.max-batch-size` | `int` | `100` |
| `firefly.webhooks.batching.max-wait-time` | `Duration` | `Duration.ofSeconds(1)` |
| `firefly.webhooks.batching.buffer-size` | `int` | `1000` |
| `firefly.webhooks.batching.providers` | `Map<String, BatchConfig>` | `new HashMap<>()` |

The `firefly.webhooks.compression` namespace is bound by [CompressionProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/config/CompressionProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.webhooks.compression.enabled` | `boolean` | `false` |
| `firefly.webhooks.compression.min-size` | `int` | `1024` |
| `firefly.webhooks.compression.algorithm` | `CompressionAlgorithm` | `CompressionAlgorithm.GZIP` |
| `firefly.webhooks.compression.level` | `int` | `6` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`WebhookProcessingService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/WebhookProcessingService.java) | Service interface for processing webhook events. |
| [`WebhookValidator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/validation/WebhookValidator.java) | Validator for webhook requests. |
| [`WebhookSecurityProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/config/WebhookSecurityProperties.java) | Configuration properties for webhook security and validation. |
| [`WebhookBatchingService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/WebhookBatchingService.java) | Service for batching webhook events before publishing to Kafka. |
| [`DeadLetterQueueService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/DeadLetterQueueService.java) | Service for publishing rejected webhooks to the Dead Letter Queue (DLQ). |

## Continue the application

Use the [Webhook Management Platform - Web Application guide](../../../docs/site/modules/webhooks-web.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-webhooks-core -am verify
./mvnw -pl :fireflyframework-webhooks-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/webhooks-core.md).
