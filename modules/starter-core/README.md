# Firefly Framework - Starter Core

[![CI](https://github.com/fireflyframework/fireflyframework-starter-core/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-starter-core/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Opinionated Spring Boot starter for core/infrastructure-tier microservices — auto-configures a resilient reactive WebClient, Actuator/observability defaults, service discovery, cloud config, and orchestration wiring out of the box.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Auto-Configurations](#auto-configurations)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-starter-core` is the opinionated Spring Boot starter for building **core/infrastructure-tier** microservices on the Firefly Framework. It is *not* the framework kernel itself — it is a convenience starter that pulls in the foundational Firefly libraries (kernel, CQRS, EDA, observability, and — optionally — the orchestration engine) and layers a set of Spring Boot auto-configurations on top so that a new service boots with sensible, production-ready defaults and zero hand-wired plumbing.

Add the single dependency and your service automatically gets a reactive, resilience-wrapped `WebClient` (circuit breaker, retry, timeout, bulkhead, metrics) with transaction-ID propagation, a curated Actuator surface (health, info, metrics, Prometheus, tracing), extended Micrometer meter binders (JVM, thread pools, HTTP client, database, cache, application), structured Logstash-compatible JSON logging, a Firefly-branded startup banner, and a `TransactionFilter` that stamps and propagates an `X-Transaction-Id` across the reactive context. Optional, classpath-gated modules add Spring Cloud Config client wiring, service registry registration (Eureka / Consul), and saga/TCC/workflow support.

Every auto-configuration is registered through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and is individually toggleable through `firefly.*` properties, so you can opt out of any layer without removing the starter. This makes the module ideal for the platform-level services that sit underneath domain logic: API gateways, config servers, messaging bridges, registries, and other infrastructure components.

Where it sits in the framework:

- It **builds on** [`fireflyframework-kernel`](https://github.com/fireflyframework/fireflyframework-kernel) (foundational types/exceptions), [`fireflyframework-cqrs`](https://github.com/fireflyframework/fireflyframework-cqrs), [`fireflyframework-eda`](https://github.com/fireflyframework/fireflyframework-eda), and [`fireflyframework-observability`](https://github.com/fireflyframework/fireflyframework-observability), exposing their capabilities as ready-to-use beans.
- It **optionally integrates** [`fireflyframework-orchestration`](https://github.com/fireflyframework/fireflyframework-orchestration); when that module is on the classpath and `firefly.orchestration.enabled` is not `false`, saga/TCC/workflow support activates automatically via `OrchestrationAutoConfiguration`.
- For domain-tier and experience-tier (BFF) services, prefer the higher-level starters; use this starter for the infrastructure tier.

## Features

- **Resilient reactive WebClient** — the auto-configured `WebClient` bean is wrapped by `ResilientWebClient`, adding Resilience4j circuit breaker, retry with exponential backoff, time limiter, bulkhead, and Micrometer metrics, plus automatic `X-Transaction-Id` header propagation through the Reactor context.
- **`WebClientTemplate`** — a thin helper that forwards inbound request headers (minus a configurable skip-list) to downstream calls for GET/POST/PUT/DELETE.
- **`WebClient.Builder` bean** — pre-customized (timeouts, connection pool, SSL/TLS, proxy, HTTP/2, codecs) for building bespoke clients with the same defaults.
- **Curated Actuator surface** — `ActuatorAutoConfiguration` + `ActuatorDefaultPropertiesAutoConfiguration` expose health, info, metrics, Prometheus, and tracing with opinionated defaults.
- **Extended metrics** — JVM (`JvmMetricsConfig`), thread-pool (`ThreadPoolMetricsConfig`), HTTP-client (`HttpClientMetricsConfig`), and application (`ApplicationMetricsConfig`) meter binders, plus database and cache health indicators (`DatabaseHealthConfig`, `CacheHealthConfig`).
- **Distributed tracing** — `TracingAutoConfiguration` with configurable sampling, B3/W3C propagation, and optional Zipkin export.
- **Structured logging** — Logstash-compatible JSON logging (`logback-spring.xml`) with recursive JSON parsing, caching, and performance controls via `LoggingProperties`.
- **Firefly startup banner** — `FireflyBannerAutoConfiguration` renders the framework banner with app name/version/Swagger URL.
- **Transaction context filter** — `TransactionFilter` (highest precedence `WebFilter`) generates/echoes `X-Transaction-Id` and propagates it through the reactive context (skips `/actuator` and `/admin`).
- **Spring Cloud Config client** — `CloudConfigAutoConfiguration` (opt-in) for centralized configuration with fail-fast and retry tuning.
- **Service registry integration** — `ServiceRegistryAutoConfiguration` (opt-in) for Eureka or Consul registration and discovery.
- **Orchestration integration** — `OrchestrationAutoConfiguration` auto-activates saga/TCC/workflow support when `fireflyframework-orchestration` is present.
- **WebFlux JSON defaults** — `WebFluxAutoConfiguration` registers Jackson codecs with JSR-310 support and ISO-8601 date serialization.
- **Broad optional messaging support** — optional dependencies for Kafka, RabbitMQ/AMQP, ActiveMQ, AWS (SQS/Kinesis/DynamoDB/CloudWatch), GCP Pub/Sub, and Azure Service Bus to back EDA transports when needed.

## Requirements

- Java 21+ (Java 25 recommended; the CI build runs on Java 25)
- Spring Boot 3.x (WebFlux / reactive stack)
- Maven 3.9+
- Optional runtime dependencies, only when the matching feature is enabled:
  - A Spring Cloud Config server (for `firefly.cloud.config`)
  - A Eureka or Consul server (for `firefly.service.registry`)
  - A message broker (Kafka, RabbitMQ, etc.) when using EDA transports
  - A Zipkin collector (for trace export)

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-core</artifactId>
    <!-- Version is managed by the Firefly BOM / parent; omit when inheriting fireflyframework-parent -->
</dependency>
```

If your project inherits the Firefly parent, the version is supplied for you:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.05.08</version>
    <relativePath/>
</parent>
```

The starter brings in `fireflyframework-kernel`, `fireflyframework-cqrs`, `fireflyframework-eda`, and `fireflyframework-observability` transitively; `fireflyframework-orchestration` is included as an optional dependency — add it explicitly to activate saga/TCC/workflow support.

## Quick Start

Add the dependency and the auto-configurations do the rest — no extra annotations are required. A resilient, transaction-aware `WebClient` (and `WebClientTemplate`) are available for injection:

```java
import org.fireflyframework.core.web.client.WebClientTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
public class CustomerGateway {

    private final WebClientTemplate webClient;

    public CustomerGateway(WebClientTemplate webClient) {
        this.webClient = webClient;
    }

    // Inbound headers (minus the configured skip-list) are forwarded downstream,
    // and X-Transaction-Id is propagated automatically.
    public Mono<Customer> fetch(String baseUrl, String id, ServerWebExchange exchange) {
        return webClient.get(baseUrl, "/customers/" + id, Customer.class, exchange);
    }
}
```

Prefer the raw `WebClient`? The auto-configured bean is already resilience-wrapped (circuit breaker, retry, timeout, bulkhead, metrics):

```java
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class InventoryClient {

    private final WebClient webClient; // resilient + transaction-aware by default

    public InventoryClient(WebClient webClient) {
        this.webClient = webClient;
    }

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

All properties live under the `firefly.*` namespace and map to validated `@ConfigurationProperties` classes. The values below are the **real defaults** from the source.

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

## Auto-Configurations

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

## Documentation

- Framework hub & module catalog: [fireflyframework on GitHub](https://github.com/fireflyframework)
- In-repo deep dives in [`docs/`](docs/):
  - [Overview](docs/OVERVIEW.md)
  - [Architecture](docs/ARCHITECTURE.md)
  - [Configuration](docs/CONFIGURATION.md)
  - [Quickstart](docs/QUICKSTART.md)
  - [API](docs/API.md)
  - [Integrations](docs/INTEGRATIONS.md)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
