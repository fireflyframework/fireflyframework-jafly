# Firefly Framework - CQRS

[![CI](https://github.com/fireflyframework/fireflyframework-cqrs/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-cqrs/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive CQRS framework for Spring Boot — annotation-driven command and query buses with built-in validation, authorization, caching, metrics, and an optional CQRS/EDA bridge.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework CQRS provides a production-ready implementation of the Command Query Responsibility Segregation pattern for reactive Spring Boot microservices. It cleanly separates state-changing **commands** from read-only **queries**, routing each through a dedicated bus (`CommandBus` / `QueryBus`) to a single auto-discovered handler. Every handler is a thin class that extends a base class and implements one method — validation, authorization, metrics, tracing, error mapping, and (for queries) caching are all applied automatically around your business logic.

The module is fully reactive (Project Reactor `Mono`) and integrates with the wider Firefly Framework: it builds on `fireflyframework-kernel` for shared abstractions, `fireflyframework-validators` and Jakarta Bean Validation for command/query validation, `fireflyframework-cache` for pluggable query-result caching (Caffeine, Redis, Hazelcast, JCache, or PostgreSQL — selected automatically), and `fireflyframework-observability` for metrics, tracing, and health. An optional bridge to `fireflyframework-eda` lets command results be published as domain events and lets incoming events invalidate query caches — giving you read/write eventual consistency with two annotations.

An `ExecutionContext` carries cross-cutting request data — user ID, tenant ID, organization ID, session/request IDs, source, client IP, feature flags, and arbitrary properties — through the command and query pipelines, so handlers and custom authorization logic can be context- and multi-tenant-aware without polluting the command/query payloads. A `CorrelationContext` propagates correlation IDs for end-to-end distributed tracing.

Where it sits in the framework: `fireflyframework-cqrs` is a domain-tier building block. Firefly microservices use it to structure their write and read paths, frequently alongside `fireflyframework-orchestration` (sagas/workflows), `fireflyframework-eventsourcing` (event-sourced aggregates), and the EDA modules for messaging. Spring Boot auto-configuration wires the buses, handler registries, validation, authorization, caching, and Actuator endpoints with zero manual setup.

## Features

- **Reactive `CommandBus` and `QueryBus`** with type-safe, single-handler dispatch (`send(...)` / `query(...)`), each with an `ExecutionContext` overload.
- **Zero-boilerplate handlers** — extend `CommandHandler<C, R>` or `QueryHandler<Q, R>` and implement only `doHandle(...)`. Command/result types are auto-detected from generics via `GenericTypeResolver`.
- **Annotation-driven registration** — `@CommandHandlerComponent` and `@QueryHandlerComponent` (meta-annotated with `@Component`) auto-register handlers and expose per-handler options (timeout, retries, metrics, tracing, validation, priority, tags, and for queries: `cacheable`, `cacheTtl`, `cacheKeyFields`, `cacheKeyPrefix`).
- **`Command<R>` and `Query<R>` interfaces** with sensible defaults for command ID, timestamp, correlation ID, initiator, metadata, and a `getResultType()` hint.
- **Built-in validation** — automatic Jakarta Bean Validation (`@NotNull`, `@Email`, ...) plus optional `customValidate()` for business rules, run by the bus before the handler executes (`AutoValidationProcessor`, `CommandValidationService`).
- **Pluggable authorization** — declarative `authorize()` / `authorize(ExecutionContext)` on commands and queries, an `AuthorizationService` with metrics, optional result caching, and the `@CustomAuthorization` documentation annotation.
- **Query-result caching** — transparent caching via `fireflyframework-cache` with a dedicated CQRS cache manager (key prefix `firefly:cqrs:queries`, `CacheType.AUTO` provider selection), smart cache-key generation, and programmatic `clearCache` / `clearAllCache`.
- **Context-aware handlers** — `ContextAwareCommandHandler` / `ContextAwareQueryHandler` and the `doHandle(command, context)` overload for multi-tenant, user-aware processing.
- **Fluent builders** — `CommandBuilder` and `QueryBuilder` for ad-hoc construction.
- **CQRS/EDA bridge (optional)** — `@PublishDomainEvent` auto-publishes a command result as a domain event, and `@InvalidateCacheOn` clears the query cache when matching events arrive. Activates only when `fireflyframework-eda` is on the classpath.
- **Observability** — `CommandMetricsService`, a CQRS Actuator endpoint (`/actuator/cqrs` with `commands`, `queries`, `handlers`, `health` selectors) and a `CqrsHealthIndicator` for `/actuator/health/cqrs`.
- **Spring Boot auto-configuration** — `CqrsAutoConfiguration`, `CqrsActuatorAutoConfiguration`, and `CqrsEdaAutoConfiguration`, all gated by `@ConditionalOnProperty` and `@ConditionalOnMissingBean` so every bean is overridable.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- (Optional) A cache provider via `fireflyframework-cache` — Caffeine in-process by default; add a Redis/Hazelcast/JCache/PostgreSQL adapter for distributed query caching.
- (Optional) `fireflyframework-eda` with a transport (e.g. Kafka or RabbitMQ) to enable the CQRS/EDA bridge.

## Installation

Add the dependency. The version is managed by the Firefly Framework BOM / parent, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cqrs</artifactId>
    <!-- version managed by fireflyframework-parent / BOM -->
</dependency>
```

If you are not inheriting the Firefly parent, import the BOM in your `<dependencyManagement>` (or pin the version explicitly):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fireflyframework</groupId>
            <artifactId>fireflyframework-bom</artifactId>
            <version>26.05.08</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Quick Start

### 1. Define a command and its handler

```java
import org.fireflyframework.cqrs.command.Command;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import reactor.core.publisher.Mono;

// Command: an immutable intention to change state. Jakarta constraints are validated automatically.
public record CreateAccountCommand(
        @NotBlank String name,
        @Email String email) implements Command<AccountResult> {}

// Handler: extend CommandHandler and implement doHandle() — that's the only boilerplate.
@CommandHandlerComponent
public class CreateAccountHandler extends CommandHandler<CreateAccountCommand, AccountResult> {

    private final AccountService accountService;

    public CreateAccountHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected Mono<AccountResult> doHandle(CreateAccountCommand command) {
        // Validation, authorization, metrics, tracing and error handling are already applied.
        return accountService.create(command.name(), command.email());
    }
}
```

### 2. Define a query and its handler

```java
import org.fireflyframework.cqrs.query.Query;
import org.fireflyframework.cqrs.query.QueryHandler;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import reactor.core.publisher.Mono;

public record GetAccountQuery(String accountId) implements Query<AccountResult> {}

// Results are cached transparently for 5 minutes via fireflyframework-cache.
@QueryHandlerComponent(cacheable = true, cacheTtl = 300)
public class GetAccountHandler extends QueryHandler<GetAccountQuery, AccountResult> {

    private final AccountService accountService;

    public GetAccountHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected Mono<AccountResult> doHandle(GetAccountQuery query) {
        return accountService.findById(query.accountId());
    }
}
```

### 3. Dispatch from a controller

```java
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.cqrs.context.ExecutionContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    public AccountController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @PostMapping
    public Mono<AccountResult> create(@RequestBody CreateAccountCommand command) {
        return commandBus.send(command);
    }

    @GetMapping("/{id}")
    public Mono<AccountResult> get(@PathVariable String id) {
        // Pass cross-cutting context (user, tenant, feature flags) without touching the query payload.
        ExecutionContext ctx = ExecutionContext.builder()
                .withUserId("user-123")
                .withTenantId("tenant-456")
                .build();
        return queryBus.query(new GetAccountQuery(id), ctx);
    }
}
```

### 4. (Optional) Bridge commands and caches to EDA

With `fireflyframework-eda` on the classpath, publish a command result as a domain event and keep query caches fresh:

```java
@CommandHandlerComponent
@PublishDomainEvent(destination = "account-events", eventType = "AccountCreated")
public class CreateAccountHandler extends CommandHandler<CreateAccountCommand, AccountResult> { /* ... */ }

@QueryHandlerComponent(cacheable = true, cacheTtl = 300)
@InvalidateCacheOn(eventTypes = {"AccountCreated", "AccountUpdated", "AccountDeleted"})
public class GetAccountHandler extends QueryHandler<GetAccountQuery, AccountResult> { /* ... */ }
```

## Configuration

All properties live under the `firefly.cqrs.*` namespace. The values below are the real defaults; you only need to set what you want to change.

```yaml
firefly:
  cqrs:
    enabled: true                 # master switch for the CQRS framework
    command:
      timeout: 30s                # default command processing timeout
      metrics-enabled: true
      tracing-enabled: true
    query:
      timeout: 15s                # default query processing timeout
      caching-enabled: true       # enable query-result caching by default
      cache-ttl: 15m              # default TTL for the dedicated CQRS query cache
      metrics-enabled: true
      tracing-enabled: true
    eda:
      enabled: true               # CQRS/EDA bridge (only active if fireflyframework-eda is present)
    authorization:
      enabled: true               # master switch for authorization checks
      custom:
        enabled: true
        timeout-ms: 5000          # timeout for custom authorize() logic
      logging:
        enabled: true
        log-successful: false     # only log failures/errors when false
        log-performance: true
        level: INFO
      performance:
        cache-enabled: false      # cache authorization results
        cache-ttl-seconds: 300
        cache-max-size: 1000
        async-enabled: false
```

### Key properties

| Property | Default | Description |
| --- | --- | --- |
| `firefly.cqrs.enabled` | `true` | Enables/disables the entire CQRS framework. |
| `firefly.cqrs.command.timeout` | `30s` | Default per-command processing timeout. |
| `firefly.cqrs.query.timeout` | `15s` | Default per-query processing timeout. |
| `firefly.cqrs.query.caching-enabled` | `true` | Turns query-result caching on/off globally. |
| `firefly.cqrs.query.cache-ttl` | `15m` | Default TTL of the dedicated CQRS query cache. |
| `firefly.cqrs.eda.enabled` | `true` | Enables the `@PublishDomainEvent` / `@InvalidateCacheOn` bridge (requires `fireflyframework-eda`). |
| `firefly.cqrs.authorization.enabled` | `true` | Enables authorization on commands and queries. |
| `firefly.cqrs.authorization.custom.timeout-ms` | `5000` | Timeout (ms) for custom `authorize()` logic. |
| `firefly.cqrs.authorization.performance.cache-enabled` | `false` | Caches authorization decisions. |

> **Query caching note:** the actual cache provider (Caffeine, Redis, Hazelcast, JCache, or PostgreSQL) is selected by `fireflyframework-cache` via `CacheType.AUTO`. Configure the provider through `firefly.cache.*`; the legacy `firefly.cqrs.query.cache.*` keys are deprecated in favor of it.

### Actuator endpoints

When Spring Boot Actuator is on the classpath, expose the CQRS endpoints:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, cqrs
```

- `GET /actuator/cqrs` — overview (with `/commands`, `/queries`, `/handlers`, `/health` selectors)
- `GET /actuator/health/cqrs` — CQRS framework health

## How It Works

Auto-configuration is split into three classes, registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

- **`CqrsAutoConfiguration`** — wires `CommandBus`, `QueryBus`, the handler registry, validation, authorization, the dedicated `cqrsQueryCacheManager`/`QueryCacheAdapter`, the correlation context, and metrics. Gated on `firefly.cqrs.enabled=true`.
- **`CqrsActuatorAutoConfiguration`** — registers the `CqrsMetricsEndpoint` and `CqrsHealthIndicator` when Actuator is present.
- **`CqrsEdaAutoConfiguration`** — registers the EDA bridge (`CommandEventPublisher`, `EventDrivenCacheInvalidator`) when `fireflyframework-eda` is on the classpath and `firefly.cqrs.eda.enabled=true`.

Each command/query flows through its bus: **validate → authorize → dispatch to handler → metrics & tracing**. Every bean is annotated `@ConditionalOnMissingBean`, so you can override any piece by declaring your own.

## Documentation

- Framework hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- In-repo guides under [`docs/`](docs/):
  - [Quickstart](docs/QUICKSTART.md)
  - [Architecture](docs/ARCHITECTURE.md)
  - [Configuration](docs/CONFIGURATION.md)
  - [Developer Guide](docs/DEVELOPER_GUIDE.md)
  - [Event Sourcing Integration](docs/EVENT_SOURCING_INTEGRATION.md)
  - [Examples](docs/examples)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
