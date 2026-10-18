# Firefly Framework - R2DBC

[![CI](https://github.com/fireflyframework/fireflyframework-r2dbc/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-r2dbc/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive data-access toolkit for Spring Boot — auto-configured R2DBC + PostgreSQL with reactive transactions, a generic dynamic filtering engine, standardized pagination, and zero-boilerplate OpenAPI filter parameters.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Observability](#observability)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework R2DBC is the reactive persistence foundation for Firefly microservices. It builds on Spring Data R2DBC and the PostgreSQL R2DBC driver to provide non-blocking database access, and layers on the cross-cutting concerns that almost every data-layer service needs but should never have to reimplement: reactive transaction management, a reflection-driven dynamic query/filtering engine, a consistent pagination contract, and automatic OpenAPI documentation for filterable list endpoints.

The headline capability is the **generic filtering engine** (`FilterUtils` / `GenericFilter`). Given a plain filter DTO, it builds Spring Data `Criteria` by reflecting over the DTO's fields — performing `LIKE` matching on strings, `IN` matching on collections and arrays, exact matching on IDs and enums, `BETWEEN`/`>=`/`<=` for numeric and date ranges, and even `IS NULL` / `IS NOT NULL` filtering — then runs a paged `select` plus a `count` against an `R2dbcEntityTemplate` and maps the results to your DTO type. A companion `FilterParameterCustomizer` expands a single `FilterRequest<T>` controller parameter into fully documented `filters.*`, `rangeFilters.*`, `pagination.*`, and `options.*` query parameters in your Swagger UI.

Everything is wired through Spring Boot auto-configuration (`AutoConfiguration.imports`), so adding the dependency is enough to get a `ReactiveTransactionManager`, the Swagger filter customizer, the filtering engine bootstrap, and R2DBC observability beans. The module also bundles Flyway (PostgreSQL flavor) so services can manage schema migrations in the same artifact.

This is a **library module**, not a pluggable adapter: it targets PostgreSQL via the bundled `r2dbc-postgresql` driver. It is consumed directly by core/data-tier microservices and is also pulled in transitively by the `fireflyframework-starter-data` starter. It depends on sibling modules `fireflyframework-utils` (for the `@FilterableId` annotation) and `fireflyframework-observability` (for the metrics/health base classes).

## Features

- **Auto-configured R2DBC + PostgreSQL** — bundles `spring-boot-starter-data-r2dbc` and the `r2dbc-postgresql` driver; just point `spring.r2dbc.*` at your database.
- **Reactive transactions** — `R2dbcTransactionAutoConfiguration` enables `@EnableTransactionManagement` and supplies an `R2dbcTransactionManager`-backed `ReactiveTransactionManager` (`@ConditionalOnMissingBean`, so you can override it).
- **Generic dynamic filtering** — `FilterUtils.createFilter(entityClass, mapper)` returns a `GenericFilter` that turns a filter DTO into Spring Data `Criteria` with:
  - `LIKE` matching for non-empty strings (optionally case-insensitive)
  - `IN` matching for `Collection` and array fields
  - exact matching for `@Id`/`id`/`*Id` fields and enums (matched by `name()`)
  - `IS NULL` / `IS NOT NULL` via `FilterRequest.setNullFilter` / `setNotNullFilter`
- **Range filtering** — `RangeFilter` carries per-field `from`/`to` bounds and is translated to `BETWEEN`, `>=`, or `<=` depending on which bounds are present.
- **ID safety** — ID-like fields (`@Id`, named `id`, or ending in `Id`) are excluded from filtering unless explicitly opted in with `@FilterableId` (from `fireflyframework-utils`), and never receive range filters.
- **Standardized pagination** — `PaginationRequest` (page number, size, sort field, sort direction; defaults `0`/`10`/`DESC`) converts to a Spring `Pageable`, and `PaginationResponse<T>` returns content plus `totalElements`, `totalPages`, and `currentPage`. `PaginationUtils.paginateQuery` composes the page + count + mapping into a single reactive pipeline.
- **Automatic OpenAPI filter docs** — `FilterParameterCustomizer` (a springdoc `OperationCustomizer`) detects `FilterRequest<T>` controller parameters and generates nested `filters.*`, `rangeFilters.ranges[field].from/to`, `pagination.*`, and `options.*` query parameters, with type-aware schemas (enums, dates, numbers, comma-separated collections).
- **Built-in observability** — an R2DBC `HealthIndicator` (lightweight `SELECT 1` probe with latency reporting and a `DEGRADED` threshold) plus `R2dbcMetrics` helpers (`timedQuery` / `timedQueryFlux`) that record `firefly.r2dbc.*` timers and counters, all gated behind the shared `firefly.observability.*` switches.
- **Flyway migrations** — `flyway-core` and `flyway-database-postgresql` are included for schema versioning.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A reactive web context (Spring WebFlux) for the OpenAPI/Swagger integration
- A PostgreSQL database reachable over R2DBC (`r2dbc:postgresql://...`)

## Installation

Add the dependency. The version is managed by the Firefly BOM / parent, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-r2dbc</artifactId>
    <!-- version managed by fireflyframework-bom / fireflyframework-parent -->
</dependency>
```

If your project inherits `fireflyframework-parent` or imports `fireflyframework-bom`, the version is supplied for you. Otherwise pin it explicitly to the release you are targeting. Most services pull this module in transitively via `fireflyframework-starter-data`.

## Quick Start

### 1. Configure the connection

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: app
    password: secret
  flyway:
    url: jdbc:postgresql://localhost:5432/mydb   # Flyway uses a JDBC URL for migrations
    user: app
    password: secret
```

### 2. Define an entity, a filter DTO, and a paginated endpoint

```java
@Table("products")
public record Product(@Id Long id, String name, String category, BigDecimal price) {}

// Filter DTO: field names match entity columns
@Data
public class ProductFilter {
    private String name;          // -> LIKE '%name%'
    private String category;      // -> LIKE '%category%'
    @FilterableId
    private Long supplierId;      // *Id is opt-in via @FilterableId -> exact match
}

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    @GetMapping
    public Mono<PaginationResponse<Product>> list(@ParameterObject FilterRequest<ProductFilter> request) {
        return FilterUtils
                .createFilter(Product.class, Function.identity())   // entity class + entity->DTO mapper
                .filter(request);                                   // returns Mono<PaginationResponse<Product>>
    }
}
```

A request such as:

```
GET /products?filters.category=tools&rangeFilters.ranges[price].from=10&rangeFilters.ranges[price].to=50&pagination.pageNumber=0&pagination.pageSize=20&pagination.sortBy=price&pagination.sortDirection=ASC
```

produces a `LIKE` filter on `category`, a `BETWEEN` filter on `price`, sorted/paged results, and a `PaginationResponse` with `content`, `totalElements`, `totalPages`, and `currentPage`. The same `filters.*`, `rangeFilters.*`, `pagination.*`, and `options.*` parameters appear automatically in the Swagger UI for this endpoint — no manual annotations required.

### 3. Case-insensitive and inherited-field options

```java
FilterUtils.FilterOptions options = FilterUtils.FilterOptions.builder()
        .caseInsensitiveStrings(true)     // LIKE on lower-cased values
        .includeInheritedFields(true)     // also reflect over superclass fields
        .build();

FilterUtils.createFilter(Product.class, Function.identity(), options).filter(request);
```

## Configuration

This module relies on the standard Spring `spring.r2dbc.*` and `spring.flyway.*` properties for connectivity and migrations — see the Spring Boot reference for the full set. The only Firefly-specific keys are the observability switches it honors (defined in `fireflyframework-observability` and respected by this module's auto-configuration):

```yaml
firefly:
  observability:
    metrics:
      enabled: true   # default true — register R2dbcMetrics when a MeterRegistry is present
    health:
      enabled: true   # default true — register the R2DBC health indicator
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.observability.metrics.enabled` | `true` | Registers `R2dbcMetrics` (the `firefly.r2dbc.*` timers/counters) when a Micrometer `MeterRegistry` bean exists. Set `false` to disable. |
| `firefly.observability.health.enabled` | `true` | Registers the R2DBC `HealthIndicator` that probes the database with `SELECT 1`. Set `false` to disable. |

Both beans use `matchIfMissing = true`, so they are active by default and back off cleanly if the host service disables observability or the underlying classes/beans (`MeterRegistry`, `ConnectionFactory`) are absent. The `ReactiveTransactionManager` and the springdoc `FilterParameterCustomizer` are both `@ConditionalOnMissingBean`, so you can override either by declaring your own bean.

This module ships an `application-logging.yml` profile fragment that sets `org.springframework.data.r2dbc` and `io.r2dbc` logging to `INFO`.

## How It Works

The module registers four Spring Boot auto-configurations (see `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`):

| Auto-configuration | Responsibility |
| --- | --- |
| `R2dbcAutoConfiguration` | On startup, hands the application's `R2dbcEntityTemplate` to `FilterUtils.initializeTemplate(...)` so the static filtering engine can run queries. |
| `R2dbcTransactionAutoConfiguration` | Enables `@EnableTransactionManagement` and provides an `R2dbcTransactionManager`-backed `ReactiveTransactionManager`. |
| `SwaggerAutoConfiguration` | Registers the `FilterParameterCustomizer` springdoc `OperationCustomizer`. |
| `R2dbcObservabilityAutoConfiguration` | Wires `R2dbcMetrics` and the R2DBC `HealthIndicator`, gated by `firefly.observability.*`. |

Filtering follows a fixed precedence per field: `NULL` / `NOT NULL` markers first, then ID handling (skip or exact match), then collections/arrays (`IN`), then non-empty strings (`LIKE`), then enums (exact, by `name()`), then exact match for everything else. Reflected fields are cached per class for performance.

## Observability

When a Micrometer `MeterRegistry` is present, `R2dbcMetrics` exposes wrappers you can use to instrument repository methods:

- `firefly.r2dbc.query.duration` — query latency, tagged by `operation` (e.g. select/insert/update/delete)
- `firefly.r2dbc.queries` — query counts, tagged by `operation` and `status`
- `firefly.r2dbc.errors` — failures, tagged by `error.type`

```java
@RequiredArgsConstructor
public class ProductRepository {
    private final R2dbcMetrics metrics;          // auto-configured bean

    public Mono<Product> findById(Long id) {
        return metrics.timedQuery("select", delegate.findById(id));
    }
}
```

The health indicator contributes a `r2dbc` entry to `/actuator/health` reporting the database name, probe result, and latency, flipping to `DEGRADED` above a 500 ms threshold and `DOWN` on timeout/failure.

## Documentation

- [Module Catalog](https://github.com/fireflyframework/.github/blob/main/docs/MODULE_CATALOG.md) — complete reference of all Firefly Framework modules and how they fit together
- [Getting Started Guide](https://github.com/fireflyframework/.github/blob/main/docs/GETTING_STARTED.md) — configure access, create your first project, and start building
- [CI/CD Configuration Guide](https://github.com/fireflyframework/.github/blob/main/docs/CI_CD_GUIDE.md) — shared workflows and the release process
- Related modules: [`fireflyframework-utils`](https://github.com/fireflyframework/fireflyframework-utils) (provides `@FilterableId`), [`fireflyframework-observability`](https://github.com/fireflyframework/fireflyframework-observability) (metrics/health base classes), and [`fireflyframework-starter-data`](https://github.com/fireflyframework/fireflyframework-starter-data) (the starter that bundles this module).

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
