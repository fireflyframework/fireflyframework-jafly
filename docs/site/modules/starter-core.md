![Jafly. Starter Core](../assets/modules/starter-core.svg)

# Starter Core

Assemble core infrastructure conventions for WebClient, actuator, logging, configuration, and service discovery.

Use this starter at the infrastructure foundation of a service. Enable optional integrations with their own settings rather than assuming discovery or remote configuration is required. The property classes below separate client, propagation, registry, logging, and actuator concerns.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-starter-core` | `jar` | [modules/starter-core](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/starter-core) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

Broker-backed messaging comes from the separate EDA transport adapters. This starter supplies no KCL consumer or AWS Glue schema-registry integration. Applications that add those SDKs must select and qualify their own SDK and protobuf combination.

## In the onboarding service

A larger onboarding service may adopt this bundle for shared HTTP-client and operational settings. Compare those dependencies with the tutorial's small CQRS/WebFlux set before adding the starter.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-core</artifactId>
    <version>26.10.18</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Add the dependency and the auto-configurations do the rest. The listed auto-configurations register its beans. A resilient, transaction-aware `WebClient` (and `WebClientTemplate`) are available for injection:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.core.web.client.WebClientTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CustomerGateway {

    private final WebClientTemplate webClient;



    // Inbound headers (minus the configured skip-list) are forwarded downstream,
    // and X-Transaction-Id is propagated automatically.
    public Mono<Customer> fetch(String baseUrl, String id, ServerWebExchange exchange) {
        return webClient.get(baseUrl, "/customers/" + id, Customer.class, exchange);
    }
}
```

Prefer the raw `WebClient`? The auto-configured bean is already resilience-wrapped (circuit breaker, retry, timeout, bulkhead, metrics):

```java
import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class InventoryClient {

    private final WebClient webClient; // resilient + transaction-aware by default



    public Mono<Stock> stock(String sku) {
        return webClient.get()
                .uri("https://inventory.internal/stock/{sku}", sku)
                .retrieve()
                .bodyToMono(Stock.class);
    }
}
```

Enable optional infrastructure features through configuration (all opt-in by default):

```yaml
firefly:
  cloud:
    config:
      enabled: true
      uri: http://config-server:8888
  service:
    registry:
      enabled: true
      type: eureka
```

## Configuration and wiring

The `firefly.actuator` namespace is bound by [ActuatorProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/ActuatorProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.actuator.endpoints` | `Endpoints` | `new Endpoints()` |
| `firefly.actuator.metrics` | `Metrics` | `new Metrics()` |
| `firefly.actuator.tracing` | `Tracing` | `new Tracing()` |
| `firefly.actuator.health` | `Health` | `new Health()` |
| `firefly.actuator.extended-metrics` | `ExtendedMetrics` | `new ExtendedMetrics()` |

The `spring.banner` namespace is bound by [FireflyBannerAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/FireflyBannerAutoConfiguration.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |

The `firefly.webclient.propagation` namespace is bound by [HeaderPropagationProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/HeaderPropagationProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.webclient.propagation.enabled` | `boolean` | `true` |
| `firefly.webclient.propagation.allowed-headers` | `List<String>` | `nested/default initializer; see source` |

The `firefly.webclient` namespace is bound by [WebClientProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/WebClientProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.webclient.enabled` | `boolean` | `true` |
| `firefly.webclient.skip-headers` | `List<String>` | `not initialized` |
| `firefly.webclient.connect-timeout-ms` | `int` | `5000` |
| `firefly.webclient.read-timeout-ms` | `int` | `10000` |
| `firefly.webclient.write-timeout-ms` | `int` | `10000` |
| `firefly.webclient.max-in-memory-size` | `int` | `16777216` |
| `firefly.webclient.ssl` | `Ssl` | `new Ssl()` |
| `firefly.webclient.proxy` | `Proxy` | `new Proxy()` |
| `firefly.webclient.connection-pool` | `ConnectionPool` | `new ConnectionPool()` |
| `firefly.webclient.http2` | `Http2` | `new Http2()` |
| `firefly.webclient.codec` | `Codec` | `new Codec()` |

The `firefly.cloud.config` namespace is bound by [CloudConfigProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/cloud/CloudConfigProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.cloud.config.enabled` | `boolean` | `false` |
| `firefly.cloud.config.uri` | `String` | `"http://localhost:8888"` |
| `firefly.cloud.config.name` | `String` | `""` |
| `firefly.cloud.config.profile` | `String` | `""` |
| `firefly.cloud.config.label` | `String` | `"main"` |
| `firefly.cloud.config.fail-fast` | `boolean` | `false` |
| `firefly.cloud.config.timeout-ms` | `int` | `5000` |
| `firefly.cloud.config.retry` | `boolean` | `true` |
| `firefly.cloud.config.max-retries` | `int` | `6` |
| `firefly.cloud.config.initial-retry-interval-ms` | `int` | `1000` |
| `firefly.cloud.config.max-retry-interval-ms` | `int` | `2000` |
| `firefly.cloud.config.retry-multiplier` | `double` | `1.1` |
| `firefly.cloud.config.refresh-enabled` | `boolean` | `true` |

The `firefly.service.registry` namespace is bound by [ServiceRegistryProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/registry/ServiceRegistryProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.service.registry.enabled` | `boolean` | `false` |
| `firefly.service.registry.type` | `RegistryType` | `RegistryType.EUREKA` |
| `firefly.service.registry.eureka` | `EurekaConfig` | `new EurekaConfig()` |
| `firefly.service.registry.consul` | `ConsulConfig` | `new ConsulConfig()` |

The `firefly.logging` namespace is bound by [LoggingProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/logging/config/LoggingProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.logging.json` | `Json` | `new Json()` |
| `firefly.logging.performance` | `Performance` | `new Performance()` |
| `firefly.logging.cache` | `Cache` | `new Cache()` |

The `firefly.webclient.resilience` namespace is bound by [WebClientResilienceProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/web/resilience/WebClientResilienceProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.webclient.resilience.circuit-breaker` | `CircuitBreakerProperties` | `new CircuitBreakerProperties()` |
| `firefly.webclient.resilience.retry` | `RetryProperties` | `new RetryProperties()` |
| `firefly.webclient.resilience.timeout` | `TimeoutProperties` | `new TimeoutProperties()` |
| `firefly.webclient.resilience.bulkhead` | `BulkheadProperties` | `new BulkheadProperties()` |
| `firefly.webclient.resilience.enabled` | `boolean` | `true` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.actuator.metrics.application", name = "enabled", havingValue = "true", matchIfMissing = true`. [ApplicationMetricsConfig.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/ApplicationMetricsConfig.java).
- `prefix = "firefly.actuator.health.cache", name = "enabled", havingValue = "true", matchIfMissing = true`. [CacheHealthConfig.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/CacheHealthConfig.java).
- `prefix = "firefly.actuator.health.database", name = "enabled", havingValue = "true", matchIfMissing = true`. [DatabaseHealthConfig.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/DatabaseHealthConfig.java).
- `prefix = "firefly.actuator.metrics.http.client", name = "enabled", havingValue = "true", matchIfMissing = true`. [HttpClientMetricsConfig.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/HttpClientMetricsConfig.java).
- `prefix = "firefly.actuator.metrics.jvm", name = "enabled", havingValue = "true", matchIfMissing = true`. [JvmMetricsConfig.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/JvmMetricsConfig.java).
- `prefix = "firefly.actuator.metrics.threadpool", name = "enabled", havingValue = "true", matchIfMissing = true`. [ThreadPoolMetricsConfig.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/ThreadPoolMetricsConfig.java).
- `prefix = "management.tracing", name = "enabled", havingValue = "true", matchIfMissing = true`. [TracingAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/TracingAutoConfiguration.java).
- `name = "firefly.webclient.enabled", havingValue = "true", matchIfMissing = true`. [WebClientAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/WebClientAutoConfiguration.java).
- `name = "firefly.webclient.enabled", havingValue = "true", matchIfMissing = true`. [WebClientBuilderCustomizer.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/WebClientBuilderCustomizer.java).
- `prefix = "firefly.cloud.config", name = "enabled", havingValue = "true"`. [CloudConfigAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/cloud/CloudConfigAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`WebClientTemplate`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/web/client/WebClientTemplate.java) | Defines `addHeaders`, `post`, `put`, `delete` operations. |
| [`ResilientWebClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/web/resilience/ResilientWebClient.java) | A decorator for `WebClient` that adds resilience patterns like circuit breaker, retry, timeout, bulkhead, and metrics. |
| [`HeaderPropagationProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/HeaderPropagationProperties.java) | Configuration for inbound-to-outbound HTTP header propagation. |
| [`CloudConfigProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/cloud/CloudConfigProperties.java) | Configuration properties for Spring Cloud Config. |
| [`ActuatorProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/ActuatorProperties.java) | Configuration properties for Spring Boot Actuator. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |
| [Common CQRS Library](cqrs.md) | Separate commands and queries with reactive dispatch, execution context, validation, and authorization hooks. |
| [: Orchestration](orchestration.md) | Coordinate workflows, compensating sagas, and try-confirm-cancel operations in a reactive application. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-starter-core -am verify
./mvnw -pl :fireflyframework-starter-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [EnhancedActuatorAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/test/java/org/fireflyframework/core/actuator/config/EnhancedActuatorAutoConfigurationTest.java).
- [CloudConfigPropertiesTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/test/java/org/fireflyframework/core/config/cloud/CloudConfigPropertiesTest.java).
- [ServiceRegistryHelperTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/test/java/org/fireflyframework/core/config/registry/ServiceRegistryHelperTest.java).
- [ServiceRegistryPropertiesTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/test/java/org/fireflyframework/core/config/registry/ServiceRegistryPropertiesTest.java).
- [LoggingUtilsTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/test/java/org/fireflyframework/core/logging/LoggingUtilsTest.java).
- [CustomerRegistrationSagaTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/test/java/org/fireflyframework/core/messaging/integration/CustomerRegistrationSagaTest.java).

## Test fixture

The `shouldLoadHttpClientMetricsConfig` test demonstrates one concrete behavior. Excerpt from [EnhancedActuatorAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/test/java/org/fireflyframework/core/actuator/config/EnhancedActuatorAutoConfigurationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void shouldLoadHttpClientMetricsConfig() {
    contextRunner
            .withPropertyValues("firefly.actuator.metrics.http.client.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(HttpClientMetricsConfig.class);

                // Verify HTTP client components are present
                assertThat(context).hasBean("httpClientHealthIndicator");
                assertThat(context).hasBean("restTemplateMetricsCustomizer");

                assertThat(context.getBean("httpClientHealthIndicator")).isInstanceOf(HealthIndicator.class);

                // Test health indicator
                HealthIndicator healthIndicator = (HealthIndicator) context.getBean("httpClientHealthIndicator");
                assertThat(healthIndicator.health()).isNotNull();
            });
}
```

## Detailed reference

### Configuration

All properties live under the `firefly.*` namespace and map to validated `@ConfigurationProperties` classes. The values below are the configured defaults from the source.

```yaml
firefly:
  # Reactive WebClient (WebClientProperties) -------------------------------
  webclient:
    enabled: true
    skip-headers: []            # inbound headers NOT forwarded downstream
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
    write-timeout-ms: 10000
    max-in-memory-size: 16777216   # 16 MB
    connection-pool:
      enabled: true
      max-connections: 500
      max-pending-acquires: 1000
      max-idle-time-ms: 30000
      max-life-time-ms: 60000
      metrics-enabled: true
    http2:
      enabled: true
      max-concurrent-streams: 100
    ssl:
      enabled: false
      verify-hostname: true
    proxy:
      enabled: false
    # WebClient resilience (WebClientResilienceProperties) -----------------
    resilience:
      enabled: true
      circuit-breaker:
        failure-rate-threshold: 50          # percent
        wait-duration-in-open-state-ms: 10000
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-size: 10
      retry:
        max-attempts: 3
        initial-backoff-ms: 500
        max-backoff-ms: 5000
        backoff-multiplier: 2.0
      timeout:
        timeout-ms: 5000
      bulkhead:
        max-concurrent-calls: 25
        max-wait-duration-ms: 0

  # Actuator & observability (ActuatorProperties) -------------------------
  actuator:
    endpoints:
      enabled: true
      web:
        exposure: "*"
        base-path: /actuator
    metrics:
      enabled: true
      prometheus:
        enabled: true
        path: /actuator/prometheus
    tracing:
      enabled: true
      sampling:
        probability: 0.1
      propagation:
        type: [B3, W3C]
      zipkin:
        enabled: false
        base-url: http://localhost:9411
    health:
      show-details: always
      show-components: true

  # Structured logging (LoggingProperties) --------------------------------
  logging:
    json:
      recursive-parsing-enabled: true
      max-recursion-depth: 10
      max-json-size-bytes: 1048576       # 1 MB
      strict-validation: false
    performance:
      metrics-enabled: true
    cache:
      enabled: true
      max-validation-cache-size: 1000
      eviction-strategy: CLEAR_ALL       # or LRU

  # Spring Cloud Config client (CloudConfigProperties) — opt-in -----------
  cloud:
    config:
      enabled: false
      uri: http://localhost:8888
      label: main
      fail-fast: false
      retry: true
      max-retries: 6
      refresh-enabled: true

  # Service registry (ServiceRegistryProperties) — opt-in -----------------
  service:
    registry:
      enabled: false
      type: eureka                       # eureka | consul
      eureka:
        service-url: http://localhost:8761/eureka/
        prefer-ip-address: true
      consul:
        host: localhost
        port: 8500

  # Orchestration integration (auto-activates if module present) ----------
  orchestration:
    enabled: true
```

Key prefixes at a glance:

| Prefix | Class | Purpose | Default state |
| --- | --- | --- | --- |
| `firefly.webclient` | `WebClientProperties` | Reactive WebClient: timeouts, pool, SSL, proxy, HTTP/2, codecs | enabled |
| `firefly.webclient.resilience` | `WebClientResilienceProperties` | Circuit breaker, retry, timeout, bulkhead | enabled |
| `firefly.actuator` | `ActuatorProperties` | Endpoints, metrics, Prometheus, tracing, health, extended meters | enabled |
| `firefly.logging` | `LoggingProperties` | JSON parsing, cache, and performance for structured logging | enabled |
| `firefly.cloud.config` | `CloudConfigProperties` | Spring Cloud Config client | disabled (opt-in) |
| `firefly.service.registry` | `ServiceRegistryProperties` | Eureka / Consul discovery | disabled (opt-in) |
| `firefly.orchestration` | (gate) | Saga/TCC/Workflow integration | enabled when module present |

### Auto-Configurations

Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

| Auto-configuration | Responsibility |
| --- | --- |
| `FireflyBannerAutoConfiguration` | Firefly-branded startup banner |
| `WebFluxAutoConfiguration` | Jackson JSON codecs (JSR-310, ISO-8601) for WebFlux |
| `WebClientAutoConfiguration` | Resilient `WebClient`, `WebClient.Builder`, and `WebClientTemplate` beans |
| `WebClientBuilderCustomizer` | Applies timeouts, pooling, SSL, proxy, HTTP/2, codec config |
| `WebFilterAutoConfiguration` | Registers the `TransactionFilter` |
| `WebClientResilienceAutoConfiguration` | Resilience4j registries (circuit breaker, retry, time limiter, bulkhead) |
| `ActuatorDefaultPropertiesAutoConfiguration` / `ActuatorAutoConfiguration` | Actuator endpoint, metrics, and health defaults |
| `DefaultHealthAutoConfiguration` | Default health indicators |
| `DefaultInfoContributorAutoConfiguration` | Enhanced `/actuator/info` contributor |
| `TracingAutoConfiguration` | Distributed tracing (sampling, propagation, Zipkin) |
| `CloudConfigAutoConfiguration` | Spring Cloud Config client (opt-in) |
| `ServiceRegistryAutoConfiguration` | Eureka / Consul registration (opt-in) |
| `OrchestrationAutoConfiguration` | Saga/TCC/Workflow support (classpath-gated) |

## Continue the application

Use the [Starter Domain guide](starter-domain.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/starter-core/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application-actuator-default.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/resources/application-actuator-default.yml).
- [application-actuator-example.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/resources/application-actuator-example.yml).
- [application-banner-default.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/resources/application-banner-default.yml).
- [application-cloud-config-example.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/resources/application-cloud-config-example.yml).
- [application-logging.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/resources/application-logging.yml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
