![Jafly. Cache - Hazelcast](../assets/modules/cache-hazelcast.svg)

# Cache - Hazelcast

Adapt a host-managed Hazelcast map to the common cache contract.

Provide a HazelcastInstance bean and select HAZELCAST in the core cache settings. The provider is discovered with ServiceLoader; this module does not configure your cluster topology or add its own Spring auto-configuration.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-cache-hazelcast` | `jar` | [modules/cache-hazelcast](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/cache-hazelcast) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

If the onboarding deployment already supplies a Hazelcast instance, this provider can back the shared cache API. The host supplies the cluster member and its networking/security configuration.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache-hazelcast</artifactId>
    <version>26.10.20</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

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
Hazelcast. Your application code keeps using the standard Firefly cache API. Nothing else changes.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`HazelcastProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-hazelcast/src/main/java/org/fireflyframework/cache/spi/providers/HazelcastProvider.java) | Defines `create` operations. |
| [`HazelcastCacheHelper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-hazelcast/src/main/java/org/fireflyframework/cache/factory/HazelcastCacheHelper.java) | Reflection-based helper to create a Hazelcast-backed CacheAdapter without a compile-time dependency. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache-hazelcast -am verify
./mvnw -pl :fireflyframework-cache-hazelcast -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [HazelcastProviderWiringTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-hazelcast/src/test/java/org/fireflyframework/cache/spi/providers/HazelcastProviderWiringTest.java).

## Test fixture

The `hazelcastProviderIsDiscoveredViaServiceLoader` test demonstrates one concrete behavior. Excerpt from [HazelcastProviderWiringTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-hazelcast/src/test/java/org/fireflyframework/cache/spi/providers/HazelcastProviderWiringTest.java). The test class supplies its fixtures and imports.

```java
@Test
void hazelcastProviderIsDiscoveredViaServiceLoader() {
    ServiceLoader<CacheProviderFactory> loader = ServiceLoader.load(CacheProviderFactory.class);

    var factories = StreamSupport.stream(loader.spliterator(), false)
            .collect(Collectors.toList());

    assertThat(factories)
            .as("ServiceLoader should discover at least one CacheProviderFactory")
            .isNotEmpty();

    assertThat(factories)
            .as("a Hazelcast provider with getType()==HAZELCAST must be present")
            .anySatisfy(factory ->
                    assertThat(factory.getType()).isEqualTo(CacheType.HAZELCAST));

    CacheProviderFactory hazelcast = factories.stream()
            .filter(f -> f.getType() == CacheType.HAZELCAST)
            .findFirst()
            .orElseThrow();

    assertThat(hazelcast).isInstanceOf(HazelcastProvider.class);
}
```

## Detailed reference

### How It Works

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

### Configuration

This adapter has no `@ConfigurationProperties` of its own. It is selected and tuned entirely through
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

## Continue the application

Use the [Cache guide](cache.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-hazelcast/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/cache-hazelcast/src/main/java).
- [org.fireflyframework.cache.spi.CacheProviderFactory](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-hazelcast/src/main/resources/META-INF/services/org.fireflyframework.cache.spi.CacheProviderFactory).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
