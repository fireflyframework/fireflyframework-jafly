# Optional Dependencies

This document explains how optional dependencies work in the Firefly Common Cache Library and how to use the library with or without Redis.

## Overview

The Firefly Common Cache Library is designed with **optional dependencies** to give you flexibility in choosing which cache implementations to use:

- **Caffeine** (In-Memory Cache) - **Always Available** ✅
- **Redis** (Distributed Cache) - **Optional** ⚠️

## Redis is Optional

Redis dependencies are marked as `<optional>true</optional>` in the library's `pom.xml`. This means:

1. ✅ **You can use the library WITHOUT Redis** - Only Caffeine cache will be available
2. ✅ **No Redis classes are loaded** if Redis is not on your classpath
3. ✅ **No errors or warnings** if Redis dependencies are missing
4. ✅ **Smaller dependency footprint** if you only need in-memory caching

## How It Works

### Auto-Configuration Architecture

The library uses **separate auto-configuration classes** to ensure Redis is truly optional:

1. **`CacheAutoConfiguration`** - Core configuration (always loaded)
   - Configures Caffeine cache adapter
   - Sets up cache manager
   - Provides serialization support

2. **`RedisCacheAutoConfiguration`** - Redis-specific configuration (conditionally loaded)
   - Only loaded when Redis classes are on the classpath
   - Configures Redis connection factory
   - Sets up Redis cache adapter

This separation ensures that:
- Redis classes are never imported in the core configuration
- No `ClassNotFoundException` occurs when Redis is not available
- The library works seamlessly with or without Redis

## Usage Scenarios

### Scenario 1: Using Only Caffeine (No Redis)

**Maven Dependencies:**
```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Application Properties:**
```yaml
firefly:
  cache:
    default-cache-name: default
    default-cache-type: caffeine
    caffeine:
      default:
        maximum-size: 1000
        expire-after-write: PT1H
```

**Result:**
- ✅ Only Caffeine cache adapter is created
- ✅ No Redis dependencies required
- ✅ Smaller application footprint

### Scenario 2: Using Both Caffeine and Redis

**Maven Dependencies:**
```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Add Redis dependencies explicitly -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

**Application Properties:**
```yaml
firefly:
  cache:
    default-cache-name: default
    default-cache-type: auto  # or 'redis'
    caffeine:
      default:
        maximum-size: 1000
        expire-after-write: PT1H
    redis:
      default:
        enabled: true
        host: localhost
        port: 6379
        database: 0
```

**Result:**
- ✅ Both Caffeine and Redis cache adapters are created
- ✅ Auto-selection strategy chooses the best cache for each operation
- ✅ Full distributed caching capabilities

### Scenario 3: Disabling Redis Even When Available

If you have Redis dependencies on your classpath but want to disable Redis caching:

**Application Properties:**
```yaml
firefly:
  cache:
    redis:
      default:
        enabled: false  # Explicitly disable Redis
```

**Result:**
- ✅ Only Caffeine cache adapter is created
- ✅ Redis beans are not instantiated
- ✅ No Redis connections are made

## Conditional Bean Creation

The library uses Spring Boot's conditional annotations to ensure beans are only created when appropriate:

### Caffeine Cache Adapter
```java
@Bean
@ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
@ConditionalOnProperty(prefix = "firefly.cache.caffeine.default", 
                       name = "enabled", 
                       havingValue = "true", 
                       matchIfMissing = true)
public CacheAdapter caffeineCacheAdapter(CacheProperties properties)
```

**Created when:**
- ✅ Caffeine is on the classpath
- ✅ Not explicitly disabled via properties

### Redis Cache Adapter
```java
@Bean
@ConditionalOnProperty(prefix = "firefly.cache.redis.default", 
                       name = "enabled", 
                       havingValue = "true", 
                       matchIfMissing = true)
@ConditionalOnBean({ReactiveRedisTemplate.class, ReactiveRedisConnectionFactory.class})
public CacheAdapter redisCacheAdapter(...)
```

**Created when:**
- ✅ Redis classes are on the classpath
- ✅ Redis host is configured
- ✅ Not explicitly disabled via properties
- ✅ Redis connection factory and template beans exist

## Testing Without Redis

The library includes comprehensive tests to verify it works without Redis:

```java
@Test
void shouldStartWithoutRedis() {
    ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            CacheAutoConfiguration.class,
            RedisCacheAutoConfiguration.class
        ))
        .withClassLoader(new FilteredClassLoader(
            ReactiveRedisTemplate.class,
            ReactiveRedisConnectionFactory.class
        ));
    
    contextRunner.run(context -> {
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(FireflyCacheManager.class);
        assertThat(context).hasSingleBean(CacheAdapter.class);
    });
}
```

See `CacheAutoConfigurationWithoutRedisTest` for complete test suite.

## Migration Guide

### From Redis-Required to Redis-Optional

If you're migrating from a version where Redis was required:

1. **Remove explicit Redis dependencies** from your `pom.xml` if you don't need Redis
2. **Update application properties** to disable Redis if needed:
   ```yaml
   firefly:
     cache:
       redis:
         default:
           enabled: false
   ```
3. **Test your application** to ensure it works with Caffeine-only caching

### Adding Redis to Caffeine-Only Setup

If you want to add Redis to an existing Caffeine-only setup:

1. **Add Redis dependencies** to your `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-redis</artifactId>
   </dependency>
   ```

2. **Configure Redis** in your application properties:
   ```yaml
   firefly:
     cache:
       redis:
         default:
           enabled: true
           host: localhost
           port: 6379
   ```

3. **Restart your application** - Redis cache adapter will be auto-configured

## Troubleshooting

### Issue: "Cannot find Redis classes"

**Cause:** Redis dependencies are not on the classpath

**Solution:** This is expected if you're using Caffeine-only mode. No action needed.

### Issue: "Redis beans not created even with dependencies"

**Possible causes:**
1. Redis host not configured in properties
2. Redis explicitly disabled: `firefly.cache.redis.default.enabled=false`
3. Another `ReactiveRedisConnectionFactory` bean already exists

**Solution:** Check your application properties and bean configuration

### Issue: "Want to use Redis but getting Caffeine"

**Cause:** Default cache type is set to `caffeine` or `auto` is selecting Caffeine

**Solution:** Set the default cache type explicitly:
```yaml
firefly:
  cache:
    default-cache-type: redis
```

## Best Practices

1. **Start with Caffeine-only** for development and testing
2. **Add Redis** when you need distributed caching or persistence
3. **Use `auto` cache type** to let the library choose the best cache
4. **Monitor cache metrics** to understand which cache is being used
5. **Test both scenarios** (with and without Redis) in your CI/CD pipeline

## Summary

✅ **Redis is truly optional** - The library works perfectly without it  
✅ **No class loading issues** - Redis classes are never loaded if not available  
✅ **Flexible configuration** - Enable/disable Redis via properties  
✅ **Seamless integration** - Add Redis anytime without code changes  
✅ **Well-tested** - Comprehensive test suite verifies both scenarios  

For more information, see:
- [Configuration Guide](CONFIGURATION.md)
- [Quick Start Guide](QUICKSTART.md)
- [Architecture Overview](ARCHITECTURE.md)

