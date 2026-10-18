# Auto-Configuration Guide

Complete guide to Spring Boot auto-configuration for the Firefly Common Cache Library.

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Configuration Properties](#configuration-properties)
- [Conditional Beans](#conditional-beans)
- [Customization](#customization)
- [Testing Auto-Configuration](#testing-auto-configuration)

## Overview

The Firefly Common Cache Library provides **automatic Spring Boot configuration** that activates when you add the library as a dependency. No manual configuration is required for basic usage.

### Key Features

- ✅ **Zero Configuration**: Works out of the box with sensible defaults
- ✅ **Property-Based**: Configure via `application.yml` or `application.properties`
- ✅ **Conditional**: Only creates beans when needed
- ✅ **Firefly Properties**: Uses `firefly.cache.*` properties (not `spring.cache.*`)
- ✅ **Customizable**: Override any bean or configuration

## How It Works

### Auto-Configuration File

The library uses Spring Boot 3.x auto-configuration mechanism:

**File**: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
org.fireflyframework.cache.config.CacheAutoConfiguration
```

### What Gets Auto-Configured

When you add the library as a dependency, Spring Boot automatically configures:

1. **CacheProperties** - Configuration properties from `firefly.cache.*`
2. **ObjectMapper** - JSON serialization (if not already present)
3. **CacheSerializer** - Cache value serialization
4. **CacheManagerFactory** - Factory to create multiple independent caches
5. **FireflyCacheManager (default)** - Primary default cache instance
6. **Redis infrastructure** - Connection factory and template (if Redis is configured)

### Activation Conditions

The auto-configuration activates when:

```yaml
firefly:
  cache:
    enabled: true  # Default: true (can be omitted)
```

To disable auto-configuration:

```yaml
firefly:
  cache:
    enabled: false
```

## Configuration Properties

### Minimal Configuration

No configuration needed! The library works with defaults:

```yaml
# No configuration required - uses defaults
```

### Basic Configuration

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: CAFFEINE
    default-cache-name: default
```

### Caffeine Configuration

```yaml
firefly:
  cache:
    caffeine:
      default:
        enabled: true  # Default: true
        maximum-size: 1000
        expire-after-write: PT1H
        record-stats: true
```

### Redis Configuration

Redis infrastructure is configured when you provide a host (no `.default` nesting):

```yaml
firefly:
  cache:
    redis:
      enabled: true  # Default: true
      host: localhost
      port: 6379
      database: 0
      password: ${REDIS_PASSWORD}  # Optional
```

## Conditional Beans

### Caffeine Cache Adapter

**Created when**:
- Caffeine library is on classpath
- `firefly.cache.caffeine.default.enabled=true` (default)

**Not created when**:
- Caffeine library is missing
- `firefly.cache.caffeine.default.enabled=false`

### Redis Connection Factory

**Created when**:
- Redis libraries are on classpath
- No existing `ReactiveRedisConnectionFactory` bean
- `firefly.cache.redis.default.host` is configured

**Not created when**:
- Redis libraries are missing
- Another `ReactiveRedisConnectionFactory` bean exists
- No host is configured

### Redis Cache Infrastructure

The library only auto-configures Redis infrastructure (connection factory and template). Actual caches are created on-demand via `CacheManagerFactory`.

**Infrastructure created when**:
- Redis libraries are on classpath
- `firefly.cache.redis.enabled=true` (default)
- `firefly.cache.redis.host` is configured

**Not created when**:
- Redis libraries are missing
- `firefly.cache.redis.enabled=false`
- No host is configured

## Customization

### Override Default ObjectMapper

Provide your own ObjectMapper bean:

```java
@Configuration
public class CustomCacheConfig {
    
    @Bean("cacheObjectMapper")
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}
```

### Override Cache Selection Strategy

Provide your own strategy:

```java
@Configuration
public class CustomCacheConfig {
    
    @Bean
    public CacheSelectionStrategy cacheSelectionStrategy() {
        return new CustomCacheSelectionStrategy();
    }
}
```

### Provide Custom Redis Connection

If you already have a Redis connection configured:

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public ReactiveRedisConnectionFactory redisConnectionFactory() {
        // Your custom Redis configuration
        return new LettuceConnectionFactory("my-redis-host", 6379);
    }
}
```

The auto-configuration will detect this bean and use it instead of creating its own.

### Add Additional Cache Adapters

Register additional caches programmatically:

```java
@Configuration
public class AdditionalCachesConfig {
    
    @Bean
    public ApplicationRunner registerAdditionalCaches(FireflyCacheManager cacheManager) {
        return args -> {
            // Create and register a custom cache
            CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .maximumSize(500L)
                .expireAfterWrite(Duration.ofMinutes(30))
                .build();
            
            CaffeineCacheAdapter adapter = new CaffeineCacheAdapter("custom-cache", config);
            cacheManager.registerCache("custom-cache", adapter);
        };
    }
}
```

## Testing Auto-Configuration

### Unit Testing with ApplicationContextRunner

```java
@Test
void shouldAutoConfigureWithDefaults() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
        .run(context -> {
            assertThat(context).hasSingleBean(FireflyCacheManager.class);
            assertThat(context).hasSingleBean(CacheProperties.class);
        });
}
```

### Integration Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.cache.enabled=true",
    "firefly.cache.default-cache-type=CAFFEINE"
})
class CacheIntegrationTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldAutoWireCacheManager() {
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.hasCache("default")).isTrue();
    }
}
```

### Testing with Disabled Auto-Configuration

```java
@SpringBootTest(properties = "firefly.cache.enabled=false")
class DisabledCacheTest {
    
    @Autowired(required = false)
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        assertThat(cacheManager).isNull();
    }
}
```

## Examples

### Example 1: Default Configuration

Just add the dependency - no configuration needed:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

The library auto-configures with:
- Caffeine in-memory cache
- Default cache name: "default"
- Maximum size: 1000 entries
- TTL: 1 hour

### Example 2: Custom Caffeine Settings

```yaml
firefly:
  cache:
    caffeine:
      default:
        maximum-size: 5000
        expire-after-write: PT2H
        expire-after-access: PT30M
        record-stats: true
```

### Example 3: Redis Configuration

```yaml
firefly:
  cache:
    default-cache-type: REDIS
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: 0
      password: ${REDIS_PASSWORD}
      key-prefix: "myapp:cache"
      default-ttl: PT1H
```

### Example 4: Multiple Caches

```yaml
firefly:
  cache:
    caffeine:
      default:
        maximum-size: 1000
        expire-after-write: PT1H
      
      user-cache:
        maximum-size: 5000
        expire-after-write: PT30M
      
      session-cache:
        maximum-size: 10000
        expire-after-write: PT15M
```

## Troubleshooting

### Cache Not Auto-Configuring

**Problem**: FireflyCacheManager bean not found

**Solutions**:
1. Check that `firefly.cache.enabled` is not set to `false`
2. Verify the library is on the classpath
3. Check for conflicting auto-configurations
4. Enable debug logging: `logging.level.org.fireflyframework.cache=DEBUG`

### Redis Not Connecting

**Problem**: Redis cache adapter not created

**Solutions**:
1. Verify `firefly.cache.redis.default.host` is configured
2. Check Redis is running and accessible
3. Verify Redis dependencies are on classpath
4. Check for connection errors in logs

### Multiple Cache Adapters with Same Name

**Problem**: Warning about duplicate cache names

**Solution**: Ensure each cache adapter has a unique name:

```yaml
firefly:
  cache:
    caffeine:
      cache1:  # Unique name
        maximum-size: 1000
      cache2:  # Different name
        maximum-size: 500
```

## Best Practices

1. **Use Property Files**: Configure via `application.yml` instead of Java code
2. **Environment-Specific**: Use Spring profiles for different environments
3. **Externalize Secrets**: Use environment variables for passwords
4. **Monitor Auto-Configuration**: Enable debug logging during development
5. **Test Configuration**: Write tests to verify auto-configuration works as expected

## See Also

- [Configuration Reference](CONFIGURATION.md) - Complete property reference
- [Quick Start Guide](QUICKSTART.md) - Getting started tutorial
- [Architecture Guide](ARCHITECTURE.md) - Understanding the design

