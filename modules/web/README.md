# Firefly Framework - Web

[![CI](https://github.com/fireflyframework/fireflyframework-web/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-web/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive web layer for Spring WebFlux — standardized RFC 7807 error handling, request idempotency, PII log masking, CORS, HTTP request logging, and build-time OpenAPI spec generation, all auto-configured.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [OpenAPI Generation Infrastructure](#openapi-generation-infrastructure)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Web is the shared web-layer library for Firefly-based reactive microservices built on Spring WebFlux. Adding it to a service's classpath gives every REST controller consistent, production-grade cross-cutting behavior — standardized error responses, safe request retries, log sanitization, CORS, and request logging — without writing any boilerplate. Everything is delivered through Spring Boot auto-configuration, so a service typically only declares the dependency and tunes a handful of `firefly.web.*` properties.

The centerpiece is a reactive `GlobalExceptionHandler` (an `ErrorWebExceptionHandler`/`@RestControllerAdvice`) backed by a pluggable `ExceptionConverterService`. A registry of 15 `ExceptionConverter` implementations maps framework exceptions, Bean Validation failures, Spring Security errors, Spring Data / JPA / R2DBC database errors, Resilience4j circuit-breaker/bulkhead/rate-limit failures, JSON (de)serialization errors, network/HTTP-client failures, and WebFlux routing errors onto the correct HTTP status with a structured, RFC 7807 Problem Details response. The module also ships 29 ready-to-throw semantic exceptions (for example `ResourceNotFoundException`, `BusinessException`, `ValidationException`, `ConflictException`, `RateLimitException`) so application code can signal intent and let the handler produce the right status code and payload.

Around the error handler sit several independent, individually toggleable subsystems: a cache-backed **idempotency** filter (`IdempotencyWebFilter`) that deduplicates requests carrying an `X-Idempotency-Key` header and replays the stored response on retries; a **PII masking** service plus Logback appender that redact emails, phone numbers, national IDs, credit cards, JWTs, API keys and more from logs and `System.out`; a **CORS** auto-configuration that is secure by default; an **HTTP request logging** web filter; and a **conditions report** that prints a categorized view of which auto-configurations activated at startup.

This module depends on `fireflyframework-kernel` (shared exceptions and abstractions), `fireflyframework-cache` (the unified cache abstraction that backs idempotency storage), and `fireflyframework-observability` (metrics and tracing integration for error reporting). It also provides the reusable **OpenAPI generation infrastructure** (`@EnableOpenApiGen`, `AutoMockMissingBeansConfig`) that lets a `-web` module produce an OpenAPI spec from controller annotations at build time, which downstream SDK modules turn into typed clients.

## Features

- **Reactive global exception handler** — `GlobalExceptionHandler` (`ErrorWebExceptionHandler` + `@RestControllerAdvice`) producing consistent error responses across all controllers.
- **15 pluggable exception converters** via `ExceptionConverterService`: validation, security, `DataAccess`/JPA/R2DBC/optimistic-locking, Resilience4j, JSON, network, HTTP client/server, external-service, and WebFlux converters.
- **RFC 7807 Problem Details** error format (`ProblemDetail` / `ErrorResponse` models) with correlation IDs, request IDs, trace/span IDs, suggestions, and documentation links.
- **29 semantic exceptions** (`ResourceNotFoundException`, `BusinessException`, `ValidationException`, `ConflictException`, `UnauthorizedException`, `ForbiddenException`, `RateLimitException`, `CircuitBreakerException`, `ServiceUnavailableException`, and more) mapped to correct HTTP statuses.
- **Request idempotency** — `IdempotencyWebFilter` deduplicates requests by `X-Idempotency-Key` header, caches responses (backed by `fireflyframework-cache`), and coalesces concurrent in-flight duplicates.
- **`@DisableIdempotency`** method annotation to opt specific endpoints out of idempotency handling and OpenAPI header documentation.
- **Automatic OpenAPI documentation** of the idempotency header via `IdempotencyOpenAPICustomizer` / `IdempotencyOperationCustomizer`.
- **PII masking** — `PiiMaskingService`, `PiiMaskingAppender` (Logback), and `StdoutMaskingService` redact 60+ built-in patterns (emails, EU phone numbers, EU national IDs, SSN, credit cards, IP/MAC, JWT, API keys) plus user-defined custom patterns.
- **Secure-by-default CORS** — `CorsAutoConfiguration` driven by `firefly.web.cors.*`; no origins allowed until explicitly configured.
- **HTTP request/response logging** — `HttpRequestLoggingWebFilter` with header/body/query controls, sensitive-header redaction, and path exclusions.
- **Distributed tracing & metrics** — error reporting integrates with Micrometer `MeterRegistry` and the Micrometer `Tracer` (OpenTelemetry / Zipkin / Jaeger) through `fireflyframework-observability`.
- **Conditions evaluation report** — `ConditionsReportListener` / `ConditionsReportService` print a categorized, color-coded report of activated and skipped auto-configurations at startup.
- **Build-time OpenAPI generation** — `@EnableOpenApiGen` and `AutoMockMissingBeansConfig` generate an OpenAPI spec from controller annotations without loading production beans.
- **Full Spring Boot auto-configuration** — 8 auto-configurations registered, each independently toggleable via `firefly.web.*` properties.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A Spring WebFlux application (this module excludes Spring MVC by design)
- For idempotency: a configured cache provider via `fireflyframework-cache` (Caffeine by default; Redis, Hazelcast, etc. as pluggable adapters)

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-web</artifactId>
    <!-- Version managed by the Firefly BOM / fireflyframework-parent -->
</dependency>
```

When inheriting from `fireflyframework-parent` (or importing the Firefly BOM), the version is managed for you and can be omitted. The module brings in `spring-boot-starter-webflux`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`, `spring-boot-starter-aop`, and the springdoc WebFlux UI starter transitively.

## Quick Start

Add the dependency and the web layer is wired automatically. Throw the framework's semantic exceptions from your reactive controllers and the `GlobalExceptionHandler` converts them to standardized RFC 7807 responses:

```java
import org.fireflyframework.web.error.exceptions.ResourceNotFoundException;
import org.fireflyframework.web.error.exceptions.ConflictException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public Mono<Order> findById(@PathVariable String id) {
        return orderService.findById(id)
            // -> 404 with an RFC 7807 Problem Details body
            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found: " + id)));
    }

    @PostMapping
    public Mono<Order> create(@Valid @RequestBody OrderRequest request) {
        // Bean Validation failures are auto-converted to 400 responses.
        // ConflictException -> 409, BusinessException -> 422, etc.
        return orderService.create(request)
            .onErrorMap(DuplicateOrderException.class,
                e -> new ConflictException("Order already exists"));
    }
}
```

A typical error response (RFC 7807 Problem Details):

```json
{
  "type": "https://docs.example.com/errors/RESOURCE_NOT_FOUND",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Order not found: 12345",
  "instance": "/api/orders/12345",
  "code": "RESOURCE_NOT_FOUND",
  "timestamp": "2026-05-31T10:15:30Z",
  "correlationId": "b1d3...",
  "traceId": "4f9a..."
}
```

### Idempotent requests

Clients send a unique `X-Idempotency-Key` header on a write request; retries with the same key replay the original cached response instead of re-executing the handler. Opt an endpoint out with `@DisableIdempotency`:

```java
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;

@PostMapping("/payments")
public Mono<Payment> pay(@RequestBody PaymentRequest req) {
    // Send header "X-Idempotency-Key: <uuid>"; a retry returns the same response.
    return paymentService.charge(req);
}

@PostMapping("/heartbeat")
@DisableIdempotency // never deduplicated, header hidden from OpenAPI
public Mono<Void> heartbeat() {
    return monitor.ping();
}
```

## Configuration

All properties live under the `firefly.web.*` namespace. The defaults below are production-safe; every subsystem can be toggled independently.

```yaml
firefly:
  web:
    # --- Global error handling (firefly.web.error-handling) ---
    error-handling:
      include-stack-trace: false       # keep false in production
      include-debug-info: false        # request params/headers in response
      include-exception-cause: false
      mask-sensitive-data: true        # uses PiiMaskingService when present
      enable-rfc7807: true             # RFC 7807 Problem Details format
      enable-metrics: true             # Micrometer error metrics
      enable-distributed-tracing: true # OpenTelemetry / Zipkin / Jaeger
      include-correlation-id: true
      include-request-id: true
      include-span-id: true
      include-suggestions: true
      include-documentation: true
      include-help-url: true
      documentation-base-url: ""       # error code appended to this URL
      help-base-url: ""
      support-email: ""
      support-phone: ""
      instance-id: ""                  # e.g. ${spring.application.name}:${random.uuid}
      max-message-length: 500
      max-details-length: 2000
      max-validation-errors: 100
      default-severity: MEDIUM
      default-category: UNKNOWN
      log-all-errors: true
      client-error-log-level: WARN     # log level for 4xx
      server-error-log-level: ERROR    # log level for 5xx
      enable-error-caching: false      # cache responses during error storms
      error-cache-ttl-seconds: 60
      error-cache-max-size: 1000

    # --- Idempotency (firefly.web.idempotency) ---
    idempotency:
      enabled: true                    # matchIfMissing=true (on by default)
      header-name: X-Idempotency-Key
      cache:
        ttl-hours: 24                  # TTL for cached idempotent responses

    # --- PII masking (firefly.web.pii-masking) ---
    pii-masking:
      enabled: true
      mask-character: "*"
      preserve-length: true
      show-characters: 3               # used when preserve-length=false
      mask-headers: true
      mask-bodies: true
      mask-query-params: true
      mask-exceptions: true
      auto-mask-logs: true
      enable-stdout-masking: true      # redact System.out.println output
      case-sensitive: false
      patterns: {}                     # override built-in patterns (60+ defaults)
      custom-patterns: {}              # add org-specific patterns

    # --- HTTP request logging (firefly.web.http-request-logging) ---
    http-request-logging:
      enabled: true
      log-level: INFO
      include-headers: true
      include-request-body: false
      include-response-body: false
      include-query-params: true
      max-body-size: 1024              # bytes
      sensitive-headers: [authorization, x-api-key, x-auth-token, cookie, set-cookie]
      excluded-paths: [/health, /actuator/**, /metrics, /info]

    # --- CORS (firefly.web.cors) — secure by default ---
    cors:
      enabled: true
      path-pattern: "/**"
      allowed-origins: []              # empty = no origins allowed (no CORS headers)
      allowed-methods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]
      allowed-headers: ["*"]
      exposed-headers: [X-Transaction-Id]
      allow-credentials: false
      max-age: 3600                    # preflight cache seconds

    # --- Conditions evaluation report (firefly.web.conditions-report) ---
    conditions-report:
      enabled: false                   # disabled by default
      show-negative: true              # show conditions that did not match
      show-details: true
      summary-only: false
      use-colors: true
```

Key notes:

- **Idempotency cache provider** is configured through `fireflyframework-cache` (`firefly.cache.*`), not here. Idempotency only stores entries under the `:idempotency:` key prefix; the actual provider (Caffeine, Redis, …) and its sizing/TTL come from the cache module.
- **CORS is secure by default**: with an empty `allowed-origins`, no `Access-Control-Allow-Origin` header is emitted. Populate origins explicitly for browser clients.
- **PII masking** ships 60+ built-in regex patterns (emails, US/EU phone numbers, EU national identity numbers, SSN, credit cards, IP/MAC addresses, JWTs, API keys, sensitive URLs). Override any via `patterns` or extend with `custom-patterns`.
- The **conditions report** is off by default; enable it during development to diagnose which Firefly/Spring auto-configurations activated.

## OpenAPI Generation Infrastructure

This module provides reusable infrastructure for automated OpenAPI spec generation and SDK creation at build time. A microservice generates an OpenAPI spec from its controller annotations without loading any production dependencies.

| Component | Purpose |
|-----------|---------|
| `@EnableOpenApiGen` | Meta-annotation combining `@SpringBootConfiguration`, `@EnableWebFlux`, and the required Springdoc / WebFlux / Jackson auto-configurations into one annotation. |
| `AutoMockMissingBeansConfig` | A `BeanDefinitionRegistryPostProcessor` that auto-creates Mockito mocks for any `@Autowired` dependency missing from the context. Active only under the `openapi-gen` Spring profile. |
| `application-openapi-gen.yaml` | Profile config that enables Springdoc and allows bean-definition overriding; auto-discovered from the classpath. |

How it works:

1. A lightweight Spring Boot app (`OpenApiGenApplication`) starts during the Maven build using the test classpath.
2. `@EnableOpenApiGen` imports the minimal auto-configurations needed for Springdoc + WebFlux.
3. `AutoMockMissingBeansConfig` scans `@RestController` beans and registers Mockito mocks for their `@Autowired` dependencies, so controllers load without their real service implementations.
4. Springdoc reads the controller annotations and exposes the spec at `/v3/api-docs.yaml`.
5. The `springdoc-openapi-maven-plugin` fetches the spec and writes it to `target/openapi/openapi.yml`.
6. The SDK module uses `openapi-generator-maven-plugin` to generate typed API clients from the spec.

Usage in a microservice:

```java
// src/test/java — OpenApiGenApplication
@EnableOpenApiGen
@ComponentScan(basePackages = "com.example.module.web.controllers")
public class OpenApiGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenApiGenApplication.class, args);
    }
}
```

```xml
<!-- the -web module's pom.xml -->
<properties>
    <openapi.gen.skip>false</openapi.gen.skip>
    <openapi.gen.mainClass>com.example.module.web.openapi.OpenApiGenApplication</openapi.gen.mainClass>
</properties>
```

The Maven profile and plugin configuration are inherited from `fireflyframework-parent`.

## Documentation

- [Firefly Framework organization](https://github.com/fireflyframework) — all modules and the module catalog.
- Configuration property reference: see the `@ConfigurationProperties` classes in `org.fireflyframework.web.*.config`.
- Related modules: [`fireflyframework-kernel`](https://github.com/fireflyframework/fireflyframework-kernel) (shared exceptions), [`fireflyframework-cache`](https://github.com/fireflyframework/fireflyframework-cache) (idempotency storage), [`fireflyframework-observability`](https://github.com/fireflyframework/fireflyframework-observability) (metrics & tracing).

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
