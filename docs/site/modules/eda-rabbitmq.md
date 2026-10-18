![Jafly. EDA - RabbitMQ](../assets/modules/eda-rabbitmq.svg)

# EDA - RabbitMQ

Connect the EDA abstractions to RabbitMQ exchanges, queues, and routing keys.

Configure the named RabbitMQ publisher and consumer entries under firefly.eda and provision the topology they address. Match routing keys and queue bindings on both sides of a message flow.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-eda-rabbitmq` | `jar` | [modules/eda-rabbitmq](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eda-rabbitmq) |

Part of [Event-driven integration](index.md#event-driven-integration).

## In the onboarding service

RabbitMQ can route application lifecycle events to the configured review queues. Match exchanges, routing keys, and queue bindings before connecting the onboarding handler to publishing.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda-rabbitmq</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependencies (the EDA core is pulled in transitively by the adapter, but listing it explicitly is fine):

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

2. Enable and configure the RabbitMQ transport in `application.yml`:

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

3. Publish events using the standard EDA API. The adapter handles transport:

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

4. Consume events with `@EventListener` (the listener container subscribes to the configured `queues`):

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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

Property-based wiring conditions found in this module:

- `prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyEdaAmqpAdminAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaAmqpAdminAutoConfiguration.java).
- `prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyEdaRabbitMqAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaRabbitMqAutoConfiguration.java).
- `prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyEdaRabbitMqConsumerAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaRabbitMqConsumerAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`RabbitMqEventPublisher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/publisher/rabbitmq/RabbitMqEventPublisher.java) | Defines `RabbitMqEventPublisher`, `publish` operations. |
| [`RabbitMqEventConsumer`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/consumer/rabbitmq/RabbitMqEventConsumer.java) | Defines `RabbitMqEventConsumer`, `initialize`, `shutdown`, `stop` operations. |
| [`FireflyEdaAmqpAdminAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaAmqpAdminAutoConfiguration.java) | Defines `FireflyEdaAmqpAdminAutoConfiguration`, `fireflyEdaAmqpAdmin` operations. |
| [`FireflyEdaRabbitMqAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaRabbitMqAutoConfiguration.java) | Defines `FireflyEdaRabbitMqAutoConfiguration`, `fireflyEdaRabbitPublisherConnectionFactory`, `fireflyEdaRabbitTemplate` operations. |
| [`FireflyEdaRabbitMqConsumerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaRabbitMqConsumerAutoConfiguration.java) | Defines `FireflyEdaRabbitMqConsumerAutoConfiguration`, `fireflyEdaRabbitConsumerConnectionFactory` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eda-rabbitmq -am verify
./mvnw -pl :fireflyframework-eda-rabbitmq -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [RabbitMqIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/test/java/org/fireflyframework/eda/integration/RabbitMqIntegrationTest.java).
- [RabbitMqConsumerIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/test/java/org/fireflyframework/eda/integration/consumer/RabbitMqConsumerIntegrationTest.java).
- [BaseIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/test/java/org/fireflyframework/eda/testconfig/BaseIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `shouldGetRabbitMqPublisherFromFactory` test demonstrates one concrete behavior. Excerpt from [RabbitMqIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/test/java/org/fireflyframework/eda/integration/RabbitMqIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
@DisplayName("Should get RabbitMQ publisher from factory")
void shouldGetRabbitMqPublisherFromFactory() {
    // Skip if publisher factory is not available
    if (publisherFactory == null) {
        System.out.println("Skipping test - Publisher factory not available");
        return;
    }

    // Act
    EventPublisher publisher = publisherFactory.getPublisher(PublisherType.RABBITMQ, "default");

    // Assert - May be null if RabbitMQ is not configured
    // Publisher may be wrapped in ResilientEventPublisher, so check publisher type instead of class
    if (publisher != null) {
        assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.RABBITMQ);
        assertThat(publisher.isAvailable()).isTrue();
    }
}
```

## Detailed reference

### Configuration

All properties live under `firefly.eda.*`. Both the publisher and consumer connections are keyed by a connection id (`default` shown here); the RabbitMQ sub-connection must be explicitly enabled (`enabled: true`) and have a `host` set for the infrastructure beans to be created.

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

#### Publisher properties (`firefly.eda.publishers.rabbitmq.default.*`)

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `false` | Activates the RabbitMQ publisher connection factory. |
| `host` / `port` | `localhost` / `5672` | Broker host and AMQP port. |
| `username` / `password` | `guest` / `guest` | Broker credentials. |
| `virtual-host` | `/` | RabbitMQ virtual host. |
| `default-exchange` | `events` | Exchange used when a publish call supplies no destination. |
| `default-routing-key` | `event` | Default routing key for the connection. |

#### Consumer properties (`firefly.eda.consumer.rabbitmq.default.*`)

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `false` | Activates the RabbitMQ consumer connection factory (required for the consumer bean to wire. see [How It Works](#how-it-works)). |
| `host` / `port` | `localhost` / `5672` | Broker host and AMQP port. |
| `username` / `password` | `guest` / `guest` | Broker credentials. |
| `virtual-host` | `/` | RabbitMQ virtual host. |
| `queues` | `events-queue` | Comma-separated list of pre-declared queues to subscribe to. |
| `concurrent-consumers` | `1` | Initial number of concurrent consumers on the listener container. |
| `max-concurrent-consumers` | `5` | Maximum number of concurrent consumers. |
| `prefetch-count` | `10` | AMQP prefetch (QoS) per consumer. |

> Note: Unlike Kafka topic patterns, RabbitMQ consumers subscribe to specific, pre-declared queues defined by `queues`. The `exchange/routingKey` destinations in `@EventListener` are used for publisher routing and post-consumption filtering, not for queue subscription.

### How It Works

The adapter creates separate publisher-side and consumer-side connection factories so each can be tuned and excluded independently:

- Publisher path. `FireflyEdaRabbitMqAutoConfiguration` builds `fireflyEdaRabbitPublisherConnectionFactory` and `fireflyEdaRabbitTemplate` from `firefly.eda.publishers.rabbitmq.default.*`; `FireflyEdaAmqpAdminAutoConfiguration` reuses that factory for a `RabbitAdmin`. `RabbitMqEventPublisher` is `@ConditionalOnBean(name = "fireflyEdaRabbitTemplate")`.
- Consumer path. `RabbitMqEventConsumer` is guarded by `@ConditionalOnBean(name = "fireflyEdaRabbitConsumerConnectionFactory")` and injects that bean by qualifier. `FireflyEdaRabbitMqConsumerAutoConfiguration` creates this `fireflyEdaRabbitConsumerConnectionFactory` (a `CachingConnectionFactory` from `firefly.eda.consumer.rabbitmq.default.*`), mirroring the publisher-side factory. this is what makes the consumer wire and run when `firefly.eda.consumer.enabled=true` and the RabbitMQ consumer is enabled.
- Native auto-config exclusion. `FireflyEdaRabbitMqEnvironmentPostProcessor` (registered via `spring.factories`) excludes Spring Boot's `RabbitAutoConfiguration` unless you explicitly opt in with `spring.rabbitmq.host`. This avoids the ambiguity where Boot's `rabbitListenerContainerFactory` cannot choose between the two Firefly `ConnectionFactory` beans, and prevents connection/health failures when RabbitMQ is not in use.

## Continue the application

Use the [EDA guide](eda.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eda-rabbitmq/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [spring.factories](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/resources/META-INF/spring.factories).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
