![Jafly. Starter Core](../../docs/site/assets/modules/starter-core.svg)

# Starter Core

Assemble core infrastructure conventions for WebClient, actuator, logging, configuration, and service discovery.

Use this starter at the infrastructure foundation of a service. Enable optional integrations with their own settings rather than assuming discovery or remote configuration is required. The property classes below separate client, propagation, registry, logging, and actuator concerns.

[Module guide](../../docs/site/modules/starter-core.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

Broker-backed messaging comes from the separate EDA transport adapters. This starter supplies no KCL consumer or AWS Glue schema-registry integration. Applications that add those SDKs must select and qualify their own SDK and protobuf combination.

## In the onboarding service

A larger onboarding service may adopt this bundle for shared HTTP-client and operational settings. Compare those dependencies with the tutorial's small CQRS/WebFlux set before adding the starter.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-core</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

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

## Configuration

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

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`WebClientTemplate`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/web/client/WebClientTemplate.java) | Defines `addHeaders`, `post`, `put`, `delete` operations. |
| [`ResilientWebClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/web/resilience/ResilientWebClient.java) | A decorator for `WebClient` that adds resilience patterns like circuit breaker, retry, timeout, bulkhead, and metrics. |
| [`HeaderPropagationProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/HeaderPropagationProperties.java) | Configuration for inbound-to-outbound HTTP header propagation. |
| [`CloudConfigProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/config/cloud/CloudConfigProperties.java) | Configuration properties for Spring Cloud Config. |
| [`ActuatorProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/starter-core/src/main/java/org/fireflyframework/core/actuator/config/ActuatorProperties.java) | Configuration properties for Spring Boot Actuator. |

## Continue the application

Use the [Starter Domain guide](../../docs/site/modules/starter-domain.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-starter-core -am verify
./mvnw -pl :fireflyframework-starter-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/starter-core.md).
