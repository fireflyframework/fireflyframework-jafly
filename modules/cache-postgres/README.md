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
    <version>26.10.19</version>
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
      username: app
      password: secret
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
