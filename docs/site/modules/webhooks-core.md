![Jafly. Webhook Management Platform - Core](../assets/modules/webhooks-core.svg)

# Webhook Management Platform - Core

Validate, enrich, batch/compress, and publish incoming webhook events with retry, rate-limit, and dead-letter support.

Pass a WebhookEventDTO to WebhookProcessingService after request validation. Configure provider-specific limits and retry/batching settings consciously; the security property defaults constrain payloads and accepted request types.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-webhooks-core` | `jar` | [modules/webhooks/fireflyframework-webhooks-core](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/webhooks/fireflyframework-webhooks-core) |

Part of [Event-driven integration](index.md#event-driven-integration).

## In the onboarding service

Validate/enrich the inbound verification-provider request and publish its configured event. Receiving a webhook is distinct from updating the onboarding application; the worker supplies that business operation.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-core</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Construct a provider-neutral `WebhookEventDTO`, validate the actual HTTP request with `WebhookValidator`, and pass the DTO to the injected processing service:

```java
Mono<WebhookResponseDTO> outcome = processingService.processWebhook(event);
```

`processingService` is `org.fireflyframework.webhooks.core.services.WebhookProcessingService`; use the exact package link in the API table. `event` carries provider name, JSON payload, event identifier, and request metadata. The [web controller](webhooks-web.md) is the reference for the complete validation/metrics/delegation order. Configure the destination transport before expecting processing to publish an event, and choose batching/compression/retry settings from the source-backed namespaces below.

## Configuration and wiring

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

The `firefly.webhooks.retry` namespace is bound by [RetryProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/config/RetryProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.webhooks.retry.defaults` | `RetryConfig` | `new RetryConfig()` |
| `firefly.webhooks.retry.providers` | `Map<String, RetryConfig>` | `new HashMap<>()` |

The `firefly.webhooks.security` namespace is bound by [WebhookSecurityProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/config/WebhookSecurityProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.webhooks.security.max-payload-size` | `long` | `1048576` |
| `firefly.webhooks.security.validate-payload-size` | `boolean` | `true` |
| `firefly.webhooks.security.validate-provider-name` | `boolean` | `true` |
| `firefly.webhooks.security.provider-name-pattern` | `String` | `"^[a-z0-9-]+$"` |
| `firefly.webhooks.security.enable-ip-whitelist` | `boolean` | `false` |
| `firefly.webhooks.security.ip-whitelist` | `Map<String, List<String>>` | `new HashMap<>()` |
| `firefly.webhooks.security.enable-request-validation` | `boolean` | `true` |
| `firefly.webhooks.security.allowed-methods` | `List<String>` | `List.of("POST")` |
| `firefly.webhooks.security.require-content-type` | `boolean` | `true` |
| `firefly.webhooks.security.allowed-content-types` | `List<String>` | `List.of( "application/json", "application/x-www-form-urlencoded" )` |

Property-based wiring conditions found in this module:

- `name = "firefly.cache.redis.enabled", havingValue = "true", matchIfMissing = false`. [RedisConnectivityHealthIndicator.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/health/RedisConnectivityHealthIndicator.java).
- `name = "firefly.webhooks.batching.enabled", havingValue = "true"`. [WebhookBatchingServiceImpl.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/impl/WebhookBatchingServiceImpl.java).
- `name = "firefly.webhooks.compression.enabled", havingValue = "true"`. [WebhookCompressionServiceImpl.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/impl/WebhookCompressionServiceImpl.java).
- `name = "firefly.webhooks.metadata-enrichment.enabled", havingValue = "true", matchIfMissing = true`. [WebhookMetadataEnrichmentServiceImpl.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/impl/WebhookMetadataEnrichmentServiceImpl.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`WebhookProcessingService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/WebhookProcessingService.java) | Service interface for processing webhook events. |
| [`WebhookValidator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/validation/WebhookValidator.java) | Validator for webhook requests. |
| [`WebhookSecurityProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/config/WebhookSecurityProperties.java) | Configuration properties for webhook security and validation. |
| [`WebhookBatchingService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/WebhookBatchingService.java) | Service for batching webhook events before publishing to Kafka. |
| [`DeadLetterQueueService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java/org/fireflyframework/webhooks/core/services/DeadLetterQueueService.java) | Service for publishing rejected webhooks to the Dead Letter Queue (DLQ). |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Webhooks Library](webhooks.md) | Group incoming webhook contracts, ingestion logic, HTTP service, processing extensions, and generated SDK. |
| [Webhook Management Platform - Interfaces](webhooks-interfaces.md) | Share the provider-neutral inbound webhook request and response DTOs. |
| [Starter Core](starter-core.md) | Assemble core infrastructure conventions for WebClient, actuator, logging, configuration, and service discovery. |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-webhooks-core -am verify
./mvnw -pl :fireflyframework-webhooks-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [WebhookEventMapperTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/test/java/org/fireflyframework/webhooks/core/mappers/WebhookEventMapperTest.java).
- [WebhookMetricsServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/test/java/org/fireflyframework/webhooks/core/metrics/WebhookMetricsServiceTest.java).
- [DeadLetterQueueServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/test/java/org/fireflyframework/webhooks/core/services/DeadLetterQueueServiceTest.java).
- [WebhookCompressionServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/test/java/org/fireflyframework/webhooks/core/services/WebhookCompressionServiceTest.java).
- [WebhookMetadataEnrichmentServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/test/java/org/fireflyframework/webhooks/core/services/WebhookMetadataEnrichmentServiceTest.java).
- [WebhookValidatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/test/java/org/fireflyframework/webhooks/core/validation/WebhookValidatorTest.java).

## Test fixture

The `shouldRecordMultipleProcessingTimesAndCalculateStatistics` test demonstrates one concrete behavior. Excerpt from [WebhookMetricsServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/src/test/java/org/fireflyframework/webhooks/core/metrics/WebhookMetricsServiceTest.java). The test class supplies its fixtures and imports.

```java
@Test
@DisplayName("Should record multiple processing times and calculate statistics")
void shouldRecordMultipleProcessingTimesAndCalculateStatistics() {
    String providerName = "slack";

    metricsService.recordProcessingTime(providerName, Instant.now().minusMillis(100));
    metricsService.recordProcessingTime(providerName, Instant.now().minusMillis(200));
    metricsService.recordProcessingTime(providerName, Instant.now().minusMillis(300));

    Timer timer = meterRegistry.find("firefly.webhooks.processing.time")
            .tag("provider", providerName)
            .timer();

    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(3);
    // Total time should be at least 600ms (100 + 200 + 300)
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(600);
}
```

## Continue the application

Use the [Webhook Management Platform - Web Application guide](webhooks-web.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-core/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/webhooks/fireflyframework-webhooks-core/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
