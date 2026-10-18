# Firefly Framework - Cache

[![CI](https://github.com/fireflyframework/fireflyframework-cache/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-cache/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive, provider-agnostic cache abstraction for Spring Boot — a unified `CacheAdapter` API with Caffeine built in and pluggable Redis, Hazelcast, JCache and PostgreSQL adapters discovered via SPI.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Cache Provider Adapters](#cache-provider-adapters)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Cache is the **core cache abstraction** of the framework. It defines a single, reactive (`Mono`-based) `CacheAdapter` port and a `FireflyCacheManager` facade, so application code caches against one stable interface and never couples itself to a specific cache technology. Switching from a local in-process cache to a distributed one is a matter of adding an adapter dependency and changing one property — no code change.

Out of the box the module ships a single provider: **Caffeine**, a high-performance in-memory (L1) cache that is always available with zero extra dependencies. The other providers — **Redis**, **Hazelcast**, **JCache (JSR-107)** and **PostgreSQL** — live in their own adapter modules. They register with the core through the `CacheProviderFactory` SPI (Java `ServiceLoader`), so dropping an adapter JAR on the classpath makes its `CacheType` available to the `CacheManagerFactory` automatically. The core has **no compile-time dependency** on any distributed provider; optional infrastructure beans (Redis connection factory, Hazelcast instance, JCache manager, R2DBC connection factory) are resolved reflectively only when present.

`CacheManagerFactory` creates independent, namespaced cache managers on demand — each with its own key prefix, TTL and provider — so different framework modules (web idempotency, webhook dedup, etc.) can share infrastructure without colliding. When a distributed provider is selected, the factory can transparently wrap it with `SmartCacheAdapter`, a write-through **L1 (Caffeine) + L2 (distributed)** composite that serves reads from local memory and backfills L1 on L2 hits. A `CacheType.AUTO` mode lets the framework pick the best available provider by SPI priority (Redis → Hazelcast → JCache → Caffeine).

The module integrates with `fireflyframework-observability` to expose cache metrics (Micrometer) and a Spring Boot Actuator `HealthIndicator`, and depends on `fireflyframework-kernel` for the shared exception hierarchy.

## Features

- **Unified reactive cache port** — `CacheAdapter` with `get`, `put` (incl. TTL), `putIfAbsent`, `evict`, `evictByPrefix`, `clear`, `exists`, `keys`, `size`, `getStats` and `getHealth`, all returning `Mono`.
- **Caffeine built in** — high-performance in-memory L1 adapter (`CaffeineCacheAdapter`) configured via `CaffeineCacheConfig`; always available with no extra dependencies.
- **Pluggable providers via SPI** — Redis, Hazelcast, JCache (JSR-107) and PostgreSQL ship as separate adapter modules discovered through the `CacheProviderFactory` `ServiceLoader` SPI; the core never compiles against them.
- **`FireflyCacheManager` facade** — delegates to the active provider with optional fallback and resilient error handling (cache failures degrade gracefully to misses instead of propagating).
- **`CacheManagerFactory`** — creates multiple independent cache managers, each with its own name, key prefix, TTL and `CacheType`.
- **Smart L1+L2 cache** — `SmartCacheAdapter` provides write-through composite caching (local Caffeine over a distributed provider) with optional L1 backfill on reads.
- **Automatic provider selection** — `CacheType.AUTO` and `AutoCacheSelectionStrategy` pick the best available provider by SPI priority.
- **Pluggable serialization** — `CacheSerializer` SPI with a default JSON implementation (`JsonCacheSerializer`) backed by a dedicated `cacheObjectMapper` (JSR-310 aware).
- **Observability** — Micrometer `CacheMetrics` and an Actuator `CacheHealthIndicator`, auto-wired via `CacheObservabilityAutoConfiguration`.
- **Zero-config auto-configuration** — `CacheAutoConfiguration` registers a primary `FireflyCacheManager`, the factory and serializer; fully configurable through validated `CacheProperties` (`firefly.cache.*`).

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A distributed cache backend (Redis, Hazelcast, JCache provider or PostgreSQL) **only** if you add the corresponding adapter module — Caffeine requires nothing extra.

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache</artifactId>
    <!-- Version managed by the Firefly BOM / parent POM -->
</dependency>
```

The version is managed by the `fireflyframework-parent` / Firefly BOM; omit `<version>` when your project inherits or imports it.

## Quick Start

With the dependency on the classpath, `CacheAutoConfiguration` provides a primary `FireflyCacheManager` (Caffeine by default). Inject it and use the reactive API:

```java
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Service
public class ProductService {

    private final FireflyCacheManager cache;
    private final ProductRepository repository;

    public ProductService(FireflyCacheManager cache, ProductRepository repository) {
        this.cache = cache;
        this.repository = repository;
    }

    public Mono<Product> findById(String id) {
        return cache.<String, Product>get(id, Product.class)
                .flatMap(cached -> cached
                        .map(Mono::just)
                        .orElseGet(() -> repository.findById(id)
                                .flatMap(product -> cache
                                        .put(id, product, Duration.ofMinutes(10))
                                        .thenReturn(product))));
    }

    public Mono<Boolean> evict(String id) {
        return cache.evict(id);
    }
}
```

Need a dedicated, namespaced cache (separate key prefix, TTL and provider)? Inject the factory:

```java
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.factory.CacheManagerFactory;
import org.fireflyframework.cache.manager.FireflyCacheManager;

FireflyCacheManager idempotencyCache = factory.createCacheManager(
        "http-idempotency",
        CacheType.AUTO,                // Redis if present, else Caffeine
        "firefly:http:idempotency",
        Duration.ofHours(24));
```

## Cache Provider Adapters

The core ships Caffeine. To enable a distributed provider, add its adapter module and select it via `firefly.cache.default-cache-type` (or `AUTO`). Each adapter self-registers through the `CacheProviderFactory` SPI.

| Provider | Adapter module | `CacheType` | Notes |
| --- | --- | --- | --- |
| Caffeine | _(built into core)_ | `CAFFEINE` | In-memory L1, always available |
| Redis | `fireflyframework-cache-redis` | `REDIS` | Reactive, Lettuce-based distributed cache |
| Hazelcast | `fireflyframework-cache-hazelcast` | `HAZELCAST` | Distributed in-memory data grid |
| JCache (JSR-107) | `fireflyframework-cache-jcache` | `JCACHE` | Standards-based (Ehcache, Infinispan, …) |
| PostgreSQL | `fireflyframework-cache-postgres` | `POSTGRES` | R2DBC-backed persistent distributed cache |

```xml
<!-- Example: add Redis as the distributed (L2) provider -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache-redis</artifactId>
</dependency>
```

```yaml
firefly:
  cache:
    default-cache-type: REDIS   # or AUTO to auto-select the best available provider
```

When a distributed provider is active and `firefly.cache.smart.enabled` is `true`, the factory wraps it with `SmartCacheAdapter` to provide an L1 (Caffeine) + L2 (distributed) write-through cache.

## Configuration

All properties are bound from the `firefly.cache.*` prefix into the validated `CacheProperties` class. Defaults shown below.

```yaml
firefly:
  cache:
    enabled: true                 # master switch for the cache library
    default-cache-type: CAFFEINE  # CAFFEINE | REDIS | HAZELCAST | JCACHE | POSTGRES | NOOP | AUTO
    metrics-enabled: true         # publish Micrometer cache metrics
    health-enabled: true          # expose the Actuator cache health indicator
    stats-enabled: true           # collect cache statistics

    caffeine:                     # built-in in-memory (L1) provider
      enabled: true
      cache-name: default
      key-prefix: "firefly:cache"
      maximum-size: 1000
      expire-after-write: 1h
      expire-after-access:        # disabled by default
      refresh-after-write:        # disabled by default
      record-stats: true
      weak-keys: false
      weak-values: false
      soft-values: false

    redis:                        # used by fireflyframework-cache-redis
      enabled: true
      cache-name: default
      host: localhost
      port: 6379
      database: 0
      username:
      password:
      connection-timeout: 10s
      command-timeout: 5s
      key-prefix: "firefly:cache"
      default-ttl:                # no TTL unless set
      enable-keyspace-notifications: false
      max-pool-size: 8
      min-pool-size: 0
      ssl: false

    postgres:                     # used by fireflyframework-cache-postgres
      enabled: false
      cache-name: default
      host: localhost
      port: 5432
      database:
      username:
      password:
      schema: public
      cache-table: firefly_cache_entries
      key-prefix: ""
      default-ttl: 30m
      auto-create-schema: true
      max-pool-size: 10
      min-pool-size: 1

    smart:                        # L1 (Caffeine) + L2 (distributed) composite
      enabled: true
      write-strategy: WRITE_THROUGH   # only WRITE_THROUGH is implemented
      backfill-l1-on-read: true
```

Key properties:

- **`default-cache-type`** — selects the provider. `CAFFEINE` (default) keeps everything in-process. Set to a distributed type once its adapter is on the classpath, or use `AUTO` to let the framework pick the highest-priority available provider.
- **`enabled`** — when `false`, `CacheAutoConfiguration` backs off entirely.
- **`caffeine.*`** — sizing and eviction for the built-in L1 cache (`maximum-size`, `expire-after-write`, `expire-after-access`, `refresh-after-write`, reference strength).
- **`redis.*` / `postgres.*`** — connection, pooling, key-prefix and TTL settings consumed by the respective adapter modules (`hazelcast` and `jcache` adapters add their own properties).
- **`smart.*`** — controls the write-through L1+L2 composite applied automatically over distributed providers.
- **Observability** — metrics and health honor `firefly.observability.metrics.enabled` and `firefly.observability.health.enabled` (both default `true`).

## Documentation

In-repo guides live under [`docs/`](docs/):

- [Quickstart](docs/QUICKSTART.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Configuration](docs/CONFIGURATION.md)
- [API Reference](docs/API_REFERENCE.md)
- [Auto-Configuration](docs/AUTO_CONFIGURATION.md)
- [Examples](docs/EXAMPLES.md)
- [Monitoring](docs/MONITORING.md)
- [Optional Dependencies](docs/OPTIONAL_DEPENDENCIES.md)
- [Testing](docs/TESTING.md)

Framework-wide documentation and the module catalog are available in the [Firefly Framework organization](https://github.com/fireflyframework).

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
