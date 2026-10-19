![Jafly. Cache - Postgres](../assets/modules/cache-postgres.svg)

# Cache - Postgres

Store cache entries in PostgreSQL through R2DBC, including expiry metadata.

Select POSTGRES through the core cache configuration and provide the database connection and cache schema described below. This is a cache provider; it is separate from the PostgreSQL EDA outbox transport.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-cache-postgres` | `jar` | [modules/cache-postgres](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/cache-postgres) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

A service that already operates PostgreSQL can use a separate cache table for repeat application reads. Cached values remain separate from the authoritative application records.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache-postgres</artifactId>
    <version>26.10.20-SNAPSHOT</version>
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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

Property-based wiring conditions found in this module:

- `prefix = "firefly.cache.postgres", name = "enabled", havingValue = "true"`. [PostgresCacheAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/config/PostgresCacheAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`PostgresCacheAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/adapter/postgres/PostgresCacheAdapter.java) | PostgreSQL-backed reactive cache adapter implemented over R2DBC. |
| [`PostgresProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/spi/providers/PostgresProvider.java) | `CacheProviderFactory` implementation that contributes the PostgreSQL R2DBC cache provider. |
| [`PostgresCacheConfig`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/adapter/postgres/PostgresCacheConfig.java) | Configuration for the PostgreSQL R2DBC cache adapter. |
| [`PostgresCacheAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/config/PostgresCacheAutoConfiguration.java) | Auto-configuration for the PostgreSQL R2DBC cache provider. |
| [`PostgresCacheHelper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/java/org/fireflyframework/cache/factory/PostgresCacheHelper.java) | Self-contained factory helper that builds a `PostgresCacheAdapter`. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache-postgres -am verify
./mvnw -pl :fireflyframework-cache-postgres -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [PostgresCacheAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/test/java/org/fireflyframework/cache/adapter/postgres/PostgresCacheAdapterIntegrationTest.java).
- [PostgresProviderWiringTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/test/java/org/fireflyframework/cache/spi/PostgresProviderWiringTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

## Test fixture

The `evictRemovesEntry` test demonstrates one concrete behavior. Excerpt from [PostgresCacheAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/test/java/org/fireflyframework/cache/adapter/postgres/PostgresCacheAdapterIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void evictRemovesEntry() {
    adapter.put("toEvict", "value").block(Duration.ofSeconds(5));

    StepVerifier.create(adapter.evict("toEvict"))
            .expectNext(true)
            .verifyComplete();

    StepVerifier.create(adapter.exists("toEvict"))
            .expectNext(false)
            .verifyComplete();

    // Evicting an absent key returns false.
    StepVerifier.create(adapter.evict("toEvict"))
            .expectNext(false)
            .verifyComplete();
}
```

## Detailed reference

### Configuration

All adapter-specific properties live under the `firefly.cache.postgres` prefix and are bound by the
core `CacheProperties.PostgresConfig`. The active provider is chosen by the core-owned
`firefly.cache.default-cache-type` key.

```yaml
firefly:
  cache:
    default-cache-type: POSTGRES    # core: choose the active provider (default: CAFFEINE)
    postgres:
      enabled: true                 # default: false
      host: localhost               # default: localhost
      port: 5432                    # default: 5432
      database: appdb               # default: none
      username: app                 # default: none
      password: secret              # default: none
      schema: public                # default: public
      cache-table: firefly_cache_entries  # default: firefly_cache_entries
      cache-name: default           # default: default
      key-prefix: ""                # default: empty
      default-ttl: PT30M            # default: 30 minutes
      auto-create-schema: true      # default: true
      max-pool-size: 10             # default: 10
      min-pool-size: 1              # default: 1
      properties: {}                # extra R2DBC driver options
```

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `firefly.cache.default-cache-type` | `CacheType` | `CAFFEINE` | Core property selecting the active provider. Set to `POSTGRES` (or `AUTO`) to use this adapter. |
| `firefly.cache.postgres.enabled` | `boolean` | `false` | Enables the PostgreSQL cache provider and its auto-configuration. |
| `firefly.cache.postgres.host` | `String` | `localhost` | PostgreSQL host. |
| `firefly.cache.postgres.port` | `int` | `5432` | PostgreSQL port. |
| `firefly.cache.postgres.database` | `String` |. | Database name. |
| `firefly.cache.postgres.username` | `String` |. | Database username. |
| `firefly.cache.postgres.password` | `String` |. | Database password. |
| `firefly.cache.postgres.schema` | `String` | `public` | Schema that holds the cache table. |
| `firefly.cache.postgres.cache-table` | `String` | `firefly_cache_entries` | Cache table name. |
| `firefly.cache.postgres.cache-name` | `String` | `default` | Default logical cache name. |
| `firefly.cache.postgres.key-prefix` | `String` | `""` | Prefix applied to namespaced keys. |
| `firefly.cache.postgres.default-ttl` | `Duration` | `PT30M` | Default entry TTL; zero/negative means no expiration. |
| `firefly.cache.postgres.auto-create-schema` | `boolean` | `true` | Create the cache table and expiry index on startup. |
| `firefly.cache.postgres.max-pool-size` | `int` | `10` | Maximum R2DBC pool connections. |
| `firefly.cache.postgres.min-pool-size` | `int` | `1` | Initial/minimum R2DBC pool connections. |
| `firefly.cache.postgres.properties` | `Map` | `{}` | Extra options passed to the PostgreSQL R2DBC driver. |

> Connection settings come only from `firefly.cache.postgres.*`, never from `spring.r2dbc.*` :
> the cache uses a dedicated, pooled `ConnectionFactory` bean
> (`fireflyCachePostgresConnectionFactory`) independent of your application datasource.

#### Storage schema

When `auto-create-schema` is `true`, the adapter provisions (idempotently) the following table and a
partial index supporting expiry scans:

```sql
CREATE TABLE IF NOT EXISTS "public"."firefly_cache_entries" (
  cache_name VARCHAR(255)  NOT NULL,
  cache_key  VARCHAR(1024) NOT NULL,
  value      BYTEA         NOT NULL,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  PRIMARY KEY (cache_name, cache_key)
);
CREATE INDEX IF NOT EXISTS firefly_cache_entries_expires_idx
  ON "public"."firefly_cache_entries" (expires_at) WHERE expires_at IS NOT NULL;
```

If you disable `auto-create-schema`, create this table and index manually before first use.

### How It Works

- `PostgresCacheAutoConfiguration` (`org.fireflyframework.cache.config`). Spring Boot
  `@AutoConfiguration` registered in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Activates when
  the R2DBC SPI is on the classpath and `firefly.cache.postgres.enabled=true`. Builds the pooled
  `fireflyCachePostgresConnectionFactory` bean and, when enabled, provisions the schema.
- `PostgresProvider` (`org.fireflyframework.cache.spi.providers`). the `CacheProviderFactory` SPI
  implementation, registered for `ServiceLoader` in
  `META-INF/services/org.fireflyframework.cache.spi.CacheProviderFactory`. Reports `CacheType.POSTGRES`
  with priority 15 and is available when the connection factory is present and the provider is enabled.
- `PostgresCacheHelper` (`org.fireflyframework.cache.factory`). the factory helper the cache core
  references reflectively to build adapters; its fully-qualified name is load-bearing and must not be
  renamed or moved.
- `PostgresCacheAdapter` (`org.fireflyframework.cache.adapter.postgres`). the `CacheAdapter`
  implementation. Stores entries in the relational table via the R2DBC `ConnectionFactory`, serializes
  values with `JsonCacheSerializer`, namespaces keys per cache, enforces TTL, and reports
  `CacheStats`/`CacheHealth`.
- `PostgresCacheConfig` (`org.fireflyframework.cache.adapter.postgres`). immutable per-adapter
  config (cache name, key prefix, default TTL, schema, table).

## Continue the application

Use the [Cache guide](cache.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/cache-postgres/src/main/java).
- [org.fireflyframework.cache.spi.CacheProviderFactory](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/resources/META-INF/services/org.fireflyframework.cache.spi.CacheProviderFactory).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-postgres/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
