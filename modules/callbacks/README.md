# Firefly Framework - Callback Management Platform

[![CI](https://github.com/fireflyframework/fireflyframework-callbacks/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-callbacks/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive outbound webhook delivery platform for Spring Boot — consumes domain events from the Firefly EDA bus and dispatches them to registered third-party endpoints with HMAC signing, retries, circuit breakers, and domain authorization.

---

## Table of Contents

- [Overview](#overview)
  - [Modules](#modules)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
  - [Run the service](#run-the-service)
  - [Register a callback configuration](#register-a-callback-configuration)
  - [Subscribe to events](#subscribe-to-events)
- [REST API](#rest-api)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Callbacks is a reactive, deployable microservice that turns internal domain events into reliable outbound webhooks. It lets Firefly services notify third-party systems whenever something happens — an order is created, a payment settles, a customer is onboarded — without each producing service having to implement HTTP delivery, retries, signing, or endpoint security itself.

The platform consumes events from the Firefly event-driven architecture (it depends on [`fireflyframework-eda`](https://github.com/fireflyframework/fireflyframework-eda) and listens over Kafka by default), matches them against subscriptions and callback configurations stored in PostgreSQL, then dispatches an HTTP request to each registered endpoint. Delivery is built on Spring WebFlux `WebClient` and Resilience4j: every callback runs through a per-configuration circuit breaker and an exponential-backoff retry policy, is optionally signed with an HMAC-SHA256 signature, and is fully tracked so operators can audit every attempt.

The flow is: an event arrives on a subscribed topic → `DynamicListenerFactory` (registered through `fireflyframework-eda`'s `DynamicEventListenerRegistry`) receives it → `CallbackRouter` resolves all callback configurations whose subscribed event types match the event → `CallbackDispatcher` checks the target domain is authorized, signs the payload, sends the request, and records a `CallbackExecution` row capturing the status, response code, duration, and attempt number.

Operationally the service is a standard Firefly microservice: it exposes a versioned REST API under `/api/v1/**` for managing configurations, subscriptions, authorized domains, and execution history; ships OpenAPI/Swagger UI; and is observable via Spring Boot Actuator, Micrometer/Prometheus metrics, and OpenTelemetry tracing.

### Modules

This is a multi-module Maven build (`fireflyframework-callbacks` is the aggregator `pom`). Each submodule has a focused responsibility:

| Module | Artifact | Purpose |
| --- | --- | --- |
| Interfaces | `fireflyframework-callbacks-interfaces` | DTOs (`CallbackConfigurationDTO`, `EventSubscriptionDTO`, `AuthorizedDomainDTO`, `CallbackExecutionDTO`) and enums (`CallbackStatus`, `CallbackExecutionStatus`, `HttpMethod`) — the public contract. |
| Models | `fireflyframework-callbacks-models` | R2DBC entities (`CallbackConfiguration`, `EventSubscription`, `AuthorizedDomain`, `CallbackExecution`), reactive repositories, and the Flyway migration that creates the `callback_*` tables. |
| Core | `fireflyframework-callbacks-core` | Business logic: `CallbackDispatcher`, `CallbackRouter`, `DomainAuthorizationService`, `CallbackConfigurationService`, `EventSubscriptionService`, the `DynamicListenerFactory` that binds EDA subscriptions to handlers, MapStruct mappers, and filtering/pagination helpers. |
| Web | `fireflyframework-callbacks-web` | The Spring Boot application (`CallbackManagementApplication`) and reactive REST controllers for configurations, subscriptions, authorized domains, and executions. |
| SDK | `fireflyframework-callbacks-sdk` | The published OpenAPI specification (`api-spec/openapi.yml`) used to generate typed clients for the platform's REST API. |

## Features

- **Event-driven delivery** — consumes domain events from the Firefly EDA bus (Kafka by default) via `fireflyframework-eda`; no producer-side webhook code required.
- **Dynamic subscriptions** — `DynamicListenerFactory` registers and unregisters event listeners at runtime from `EventSubscription` rows through EDA's `DynamicEventListenerRegistry`, with wildcard event-type matching (e.g. `customer.*`).
- **Event routing** — `CallbackRouter` fans an event out to every callback configuration whose `subscribedEventTypes` match, returning the number of callbacks triggered.
- **Resilient HTTP dispatch** — reactive `WebClient` delivery with per-configuration Resilience4j circuit breakers and exponential-backoff retries that retry only on `5xx`, `408`, `429`, and network errors.
- **HMAC payload signing** — optional `HmacSHA256` signature added under a configurable header (default `X-Firefly-Signature`) for tamper-evident, verifiable delivery.
- **Domain authorization** — `DomainAuthorizationService` validates every target URL against an allow-list of authorized domains before sending, and records per-domain success/failure stats.
- **Full execution tracking** — every attempt persists a `CallbackExecution` with status (`PENDING`, `IN_PROGRESS`, `SUCCESS`, `FAILED_RETRYING`, `FAILED_PERMANENT`, `SKIPPED`), response code, duration, attempt number, and error message.
- **Auto-disable on failure** — configurations carry a failure threshold and failure count so chronically failing endpoints can be paused/disabled.
- **Multi-tenant** — configurations, subscriptions, and domains are tenant-aware via `tenantId`.
- **REST management API** — CRUD plus filter/paginate endpoints for configurations, subscriptions, authorized domains, and executions, documented with OpenAPI/Swagger UI.
- **Production-ready operations** — Actuator health (with liveness/readiness probes and circuit-breaker indicators), Prometheus metrics, OpenTelemetry tracing, and graceful shutdown out of the box.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- PostgreSQL (event/callback persistence via R2DBC + Flyway)
- An Apache Kafka broker (default event transport, via `fireflyframework-eda`)

## Installation

The platform is normally run as a deployable microservice (build and run `fireflyframework-callbacks-web`). If you instead want to embed parts of it — for example to reuse the DTOs, entities, or services in another Firefly service — depend on the individual modules. Versions are managed by the Firefly parent/BOM, so the `<version>` can be omitted when your project inherits it.

```xml
<!-- Public contract: DTOs and enums -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-interfaces</artifactId>
</dependency>

<!-- Core services: dispatcher, router, authorization, dynamic listeners -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-core</artifactId>
</dependency>

<!-- R2DBC entities and repositories -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-models</artifactId>
</dependency>
```

If your build does not inherit the Firefly parent, pin the version explicitly (the current release line is `26.05.x`):

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-core</artifactId>
    <version>26.05.08</version>
</dependency>
```

## Quick Start

### Run the service

Build the aggregator and start the web module against a PostgreSQL database and a Kafka broker:

```bash
mvn -q clean install
mvn -q -pl fireflyframework-callbacks-web spring-boot:run
```

Connection settings are environment-driven (see [Configuration](#configuration)); the defaults assume PostgreSQL on `localhost:5432/callbacks_db` and Kafka on `localhost:9092`. Once running, the API and Swagger UI are available at:

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### Register a callback configuration

Tell the platform where to deliver events and which event types to deliver. The target domain must be authorized first (see the authorized-domains API).

```bash
curl -X POST http://localhost:8080/api/v1/callback-configurations \
  -H "Content-Type: application/json" \
  -d '{
        "name": "orders-webhook",
        "url": "https://partner.example.com/webhooks/orders",
        "httpMethod": "POST",
        "status": "ACTIVE",
        "subscribedEventTypes": ["order.created", "order.updated"],
        "signatureEnabled": true,
        "secret": "s3cr3t",
        "signatureHeader": "X-Firefly-Signature",
        "maxRetries": 3,
        "retryDelayMs": 1000,
        "timeoutMs": 30000
      }'
```

When an `order.created` event arrives on a subscribed topic, the platform delivers a signed `POST` to the URL above, retrying with exponential backoff on transient failures and recording every attempt.

### Subscribe to events

An `EventSubscription` binds the platform to a messaging topic/queue and a set of event-type patterns. Subscriptions are loaded from the database and registered as dynamic EDA listeners at runtime.

```bash
curl -X POST http://localhost:8080/api/v1/event-subscriptions \
  -H "Content-Type: application/json" \
  -d '{
        "name": "order-events",
        "messagingSystemType": "KAFKA",
        "topicOrQueue": "orders",
        "consumerGroupId": "callbacks-mgmt-consumer",
        "eventTypePatterns": ["order.*"],
        "active": true
      }'
```

### Reusing the dispatcher in code

If you embed the core module, dispatching is a reactive call that takes a resolved configuration, the event metadata, and a JSON payload:

```java
import org.fireflyframework.callbacks.core.service.CallbackDispatcher;
import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class OrderNotifier {

    private final CallbackDispatcher dispatcher;

    public OrderNotifier(CallbackDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public Mono<Void> notify(CallbackConfigurationDTO config, UUID eventId, JsonNode payload) {
        // Validates the domain, signs the payload, retries, and records the execution.
        return dispatcher.dispatch(config, "order.created", eventId, payload);
    }
}
```

## REST API

All endpoints are reactive and served under `/api/v1`. Each resource provides standard CRUD plus a `POST /filter` endpoint that accepts a `FilterRequest` and returns a paginated `PaginationResponse`.

| Resource | Base path | Purpose |
| --- | --- | --- |
| Callback configurations | `/api/v1/callback-configurations` | Register and manage outbound webhook endpoints. |
| Event subscriptions | `/api/v1/event-subscriptions` | Bind the platform to messaging topics/queues and event-type patterns. |
| Authorized domains | `/api/v1/authorized-domains` | Maintain the allow-list of domains callbacks may target. |
| Callback executions | `/api/v1/callback-executions` | Query the audit trail of delivery attempts. |

## Configuration

The platform reads plain Spring properties — there is no `@ConfigurationProperties` binding class; values are environment-overridable. The defaults below match `fireflyframework-callbacks-web/src/main/resources/application.yml`.

```yaml
spring:
  application:
    name: common-platform-callbacks-mgmt
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:callbacks_db}
    username: ${DB_USERNAME:firefly}
    password: ${DB_PASSWORD:firefly}
    pool:
      initial-size: 10
      max-size: 50

server:
  port: ${SERVER_PORT:8080}

firefly:
  callbacks:
    # Topics are loaded dynamically from EventSubscription rows at startup
    listener:
      topics: ${CALLBACK_LISTENER_TOPICS:*}
      group-id: ${CALLBACK_LISTENER_GROUP_ID:callbacks-mgmt-consumer}
    dispatcher:
      thread-pool-size: ${CALLBACK_DISPATCHER_THREADS:20}
      default-timeout-ms: ${CALLBACK_DEFAULT_TIMEOUT:30000}
      follow-redirects: ${CALLBACK_FOLLOW_REDIRECTS:false}
    circuit-breaker:
      failure-rate-threshold: ${CALLBACK_CB_FAILURE_RATE:50}
      slow-call-duration-threshold-ms: ${CALLBACK_CB_SLOW_DURATION:10000}
      sliding-window-size: ${CALLBACK_CB_WINDOW_SIZE:100}
      minimum-number-of-calls: ${CALLBACK_CB_MIN_CALLS:10}
      wait-duration-in-open-state-ms: ${CALLBACK_CB_WAIT_DURATION:60000}
    retry:
      max-attempts: ${CALLBACK_RETRY_MAX_ATTEMPTS:3}
      initial-delay-ms: ${CALLBACK_RETRY_INITIAL_DELAY:1000}
      max-delay-ms: ${CALLBACK_RETRY_MAX_DELAY:60000}
      multiplier: ${CALLBACK_RETRY_MULTIPLIER:2.0}
    signature:
      algorithm: ${CALLBACK_SIGNATURE_ALGORITHM:HmacSHA256}
      header-name: ${CALLBACK_SIGNATURE_HEADER:X-Firefly-Signature}

  # Event-driven architecture (provided by fireflyframework-eda)
  eda:
    enabled: true
    consumer:
      kafka:
        default:
          enabled: true
          bootstrap-servers: ${FIREFLY_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

Key properties:

| Property | Default | Description |
| --- | --- | --- |
| `firefly.callbacks.listener.topics` | `*` | Topics the consumer subscribes to; populated at startup from `EventSubscription` rows. Supports wildcards (`*`, `customer.*`). |
| `firefly.callbacks.dispatcher.thread-pool-size` | `20` | Worker pool size for HTTP callbacks. |
| `firefly.callbacks.dispatcher.default-timeout-ms` | `30000` | Default per-request HTTP timeout. |
| `firefly.callbacks.circuit-breaker.failure-rate-threshold` | `50` | Percentage of failed calls that opens the circuit. |
| `firefly.callbacks.retry.max-attempts` | `3` | Maximum delivery attempts before a callback is marked `FAILED_PERMANENT`. |
| `firefly.callbacks.retry.multiplier` | `2.0` | Exponential backoff multiplier between retries. |
| `firefly.callbacks.signature.header-name` | `X-Firefly-Signature` | Header carrying the HMAC signature. |
| `firefly.eda.consumer.kafka.default.bootstrap-servers` | `localhost:9092` | Kafka brokers for the EDA consumer. |

Per-configuration values (`maxRetries`, `retryDelayMs`, `timeoutMs`, `signatureEnabled`, `signatureHeader`, `secret`, `failureThreshold`) stored on each `CallbackConfiguration` override the service-wide defaults at dispatch time. Resilience4j circuit-breaker and time-limiter instances (`callbackDispatcher`) and Actuator/metrics/tracing are also configured in `application.yml`.

## Documentation

In-repo guides live under [`docs/`](docs/):

- [Quickstart Guide](docs/QUICKSTART_GUIDE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Callback System Reference](docs/CALLBACK_SYSTEM_REFERENCE.md)
- [Callback System Summary](docs/CALLBACK_SYSTEM_SUMMARY.md)
- [Callback Examples](docs/CALLBACK_EXAMPLES.md)
- [Testing Guide](docs/TESTING_GUIDE.md)

Framework-wide documentation and the full module catalog are available in the [Firefly Framework organization](https://github.com/fireflyframework). The event transport this platform builds on is documented in [`fireflyframework-eda`](https://github.com/fireflyframework/fireflyframework-eda).

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
