![Jafly. Cache - Redis](../assets/modules/cache-redis.svg)

# Cache - Redis

Back the shared cache abstraction with Redis and reactive serialization.

Add this provider beside cache, select REDIS through the core cache settings, and supply reachable Redis connection settings. Test serialization and expiry with the integration fixtures before using shared entries across services.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-cache-redis` | `jar` | [modules/cache-redis](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/cache-redis) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

Use a Redis-backed cache when several onboarding-service instances need the same cached application data. Define expiry and status-change invalidation in the application.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache-redis</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#add-the-component)).

2. Select Redis as the cache type and enable the adapter in `application.yml`:

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: REDIS    # core selects the active provider (CAFFEINE | REDIS | HAZELCAST | JCACHE | POSTGRES | AUTO)
    redis:
      enabled: true              # the Redis provider activates only when true
      host: localhost
      port: 6379
      default-ttl: PT30M
```

3. Inject and use the cache through the core abstraction. Your code never references Redis directly:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cache.core.CacheAdapter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final CacheAdapter cache;



    public Mono<Product> getProduct(String id) {
        return cache.<String, Product>get(id, Product.class)
                .flatMap(opt -> opt.map(Mono::just)
                        .orElseGet(() -> loadFromDatabase(id)
                                .flatMap(p -> cache.put(id, p, Duration.ofMinutes(30))
                                        .thenReturn(p))));
    }

    public Mono<Boolean> invalidate(String id) {
        return cache.evict(id);
    }
}
```

Swapping providers later is a configuration change only: point `firefly.cache.default-cache-type` at another type and add that adapter's dependency. Application code is untouched. With `default-cache-type: AUTO`, the framework prefers Redis when it is configured and available, then Hazelcast, JCache, Caffeine, and finally a no-op cache.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`RedisCacheAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/adapter/redis/RedisCacheAdapter.java) | Redis-based cache adapter implementation. |
| [`RedisProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/spi/providers/RedisProvider.java) | Defines `create` operations. |
| [`RedisCacheConfig`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/adapter/redis/RedisCacheConfig.java) | Configuration class for Redis cache adapter. |
| [`RedisCacheAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/config/RedisCacheAutoConfiguration.java) | Auto-configuration for Redis infrastructure beans used by fireflyframework-cache. |
| [`RedisCacheHelper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/factory/RedisCacheHelper.java) | Helper class for creating Redis cache adapters. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache-redis -am verify
./mvnw -pl :fireflyframework-cache-redis -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [RedisCacheAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/test/java/org/fireflyframework/cache/adapter/redis/RedisCacheAdapterIntegrationTest.java).
- [CacheAutoConfigurationRedisIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/test/java/org/fireflyframework/cache/config/CacheAutoConfigurationRedisIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

## Test fixture

The `shouldNotCreateRedisBeansWhenRedisDisabled` test demonstrates one concrete behavior. Excerpt from [CacheAutoConfigurationRedisIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/test/java/org/fireflyframework/cache/config/CacheAutoConfigurationRedisIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void shouldNotCreateRedisBeansWhenRedisDisabled() {
    contextRunner
            .withPropertyValues(
                    "firefly.cache.enabled=true",
                    "firefly.cache.redis.enabled=false",
                    "firefly.cache.redis.host=" + redisContainer.getHost(),
                    "firefly.cache.redis.port=" + redisContainer.getMappedPort(6379)
            )
            .run(context -> {
                assertThat(context).hasSingleBean(CacheProperties.class);
                assertThat(context).hasSingleBean(FireflyCacheManager.class);

                // Should not create Redis beans when disabled
                assertThat(context).doesNotHaveBean(ReactiveRedisConnectionFactory.class);
                assertThat(context).doesNotHaveBean(ReactiveRedisTemplate.class);
            });
}
```

## Detailed reference

### Configuration

Redis properties live under `firefly.cache.redis.*` and are bound by the core `CacheProperties.RedisConfig`. Provider selection (`firefly.cache.default-cache-type`) and the master switch (`firefly.cache.enabled`) belong to the core module. The block below shows the real keys with their defaults:

```yaml
firefly:
  cache:
    enabled: true                 # master switch for the cache abstraction (default: true)
    default-cache-type: REDIS     # active provider (default: CAFFEINE)
    redis:
      enabled: true               # Redis provider switch (default: true; provider still needs a host + Redis on classpath)
      cache-name: default         # logical cache name (default: default)
      host: localhost             # Redis host (default: localhost)
      port: 6379                  # Redis port (default: 6379)
      database: 0                 # Redis database index (default: 0)
      username:                   # ACL username (optional)
      password:                   # auth password (optional)
      key-prefix: firefly:cache   # key namespace prefix (default: firefly:cache)
      default-ttl:                # default TTL; unset = entries never expire automatically
      connection-timeout: PT10S   # connection timeout (default: 10s)
      command-timeout: PT5S       # command timeout (default: 5s)
      max-pool-size: 8            # max pooled connections (default: 8)
      min-pool-size: 0            # min pooled connections (default: 0)
      ssl: false                  # use TLS (rediss://) (default: false)
      enable-keyspace-notifications: false  # subscribe to key-expiry events (default: false)
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.cache.default-cache-type` | `CAFFEINE` | Core: active provider. Set to `REDIS` to select this adapter, or `AUTO` for availability-based selection. |
| `firefly.cache.redis.enabled` | `true` | Whether the Redis provider may activate. The provider also needs a configured `host` and Redis on the classpath. |
| `firefly.cache.redis.host` / `port` / `database` | `localhost` / `6379` / `0` | Redis connection coordinates. |
| `firefly.cache.redis.username` / `password` | _(unset)_ | Optional ACL username and auth password. |
| `firefly.cache.redis.key-prefix` | `firefly:cache` | Namespace prefix; effective key pattern is `<key-prefix>:<cacheName>:<key>`. Scopes `clear()`/`keys()`. |
| `firefly.cache.redis.default-ttl` | _(unset)_ | TTL applied to `put` without an explicit TTL. When unset, entries do not expire automatically. ISO-8601 duration (`PT30M`). |
| `firefly.cache.redis.connection-timeout` | `PT10S` | Lettuce connection timeout. |
| `firefly.cache.redis.command-timeout` | `PT5S` | Lettuce command timeout. |
| `firefly.cache.redis.max-pool-size` / `min-pool-size` | `8` / `0` | Connection-pool bounds. |
| `firefly.cache.redis.ssl` | `false` | Enable TLS (`rediss://`). |
| `firefly.cache.redis.enable-keyspace-notifications` | `false` | Enable Redis keyspace/expiry notifications. |

> Auto-configuration is conditional: `RedisCacheAutoConfiguration` only contributes the connection factory and template when `ReactiveRedisTemplate` is on the classpath, `firefly.cache.redis.enabled` is `true`, and a non-empty `firefly.cache.redis.host` is set. Both the `ReactiveRedisConnectionFactory` and `ReactiveRedisTemplate` beans are `@ConditionalOnMissingBean`. Define your own to override serialization, clustering, or connection behaviour.

## Continue the application

Use the [Cache guide](cache.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/cache-redis/src/main/java).
- [org.fireflyframework.cache.spi.CacheProviderFactory](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/resources/META-INF/services/org.fireflyframework.cache.spi.CacheProviderFactory).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
