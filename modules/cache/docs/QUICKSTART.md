# Quick Start Guide

This guide will help you get started with the Firefly Common Cache Library in just a few minutes.

## Prerequisites

- Java 21 or higher
- Maven 3.6+ or Gradle 7+
- Spring Boot 3.5+

## Step 1: Add the Dependency

### Maven

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle

Add the following to your `build.gradle`:

```gradle
implementation 'org.fireflyframework:fireflyframework-cache:1.0.0-SNAPSHOT'
```

## Step 2: Enable Caching

Add the `@EnableCaching` annotation to your Spring Boot application:

```java
package com.example.myapp;

import org.fireflyframework.cache.annotation.EnableCaching;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCaching
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## Step 3: Configure Cache Properties

Create or update your `application.yml` with basic cache configuration:

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: CAFFEINE  # Start with in-memory cache
    default-cache-name: default
    
    # Caffeine (in-memory) configuration
    caffeine:
      default:
        enabled: true
        maximum-size: 1000
        expire-after-write: PT1H  # 1 hour
        record-stats: true
```

## Step 4: Use the Cache

### Option A: Programmatic API (Recommended)

Inject `FireflyCacheManager` and use it directly:

```java
package com.example.myapp.service;

import org.fireflyframework.cache.manager.FireflyCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final FireflyCacheManager cacheManager;
    private final UserRepository userRepository;
    
    public Mono<User> getUser(String userId) {
        // Try to get from cache first
        return cacheManager.get(userId, User.class)
            .flatMap(cachedUser -> {
                if (cachedUser.isPresent()) {
                    log.info("Cache hit for user: {}", userId);
                    return Mono.just(cachedUser.get());
                }
                
                // Cache miss - load from database
                log.info("Cache miss for user: {}", userId);
                return loadUserFromDatabase(userId)
                    .flatMap(user -> 
                        // Store in cache with 30 minute TTL
                        cacheManager.put(userId, user, Duration.ofMinutes(30))
                            .thenReturn(user)
                    );
            });
    }
    
    public Mono<User> updateUser(User user) {
        return userRepository.save(user)
            .flatMap(savedUser -> 
                // Evict old cache entry
                cacheManager.evict(savedUser.getId())
                    .thenReturn(savedUser)
            );
    }
    
    private Mono<User> loadUserFromDatabase(String userId) {
        return userRepository.findById(userId);
    }
}
```

### Option B: Declarative Annotations (Future)

> **Note**: Annotation support is defined but not yet fully implemented. Use the programmatic API for now.

```java
@Service
public class ProductService {
    
    @Cacheable(value = "products", key = "#productId", ttl = "PT2H")
    public Mono<Product> getProduct(String productId) {
        return productRepository.findById(productId);
    }
    
    @CacheEvict(value = "products", key = "#product.id")
    public Mono<Product> updateProduct(Product product) {
        return productRepository.save(product);
    }
}
```

## Step 5: Verify It's Working

### Check Application Logs

When your application starts, you should see logs like:

```
INFO  c.f.c.c.config.CacheAutoConfiguration - Firefly Cache Auto-Configuration - Starting initialization
INFO  c.f.c.c.config.CacheAutoConfiguration - Cache adapters will be auto-discovered: Caffeine, Redis
INFO  c.f.c.manager.FireflyCacheManager - Created Firefly Cache Manager with strategy: AutoCacheSelectionStrategy, default cache: default
INFO  c.f.c.manager.FireflyCacheManager - Registered cache 'default' of type CAFFEINE
```

### Test Cache Operations

Create a simple test:

```java
package com.example.myapp;

import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CacheQuickStartTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldCacheAndRetrieveValue() {
        String key = "test-key";
        String value = "test-value";
        
        // Put value in cache
        StepVerifier.create(cacheManager.put(key, value))
            .verifyComplete();
        
        // Retrieve value from cache
        StepVerifier.create(cacheManager.get(key, String.class))
            .assertNext(result -> {
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(value);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldRespectTTL() throws InterruptedException {
        String key = "ttl-key";
        String value = "ttl-value";
        Duration ttl = Duration.ofMillis(100);
        
        // Put with short TTL
        StepVerifier.create(cacheManager.put(key, value, ttl))
            .verifyComplete();
        
        // Should exist immediately
        StepVerifier.create(cacheManager.exists(key))
            .expectNext(true)
            .verifyComplete();
        
        // Wait for expiration
        Thread.sleep(150);
        
        // Should be gone
        StepVerifier.create(cacheManager.get(key, String.class))
            .assertNext(result -> assertThat(result).isEmpty())
            .verifyComplete();
    }
}
```

## Step 6: Monitor Cache Health

If you have Spring Boot Actuator enabled, you can check cache health:

```bash
curl http://localhost:8080/actuator/health/cache
```

Response:

```json
{
  "status": "UP",
  "details": {
    "totalCaches": 1,
    "healthyCaches": 1,
    "unhealthyCaches": 0,
    "caches": {
      "default": {
        "type": "caffeine",
        "status": "UP",
        "available": true,
        "configured": true,
        "responseTimeMs": 1
      }
    }
  }
}
```

## Next Steps

Now that you have the basics working, you can:

1. **[Configure Redis](CONFIGURATION.md#redis-configuration)** - Add distributed caching
2. **[Explore Advanced Features](EXAMPLES.md)** - Learn about advanced patterns
3. **[Set Up Monitoring](MONITORING.md)** - Configure metrics and dashboards
4. **[Understand the Architecture](ARCHITECTURE.md)** - Learn how it works under the hood

## Common Issues

### Cache Not Working

**Problem**: Cache operations don't seem to work.

**Solution**: 
- Verify `@EnableCaching` is present on your configuration class
- Check that `firefly.cache.enabled=true` in your properties
- Look for error messages in the logs

### Dependency Conflicts

**Problem**: Maven/Gradle dependency resolution errors.

**Solution**:
- Ensure you're using Spring Boot 3.5+
- Check that Java 21 is configured
- Run `mvn dependency:tree` or `gradle dependencies` to identify conflicts

### No Cache Adapter Available

**Problem**: Error message "No caches registered" or "No available cache found".

**Solution**:
- Verify Caffeine dependency is on the classpath (it should be transitive)
- Check cache configuration in `application.yml`
- Ensure `firefly.cache.caffeine.default.enabled=true`

## Getting Help

- Check the [Troubleshooting Guide](../README.md#-troubleshooting)
- Review the [Configuration Reference](CONFIGURATION.md)
- Look at [Examples](EXAMPLES.md) for common patterns

