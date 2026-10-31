![Jafly. Cache - Postgres](../../docs/site/assets/modules/cache-postgres.svg)

# Cache - Postgres

Store cache entries in PostgreSQL through R2DBC, including expiry metadata.

Select POSTGRES through the core cache configuration and provide the database connection and cache schema described below. This is a cache provider; it is separate from the PostgreSQL EDA outbox transport.

[Module guide](../../docs/site/modules/cache-postgres.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A service that already operates PostgreSQL can use a separate cache table for repeat application reads. Cached values remain separate from the authoritative application records.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache-postgres</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependencies. The cache core and this adapter:

```xml
<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-cache</artifactId>
    </dependency>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-cache-postgres</artifactId>
    </dependency>
</dependencies>
```

2. Enable the provider and point it at your database in `application.yml`:

```yaml
firefly:
  cache:
    default-cache-type: POSTGRES   # select this adapter (or AUTO to let the core pick)
    postgres:
      enabled: true
      host: localhost
      port: 5432
      database: appdb
      username: ${CACHE_POSTGRES_USERNAME}
      password: ${CACHE_POSTGRES_PASSWORD}
      schema: firefly_cache
      auto-create-schema: false
```

3. Use the unified cache API. Obtain a `CacheAdapter` from the core `FireflyCacheManager`; it is
   now backed by PostgreSQL with no other code changes:

```java
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductService {

    private final CacheAdapter cache;

    public ProductService(FireflyCacheManager cacheManager) {
        this.cache = cacheManager.getCache("products"); // CacheType.POSTGRES when selected
    }

    public Mono<Product> findById(String id) {
        return cache.<String, Product>get(id, Product.class)
            .flatMap(opt -> opt.map(Mono::just).orElseGet(() ->
                loadFromDatabase(id).flatMap(p -> cache.put(id, p).thenReturn(p))));
    }
}
```

Because every adapter implements the same `CacheAdapter` SPI, switching between Caffeine, Redis,
Hazelcast, JCache and PostgreSQL is a configuration change (`firefly.cache.default-cache-type`) plus
the matching dependency. Application code stays the same.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

The provider defaults to enabled, schema `firefly_cache`, table `firefly_cache_entries` and a 30-minute default TTL. Provision the technical schema/table before startup, or explicitly opt into `auto-create-schema=true` for technical cache DDL. The adapter creates no users, roles or grants. Existing deployments that use `public` must explicitly retain `firefly.cache.postgres.schema=public`.

The framework-owned `fireflyCachePostgresConnectionFactory` is lazy and is not a default bean candidate. It reads only `firefly.cache.postgres.*`, remains separate from Core business `spring.r2dbc.*`, and is disposed once by its owning context. An externally supplied factory with this name is not disposed by the adapter; declare it as a non-default candidate to preserve business factory selection.

Owned pool defaults are maximum 10/minimum 1 connections; zero minimum is supported. Connect/acquisition deadlines default to 5 seconds, selected-provider storage initialization to 30 seconds, and shutdown to 5 seconds. Override them with `connect-timeout`, `acquire-timeout`, `initialization-timeout` and `shutdown-timeout` under `firefly.cache.postgres`. Initialization checks the actual technical table; required failures stop cache startup.

Domain/Experience starter roles exclude Boot business R2DBC factory, Spring Data repository/template and transaction auto-configuration. Their technical cache pool does not authorize business persistence. Core retains its business factory supplied by URL, `R2dbcConnectionDetails` or an explicit bean.

## Namespace and conditional-write migration

Set an explicit nonblank `firefly.cache.postgres.key-prefix`, or provide `spring.application.name` so cache bootstrap can use it as the fallback. Explicit prefixes take precedence. A selected PostgreSQL provider and direct programmatic PostgreSQL adapters require a namespace; other provider selections retain their existing behavior without PostgreSQL configuration.

The physical `cache_name` is now `v2:` followed by SHA-256 of big-endian version `2` and independently length-prefixed UTF-8 service namespace, logical cache name and effective feature prefix. A caller's fixed feature prefix cannot replace the service namespace. Null feature prefixes fall back to the service prefix; explicit empty/ASCII-blank prefixes retain the legacy `cache:` key format and share one canonical feature identity. Malformed UTF-16 is rejected before encoding; well-formed supplementary Unicode remains supported. Public cache names and key APIs remain logical. Exact physical namespace equality isolates every read, write, clear, key listing and size operation, including nested prefixes. Separate instances of the same service and logical cache share entries. Different services need distinct application names or explicit namespaces.

Both unversioned and `v1` physical rows are retained as cold cache entries. The framework performs no automatic row deletion, journal/counter migration or business idempotency reset. This service namespace is not tenant authorization: callers must still supply trusted tenant-scoped keys, and authoritative data and access decisions remain owned by Core services.

The HTTP response cache is not authoritative business idempotency. Before rollout, verify that Core retains stable operation journals and retry keys across cache versions, and use an explicit mixed-version drain/cutover plan. Do not reinterpret shared legacy feature entries or silently replay non-idempotent commands after a cache miss. Cold-row retention is not complete-journey or retry acceptance.


`putIfAbsent` atomically replaces an expired conflicting row while preserving live rows and rows without expiry. Both ordinary `put` and conditional replacement compute renewed TTL using PostgreSQL's actual database clock after the conflict row lock. Ordinary `put` acknowledges exactly one affected row; conditional writes accept zero or one. Missing/non-single ordinary-write counts fail. Null, zero and negative per-call TTLs retain no-expiry behavior. The preceding configuration checkpoint's stricter default-TTL validation remains: null/negative configured defaults are rejected; zero is allowed. Prefix eviction matches literal prefixes, including `%`, `_` and backslash characters.

Programmatic factories/helpers must supply the service namespace through `CacheProperties.postgres.keyPrefix`, even when the caller supplies a separate feature prefix. The existing four-argument adapter constructor retains direct explicit-prefix callers; the added overload accepts a separate service namespace. Logical public names remain unchanged.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`PostgresCacheAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/adapter/postgres/PostgresCacheAdapter.java) | PostgreSQL-backed reactive cache adapter implemented over R2DBC. |
| [`PostgresProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/spi/providers/PostgresProvider.java) | `CacheProviderFactory` implementation that contributes the PostgreSQL R2DBC cache provider. |
| [`PostgresCacheConfig`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/adapter/postgres/PostgresCacheConfig.java) | Configuration for the PostgreSQL R2DBC cache adapter. |
| [`PostgresCacheAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/config/PostgresCacheAutoConfiguration.java) | Auto-configuration for the PostgreSQL R2DBC cache provider. |
| [`PostgresCacheHelper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/factory/PostgresCacheHelper.java) | Self-contained factory helper that builds a `PostgresCacheAdapter`. |

## Continue the application

Use the [Cache guide](../../docs/site/modules/cache.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache-postgres -am verify
./mvnw -pl :fireflyframework-cache-postgres -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/cache-postgres.md).
