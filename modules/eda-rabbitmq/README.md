![Jafly. EDA - RabbitMQ](../../docs/site/assets/modules/eda-rabbitmq.svg)

# EDA - RabbitMQ

Connect the EDA abstractions to RabbitMQ exchanges, queues, and routing keys.

Configure the named RabbitMQ publisher and consumer entries under firefly.eda and provision the topology they address. Match routing keys and queue bindings on both sides of a message flow.

[Module guide](../../docs/site/modules/eda-rabbitmq.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

RabbitMQ can route application lifecycle events to the configured review queues. Match exchanges, routing keys, and queue bindings before connecting the onboarding handler to publishing.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda-rabbitmq</artifactId>
    <version>26.10.21-SNAPSHOT</version>
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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`RabbitMqEventPublisher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/publisher/rabbitmq/RabbitMqEventPublisher.java) | Defines `RabbitMqEventPublisher`, `publish` operations. |
| [`RabbitMqEventConsumer`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/consumer/rabbitmq/RabbitMqEventConsumer.java) | Defines `RabbitMqEventConsumer`, `initialize`, `shutdown`, `stop` operations. |
| [`FireflyEdaAmqpAdminAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaAmqpAdminAutoConfiguration.java) | Defines `FireflyEdaAmqpAdminAutoConfiguration`, `fireflyEdaAmqpAdmin` operations. |
| [`FireflyEdaRabbitMqAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaRabbitMqAutoConfiguration.java) | Defines `FireflyEdaRabbitMqAutoConfiguration`, `fireflyEdaRabbitPublisherConnectionFactory`, `fireflyEdaRabbitTemplate` operations. |
| [`FireflyEdaRabbitMqConsumerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-rabbitmq/src/main/java/org/fireflyframework/eda/config/FireflyEdaRabbitMqConsumerAutoConfiguration.java) | Defines `FireflyEdaRabbitMqConsumerAutoConfiguration`, `fireflyEdaRabbitConsumerConnectionFactory` operations. |

## Continue the application

Use the [EDA guide](../../docs/site/modules/eda.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eda-rabbitmq -am verify
./mvnw -pl :fireflyframework-eda-rabbitmq -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/eda-rabbitmq.md).
