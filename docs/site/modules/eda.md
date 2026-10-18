![Jafly. Common EDA Library](../assets/modules/eda.svg)

# Common EDA Library

Publish and consume events through a common envelope and transport-independent APIs.

Start with EventPublisher and the listener annotations; add a transport adapter for broker or database delivery. Choose publisher and consumer names explicitly when several transports coexist.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-eda` | `jar` | [modules/eda](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eda) |

Part of [Event-driven integration](index.md#event-driven-integration).

## In the onboarding service

Once a submitted application must notify another service, define an application.submitted event and select its transport. The first-service tutorial has no event publisher, so adding delivery is a separate change with its own failure behavior.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

This optional extension publishes an application ID after the onboarding service has accepted a submission. The Java class is an API fragment: register it in a Spring application with EDA enabled, inject it into application code, and subscribe to or compose its returned `Mono`. It does not save the application or make the save and publication atomic.

```java
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ApplicationEventPublisher {
    private final EventPublisherFactory publishers;



    public Mono<Void> submitted(UUID applicationId) {
        return publishers.getPublisher(PublisherType.APPLICATION_EVENT)
                .publish(applicationId, "application-submitted");
    }
}
```

`APPLICATION_EVENT` selects process-local Spring events explicitly. It does not reach another service or survive process failure. A distributed integration selects and configures a broker provider instead; define the event schema, destination, duplicate handling, and recovery before connecting business effects. The publishing API also offers `@PublishResult` for Spring-managed methods and `@EventListener` for consumers. An `EventEnvelope` is a Java record; access its payload with `payload()`.


## Configuration and wiring

The `firefly.eda` namespace is bound by [EdaProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/properties/EdaProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.eda.enabled` | `boolean` | `true` |
| `firefly.eda.default-publisher-type` | `PublisherType` | `PublisherType.AUTO` |
| `firefly.eda.default-connection-id` | `String` | `"default"` |
| `firefly.eda.default-destination` | `String` | `"events"` |
| `firefly.eda.default-serialization-format` | `String` | `"json"` |
| `firefly.eda.default-timeout` | `Duration` | `Duration.ofSeconds(30)` |
| `firefly.eda.metrics-enabled` | `boolean` | `true` |
| `firefly.eda.health-enabled` | `boolean` | `true` |
| `firefly.eda.tracing-enabled` | `boolean` | `true` |
| `firefly.eda.resilience` | `Resilience` | `new Resilience()` |
| `firefly.eda.publishers` | `Publishers` | `new Publishers()` |
| `firefly.eda.consumer` | `Consumer` | `new Consumer()` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyEdaAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/config/FireflyEdaAutoConfiguration.java).
- `prefix = "firefly.eda.consumer.noop", name = "enabled", havingValue = "true"`. [NoopEventConsumer.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/consumer/NoopEventConsumer.java).
- `prefix = "firefly.eda.consumer.application-event", name = "enabled", havingValue = "true"`. [SpringApplicationEventConsumer.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/consumer/SpringApplicationEventConsumer.java).
- `prefix = "firefly.eda.error.metrics", name = "enabled", havingValue = "true", matchIfMissing = true`. [MetricsErrorHandler.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/error/impl/MetricsErrorHandler.java).
- `prefix = "firefly.eda.error.notification", name = "enabled", havingValue = "true"`. [NotificationErrorHandler.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/error/impl/NotificationErrorHandler.java).
- `prefix = "firefly.eda", name = "health-enabled", havingValue = "true", matchIfMissing = true`. [EdaHealthIndicator.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/health/EdaHealthIndicator.java).
- `prefix = "firefly.eda", name = "metrics-enabled", havingValue = "true", matchIfMissing = true`. [EdaMetrics.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/metrics/EdaMetrics.java).
- `prefix = "firefly.eda.publishers.noop", name = "enabled", havingValue = "true"`. [NoopEventPublisher.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/publisher/NoopEventPublisher.java).
- `prefix = "firefly.eda.publishers.application-event", name = "enabled", havingValue = "true", matchIfMissing = true`. [SpringApplicationEventPublisher.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/publisher/SpringApplicationEventPublisher.java).
- `prefix = "firefly.eda.resilience", name = "enabled", havingValue = "true", matchIfMissing = true`. [ResilientEventPublisherFactory.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/resilience/ResilientEventPublisherFactory.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`EventPublisher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/publisher/EventPublisher.java) | Unified interface for publishing events to different messaging systems. |
| [`EventEnvelope`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/event/EventEnvelope.java) | Unified event envelope for both publishing and consuming events. |
| [`EventPublisherFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/publisher/EventPublisherFactory.java) | Factory for creating and managing event publishers. |
| [`EdaProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/properties/EdaProperties.java) | Configuration properties for the Event-Driven Architecture library. |
| [`EventConsumer`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/consumer/EventConsumer.java) | Interface for consuming events from messaging systems. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eda -am verify
./mvnw -pl :fireflyframework-eda -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [EventListenerAnnotationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/test/java/org/fireflyframework/eda/annotation/EventListenerAnnotationTest.java).
- [EventPublisherAnnotationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/test/java/org/fireflyframework/eda/annotation/EventPublisherAnnotationTest.java).
- [PublishResultAnnotationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/test/java/org/fireflyframework/eda/annotation/PublishResultAnnotationTest.java).
- [EventPublisherAspectIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/test/java/org/fireflyframework/eda/aspect/EventPublisherAspectIntegrationTest.java).
- [PublishResultAspectIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/test/java/org/fireflyframework/eda/aspect/PublishResultAspectIntegrationTest.java).
- [EdaAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/test/java/org/fireflyframework/eda/config/EdaAutoConfigurationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

## Test fixture

The `shouldSupportMultipleDestinations` test demonstrates one concrete behavior. Excerpt from [EventListenerAnnotationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/test/java/org/fireflyframework/eda/annotation/EventListenerAnnotationTest.java). The test class supplies its fixtures and imports.

```java
@Test
@DisplayName("Should support multiple destinations")
void shouldSupportMultipleDestinations() throws NoSuchMethodException {
    // Arrange
    Method method = TestListener.class.getMethod("listenerWithMultipleDestinations", Object.class);

    // Act
    EventListener annotation = method.getAnnotation(EventListener.class);

    // Assert
    assertThat(annotation).isNotNull();
    assertThat(annotation.destinations()).hasSize(3);
    assertThat(annotation.destinations()).containsExactly(
            "topic1",
            "topic2",
            "topic3"
    );
}
```

## Detailed reference

### Configuration

All properties live under the `firefly.eda.*` namespace (see `EdaProperties`). The EDA core is enabled by default; publishers and consumers are disabled by default and opt in per transport. A representative configuration:

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

#### Key properties

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

## Continue the application

Use the [EDA - Kafka guide](eda-kafka.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/eda/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
