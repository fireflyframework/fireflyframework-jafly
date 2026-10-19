![Jafly. Cache - JCache](../../docs/site/assets/modules/cache-jcache.svg)

# Cache - JCache

Adapt a host-managed JCache implementation to the common cache contract.

Provide the JCache CacheManager expected by JCacheProvider and select JCACHE through the core settings. The application owns the JSR-107 implementation and cache configuration.

[Module guide](../../docs/site/modules/cache-jcache.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Use this adapter when the onboarding host supplies a JCache CacheManager. The application chooses the JSR-107 implementation and creates/configures its caches.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache-jcache</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Provide a JSR-107 `CacheManager` bean. The adapter does not create one for you. It consumes the one your application exposes:

```java
@Configuration
public class JCacheConfig {

    @Bean
    public javax.cache.CacheManager jCacheManager() {
        // Resolves the JSR-107 provider on the classpath (e.g. Ehcache).
        return javax.cache.Caching.getCachingProvider().getCacheManager();
    }
}
```

> Using the Jakarta variant of a provider? Expose a `jakarta.cache.CacheManager` bean instead. The adapter handles both namespaces reflectively.

2. Select JCache as the cache backend:

```yaml
firefly:
  cache:
    default-cache-type: JCACHE
```

3. Use the Firefly cache API as usual. It is now served by your JCache provider:

```java

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final FireflyCacheManager cache; // from fireflyframework-cache



    public Mono<Product> findById(String id) {
        return cache.get("products", id, Product.class)
                .switchIfEmpty(loadFromDb(id)
                        .flatMap(p -> cache.put("products", id, p).thenReturn(p)));
    }
}
```

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`JCacheProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-jcache/src/main/java/org/fireflyframework/cache/spi/providers/JCacheProvider.java) | Defines `create` operations. |
| [`JCacheCacheHelper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-jcache/src/main/java/org/fireflyframework/cache/factory/JCacheCacheHelper.java) | Reflection-based helper to create a JCache-backed CacheAdapter (JSR-107) without a compile-time dependency. |

## Continue the application

Use the [Cache guide](../../docs/site/modules/cache.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache-jcache -am verify
./mvnw -pl :fireflyframework-cache-jcache -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/cache-jcache.md).
