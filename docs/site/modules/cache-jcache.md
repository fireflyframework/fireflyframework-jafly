![Jafly. Cache - JCache](../assets/modules/cache-jcache.svg)

# Cache - JCache

Adapt a host-managed JCache implementation to the common cache contract.

Provide the JCache CacheManager expected by JCacheProvider and select JCACHE through the core settings. The application owns the JSR-107 implementation and cache configuration.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-cache-jcache` | `jar` | [modules/cache-jcache](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/cache-jcache) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

Use this adapter when the onboarding host supplies a JCache CacheManager. The application chooses the JSR-107 implementation and creates/configures its caches.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache-jcache</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`JCacheProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-jcache/src/main/java/org/fireflyframework/cache/spi/providers/JCacheProvider.java) | Defines `create` operations. |
| [`JCacheCacheHelper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-jcache/src/main/java/org/fireflyframework/cache/factory/JCacheCacheHelper.java) | Reflection-based helper to create a JCache-backed CacheAdapter (JSR-107) without a compile-time dependency. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache-jcache -am verify
./mvnw -pl :fireflyframework-cache-jcache -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [JCacheProviderWiringTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-jcache/src/test/java/org/fireflyframework/cache/spi/providers/JCacheProviderWiringTest.java).

## Test fixture

The `serviceLoaderDiscoversJCacheProvider` test demonstrates one concrete behavior. Excerpt from [JCacheProviderWiringTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-jcache/src/test/java/org/fireflyframework/cache/spi/providers/JCacheProviderWiringTest.java). The test class supplies its fixtures and imports.

```java
@Test
void serviceLoaderDiscoversJCacheProvider() {
    ServiceLoader<CacheProviderFactory> loader = ServiceLoader.load(CacheProviderFactory.class);

    CacheProviderFactory jcache = loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(f -> f.getType() == CacheType.JCACHE)
            .findFirst()
            .orElse(null);

    assertThat(jcache)
            .as("ServiceLoader must discover a CacheProviderFactory with getType() == JCACHE")
            .isNotNull();
    assertThat(jcache).isInstanceOf(JCacheProvider.class);
    assertThat(jcache.getType()).isEqualTo(CacheType.JCACHE);
    assertThat(jcache.priority()).isEqualTo(30);
}
```

## Detailed reference

### Configuration

This adapter has no `@ConfigurationProperties` of its own. It is activated through the Firefly cache core's properties (`firefly.cache.*`). The only key strictly required to engage this adapter is the backend selector:

```yaml
firefly:
  cache:
    default-cache-type: JCACHE   # route Firefly caching through the JCache adapter
```

| Property | Description | Default |
|----------|-------------|---------|
| `firefly.cache.default-cache-type` | Selects the active cache provider. Set to `JCACHE` to use this adapter. | `CAFFEINE` |

All other cache tuning (default TTL, per-cache settings, statistics, health) is governed by `fireflyframework-cache` and applies uniformly across providers. Backend-specific tuning (heap/off-heap tiers, disk persistence, clustering) is configured on your JSR-107 provider itself (e.g. Via Ehcache XML or programmatic `CacheManager` setup). See the `fireflyframework-cache` README for the full list of `firefly.cache.*` properties.

### How It Works

1. At startup, the Firefly cache core scans the classpath via `ServiceLoader` for `CacheProviderFactory` implementations registered under `META-INF/services/`.
2. This module registers `JCacheProvider`, which advertises `CacheType.JCACHE` and priority `30`.
3. When `firefly.cache.default-cache-type=JCACHE`, the core invokes the provider with a context carrying your `CacheManager` bean.
4. `JCacheProvider` wraps the manager in `JCacheCacheHelper` (handling `javax`/`jakarta` reflectively) and returns a reactive `CacheAdapter` with a portable TTL overlay layered on top of the JCache store.

## Continue the application

Use the [Cache guide](cache.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-jcache/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/cache-jcache/src/main/java).
- [org.fireflyframework.cache.spi.CacheProviderFactory](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-jcache/src/main/resources/META-INF/services/org.fireflyframework.cache.spi.CacheProviderFactory).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
