![Jafly. Cache - Redis](../../docs/site/assets/modules/cache-redis.svg)

# Cache - Redis

Back the shared cache abstraction with Redis and reactive serialization.

Add this provider beside cache, select REDIS through the core cache settings, and supply reachable Redis connection settings. Test serialization and expiry with the integration fixtures before using shared entries across services.

[Module guide](../../docs/site/modules/cache-redis.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Use a Redis-backed cache when several onboarding-service instances need the same cached application data. Define expiry and status-change invalidation in the application.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache-redis</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#installation)).

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
import org.fireflyframework.cache.core.CacheAdapter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Service
public class ProductService {

    private final CacheAdapter cache;

    public ProductService(CacheAdapter cache) {
        this.cache = cache;
    }

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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`RedisCacheAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/adapter/redis/RedisCacheAdapter.java) | Redis-based cache adapter implementation. |
| [`RedisProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/spi/providers/RedisProvider.java) | Defines `create` operations. |
| [`RedisCacheConfig`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/adapter/redis/RedisCacheConfig.java) | Configuration class for Redis cache adapter. |
| [`RedisCacheAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/config/RedisCacheAutoConfiguration.java) | Auto-configuration for Redis infrastructure beans used by fireflyframework-cache. |
| [`RedisCacheHelper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-redis/src/main/java/org/fireflyframework/cache/factory/RedisCacheHelper.java) | Helper class for creating Redis cache adapters. |

## Continue the application

Use the [Cache guide](../../docs/site/modules/cache.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache-redis -am verify
./mvnw -pl :fireflyframework-cache-redis -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/cache-redis.md).
