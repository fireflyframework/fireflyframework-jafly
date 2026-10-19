![Jafly. Common Client Library](../../docs/site/assets/modules/client.svg)

# Common Client Library

Call REST, SOAP, and gRPC services through reactive client builders with shared resilience and observability facilities.

Choose the protocol-specific builder, set the remote service endpoint and authentication, and retain the client for reuse. Treat retries and circuit breakers as operation policy: verify whether the remote operation is safe to retry.

[Module guide](../../docs/site/modules/client.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

The service may call a document-verification partner during a later onboarding step. Set the endpoint and authentication on the selected protocol client, and choose retries according to whether that partner operation can safely repeat.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-client</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Auto-configuration provides a default `RestClientBuilder`, `CircuitBreakerManager`, and tuned `WebClient.Builder` as soon as the dependency is on the classpath. You can use the `ServiceClient` static factories directly, or define your own client beans.

### REST

```java
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PaymentService {

    private final RestClient paymentClient;



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

## Configuration

The `firefly.service-client` namespace is bound by [ServiceClientProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/config/ServiceClientProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.service-client.enabled` | `boolean` | `true` |
| `firefly.service-client.default-timeout` | `Duration` | `Duration.ofSeconds(30)` |
| `firefly.service-client.environment` | `Environment` | `Environment.DEVELOPMENT` |
| `firefly.service-client.default-headers` | `Map<String, String>` | `new HashMap<>()` |
| `firefly.service-client.rest` | `Rest` | `new Rest()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`RestClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/RestClient.java) | REST-specific service client interface with HTTP semantics. |
| [`RestClientBuilder`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/builder/RestClientBuilder.java) | Simplified builder for REST service clients. |
| [`SoapClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/SoapClient.java) | SOAP-specific service client interface with WSDL-based operation invocation. |
| [`GrpcClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/GrpcClient.java) | gRPC-specific service client interface with native gRPC semantics. |
| [`ServiceClient`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/main/java/org/fireflyframework/client/ServiceClient.java) | Core interface for all service clients providing common lifecycle and health operations. |

## Continue the application

Use the [Orchestration guide](../../docs/site/modules/orchestration.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-client -am verify
./mvnw -pl :fireflyframework-client -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/client.md).
