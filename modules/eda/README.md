# Firefly Framework - Event-Driven Architecture (EDA)

[![CI](https://github.com/fireflyframework/fireflyframework-eda/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-eda/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive, transport-agnostic event-driven architecture core for Spring Boot — one publisher/consumer API with annotation-driven publishing, declarative listeners, and pluggable Kafka, RabbitMQ and PostgreSQL adapters.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework EDA is the **core abstraction** for event-driven architectures across the Firefly Framework. It defines a single, reactive publisher/consumer SPI (`EventPublisher`, `EventConsumer`) and a rich annotation model (`@EventPublisher`, `@PublishResult`, `@EventListener`) so application code emits and handles domain events without binding to any particular broker. The same business code runs unchanged whether events flow over Apache Kafka, RabbitMQ, PostgreSQL, or the in-process Spring application event bus.

This module ships **everything that is broker-independent**: the SPI, the annotation aspects, the `EventPublisherFactory` (which auto-discovers every `EventPublisher` bean on the classpath and selects one by type or priority), the `EventEnvelope` metadata model, pluggable serialization (JSON, Avro, Protobuf), composable event filters, a resilient publisher wrapper (circuit breaker, retry, rate limiter via Resilience4j), a dead-letter-queue handler, plus Actuator health indicators and Micrometer metrics. Out of the box, the core provides a working **Spring Application Events** transport (in-JVM publish/consume) and a `Noop` transport for testing — no external infrastructure required.

The concrete broker transports are now **separate adapter modules**. The core discovers them at runtime: add the adapter dependency, enable it in `firefly.eda.*` configuration, and the `EventPublisherFactory` picks it up automatically. The `firefly.eda.default-publisher-type` property (or `PublisherType.AUTO`) selects which transport a publish goes to, with `AUTO` resolving in priority order `KAFKA → RABBITMQ → POSTGRES → APPLICATION_EVENT` based on what is configured and on the classpath.

### Transport adapters

| Transport | Adapter module | Selected by | Persistence | Ordering |
|-----------|----------------|-------------|-------------|----------|
| Apache Kafka | [`fireflyframework-eda-kafka`](https://github.com/fireflyframework/fireflyframework-eda-kafka) | `PublisherType.KAFKA` | yes | yes |
| RabbitMQ (AMQP) | [`fireflyframework-eda-rabbitmq`](https://github.com/fireflyframework/fireflyframework-eda-rabbitmq) | `PublisherType.RABBITMQ` | yes | no |
| PostgreSQL (outbox + `LISTEN`/`NOTIFY`) | [`fireflyframework-eda-postgres`](https://github.com/fireflyframework/fireflyframework-eda-postgres) | `PublisherType.POSTGRES` | yes | yes |
| Spring Application Events (in-JVM) | **built into this core** | `PublisherType.APPLICATION_EVENT` | no | no |
| No-op (testing / disabled) | **built into this core** | `PublisherType.NOOP` | no | no |

The `firefly.eda.*` configuration schema (including `publishers.kafka.*`, `publishers.rabbitmq.*`, `publishers.postgres.*` and their consumer counterparts) is defined here in the core so it remains stable and consistent regardless of which adapters you install.

## Features

- **Unified reactive SPI** — `EventPublisher` and `EventConsumer` expose `Mono`/`Flux` APIs; one programming model for all transports
- **Annotation-driven publishing** — `@EventPublisher` (publish a parameter or wrapped method args, `BEFORE`/`AFTER`/`BOTH` execution) and `@PublishResult` (publish the method's return value), both with SpEL `condition`, `key`, `headers`, `destination` and `eventType`
- **Declarative listeners** — `@EventListener` with `destinations`, `eventTypes` (glob patterns), SpEL `condition`, `priority`, per-listener retry and `errorStrategy`
- **Pluggable transports** — Kafka, RabbitMQ and PostgreSQL adapters are independent modules; the in-JVM Spring Application Event transport and a `Noop` transport ship in the core
- **Auto transport selection** — `EventPublisherFactory` discovers all `EventPublisher` beans and resolves `PublisherType.AUTO` in priority order `KAFKA → RABBITMQ → POSTGRES → APPLICATION_EVENT`
- **Dynamic destination selection** — `getPublisherWithDestination(...)` overrides the default topic/queue/channel at runtime via `DestinationAwarePublisher`
- **Event envelope pattern** — `EventEnvelope` carries payload plus metadata (event type, destination, headers, timestamps) end to end
- **Pluggable serialization** — `MessageSerializer` with built-in JSON, Avro and Protobuf implementations (`SerializationFormat`)
- **Composable filtering** — `EventTypeFilter`, `HeaderEventFilter`, `DestinationEventFilter` and `CompositeEventFilter` implementing a common `EventFilter` SPI
- **Resilience** — `ResilientEventPublisher` wraps any publisher with Resilience4j circuit breaker, retry and rate limiter (all configurable, opt-in by classpath)
- **Dead-letter handling** — `DeadLetterQueueHandler` and `DeadLetterQueueEvent` capture messages that exhaust retries
- **Custom error handling** — `CustomErrorHandler` SPI with a registry and built-in `MetricsErrorHandler` / `NotificationErrorHandler`
- **Dynamic registration** — `DynamicEventListenerRegistry` registers and removes listeners at runtime
- **Observability** — `EdaHealthIndicator` for Actuator and `EdaMetrics` for Micrometer, wired through `fireflyframework-observability`
- **Spring Boot auto-configuration** — `FireflyEdaAutoConfiguration` activates on `firefly.eda.enabled=true` (default) and component-scans the whole module

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A transport adapter for production messaging: an Apache Kafka broker, a RabbitMQ broker, or a PostgreSQL 11+ database (the in-JVM Spring Application Event transport needs no external infrastructure)

## Installation

Add the EDA core. The version is managed by the Firefly BOM / parent, so you normally omit it:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda</artifactId>
    <!-- version managed by fireflyframework-bom / fireflyframework-parent -->
</dependency>
```

To publish/consume over a real broker, add the matching transport adapter alongside the core. For example, for Apache Kafka:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda-kafka</artifactId>
    <!-- version managed by the BOM -->
</dependency>
```

Swap the artifact for `fireflyframework-eda-rabbitmq` or `fireflyframework-eda-postgres` as needed. You can install more than one adapter and route events per transport with `PublisherType`.

## Quick Start

With only the core on the classpath, events flow over the in-JVM Spring Application Event bus — perfect for a single instance, tests, or local development.

```java
import org.fireflyframework.eda.annotation.EventPublisher;
import org.fireflyframework.eda.annotation.EventListener;
import org.fireflyframework.eda.event.EventEnvelope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class OrderService {

    // Publishes the method's return value to the "orders" destination after it completes.
    @PublishResult(destination = "orders", eventType = "order.created")
    public Mono<OrderCreatedEvent> createOrder(OrderRequest request) {
        // business logic — the returned event is published automatically
        return Mono.just(new OrderCreatedEvent(request.getId()));
    }
}

@Component
public class OrderEventHandler {

    // Receives events from "orders"; eventTypes/condition support glob + SpEL filtering.
    @EventListener(destinations = "orders", eventTypes = "order.created")
    public Mono<Void> onOrderCreated(EventEnvelope envelope) {
        OrderCreatedEvent event = (OrderCreatedEvent) envelope.getPayload();
        return processOrder(event);
    }
}
```

You can also publish imperatively through the factory, which selects the transport for you:

```java
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.publisher.EventPublisherFactory;

@Service
public class PaymentService {

    private final EventPublisherFactory publishers;

    public PaymentService(EventPublisherFactory publishers) {
        this.publishers = publishers;
    }

    public Mono<Void> emit(PaymentCaptured event) {
        // PublisherType.AUTO resolves KAFKA -> RABBITMQ -> POSTGRES -> APPLICATION_EVENT
        return publishers.getPublisher(PublisherType.AUTO)
                .publish(event, "payments");
    }
}
```

To send the same code over Apache Kafka, add `fireflyframework-eda-kafka` and configure a Kafka publisher (see below) — no code changes required.

## Configuration

All properties live under the `firefly.eda.*` namespace (see `EdaProperties`). The EDA core is enabled by default; publishers and consumers are **disabled by default** and opt in per transport. A representative configuration:

```yaml
firefly:
  eda:
    enabled: true                          # master switch for the whole module (default: true)
    default-publisher-type: AUTO           # AUTO | APPLICATION_EVENT | KAFKA | RABBITMQ | POSTGRES | NOOP
    default-connection-id: default         # connection key used when none is given
    default-destination: events            # destination used when none is specified
    default-serialization-format: json     # json | avro | protobuf
    default-timeout: 30s
    metrics-enabled: true
    health-enabled: true
    tracing-enabled: true

    resilience:
      enabled: true
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50
        minimum-number-of-calls: 10
        sliding-window-size: 10
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
      retry:
        enabled: true
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2.0
      rate-limiter:
        enabled: false
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 5s

    publishers:
      enabled: true                        # global publisher switch (default: false)
      application-event:
        enabled: true                      # in-JVM transport, always available in the core
        default-destination: application-events
      # --- requires the fireflyframework-eda-kafka adapter ---
      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092
          default-topic: events
      # --- requires the fireflyframework-eda-rabbitmq adapter ---
      rabbitmq:
        default:
          enabled: true
          host: localhost
          port: 5672
          username: guest
          password: guest
          default-exchange: events
          default-routing-key: event
      # --- requires the fireflyframework-eda-postgres adapter ---
      postgres:
        default:
          enabled: true
          host: localhost
          port: 5432
          database: app
          username: app
          password: secret
          schema: public
          outbox-table: firefly_eda_outbox
          auto-create-schema: true         # provision outbox table + NOTIFY trigger at startup

    consumer:
      enabled: true                        # global consumer switch (default: false)
      group-id: my-service
      concurrency: 1
      retry:
        enabled: true
        max-attempts: 3
        initial-delay: 1s
        max-delay: 5m
        multiplier: 2.0
      application-event:
        enabled: true
      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092
          topics: events
          auto-offset-reset: earliest
      rabbitmq:
        default:
          enabled: true
          host: localhost
          port: 5672
          queues: events-queue
          prefetch-count: 10
      postgres:
        default:
          enabled: true
          host: localhost
          port: 5432
          database: app
          username: app
          password: secret
          channels: events                 # destinations to LISTEN on
          polling-interval: 30s            # NOTIFY-loss fallback poll cadence (0s disables polling)
          max-attempts: 3                  # outbox row -> DEAD_LETTER after N failures
          batch-size: 50
```

### Key properties

| Property | Default | Description |
|----------|---------|-------------|
| `firefly.eda.enabled` | `true` | Master switch for the entire EDA module. |
| `firefly.eda.default-publisher-type` | `AUTO` | Transport used when none is specified. `AUTO` resolves `KAFKA → RABBITMQ → POSTGRES → APPLICATION_EVENT`. |
| `firefly.eda.default-serialization-format` | `json` | Serializer used by default (`json`, `avro`, `protobuf`). |
| `firefly.eda.default-timeout` | `30s` | Default timeout for publish operations. |
| `firefly.eda.metrics-enabled` / `health-enabled` / `tracing-enabled` | `true` | Toggle Micrometer metrics, Actuator health, and tracing integration. |
| `firefly.eda.publishers.enabled` | `false` | Global opt-in for all publishers. |
| `firefly.eda.consumer.enabled` | `false` | Global opt-in for all consumers. |
| `firefly.eda.consumer.group-id` | `firefly-eda` | Default consumer group id (used by group-based transports such as Kafka). |
| `firefly.eda.resilience.*` | see above | Circuit breaker, retry and rate-limiter settings applied by `ResilientEventPublisher`. |

The `kafka.*`, `rabbitmq.*` and `postgres.*` sub-trees are mapped by connection id (the `default` key shown above), letting you define multiple named connections per transport. These keys are recognized by the core's property schema even before the corresponding adapter is installed; the actual transport beans only activate when the matching adapter module is on the classpath and enabled.

## Documentation

- Framework documentation hub and module catalog: [fireflyframework/fireflyframework](https://github.com/fireflyframework)
- Transport adapters: [eda-kafka](https://github.com/fireflyframework/fireflyframework-eda-kafka), [eda-rabbitmq](https://github.com/fireflyframework/fireflyframework-eda-rabbitmq), [eda-postgres](https://github.com/fireflyframework/fireflyframework-eda-postgres)

In-repo guides in [docs/](docs/):

- [Quickstart](docs/QUICKSTART.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Configuration](docs/CONFIGURATION.md)
- [Annotations](docs/ANNOTATIONS.md)
- [API Reference](docs/API_REFERENCE.md)
- [Publisher Types](docs/PUBLISHER_TYPES.md)
- [Dynamic Topic Selection](docs/DYNAMIC_TOPIC_SELECTION.md)
- [EventListener Examples](docs/EVENTLISTENER_EXAMPLES.md)
- [Custom Error Handling](docs/CUSTOM_ERROR_HANDLING.md)
- [Examples](docs/EXAMPLES.md)
- [Developer Guide](docs/DEVELOPER_GUIDE.md)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](docs/CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
