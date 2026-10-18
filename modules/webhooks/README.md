# Firefly Framework - Webhooks

[![CI](https://github.com/fireflyframework/fireflyframework-webhooks/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-webhooks/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive inbound-webhook ingestion platform for Spring Boot — a provider-agnostic endpoint that accepts webhooks from any provider, publishes them to an event-driven backbone, and lets workers consume them with idempotency, signature validation, and tracing.

---

## Table of Contents

- [Overview](#overview)
  - [Modules](#modules)
  - [How it fits in the framework](#how-it-fits-in-the-framework)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
  - [1. Receive webhooks (ingestion)](#1-receive-webhooks-ingestion)
  - [2. Process webhooks (consumer)](#2-process-webhooks-consumer)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Webhooks is a reactive, non-blocking platform for **receiving** inbound webhooks from external providers (Stripe, PayPal, Twilio, GitHub, or any custom provider) and **delivering** them to your services for processing. It cleanly separates the two halves of webhook handling into a deliberate two-phase pipeline:

1. **Ingestion** — a single universal REST endpoint (`POST /api/v1/webhook/{providerName}`) accepts any JSON payload *as-is*, captures all HTTP headers and query parameters, enriches metadata (source IP, User-Agent parsing, request size), applies per-provider and per-IP rate limiting, and publishes a `WebhookReceivedEvent` to a message broker via the Firefly EDA abstraction. The ingestion endpoint deliberately does **not** validate signatures — it stores the raw headers so the consumer can validate later, which enables audit trails and event replay.

2. **Processing** — workers consume `WebhookReceivedEvent` from the broker. The `AbstractWebhookEventListener` base class wires in content-based idempotency (exactly-once processing across an autoscaled worker fleet), per-provider signature validation, and tracing-context propagation, then delegates to your `WebhookProcessor` implementation for the actual business logic.

This split makes ingestion fast and resilient (a slow downstream processor never blocks the provider's HTTP call) while keeping processing safe to retry and horizontally scalable. The platform is built on Project Reactor / Spring WebFlux end-to-end, backed by Resilience4j for rate limiting and circuit breaking, the Firefly cache abstraction for idempotency state, and the Firefly observability stack for metrics, health probes, and distributed tracing.

### Modules

This repository is a Maven aggregator (`pom` packaging) composed of five submodules:

| Module | Artifact | Purpose |
| --- | --- | --- |
| Interfaces | `fireflyframework-webhooks-interfaces` | Shared DTOs and API contracts (`WebhookEventDTO`, `WebhookResponseDTO`) with no business logic. |
| Core | `fireflyframework-webhooks-core` | Ingestion-side business logic: processing/validation services, metadata enrichment, rate limiting, batching, compression, dead-letter queue, domain events (`WebhookReceivedEvent`, `WebhookRejectedEvent`), `@ConfigurationProperties`, Micrometer metrics, and health indicators. |
| Web | `fireflyframework-webhooks-web` | Runnable Spring Boot WebFlux application exposing the `WebhookController` ingestion endpoint, actuator, OpenAPI/Swagger UI, and the wired-up Kafka (EDA) + Redis (cache) adapters. |
| Processor | `fireflyframework-webhooks-processor` | Consumer-side hexagonal ports (`WebhookProcessor`, `WebhookSignatureValidator`, `WebhookIdempotencyService`) plus `AbstractWebhookEventListener` and the cache-based idempotency auto-configuration. This is the dependency you add to your worker service. |
| SDK | `fireflyframework-webhooks-sdk` | Auto-generated reactive Java client (OpenAPI Generator, WebClient library) for calling the ingestion API. |

### How it fits in the framework

- **EDA backbone** — events are published and consumed through `fireflyframework-eda`. The web app and processor select the Kafka transport via `fireflyframework-eda-kafka` and `firefly.eda.default-publisher-type=KAFKA`.
- **Cache abstraction** — idempotency state and rate-limit/idempotency caching use `fireflyframework-cache`. The web app selects the Redis provider via `fireflyframework-cache-redis` and `firefly.cache.default-cache-type=REDIS` (use `CAFFEINE` for single-instance deployments).
- **Observability** — metrics, health, tracing, and structured logging come from `fireflyframework-observability`.
- **Web foundation** — the ingestion app builds on `fireflyframework-web` (which also provides the `IdempotencyWebFilter` that transparently handles the `X-Idempotency-Key` HTTP header).

## Features

- **Universal ingestion endpoint** — `POST /api/v1/webhook/{providerName}` accepts any provider without code changes; payload stored as-is.
- **Provider-agnostic event model** — `WebhookReceivedEvent` carries payload, headers, query params, source IP, HTTP method, and enriched metadata for downstream consumers.
- **Pluggable consumer SPI** — implement `WebhookProcessor` for business logic and `WebhookSignatureValidator` for provider-specific signature checks (HMAC, etc.).
- **Content-based idempotency** — `CacheBasedWebhookIdempotencyService` derives a deterministic key from the payload (`provider:id` or full-payload hash) so duplicate events are dropped even across consumer-group rebalances and worker autoscaling; distributed locking with auto-expiring TTLs.
- **Per-provider & per-IP rate limiting** — `WebhookRateLimitService` backed by Resilience4j, returning HTTP 429 on overflow.
- **Configurable retry** — global defaults plus per-provider overrides with exponential backoff, jitter, and selective retry-on-error policies (`RetryProperties`).
- **Batching** — optional grouping of events to improve broker throughput (`WebhookBatchingService`, per-provider overrides).
- **Compression** — optional payload compression (GZIP / ZSTD / LZ4) above a configurable size threshold (`WebhookCompressionService`).
- **Dead-letter queue** — rejected/failed webhooks routed to a configurable DLQ topic (`DeadLetterQueueService`).
- **Metadata enrichment** — source-IP extraction (X-Forwarded-For aware), User-Agent parsing, request sizing, request IDs.
- **Configurable destination routing** — topic/queue naming via prefix/suffix, provider-as-topic, or a single custom destination.
- **Security validation** — payload-size limits, provider-name pattern enforcement, IP whitelisting (exact + CIDR), Content-Type and HTTP-method allow-lists.
- **Resilience** — circuit breaker around broker publishing (`ResilientWebhookProcessingService`) with a dedicated health indicator.
- **Observability** — Micrometer metrics (received/published/rejected/failed counters, payload size, processing time), Prometheus export, tracing-context propagation (`TracingWebFilter`, `TracingContextExtractor`), and liveness/readiness/Redis/circuit-breaker health indicators.
- **`AbstractWebhookEventListener`** — base class that orchestrates idempotency → signature validation → processing → marking-processed, with lock release on failure for safe retries.
- **OpenAPI + generated SDK** — Swagger UI on the web app and a reactive WebClient SDK module.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A Kafka broker (default EDA transport for publishing/consuming `WebhookReceivedEvent`)
- A Redis server (recommended for distributed idempotency and rate-limit state; Caffeine works for single-instance deployments)

## Installation

All artifacts are published under the `org.fireflyframework` group, and versions are managed by the Firefly parent/BOM — omit `<version>` when your project inherits or imports it.

**Consumer / worker service** (the most common dependency — adds the ports and `AbstractWebhookEventListener`):

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-processor</artifactId>
    <!-- version managed by the Firefly BOM / parent -->
</dependency>
```

**Ingestion core** (if you embed the processing services into your own app rather than running the web app):

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-core</artifactId>
    <!-- version managed by the Firefly BOM / parent -->
</dependency>
```

**Generated client SDK** (to call the ingestion API from another service):

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-sdk</artifactId>
    <!-- version managed by the Firefly BOM / parent -->
</dependency>
```

The runnable ingestion service (`fireflyframework-webhooks-web`) is a deployable Spring Boot application; build it with `mvn -pl fireflyframework-webhooks-web -am package` and run the resulting jar.

## Quick Start

### 1. Receive webhooks (ingestion)

Run the `fireflyframework-webhooks-web` application (or embed `fireflyframework-webhooks-core`). Any provider can POST to the universal endpoint — no per-provider code is required:

```bash
curl -X POST http://localhost:8080/api/v1/webhook/stripe \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: t=1700000000,v1=abc123..." \
  -d '{"id":"evt_123","type":"payment_intent.succeeded","data":{"object":{}}}'
```

The endpoint responds `202 Accepted`, captures all headers (including the signature) for later validation, and publishes a `WebhookReceivedEvent` to the broker. An optional `X-Idempotency-Key` header is handled transparently by the web layer's `IdempotencyWebFilter`.

### 2. Process webhooks (consumer)

In your worker service, depend on `fireflyframework-webhooks-processor`, implement a `WebhookProcessor` for your provider, and extend `AbstractWebhookEventListener` to bind it to an EDA destination. Idempotency and signature validation are wired in automatically when the corresponding beans are present.

```java
// 1) The business logic for one provider
@Component
public class StripeWebhookProcessor implements WebhookProcessor {

    @Override
    public Mono<WebhookProcessingResult> process(WebhookProcessingContext context) {
        String eventType = context.getPayload().path("type").asText();
        return switch (eventType) {
            case "payment_intent.succeeded" -> handlePaymentSuccess(context)
                    .thenReturn(WebhookProcessingResult.success("payment recorded"));
            default -> Mono.just(WebhookProcessingResult.skipped("Unhandled type: " + eventType));
        };
    }

    @Override
    public String getProviderName() {
        return "stripe";
    }
}

// 2) Bind the processor to a broker destination
@Component
public class StripeWebhookListener extends AbstractWebhookEventListener {

    public StripeWebhookListener(StripeWebhookProcessor processor,
                                 ObjectMapper objectMapper,
                                 WebhookSignatureValidator stripeValidator,   // optional
                                 WebhookIdempotencyService idempotencyService) { // auto-configured
        super(processor, objectMapper, stripeValidator, idempotencyService);
    }

    @org.fireflyframework.eda.annotation.EventListener(
            destinations = {"stripe"},
            groupId = "stripe-webhook-processor",
            consumerType = PublisherType.KAFKA,
            errorStrategy = ErrorHandlingStrategy.LOG_AND_CONTINUE
    )
    public Mono<Void> onStripeWebhook(WebhookReceivedEvent event) {
        return handleWebhookEvent(event); // idempotency → signature → process
    }
}
```

`WebhookProcessingResult` provides `success(...)`, `retry(...)`, `failed(...)`, and `skipped(...)` factory methods to signal the outcome back to the listener pipeline.

## Configuration

All ingestion-side properties live under the `firefly.webhooks.*` prefix (defined by `WebhookSecurityProperties`, `BatchingProperties`, `CompressionProperties`, `RetryProperties`, and `@Value`-bound destination/DLQ keys). The following shows the **real defaults**:

```yaml
firefly:
  webhooks:
    # Broker destination naming: {prefix}{provider-name}{suffix}
    destination:
      prefix: ""                     # e.g. "webhooks." -> "webhooks.stripe"
      suffix: ""                     # e.g. ".received" -> "stripe.received"
      use-provider-as-topic: true    # false -> topic = {prefix}{suffix}
      custom: ""                     # if set, all providers publish here

    security:
      validate-payload-size: true
      max-payload-size: 1048576      # 1 MB, in bytes
      validate-provider-name: true
      provider-name-pattern: "^[a-z0-9-]+$"
      enable-ip-whitelist: false
      ip-whitelist: {}               # per-provider exact IPs and/or CIDR ranges
      enable-request-validation: true
      allowed-methods: [POST]
      require-content-type: true
      allowed-content-types: [application/json, application/x-www-form-urlencoded]

    dlq:
      enabled: true
      topic: webhooks.dlq

    retry:
      defaults:
        max-attempts: 3
        initial-delay: PT1S
        max-delay: PT30S
        multiplier: 2.0
        enable-jitter: true
        jitter-factor: 0.5
        retry-on-timeout: true
        retry-on-connection-error: true
        retry-on-server-error: true
        retry-on-client-error: false
      providers: {}                  # per-provider overrides

    batching:
      enabled: false
      max-batch-size: 100
      max-wait-time: PT1S
      buffer-size: 1000
      providers: {}                  # per-provider overrides

    compression:
      enabled: false
      min-size: 1024                 # bytes; payloads below this are not compressed
      algorithm: GZIP                # GZIP | ZSTD | LZ4
      level: 6                       # 1-9 (GZIP), 1-22 (ZSTD)

    metadata-enrichment:
      enabled: true

  # Idempotency + rate-limit state (consumed by the cache abstraction)
  cache:
    enabled: true
    default-cache-type: REDIS        # REDIS (distributed) or CAFFEINE (single instance)

  # Event-driven backbone
  eda:
    enabled: true
    default-publisher-type: KAFKA
```

Key knobs:

| Property | Default | Description |
| --- | --- | --- |
| `firefly.webhooks.security.max-payload-size` | `1048576` | Reject payloads larger than this (bytes). |
| `firefly.webhooks.security.enable-ip-whitelist` | `false` | When `true`, only allow-listed IPs/CIDRs per provider may post. |
| `firefly.webhooks.destination.use-provider-as-topic` | `true` | Route each provider to its own broker topic. |
| `firefly.webhooks.dlq.enabled` / `dlq.topic` | `true` / `webhooks.dlq` | Dead-letter destination for rejected/failed webhooks. |
| `firefly.webhooks.retry.defaults.*` | see above | Exponential-backoff retry policy; override per provider under `retry.providers.<name>`. |
| `firefly.webhooks.batching.enabled` | `false` | Group events before publishing to the broker. |
| `firefly.webhooks.compression.enabled` | `false` | Compress large payloads (`min-size`, `algorithm`, `level`). |
| `firefly.webhooks.metadata-enrichment.enabled` | `true` | Toggle User-Agent/IP/request enrichment. |
| `firefly.cache.default-cache-type` | `REDIS` | `REDIS` for distributed workers, `CAFFEINE` for single-instance. |
| `firefly.eda.default-publisher-type` | `KAFKA` | EDA transport used to publish/consume webhook events. |

Resilience4j circuit-breaker, rate-limiter, and time-limiter behavior (e.g. `ratelimiter.configs.default.limit-for-period`) is configured under the standard `resilience4j.*` keys; see the web module's `application.yml`.

> Every property is also overridable via environment variables (e.g. `FIREFLY_WEBHOOKS_SECURITY_MAX_PAYLOAD_SIZE`) and system properties, per the standard Spring Boot relaxed-binding rules.

## Documentation

In-repo guides:

- [Architecture](ARCHITECTURE.md)
- [Configuration](CONFIGURATION.md)

Framework-wide documentation and the module catalog are available in the [Firefly Framework organization](https://github.com/fireflyframework).

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
