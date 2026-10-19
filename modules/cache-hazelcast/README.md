![Jafly. Cache - Hazelcast](../../docs/site/assets/modules/cache-hazelcast.svg)

# Cache - Hazelcast

Adapt a host-managed Hazelcast map to the common cache contract.

Provide a HazelcastInstance bean and select HAZELCAST in the core cache settings. The provider is discovered with ServiceLoader; this module does not configure your cluster topology or add its own Spring auto-configuration.

[Module guide](../../docs/site/modules/cache-hazelcast.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

If the onboarding deployment already supplies a Hazelcast instance, this provider can back the shared cache API. The host supplies the cluster member and its networking/security configuration.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`HazelcastProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-hazelcast/src/main/java/org/fireflyframework/cache/spi/providers/HazelcastProvider.java) | Defines `create` operations. |
| [`HazelcastCacheHelper`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cache-hazelcast/src/main/java/org/fireflyframework/cache/factory/HazelcastCacheHelper.java) | Reflection-based helper to create a Hazelcast-backed CacheAdapter without a compile-time dependency. |

## Continue the application

Use the [Cache guide](../../docs/site/modules/cache.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-cache-hazelcast -am verify
./mvnw -pl :fireflyframework-cache-hazelcast -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/cache-hazelcast.md).
