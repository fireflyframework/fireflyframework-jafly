![Jafly. Common Cache Library](../../docs/site/assets/modules/cache.svg)

# Common Cache Library

Use a common cache API with a local Caffeine default and pluggable distributed providers.

Depend on the core abstraction, inject FireflyCacheManager or use CacheAdapter, and choose a provider through firefly.cache. Provider discovery and selection live in the core; add only the external provider library you need.

[Module guide](../../docs/site/modules/cache.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A reviewer portal may read the same application status repeatedly. Cache those reads only after defining when status changes invalidate them; the tutorial query deliberately bypasses caching.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

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

## Configuration

The `firefly.cache` namespace is bound by [CacheProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache/src/main/java/org/fireflyframework/cache/properties/CacheProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.cache.enabled` | `boolean` | `true` |
| `firefly.cache.default-cache-type` | `CacheType` | `CacheType.CAFFEINE` |
| `firefly.cache.metrics-enabled` | `boolean` | `true` |
| `firefly.cache.health-enabled` | `boolean` | `true` |
| `firefly.cache.stats-enabled` | `boolean` | `true` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`FireflyCacheManager`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache/src/main/java/org/fireflyframework/cache/manager/FireflyCacheManager.java) | Unified cache manager that provides a single cache interface. |
| [`CacheAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache/src/main/java/org/fireflyframework/cache/core/CacheAdapter.java) | Unified interface for cache operations across different cache providers. |
| [`CacheProviderFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache/src/main/java/org/fireflyframework/cache/spi/CacheProviderFactory.java) | Service Provider Interface for creating cache adapters. |
| [`CacheProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache/src/main/java/org/fireflyframework/cache/properties/CacheProperties.java) | Configuration properties for the Cache library. |
| [`CacheSelectionStrategy`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache/src/main/java/org/fireflyframework/cache/manager/CacheSelectionStrategy.java) | Strategy interface for selecting the best cache from available options. |

## Continue the application

Use the [CQRS guide](../../docs/site/modules/cqrs.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache -am verify
./mvnw -pl :fireflyframework-cache -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/cache.md).
