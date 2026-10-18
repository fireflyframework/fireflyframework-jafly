# Firefly Framework - EDA RabbitMQ

[![CI](https://github.com/fireflyframework/fireflyframework-eda-rabbitmq/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-eda-rabbitmq/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> RabbitMQ transport adapter for the Firefly Framework event-driven architecture (EDA) abstraction — reactive event publishing and consumption over RabbitMQ via Spring AMQP.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework EDA RabbitMQ implements the `EventPublisher` and `EventConsumer` ports of the Firefly EDA core ([`fireflyframework-eda`](https://github.com/fireflyframework/fireflyframework-eda)) using RabbitMQ as the underlying transport, on top of Spring AMQP. It provides reactive, non-blocking publishing through `RabbitMqEventPublisher` and queue-based consumption through `RabbitMqEventConsumer`, integrating cleanly with the framework's `@EventPublisher` / `@EventListener` programming model.

RabbitMQ is one of several pluggable EDA transports — alongside the Kafka adapter (`fireflyframework-eda-kafka`) and the PostgreSQL outbox adapter (`fireflyframework-eda-postgres`) — selected by `PublisherType.RABBITMQ`. The core component scan discovers the `@Component` publisher and consumer shipped here, while this module's auto-configurations build the broker `ConnectionFactory`, `RabbitTemplate`, and `AmqpAdmin` beans. Set `firefly.eda.default-publisher-type: RABBITMQ` to route events through this adapter, or leave the default `AUTO` (priority `KAFKA → RABBITMQ → POSTGRES → APPLICATION_EVENT`) to let the runtime pick the first configured transport.

The adapter follows the framework's hexagonal configuration principle: it reads **exclusively** from the `firefly.eda.publishers.rabbitmq.*` and `firefly.eda.consumer.rabbitmq.*` namespaces and never from Spring AMQP's native `spring.rabbitmq.*` properties — keeping the configuration surface consistent across every Firefly EDA transport. A `FireflyEdaRabbitMqEnvironmentPostProcessor` conditionally excludes Spring Boot's `RabbitAutoConfiguration` (unless you explicitly set `spring.rabbitmq.host`), so Firefly owns its own publisher and consumer connection factories without bean conflicts or spurious connection/health failures on startup.

## Features

- **Reactive publisher** — `RabbitMqEventPublisher` implements `EventPublisher` and `ConnectionAwarePublisher`, publishing on the bounded-elastic scheduler with full `MessageProperties` mapping (content type, timestamp, custom headers, and `publisher_type` / `connection_id` / `event_class` metadata).
- **Queue-based consumer** — `RabbitMqEventConsumer` implements `EventConsumer`, subscribing to pre-declared queues via a `SimpleMessageListenerContainer` and routing deserialized events to `@EventListener` methods through the core `EventListenerProcessor`.
- **Exchange / routing-key destinations** — destinations use the `exchange/routingKey` format (or just `exchange` for an empty routing key); the publisher falls back to the configured `default-exchange` when no destination is supplied.
- **Hexagonal configuration** — driven entirely by `firefly.eda.{publishers,consumer}.rabbitmq.*`; Spring AMQP's `spring.rabbitmq.*` is intentionally ignored.
- **Publisher infrastructure auto-config** — `FireflyEdaRabbitMqAutoConfiguration` creates the `fireflyEdaRabbitPublisherConnectionFactory` (`CachingConnectionFactory`) and `fireflyEdaRabbitTemplate`; `FireflyEdaAmqpAdminAutoConfiguration` adds an `AmqpAdmin` (`RabbitAdmin`) for infrastructure management.
- **Consumer infrastructure auto-config** — `FireflyEdaRabbitMqConsumerAutoConfiguration` creates the `fireflyEdaRabbitConsumerConnectionFactory` that the consumer depends on (see [How It Works](#how-it-works)).
- **Tunable consumer concurrency** — `concurrent-consumers`, `max-concurrent-consumers`, and `prefetch-count` flow straight through to the listener container.
- **Self-excluding native auto-config** — `FireflyEdaRabbitMqEnvironmentPostProcessor` excludes Spring Boot's `RabbitAutoConfiguration` in the hexagonal case, preserving any existing `spring.autoconfigure.exclude` entries.
- **Health monitoring** — both publisher (`PublisherHealth`) and consumer (`ConsumerHealth`) expose live connection status plus consumed/processed/failed message counters.
- **Spring Boot auto-configuration** — registered via `AutoConfiguration.imports`; the environment post-processor via `spring.factories`.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A reachable RabbitMQ broker (3.x+)
- The Firefly EDA core (`fireflyframework-eda`), brought in transitively

## Installation

Add the adapter alongside the EDA core. The version is managed by the Firefly parent/BOM, so you normally omit it:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda-rabbitmq</artifactId>
    <!-- version managed by the Firefly Framework parent/BOM -->
</dependency>
```

This adapter declares a dependency on `fireflyframework-eda`, so adding it to the classpath is enough to make the RabbitMQ transport available.

## Quick Start

**1. Add the dependencies** (the EDA core is pulled in transitively by the adapter, but listing it explicitly is fine):

```xml
<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-eda</artifactId>
    </dependency>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-eda-rabbitmq</artifactId>
    </dependency>
</dependencies>
```

**2. Enable and configure the RabbitMQ transport** in `application.yml`:

```yaml
firefly:
  eda:
    enabled: true
    default-publisher-type: RABBITMQ   # or AUTO to let the runtime pick
    publishers:
      enabled: true
      rabbitmq:
        default:
          enabled: true
          host: localhost
          default-exchange: events
    consumer:
      enabled: true
      rabbitmq:
        default:
          enabled: true
          host: localhost
          queues: events-queue
```

**3. Publish events** using the standard EDA API — the adapter handles transport:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final EventPublisher eventPublisher;

    public Mono<Void> placeOrder(OrderPlaced event) {
        // destination format: "exchange/routingKey" (routing key optional)
        return eventPublisher.publish(event, "events/order.placed", Map.of());
    }
}
```

**4. Consume events** with `@EventListener` (the listener container subscribes to the configured `queues`):

```java
@Component
public class OrderEventListener {

    @EventListener(destinations = "events/order.placed")
    public Mono<Void> onOrderPlaced(OrderPlaced event) {
        // handle the event
        return Mono.empty();
    }
}
```

## Configuration

All properties live under `firefly.eda.*`. Both the publisher and consumer connections are keyed by a connection id (`default` shown here); the RabbitMQ sub-connection must be explicitly enabled (`enabled: true`) **and** have a `host` set for the infrastructure beans to be created.

```yaml
firefly:
  eda:
    enabled: true                       # default: true
    default-publisher-type: AUTO        # AUTO | RABBITMQ | KAFKA | POSTGRES | APPLICATION_EVENT
    publishers:
      enabled: true                     # default: true
      rabbitmq:
        default:
          enabled: false                # default: false — set true to activate
          host: localhost               # default: localhost
          port: 5672                    # default: 5672
          username: guest               # default: guest
          password: guest               # default: guest
          virtual-host: /               # default: /
          default-exchange: events      # default: events
          default-routing-key: event    # default: event
    consumer:
      enabled: false                    # default: false — set true to activate consumption
      rabbitmq:
        default:
          enabled: false                # default: false — set true to activate
          host: localhost               # default: localhost
          port: 5672                    # default: 5672
          username: guest               # default: guest
          password: guest               # default: guest
          virtual-host: /               # default: /
          queues: events-queue          # default: events-queue (comma-separated for multiple)
          concurrent-consumers: 1       # default: 1
          max-concurrent-consumers: 5   # default: 5
          prefetch-count: 10            # default: 10
```

### Publisher properties (`firefly.eda.publishers.rabbitmq.default.*`)

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `false` | Activates the RabbitMQ publisher connection factory. |
| `host` / `port` | `localhost` / `5672` | Broker host and AMQP port. |
| `username` / `password` | `guest` / `guest` | Broker credentials. |
| `virtual-host` | `/` | RabbitMQ virtual host. |
| `default-exchange` | `events` | Exchange used when a publish call supplies no destination. |
| `default-routing-key` | `event` | Default routing key for the connection. |

### Consumer properties (`firefly.eda.consumer.rabbitmq.default.*`)

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `false` | Activates the RabbitMQ consumer connection factory (required for the consumer bean to wire — see [How It Works](#how-it-works)). |
| `host` / `port` | `localhost` / `5672` | Broker host and AMQP port. |
| `username` / `password` | `guest` / `guest` | Broker credentials. |
| `virtual-host` | `/` | RabbitMQ virtual host. |
| `queues` | `events-queue` | Comma-separated list of pre-declared queues to subscribe to. |
| `concurrent-consumers` | `1` | Initial number of concurrent consumers on the listener container. |
| `max-concurrent-consumers` | `5` | Maximum number of concurrent consumers. |
| `prefetch-count` | `10` | AMQP prefetch (QoS) per consumer. |

> **Note:** Unlike Kafka topic patterns, RabbitMQ consumers subscribe to specific, pre-declared queues defined by `queues`. The `exchange/routingKey` destinations in `@EventListener` are used for publisher routing and post-consumption filtering, not for queue subscription.

## How It Works

The adapter creates separate publisher-side and consumer-side connection factories so each can be tuned and excluded independently:

- **Publisher path** — `FireflyEdaRabbitMqAutoConfiguration` builds `fireflyEdaRabbitPublisherConnectionFactory` and `fireflyEdaRabbitTemplate` from `firefly.eda.publishers.rabbitmq.default.*`; `FireflyEdaAmqpAdminAutoConfiguration` reuses that factory for a `RabbitAdmin`. `RabbitMqEventPublisher` is `@ConditionalOnBean(name = "fireflyEdaRabbitTemplate")`.
- **Consumer path** — `RabbitMqEventConsumer` is guarded by `@ConditionalOnBean(name = "fireflyEdaRabbitConsumerConnectionFactory")` and injects that bean by qualifier. `FireflyEdaRabbitMqConsumerAutoConfiguration` creates this `fireflyEdaRabbitConsumerConnectionFactory` (a `CachingConnectionFactory` from `firefly.eda.consumer.rabbitmq.default.*`), mirroring the publisher-side factory — this is what makes the consumer wire and run when `firefly.eda.consumer.enabled=true` and the RabbitMQ consumer is enabled.
- **Native auto-config exclusion** — `FireflyEdaRabbitMqEnvironmentPostProcessor` (registered via `spring.factories`) excludes Spring Boot's `RabbitAutoConfiguration` unless you explicitly opt in with `spring.rabbitmq.host`. This avoids the ambiguity where Boot's `rabbitListenerContainerFactory` cannot choose between the two Firefly `ConnectionFactory` beans, and prevents connection/health failures when RabbitMQ is not in use.

## Documentation

- Module catalog and architecture overview: [Firefly Framework organization hub](https://github.com/fireflyframework)
- EDA core abstraction (ports, annotations, `EventEnvelope`, serialization): [`fireflyframework-eda`](https://github.com/fireflyframework/fireflyframework-eda)
- Sibling transports: [`fireflyframework-eda-kafka`](https://github.com/fireflyframework/fireflyframework-eda-kafka), [`fireflyframework-eda-postgres`](https://github.com/fireflyframework/fireflyframework-eda-postgres)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
