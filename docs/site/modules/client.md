![Jafly. Common Client Library](../assets/modules/client.svg)

# Common Client Library

Call REST, SOAP, and gRPC services through reactive client builders with shared resilience and observability facilities.

Choose the protocol-specific builder, set the remote service endpoint and authentication, and retain the client for reuse. Treat retries and circuit breakers as operation policy: verify whether the remote operation is safe to retry.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-client` | `jar` | [modules/client](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/client) |

Part of [External services](index.md#external-services).

## In the onboarding service

The service may call a document-verification partner during a later onboarding step. Set the endpoint and authentication on the selected protocol client, and choose retries according to whether that partner operation can safely repeat.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-client</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Auto-configuration provides a default `RestClientBuilder`, `CircuitBreakerManager`, and tuned `WebClient.Builder` as soon as the dependency is on the classpath. You can use the `ServiceClient` static factories directly, or define your own client beans.

### REST

```java
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
public class ClientConfig {

    @Bean
    public RestClient paymentClient(CircuitBreakerManager circuitBreakerManager) {
        return ServiceClient.rest("payment-service")
            .baseUrl("https://api.payments.com")
            .timeout(Duration.ofSeconds(5))
            .defaultHeader("Accept", "application/json")
            .circuitBreakerManager(circuitBreakerManager)
            .build();
    }
}

@Service
public class PaymentService {

    private final RestClient paymentClient;

    public PaymentService(RestClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    // Fluent RequestBuilder: set body/params/headers, then execute()
    public Mono<PaymentResponse> charge(PaymentRequest request) {
        return paymentClient.post("/charges", PaymentResponse.class)
            .withBody(request)
            .execute();
    }

    public Mono<Payment> get(String id) {
        return paymentClient.get("/charges/{id}", Payment.class)
            .withPathParam("id", id)
            .execute();
    }
}
```

### SOAP

```java
SoapClient weather = ServiceClient.soap("weather-service")
    .wsdlUrl("https://example.com/weather?wsdl")
    .credentials("user", "secret")     // WS-Security UsernameToken
    .timeout(Duration.ofSeconds(30))
    .build();

Mono<WeatherResponse> forecast = weather.invokeAsync(
    "GetWeatherByCity", new GetWeatherRequest("Madrid"), WeatherResponse.class);
```

### gRPC

```java
GrpcClient<UserServiceGrpc.UserServiceStub> users =
    ServiceClient.grpc("user-service", UserServiceGrpc.UserServiceStub.class)
        .address("localhost:9090")
        .usePlaintext()
        .stubFactory(UserServiceGrpc::newStub)
        .build();
```

## Configuration and wiring

The `firefly.service-client` namespace is bound by [ServiceClientProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/config/ServiceClientProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.service-client.enabled` | `boolean` | `true` |
| `firefly.service-client.default-timeout` | `Duration` | `Duration.ofSeconds(30)` |
| `firefly.service-client.environment` | `Environment` | `Environment.DEVELOPMENT` |
| `firefly.service-client.default-headers` | `Map<String, String>` | `new HashMap<>()` |
| `firefly.service-client.rest` | `Rest` | `new Rest()` |
| `firefly.service-client.grpc` | `Grpc` | `new Grpc()` |
| `firefly.service-client.soap` | `Soap` | `new Soap()` |
| `firefly.service-client.sdk` | `Sdk` | `new Sdk()` |
| `firefly.service-client.circuit-breaker` | `CircuitBreaker` | `new CircuitBreaker()` |
| `firefly.service-client.retry` | `Retry` | `new Retry()` |
| `firefly.service-client.metrics` | `Metrics` | `new Metrics()` |
| `firefly.service-client.security` | `Security` | `new Security()` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.service-client", name = "enabled", havingValue = "true", matchIfMissing = true`. [ServiceClientAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/config/ServiceClientAutoConfiguration.java).
- `prefix = "firefly.service-client.metrics", name = "enabled", havingValue = "true", matchIfMissing = true`. [ServiceClientAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/config/ServiceClientAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`RestClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/RestClient.java) | REST-specific service client interface with HTTP semantics. |
| [`RestClientBuilder`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/builder/RestClientBuilder.java) | Simplified builder for REST service clients. |
| [`SoapClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/SoapClient.java) | SOAP-specific service client interface with WSDL-based operation invocation. |
| [`GrpcClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/GrpcClient.java) | gRPC-specific service client interface with native gRPC semantics. |
| [`ServiceClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/ServiceClient.java) | Core interface for all service clients providing common lifecycle and health operations. |

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
./mvnw -pl :fireflyframework-client -am verify
./mvnw -pl :fireflyframework-client -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [HttpCacheManagerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/test/java/org/fireflyframework/client/cache/HttpCacheManagerTest.java).
- [ChaosEngineeringInterceptorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/test/java/org/fireflyframework/client/chaos/ChaosEngineeringInterceptorTest.java).
- [ServiceClientAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/test/java/org/fireflyframework/client/config/ServiceClientAutoConfigurationTest.java).
- [RequestDeduplicationManagerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/test/java/org/fireflyframework/client/deduplication/RequestDeduplicationManagerTest.java).
- [ServiceDiscoveryClientTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/test/java/org/fireflyframework/client/discovery/ServiceDiscoveryClientTest.java).
- [DynamicClassGeneratorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/test/java/org/fireflyframework/client/dynamic/DynamicClassGeneratorTest.java).

## Test fixture

The `shouldRespectMaxCacheSize` test demonstrates one concrete behavior. Excerpt from [HttpCacheManagerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/test/java/org/fireflyframework/client/cache/HttpCacheManagerTest.java). The test class supplies its fixtures and imports.

```java
@Test
void shouldRespectMaxCacheSize() {
    // Given
    HttpCacheConfig smallConfig = HttpCacheConfig.builder()
        .enabled(true)
        .maxCacheSize(2)
        .build();

    HttpCacheManager smallCache = new HttpCacheManager(smallConfig);

    // When
    smallCache.put("key1", "value1", null, null);
    smallCache.put("key2", "value2", null, null);
    smallCache.put("key3", "value3", null, null);

    // Then
    assertThat(smallCache.getStatistics().size()).isLessThanOrEqualTo(2);
}
```

## Detailed reference

### Configuration

All settings live under the `firefly.service-client.*` prefix and are bound (and validated) by `ServiceClientProperties`. Properties are global defaults organized by protocol/concern; per-client overrides are expressed through the builders shown above. Environment-aware defaults are applied automatically based on `firefly.service-client.environment` (`development`, `testing`, `production`).

```yaml
firefly:
  service-client:
    enabled: true                 # master switch for the framework
    default-timeout: 30s          # global default for all client operations
    environment: development      # development | testing | production (drives smart defaults)
    default-headers: {}           # headers applied to all REST clients

    rest:
      max-connections: 100        # connection pool size
      max-idle-time: 5m
      max-life-time: 30m
      pending-acquire-timeout: 10s
      response-timeout: 30s
      max-in-memory-size: 1048576 # 1 MB response buffer
      connect-timeout: 10s
      read-timeout: 30s
      follow-redirects: true
      compression-enabled: true
      default-content-type: application/json
      default-accept-type: application/json
      logging-enabled: false
      max-retries: 3

    grpc:
      keep-alive-time: 5m
      keep-alive-timeout: 30s
      keep-alive-without-calls: true
      max-inbound-message-size: 4194304   # 4 MB
      max-inbound-metadata-size: 8192     # 8 KB
      call-timeout: 30s
      retry-enabled: true
      use-plaintext-by-default: true
      compression-enabled: true
      max-concurrent-streams: 100

    soap:
      default-timeout: 30s
      connection-timeout: 10s
      receive-timeout: 30s
      mtom-enabled: false
      schema-validation-enabled: true
      message-logging-enabled: false
      max-message-size: 10485760          # 10 MB
      ws-addressing-enabled: false
      ws-security-enabled: false
      soap-version: "1.1"                 # 1.1 or 1.2
      wsdl-cache-enabled: true
      wsdl-cache-expiration: 1h
      follow-redirects: true

    sdk:
      default-timeout: 45s
      auto-shutdown-enabled: true
      max-concurrent-operations: 50
      logging-enabled: false

    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50.0        # percent
      wait-duration-in-open-state: 60s
      sliding-window-size: 10
      minimum-number-of-calls: 5
      slow-call-rate-threshold: 100.0     # percent
      slow-call-duration-threshold: 60s
      permitted-number-of-calls-in-half-open-state: 3
      call-timeout: 30s
      automatic-transition-from-open-to-half-open-enabled: true

    retry:
      enabled: true
      max-attempts: 3
      wait-duration: 500ms
      exponential-backoff-multiplier: 2.0
      max-wait-duration: 10s
      jitter-enabled: true

    metrics:
      enabled: true
      detailed-metrics: false
      export-interval: 1m
      histogram-enabled: true
      tags: {}

    security:
      enabled: true
      default-auth-type: NONE             # NONE | BASIC | BEARER | OAUTH2 | CUSTOM
      ssl-validation-enabled: true
      request-signing-enabled: false
```

#### Key properties

| Property | Default | Purpose |
| --- | --- | --- |
| `firefly.service-client.enabled` | `true` | Master switch; when `false`, no client beans are auto-configured. |
| `firefly.service-client.environment` | `development` | Selects environment-aware defaults (e.g. relaxed SSL and verbose logging in `development`, hardened defaults in `production`). |
| `firefly.service-client.default-timeout` | `30s` | Fallback timeout for all client operations. |
| `firefly.service-client.rest.max-connections` | `100` | Reactor Netty connection-pool size for REST. |
| `firefly.service-client.circuit-breaker.failure-rate-threshold` | `50.0` | Percentage of failures within the sliding window that opens the breaker. |
| `firefly.service-client.retry.max-attempts` | `3` | Maximum retry attempts (with exponential backoff + optional jitter). |
| `firefly.service-client.grpc.use-plaintext-by-default` | `true` | Whether new gRPC channels default to plaintext (forced to TLS in `production`). |
| `firefly.service-client.metrics.enabled` | `true` | Enables `ServiceClientMetrics` Micrometer integration (auto-disabled in `testing`). |

> Note: environment-aware defaults only override values you have not set explicitly, so any property you configure always wins.

## Continue the application

Use the [Orchestration guide](orchestration.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/client/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [spring-configuration-metadata.json](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/resources/META-INF/spring-configuration-metadata.json).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
