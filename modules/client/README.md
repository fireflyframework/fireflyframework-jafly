# Firefly Framework - Client

[![CI](https://github.com/fireflyframework/fireflyframework-client/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-client/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Unified, reactive service-client library for Spring Boot — one fluent, resilient API over REST, SOAP, gRPC, GraphQL, and WebSocket with built-in circuit breakers, retries, discovery, and observability.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-client` is the Firefly Framework's common client library: a single, consistent, fully reactive abstraction for calling other services regardless of the wire protocol. Instead of juggling `WebClient`, JAX-WS/CXF, raw gRPC stubs, and bespoke GraphQL/WebSocket plumbing, application code uses one fluent entry point — `ServiceClient` — and gets resilience, security, discovery, and observability wired in by default.

The library is built on Spring WebFlux (`WebClient`) and Project Reactor, so every operation returns `Mono`/`Flux` and is non-blocking from end to end. Clients are constructed through type-safe builders (`RestClientBuilder`, `SoapClientBuilder`, `GrpcClientBuilder`) reachable from the `ServiceClient.rest(...)`, `ServiceClient.soap(...)`, and `ServiceClient.grpc(...)` static factories, and each client carries an integrated circuit breaker (Firefly's own `CircuitBreakerManager`, backed by Resilience4j), retry-with-backoff, health checks, and per-client Micrometer metrics.

Where it sits in the framework: this module depends only on `fireflyframework-kernel` (shared exception hierarchy and abstractions) and `fireflyframework-observability` (metrics, tracing, health, structured logging). It is the outbound-communication backbone used by Firefly microservices to consume core/domain services, third-party REST/SOAP APIs, and generated SDKs. It auto-configures itself via Spring Boot — drop it on the classpath and `ServiceClientAutoConfiguration` provides a tuned `WebClient.Builder`, a default `CircuitBreakerManager`, a default `RestClientBuilder`, a gRPC builder factory, and `ServiceClientMetrics` (when a `MeterRegistry` is present).

Beyond the core REST/SOAP/gRPC clients, the module bundles a set of companion helpers and cross-cutting concerns: a `GraphQLClientHelper` (query/mutation/batch/subscribe with response caching), a `WebSocketClientHelper` (reconnection, heartbeat, message queue), a `WebhookClientHelper` (HMAC signature generation/verification, dispatch), HTTP response caching, request deduplication, service discovery (Eureka, Consul, Kubernetes, static), client-side rate limiting, certificate pinning, API-key and OAuth2 helpers, an interceptor chain, a chaos-engineering interceptor for fault injection, and a plugin SPI for extending client behavior.

## Features

- Unified `ServiceClient` SPI with protocol-specific subtypes: `RestClient`, `SoapClient`, `GrpcClient`
- Fluent, type-safe builders: `RestClientBuilder`, `SoapClientBuilder`, `GrpcClientBuilder` (via `ServiceClient.rest/soap/grpc`)
- Reactive, non-blocking operations end to end (`Mono`/`Flux`) on Spring WebFlux + Reactor Netty
- REST: HTTP verb methods (`get`/`post`/`put`/`patch`/`delete`), fluent `RequestBuilder` (path/query params, headers, body, timeout), and streaming (`stream(...)`)
- SOAP: modern reactive API over JAX-WS/Apache CXF with WS-Security, MTOM, schema validation, WSDL caching, and TLS trust/key stores
- gRPC: builder over `grpc-netty-shaded` with plaintext/TLS, keep-alive, deadlines, and stub factories
- `GraphQLClientHelper`: queries, mutations, variables, data-path extraction, batch execution, subscriptions, and response caching
- `WebSocketClientHelper`: configurable handshake timeout, automatic reconnection with backoff, heartbeat, message queue, and compression
- `WebhookClientHelper`: HMAC signature generation/verification, event subscription/dispatch, and retry
- Resilience: integrated `CircuitBreakerManager` (sliding window, failure-rate, slow-call, half-open transitions) and retry with exponential backoff + jitter
- Service discovery: `EurekaServiceDiscoveryClient`, `ConsulServiceDiscoveryClient`, `KubernetesServiceDiscoveryClient`, `StaticServiceDiscoveryClient` behind a `ServiceDiscoveryClient` SPI, plus `LoadBalancerStrategy`
- Security: `OAuth2ClientHelper`, `ApiKeyManager`, `CertificatePinningManager`, `ClientSideRateLimiter`, `JwtValidator`, `SecretsEncryptionManager`
- Performance: `HttpCacheManager`/`HttpCacheInterceptor` HTTP response caching and `RequestDeduplicationManager`
- Extensibility: `ServiceClientInterceptor` chain (logging, metrics, custom), `ServiceClientPlugin` SPI + `PluginManager`, and `DynamicJsonResponse` for schemaless responses
- Testing aids: `ChaosEngineeringInterceptor` for fault injection, plus a rich, mapped exception hierarchy (`ServiceTimeoutException`, `ServiceNotFoundException`, `CircuitBreakerOpenException`, `SoapFaultException`, ...) with HTTP/gRPC/SOAP error mappers
- Observability: per-client health indicators (`ServiceClientHealthIndicator`/`Manager`), Micrometer metrics (`ServiceClientMetrics`), and `MultipartUploadHelper`
- Spring Boot auto-configuration with validated `@ConfigurationProperties` and environment-aware defaults (development / testing / production)

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A reactive (WebFlux) application context. Optional runtime dependencies only when their feature is used: a service registry (Eureka/Consul) or Kubernetes for dynamic discovery, and a reachable WSDL endpoint for SOAP clients.

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-client</artifactId>
    <!-- Version is managed by the Firefly BOM / parent; omit when inheriting it -->
</dependency>
```

If your project inherits `fireflyframework-parent` (or imports `fireflyframework-bom`), the version is managed for you and can be omitted. Otherwise pin the current release explicitly.

## Quick Start

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

## Configuration

All settings live under the `firefly.service-client.*` prefix and are bound (and validated) by `ServiceClientProperties`. Properties are **global defaults** organized by protocol/concern; per-client overrides are expressed through the builders shown above. Environment-aware defaults are applied automatically based on `firefly.service-client.environment` (`development`, `testing`, `production`).

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

### Key properties

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

> Note: environment-aware defaults only override values you have **not** set explicitly, so any property you configure always wins.

## Documentation

- Firefly Framework module catalog and architecture docs: [github.com/fireflyframework/.github](https://github.com/fireflyframework/.github/blob/main/profile/MODULE_CATALOG.md)
- In-repo guides under [`docs/`](docs/):
  - [Quick Start](docs/QUICK_START.md) · [Configuration](docs/CONFIGURATION.md)
  - [REST Client](docs/REST_CLIENT.md) · [SOAP Client](docs/SOAP_CLIENT.md) · [gRPC Client](docs/GRPC_CLIENT.md) · [GraphQL Client](docs/GRAPHQL_CLIENT.md) · [WebSocket Helper](docs/WEBSOCKET_HELPER.md)
  - [Multipart Helper](docs/MULTIPART_HELPER.md) · [OAuth2 Helper](docs/OAUTH2_HELPER.md) · [Security](docs/SECURITY.md) · [Observability](docs/OBSERVABILITY.md)
  - [Advanced Features](docs/ADVANCED_FEATURES.md) · [Integration Testing](docs/INTEGRATION_TESTING.md)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
