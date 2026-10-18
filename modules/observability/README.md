![Jafly. Observability](../../docs/site/assets/modules/observability.svg)

# Observability

Provide a common metrics, tracing, health, and context-propagation foundation for framework components.

Extend the metrics/health support classes where application instrumentation is needed and select exporters through configuration. A library on the classpath is only one part of observability: the host must expose endpoints and operate the chosen telemetry backend.

[Module guide](../../docs/site/modules/observability.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Measure application submissions and review latency with the metrics support, and trace calls to configured providers. Telemetry export and backend access must be configured in the service deployment.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-observability</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

### Metrics

```java
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.fireflyframework.observability.metrics.MetricTags;
import io.micrometer.core.instrument.MeterRegistry;

public class OrderMetrics extends FireflyMetricsSupport {

    public OrderMetrics(MeterRegistry registry) {
        super(registry, "orders"); // all metrics prefixed with firefly.orders.*
    }

    public void orderPlaced(String orderType) {
        counter("placed", MetricTags.OPERATION, orderType).increment();
    }

    public <T> Mono<T> timedProcessing(Mono<T> operation) {
        return timed("processing.duration", operation);
    }

    public void orderFailed(Throwable error) {
        recordFailure("placed", error);
    }
}
```

### Health Indicators

```java
import org.fireflyframework.observability.health.FireflyHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class OrderHealthIndicator extends FireflyHealthIndicator {

    public OrderHealthIndicator() {
        super("orders");
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        builder.up();
        addErrorRate(builder, getErrorRate(), 0.05);
        addLatency(builder, getP99Latency(), Duration.ofMillis(500));
    }
}
```

### Reactive Tracing

```java
import org.fireflyframework.observability.tracing.FireflyTracingSupport;

@Service
public class OrderService {
    private final FireflyTracingSupport tracingSupport;

    public Mono<Order> processOrder(Order order) {
        return tracingSupport.trace("order.process",
            doProcessOrder(order),
            KeyValues.of("order.type", order.getType()),
            KeyValues.empty()
        );
    }
}
```

## Configuration

The `firefly.observability` namespace is bound by [FireflyObservabilityProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.observability.metrics` | `Metrics` | `new Metrics()` |
| `firefly.observability.tracing` | `Tracing` | `new Tracing()` |
| `firefly.observability.health` | `Health` | `new Health()` |
| `firefly.observability.logging` | `Logging` | `new Logging()` |
| `firefly.observability.context-propagation` | `ContextPropagation` | `new ContextPropagation()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`FireflyMetricsSupport`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/metrics/FireflyMetricsSupport.java) | Abstract base class for all Firefly module metrics services. |
| [`FireflyHealthIndicator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/health/FireflyHealthIndicator.java) | Abstract base class for all Firefly health indicators. |
| [`FireflyObservabilityProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityProperties.java) | Unified configuration properties for Firefly observability. |
| [`FireflyObservabilityAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityAutoConfiguration.java) | Master auto-configuration for Firefly Framework observability. |
| [`FireflyObservabilityEnvironmentPostProcessor`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityEnvironmentPostProcessor.java) | Configures observability backends at startup based on Firefly properties. |

## Continue the application

Use the [Starter Core guide](../../docs/site/modules/starter-core.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-observability -am verify
./mvnw -pl :fireflyframework-observability -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/observability.md).
