![Jafly. EDA - Kafka](../assets/modules/eda-kafka.svg)

# EDA - Kafka

Connect the EDA publishing and consuming abstractions to Apache Kafka.

Add this adapter with eda, configure named Kafka publishers/consumers, and provision the broker and topics for your environment. Exercise the broker-backed tests when changing serialization, routing, or acknowledgment behavior.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-eda-kafka` | `jar` | [modules/eda-kafka](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eda-kafka) |

Part of [Event-driven integration](index.md#event-driven-integration).

## In the onboarding service

Publish application lifecycle events to Kafka when a separate review worker consumes them. Provision the topic, configure both sides, and decide how the worker handles duplicates and failed processing.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda-kafka</artifactId>
    <version>26.10.20-SNAPSHOT</version>
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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

Property-based wiring conditions found in this module:

- `prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyEdaKafkaConsumerAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/config/FireflyEdaKafkaConsumerAutoConfiguration.java).
- `prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyEdaKafkaPublisherAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/config/FireflyEdaKafkaPublisherAutoConfiguration.java).
- `prefix = "firefly.eda.consumer", name = "enabled", havingValue = "true"`. [KafkaEventConsumer.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/consumer/kafka/KafkaEventConsumer.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`KafkaEventPublisher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/publisher/kafka/KafkaEventPublisher.java) | Kafka event publisher implementation using Spring Kafka. |
| [`KafkaEventConsumer`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/consumer/kafka/KafkaEventConsumer.java) | Kafka event consumer implementation with dynamic topic subscription. |
| [`FireflyEdaKafkaConsumerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/config/FireflyEdaKafkaConsumerAutoConfiguration.java) | Auto-configuration for Kafka Consumer infrastructure. |
| [`FireflyEdaKafkaEnvironmentPostProcessor`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/config/FireflyEdaKafkaEnvironmentPostProcessor.java) | Environment post-processor that conditionally excludes Spring Boot's Kafka auto-configuration when the Firefly EDA Kafka transport is not enabled. |
| [`FireflyEdaKafkaPublisherAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/java/org/fireflyframework/eda/config/FireflyEdaKafkaPublisherAutoConfiguration.java) | Auto-configuration for Kafka Publisher infrastructure. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eda-kafka -am verify
./mvnw -pl :fireflyframework-eda-kafka -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [KafkaIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/test/java/org/fireflyframework/eda/integration/KafkaIntegrationTest.java).
- [KafkaConsumerIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/test/java/org/fireflyframework/eda/integration/consumer/KafkaConsumerIntegrationTest.java).
- [BaseIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/test/java/org/fireflyframework/eda/testconfig/BaseIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `shouldGetKafkaPublisherFromFactory` test demonstrates one concrete behavior. Excerpt from [KafkaIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/test/java/org/fireflyframework/eda/integration/KafkaIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
@DisplayName("Should get Kafka publisher from factory")
void shouldGetKafkaPublisherFromFactory() {
    // Skip if publisher factory is not available
    if (publisherFactory == null) {
        System.out.println("Skipping test - Publisher factory not available");
        return;
    }

    // Act
    EventPublisher publisher = publisherFactory.getPublisher(PublisherType.KAFKA, "default");

    // Assert - May be null if Kafka is not configured
    // Publisher may be wrapped in ResilientEventPublisher, so check publisher type instead of class
    if (publisher != null) {
        assertThat(publisher.getPublisherType()).isEqualTo(PublisherType.KAFKA);
        assertThat(publisher.isAvailable()).isTrue();
    }
}
```

## Detailed reference

### Configuration

All settings live under `firefly.eda.*`. The Kafka publisher and consumer are opt-in (`enabled: false` by default at every level), so nothing connects to a broker until you turn it on. Values below are the configured defaults from `EdaProperties`.

```yaml
firefly:
  eda:
    enabled: true                       # master switch for EDA (default: true)
    publishers:
      enabled: false                    # enable the publisher side (default: false)
      kafka:
        default:                        # connection id; add more named connections as needed
          enabled: false                # enable the Kafka publisher (default: false)
          bootstrap-servers:            # e.g. localhost:9092 (required to create beans)
          default-topic: events         # topic used when none is specified (default: events)
          key-serializer: org.apache.kafka.common.serialization.StringSerializer
          value-serializer: org.apache.kafka.common.serialization.StringSerializer
          properties: {}                # extra raw Kafka producer properties
    consumer:
      enabled: false                    # enable the consumer side (default: false)
      group-id: firefly-eda             # Kafka consumer group id (default: firefly-eda)
      kafka:
        default:
          enabled: false                # enable the Kafka consumer (default: false)
          bootstrap-servers:            # e.g. localhost:9092 (required to create beans)
          topics: events                # fallback topic pattern when no @EventListener topics are found
          auto-offset-reset: earliest   # default: earliest
          key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
          value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
          properties: {}                # extra raw Kafka consumer properties
```

Key properties:

| Property | Default | Purpose |
| --- | --- | --- |
| `firefly.eda.publishers.enabled` | `false` | Global publisher switch; must be `true` to create Kafka producer beans. |
| `firefly.eda.publishers.kafka.default.enabled` | `false` | Enables the Kafka publisher for the `default` connection. |
| `firefly.eda.publishers.kafka.default.bootstrap-servers` | _(none)_ | Broker list; beans are only created when this is non-empty. |
| `firefly.eda.publishers.kafka.default.default-topic` | `events` | Destination used when a publish call supplies no topic. |
| `firefly.eda.publishers.kafka.default.key-serializer` / `value-serializer` | `StringSerializer` | Producer serializers. |
| `firefly.eda.publishers.kafka.default.properties` | `{}` | Pass-through map of raw Kafka producer config. |
| `firefly.eda.consumer.enabled` | `false` | Global consumer switch; required for the consumer beans and `KafkaEventConsumer`. |
| `firefly.eda.consumer.group-id` | `firefly-eda` | Consumer group id applied to the listener. |
| `firefly.eda.consumer.kafka.default.enabled` | `false` | Enables the Kafka consumer for the `default` connection. |
| `firefly.eda.consumer.kafka.default.bootstrap-servers` | _(none)_ | Broker list; beans are only created when this is non-empty. |
| `firefly.eda.consumer.kafka.default.topics` | `events` | Fallback topic pattern when no `@EventListener` Kafka topics are discovered. |
| `firefly.eda.consumer.kafka.default.auto-offset-reset` | `earliest` | Offset reset policy. |
| `firefly.eda.consumer.kafka.default.key-deserializer` / `value-deserializer` | `StringDeserializer` | Consumer deserializers. |
| `firefly.eda.consumer.kafka.default.properties` | `{}` | Pass-through map of raw Kafka consumer config. |

> Note: `spring.kafka.*` properties are intentionally not consulted by this adapter. If `spring.kafka.bootstrap-servers` is set explicitly, the adapter steps aside and leaves Spring Boot's own `KafkaAutoConfiguration` in place. Multiple named connections are supported by adding keys alongside `default` (e.g. `firefly.eda.publishers.kafka.audit.*`).

### How It Works

1. Startup gating. `FireflyEdaKafkaEnvironmentPostProcessor` runs first and excludes Spring Boot's `KafkaAutoConfiguration` unless the Kafka publisher or consumer is enabled (or `spring.kafka.bootstrap-servers` is explicitly set).
2. Publisher wiring. `FireflyEdaKafkaPublisherAutoConfiguration` creates `fireflyEdaKafkaProducerFactory` and `fireflyEdaKafkaTemplate` only when publishers are enabled, the Kafka publisher is enabled, and `bootstrap-servers` is non-empty. `KafkaEventPublisher` consumes the template via an `ObjectProvider`.
3. Consumer wiring. `FireflyEdaKafkaConsumerAutoConfiguration` creates `fireflyEdaKafkaConsumerFactory` and the `@Primary` `fireflyEdaKafkaListenerContainerFactory` under the equivalent conditions. `KafkaEventConsumer` registers a single `@KafkaListener` whose `topicPattern` is computed from discovered `@EventListener` annotations.
4. Dynamic topics. When listeners are added at runtime, `EventListenerProcessor` notifies the consumer, which restarts its listener containers to pick up the new topic pattern (a brief consumption pause). If no annotations are found, it falls back to `firefly.eda.consumer.kafka.default.topics`.
5. Envelope flow. Inbound records are wrapped in the unified `EventEnvelope`, emitted to a reactive `Sinks.Many` stream, and dispatched through `EventListenerProcessor` to your handlers.

## Continue the application

Use the [EDA guide](eda.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eda-kafka/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [spring.factories](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda-kafka/src/main/resources/META-INF/spring.factories).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
