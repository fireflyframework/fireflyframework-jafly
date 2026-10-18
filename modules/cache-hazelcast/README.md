# Firefly Framework - Cache Hazelcast

[![CI](https://github.com/fireflyframework/fireflyframework-cache-hazelcast/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-cache-hazelcast/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Hazelcast distributed-cache provider for the Firefly Framework cache abstraction — drop it on the classpath and it is discovered through the JDK ServiceLoader SPI.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-cache-hazelcast` is a pluggable **provider adapter** for the unified Firefly cache
abstraction defined in [`fireflyframework-cache`](https://github.com/fireflyframework/fireflyframework-cache).
It contributes a Hazelcast-backed cache implementation so that applications can move from the default
in-process Caffeine cache to a distributed, cluster-shared cache without changing any application code —
only the dependency and the selected provider change.

The adapter is **SPI-only**: it ships a `HazelcastProvider` (a `CacheProviderFactory` reporting
`CacheType.HAZELCAST`) registered under
`META-INF/services/org.fireflyframework.cache.spi.CacheProviderFactory`. The core module's
`CacheManagerFactory` discovers it through the JDK `ServiceLoader` at runtime — there is **no Spring
auto-configuration** in this module. Instead, the adapter binds to a `com.hazelcast.core.HazelcastInstance`
bean that the host application supplies, which keeps cluster topology, networking, and security entirely
under the application's control.

To avoid forcing a Hazelcast dependency onto every consumer of the core abstraction, the cache delegate
(`HazelcastCacheHelper`, backing `HazelcastCacheAdapterReflective`) accesses Hazelcast **reflectively**.
The core cache module therefore never carries a compile-time Hazelcast dependency; Hazelcast is only on the
classpath when this adapter is present.

This module sits alongside the other cache provider adapters for the same SPI — Caffeine (the default,
bundled in core), Redis, JCache, and PostgreSQL/R2DBC — any of which can be selected through the core
module's `firefly.cache.default-cache-type` property.

## Features

- **SPI-discovered provider** — `HazelcastProvider` registered via
  `META-INF/services/org.fireflyframework.cache.spi.CacheProviderFactory`; no wiring required beyond
  adding the jar.
- **Reports `CacheType.HAZELCAST`** with a provider priority of `20` used by the core selection ordering.
- **Distributed cache backing** — entries are stored in a Hazelcast `IMap`, including TTL-aware writes so
  per-entry expiry honors the abstraction's contract.
- **Reflective delegate** (`HazelcastCacheHelper` / `HazelcastCacheAdapterReflective`) keeps the core cache
  module free of any compile-time Hazelcast dependency.
- **Key namespacing** — honors the configured `keyPrefix`, producing keys of the form
  `<prefix>:<cacheName>:<key>`.
- **No Spring auto-configuration** — binds to a host-supplied `HazelcastInstance` bean, leaving cluster
  configuration to the application.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A reachable Hazelcast cluster (or embedded member) and a `com.hazelcast.core.HazelcastInstance` bean
  provided by the host application

## Installation

Add the core cache abstraction plus this adapter. Versions are managed by the Firefly parent/BOM, so you
do not pin them yourself:

```xml
<dependencies>
    <!-- Core cache abstraction (SPI + Caffeine default) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-cache</artifactId>
        <!-- version managed by the Firefly BOM / parent -->
    </dependency>

    <!-- Hazelcast provider adapter (this module) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-cache-hazelcast</artifactId>
        <!-- version managed by the Firefly BOM / parent -->
    </dependency>
</dependencies>
```

If your project does not already inherit the Firefly parent or import the BOM, add explicit versions
matching your platform release.

## Quick Start

1. Provide a `HazelcastInstance` bean (embedded member shown; use a client config to join an external
   cluster):

   ```java
   import com.hazelcast.core.Hazelcast;
   import com.hazelcast.core.HazelcastInstance;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;

   @Configuration
   public class HazelcastConfig {

       @Bean
       public HazelcastInstance hazelcastInstance() {
           return Hazelcast.newHazelcastInstance();
       }
   }
   ```

2. Select Hazelcast as the cache provider:

   ```yaml
   firefly:
     cache:
       default-cache-type: HAZELCAST
   ```

With both the `HazelcastInstance` bean on the context and the property set, the core
`CacheManagerFactory` discovers `HazelcastProvider` through the SPI and routes all cache operations to
Hazelcast. Your application code keeps using the standard Firefly cache API — nothing else changes.

## How It Works

```
Application code
      │  (Firefly cache API, provider-agnostic)
      ▼
fireflyframework-cache  ──►  CacheManagerFactory
                                   │ ServiceLoader lookup of CacheProviderFactory
                                   ▼
                          HazelcastProvider  (CacheType.HAZELCAST, priority 20)
                                   │ wraps the host HazelcastInstance bean
                                   ▼
                  HazelcastCacheHelper / HazelcastCacheAdapterReflective
                                   │ reflective access (no compile-time dep in core)
                                   ▼
                          Hazelcast IMap  (TTL-aware, key = <prefix>:<cacheName>:<key>)
```

## Configuration

This adapter has **no `@ConfigurationProperties` of its own**. It is selected and tuned entirely through
the core module's `firefly.cache.*` properties; the only requirement specific to this adapter is a
`HazelcastInstance` bean on the application context. The minimum configuration to activate it:

```yaml
firefly:
  cache:
    default-cache-type: HAZELCAST   # selects this provider (default is CAFFEINE)
```

| Property | Owner | Purpose |
| --- | --- | --- |
| `firefly.cache.default-cache-type` | core | Set to `HAZELCAST` to activate this adapter. |
| `firefly.cache.*` (TTL, key prefix, etc.) | core | Shared cache settings; the adapter honors `keyPrefix` to namespace keys as `<prefix>:<cacheName>:<key>`. |

Hazelcast cluster topology, discovery, networking, and security are configured on the
`HazelcastInstance` bean by the host application, not through Firefly properties.

## Documentation

- Firefly Framework module catalog and docs hub:
  [github.com/fireflyframework](https://github.com/fireflyframework)
- Core cache abstraction:
  [fireflyframework-cache](https://github.com/fireflyframework/fireflyframework-cache)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
