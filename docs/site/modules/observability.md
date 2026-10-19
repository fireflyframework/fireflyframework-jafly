![Jafly. Observability](../assets/modules/observability.svg)

# Observability

Provide a common metrics, tracing, health, and context-propagation foundation for framework components.

Extend the metrics/health support classes where application instrumentation is needed and select exporters through configuration. A library on the classpath is only one part of observability: the host must expose endpoints and operate the chosen telemetry backend.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-observability` | `jar` | [modules/observability](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/observability) |

Part of [Service operations](index.md#service-operations).

## In the onboarding service

Measure application submissions and review latency with the metrics support, and trace calls to configured providers. Telemetry export and backend access must be configured in the service deployment.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-observability</artifactId>
    <version>26.10.20-SNAPSHOT</version>
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

## Configuration and wiring

The `firefly.observability` namespace is bound by [FireflyObservabilityProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.observability.metrics` | `Metrics` | `new Metrics()` |
| `firefly.observability.tracing` | `Tracing` | `new Tracing()` |
| `firefly.observability.health` | `Health` | `new Health()` |
| `firefly.observability.logging` | `Logging` | `new Logging()` |
| `firefly.observability.context-propagation` | `ContextPropagation` | `new ContextPropagation()` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyObservabilityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityAutoConfiguration.java).
- `prefix = "firefly.observability.tracing", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyObservabilityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityAutoConfiguration.java).
- `prefix = "firefly.observability.health", name = "enabled", havingValue = "true", matchIfMissing = true`. [FireflyObservabilityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityAutoConfiguration.java).
- `prefix = "firefly.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true`. [ExemplarsAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/actuator/ExemplarsAutoConfiguration.java).
- `prefix = "firefly.observability.health", name = "kubernetes-probes", havingValue = "true", matchIfMissing = true`. [KubernetesProbesAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/health/KubernetesProbesAutoConfiguration.java).
- `prefix = "firefly.observability.logging", name = "enabled", havingValue = "true", matchIfMissing = true`. [StructuredLoggingAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/logging/StructuredLoggingAutoConfiguration.java).
- `prefix = "firefly.observability.context-propagation", name = "reactor-hooks-enabled", havingValue = "true", matchIfMissing = true`. [ReactiveContextPropagationAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/tracing/ReactiveContextPropagationAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`FireflyMetricsSupport`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/metrics/FireflyMetricsSupport.java) | Abstract base class for all Firefly module metrics services. |
| [`FireflyHealthIndicator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/health/FireflyHealthIndicator.java) | Abstract base class for all Firefly health indicators. |
| [`FireflyObservabilityProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityProperties.java) | Unified configuration properties for Firefly observability. |
| [`FireflyObservabilityAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityAutoConfiguration.java) | Master auto-configuration for Firefly Framework observability. |
| [`FireflyObservabilityEnvironmentPostProcessor`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/java/org/fireflyframework/observability/FireflyObservabilityEnvironmentPostProcessor.java) | Configures observability backends at startup based on Firefly properties. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-observability -am verify
./mvnw -pl :fireflyframework-observability -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [FireflyObservabilityAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/autoconfigure/FireflyObservabilityAutoConfigurationTest.java).
- [FireflyObservabilityEnvironmentPostProcessorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/autoconfigure/FireflyObservabilityEnvironmentPostProcessorTest.java).
- [TracingBridgeStartupTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/autoconfigure/TracingBridgeStartupTest.java).
- [FireflyHealthIndicatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/health/FireflyHealthIndicatorTest.java).
- [FireflyMetricsSupportTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/metrics/FireflyMetricsSupportTest.java).
- [MetricNamingTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/metrics/MetricNamingTest.java).

## Test fixture

The `defaultsLoadingIsIdempotent` test demonstrates one concrete behavior. Excerpt from [FireflyObservabilityEnvironmentPostProcessorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/autoconfigure/FireflyObservabilityEnvironmentPostProcessorTest.java). The test class supplies its fixtures and imports.

```java
@Test
void defaultsLoadingIsIdempotent() {
    MockEnvironment environment = new MockEnvironment();
    MutablePropertySources sources = environment.getPropertySources();
    int sizeBefore = sources.size();

    processor.postProcessEnvironment(environment, new SpringApplication());
    int sizeAfterFirst = sources.size();

    processor.postProcessEnvironment(environment, new SpringApplication());
    int sizeAfterSecond = sources.size();

    // First run adds defaults (and possibly the post-processor source). Second run should
    // not duplicate the defaults source.
    assertThat(sizeAfterSecond).isEqualTo(sizeAfterFirst);
    assertThat(sizeAfterFirst).isGreaterThan(sizeBefore);
}
```

## Detailed reference

### Configuration

All observability settings live under the `firefly.observability` prefix:

```yaml
firefly:
  observability:
    metrics:
      enabled: true                        # Master switch for metrics
      prefix: firefly                      # Default metric prefix
      exporter: PROMETHEUS                 # PROMETHEUS (default), OTLP, or BOTH
    tracing:
      enabled: true                        # Master switch for tracing
      bridge: OTEL                         # OTEL (default) or BRAVE — no POM changes needed
      sampling-probability: 1.0            # Override in production (e.g. 0.1 for 10%)
      propagation-type: W3C               # W3C (default) or B3
      baggage-fields:                      # Custom baggage propagated across services
        - X-Transaction-Id
    health:
      enabled: true                        # Master switch for health indicators
      kubernetes-probes: true              # Configure liveness/readiness/startup groups
    logging:
      enabled: true                        # Master switch for structured logging
      structured-format: logstash          # logstash, ecs, or gelf
    context-propagation:
      reactor-hooks-enabled: true          # Enable Hooks.enableAutomaticContextPropagation()
```

### Metrics Exporter Selection

Both Prometheus and OTLP metrics registries ship on the classpath. An `EnvironmentPostProcessor` reads the `exporter` property at startup and enables/disables the appropriate metrics backends. No POM changes are needed to switch exporters.

| Exporter | Endpoint | Protocol | When to Use |
|----------|----------|----------|-------------|
| PROMETHEUS (default) | `/actuator/prometheus` | Pull (scrape) | Standard Prometheus/Grafana/Thanos/Mimir setups |
| OTLP | otel-collector-contrib | Push (OTLP) | When all telemetry (traces + metrics) flows through OTel Collector |
| BOTH | Both | Pull + Push | During migration, or when multiple backends consume metrics |

#### Prometheus (Default)

```yaml
firefly:
  observability:
    metrics:
      exporter: PROMETHEUS
```

- Exposes `/actuator/prometheus` scrape endpoint
- Standard for Prometheus, Grafana, Thanos, Mimir
- Zero additional infrastructure. Prometheus scrapes the endpoint directly

#### OTLP Push

```yaml
firefly:
  observability:
    metrics:
      exporter: OTLP
```

- Pushes metrics to otel-collector-contrib via OTLP protocol
- No scrape endpoint exposed
- Configure the OTLP metrics endpoint via env var:

```bash
OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=http://otel-collector.monitoring:4318/v1/metrics
```

#### Both

```yaml
firefly:
  observability:
    metrics:
      exporter: BOTH
```

- Enables both Prometheus scrape and OTLP push simultaneously
- Useful during migration from Prometheus to OTLP, or when multiple backends consume metrics

### Tracing Bridge Selection

Both OpenTelemetry and Brave tracing bridges ship on the classpath. An `EnvironmentPostProcessor` reads the `bridge` property at startup and excludes the unwanted bridge's Spring Boot auto-configuration. No POM changes are needed to switch bridges.

#### OpenTelemetry (Default)

```yaml
firefly:
  observability:
    tracing:
      bridge: OTEL
```

- Uses `micrometer-tracing-bridge-otel` with OTLP export
- W3C TraceContext propagation (with B3 fallback via composite propagator)
- Designed for the open-source [OpenTelemetry Collector (contrib)](https://github.com/open-telemetry/opentelemetry-collector-contrib) (`otel/opentelemetry-collector-contrib`)
- Compatible with Jaeger, Tempo, Grafana Alloy, Datadog, and any OTLP-compatible backend

#### OTLP Transport (gRPC vs HTTP)

Both gRPC and HTTP/protobuf senders are on the classpath. Default: gRPC (port 4317). Switch via env var:

| Transport | Port | Env Var | When to Use |
|-----------|------|---------|-------------|
| gRPC (default) | 4317 | `OTEL_EXPORTER_OTLP_PROTOCOL=grpc` | Production. persistent connections, binary framing, multiplexing |
| HTTP/protobuf | 4318 | `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf` | Behind HTTP proxies/ALBs, or when gRPC is not available |

```bash
# Default — gRPC to otel-collector-contrib
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.monitoring:4317

# Switch to HTTP/protobuf (e.g., behind an ALB)
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.monitoring:4318

# Per-signal overrides
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://tempo:4317
OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=http://mimir:4318/v1/metrics
```

#### Brave (Zipkin Ecosystems)

```yaml
firefly:
  observability:
    tracing:
      bridge: BRAVE
```

- Uses `micrometer-tracing-bridge-brave` with Zipkin reporter
- B3 propagation (with W3C fallback via composite propagator)
- Compatible with Zipkin-based tracing infrastructure
- Use when migrating from legacy Zipkin/Brave-based systems

### Structured Logging

Include the shared logback configuration in your module's `logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="logback-firefly.xml"/>

    <root level="INFO">
        <appender-ref ref="FIREFLY_ASYNC"/>
    </root>

    <logger name="org.fireflyframework.yourmodule" level="DEBUG"/>
</configuration>
```

This provides:
- JSON structured logging via Logstash encoder (production)
- Pretty-printing with colors and trace context (dev profiles)
- Async appender for production performance
- Automatic MDC fields: `traceId`, `spanId`, `X-Transaction-Id`, `userId`, `correlationId`, `requestId`

### Architecture

```
fireflyframework-observability/
├── FireflyObservabilityAutoConfiguration          # Master auto-configuration
├── FireflyObservabilityProperties                 # Unified @ConfigurationProperties
├── FireflyObservabilityEnvironmentPostProcessor   # Config-based bridge + exporter selection
├── metrics/
│   ├── FireflyMetricsSupport                      # Abstract base for ALL module metrics
│   ├── MetricNaming                               # Enforces firefly.{module}.{metric}
│   ├── MetricTags                                 # Standard tag constants
│   └── FireflyMeterRegistryCustomizer             # Common tags: app, env, instance
├── tracing/
│   ├── FireflyTracingSupport                      # Reactive-safe tracing (Mono/Flux)
│   ├── FireflyBaggageConfiguration                # Standard baggage fields
│   ├── ReactiveContextPropagationAutoConfiguration # Hooks.enableAutomaticContextPropagation()
│   └── TracingWebClientCustomizer                 # WebClient trace context propagation
├── health/
│   ├── FireflyHealthIndicator                     # Abstract base for health indicators
│   ├── HealthMetricsBridge                        # Health status → Prometheus gauge
│   └── KubernetesProbesAutoConfiguration          # Liveness/readiness/startup probes
├── logging/
│   ├── MdcConstants                               # Standard MDC field names
│   └── StructuredLoggingAutoConfiguration         # Spring Boot structured logging setup
└── actuator/
    ├── FireflyActuatorAutoConfiguration           # Default endpoint exposure
    └── ExemplarsAutoConfiguration                 # Prometheus exemplar linking
```

## Continue the application

Use the [Starter Core guide](starter-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/observability/src/main/java).
- [additional-spring-configuration-metadata.json](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/resources/META-INF/additional-spring-configuration-metadata.json).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [spring.factories](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/resources/META-INF/spring.factories).
- [application-firefly-observability.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/resources/application-firefly-observability.yml).
- [logback-firefly.xml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/main/resources/logback-firefly.xml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
