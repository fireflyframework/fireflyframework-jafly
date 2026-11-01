![Jafly. EDA - Kafka](../../docs/site/assets/modules/eda-kafka.svg)

# EDA - Kafka

Connect the EDA publishing and consuming abstractions to Apache Kafka.

Add this adapter with eda, configure named Kafka publishers/consumers, and provision the broker and topics for your environment. Exercise the broker-backed tests when changing serialization, routing, or acknowledgment behavior.

[Module guide](../../docs/site/modules/eda-kafka.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Publish application lifecycle events to Kafka when a separate review worker consumes them. Provision the topic, configure both sides, and decide how the worker handles duplicates and failed processing.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda-kafka</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see above). The Kafka transport beans light up only when you enable them.

2. Enable and configure the Kafka transport in `application.yml`:

```yaml
firefly:
  eda:
    publishers:
      enabled: true
      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092
          default-topic: events
    consumer:
      enabled: true
      group-id: my-service
      kafka:
        default:
          enabled: true
          bootstrap-servers: localhost:9092
          topics: events
          auto-offset-reset: earliest
```

3. Publish events with the framework's transport-agnostic annotation. The result of the method is published to Kafka:

```java
@Service
public class PaymentService {

    @PublishResult(publisherType = PublisherType.KAFKA,
                   destination = "payments",
                   eventType = "payment.completed")
    public Mono<PaymentCompleted> complete(PaymentCommand cmd) {
        return process(cmd); // returned value is published to the "payments" topic
    }
}
```

4. Consume events by annotating a handler. The Kafka consumer discovers the topic automatically and subscribes:

```java
@Component
public class PaymentListener {

    @EventListener(destinations = "payments",
                   eventTypes = "payment.completed",
                   consumerType = PublisherType.KAFKA)
    public Mono<Void> onPaymentCompleted(PaymentCompleted event) {
        log.info("Received payment: {}", event.id());
        return Mono.empty();
    }
}
```

No `@KafkaListener` of your own and no `spring.kafka.*` is required. The adapter owns the `KafkaTemplate`, the listener container factory, and the dynamic topic pattern.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`KafkaEventPublisherImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/publisher/kafka/KafkaEventPublisherImpl.java) | Kafka event publisher implementation using Spring Kafka. |
| [`KafkaEventConsumerImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/consumer/kafka/KafkaEventConsumerImpl.java) | Kafka event consumer implementation with dynamic topic subscription. |
| [`FireflyEdaKafkaConsumerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/config/FireflyEdaKafkaConsumerAutoConfiguration.java) | Auto-configuration for Kafka Consumer infrastructure. |
| [`FireflyEdaKafkaEnvironmentPostProcessorImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/config/FireflyEdaKafkaEnvironmentPostProcessorImpl.java) | Environment post-processor that conditionally excludes Spring Boot's Kafka auto-configuration when the Firefly EDA Kafka transport is not enabled. |
| [`FireflyEdaKafkaPublisherAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/config/FireflyEdaKafkaPublisherAutoConfiguration.java) | Auto-configuration for Kafka Publisher infrastructure. |

## Continue the application

Use the [EDA guide](../../docs/site/modules/eda.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eda-kafka -am verify
./mvnw -pl :fireflyframework-eda-kafka -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/eda-kafka.md).
