# API Reference

Complete API reference for the Firefly Common Cache Library.

## Table of Contents

- [FireflyCacheManager](#fireflycachemanager)
- [CacheAdapter](#cacheadapter)
- [Annotations](#annotations)
- [Configuration Classes](#configuration-classes)
- [Health and Statistics](#health-and-statistics)
- [Exceptions](#exceptions)

## FireflyCacheManager

**Package**: `org.fireflyframework.cache.manager.FireflyCacheManager`

The main entry point for cache operations. Manages multiple cache instances and provides a unified API.

### Constructor

```java
public FireflyCacheManager()
public FireflyCacheManager(CacheSelectionStrategy selectionStrategy, String defaultCacheName)
```

### Cache Operations (Default Cache)

#### get

Retrieve a value from the default cache.

```java
public <K, V> Mono<Optional<V>> get(K key)
public <K, V> Mono<Optional<V>> get(K key, Class<V> valueType)
```

**Parameters**:
- `key`: The cache key
- `valueType`: Expected value type (for type-safe retrieval)

**Returns**: `Mono<Optional<V>>` containing the value if present

**Example**:
```java
cacheManager.get("user:123", User.class)
    .subscribe(optionalUser -> {
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            // Use user
        }
    });
```

#### put

Store a value in the default cache.

```java
public <K, V> Mono<Void> put(K key, V value)
public <K, V> Mono<Void> put(K key, V value, Duration ttl)
```

**Parameters**:
- `key`: The cache key
- `value`: The value to store
- `ttl`: Time-to-live (optional)

**Returns**: `Mono<Void>` that completes when stored

**Example**:
```java
User user = new User("123", "John");
cacheManager.put("user:123", user, Duration.ofMinutes(30))
    .subscribe();
```

#### putIfAbsent

Store a value only if the key doesn't exist.

```java
public <K, V> Mono<Boolean> putIfAbsent(K key, V value)
public <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl)
```

**Parameters**:
- `key`: The cache key
- `value`: The value to store
- `ttl`: Time-to-live (optional)

**Returns**: `Mono<Boolean>` - true if stored, false if key existed

**Example**:
```java
cacheManager.putIfAbsent("lock:resource", "locked", Duration.ofSeconds(30))
    .subscribe(wasStored -> {
        if (wasStored) {
            // Lock acquired
        } else {
            // Lock already held
        }
    });
```

#### evict

Remove a value from the default cache.

```java
public <K> Mono<Boolean> evict(K key)
```

**Parameters**:
- `key`: The cache key to remove

**Returns**: `Mono<Boolean>` - true if removed, false if not found

**Example**:
```java
cacheManager.evict("user:123")
    .subscribe(removed -> {
        if (removed) {
            log.info("User cache entry removed");
        }
    });
```

#### clear

Remove all entries from the default cache.

```java
public Mono<Void> clear()
```

**Returns**: `Mono<Void>` that completes when cleared

**Example**:
```java
cacheManager.clear()
    .subscribe(() -> log.info("Cache cleared"));
```

#### exists

Check if a key exists in the default cache.

```java
public <K> Mono<Boolean> exists(K key)
```

**Parameters**:
- `key`: The cache key to check

**Returns**: `Mono<Boolean>` - true if exists

**Example**:
```java
cacheManager.exists("user:123")
    .subscribe(exists -> {
        if (exists) {
            log.info("User is cached");
        }
    });
```

### Cache Operations (Named Cache)

All operations have named cache variants:

```java
public <K, V> Mono<Optional<V>> get(String cacheName, K key)
public <K, V> Mono<Void> put(String cacheName, K key, V value)
public <K> Mono<Boolean> evict(String cacheName, K key)
public Mono<Void> clear(String cacheName)
```

**Example**:
```java
cacheManager.put("user-cache", "123", user)
    .subscribe();
```

### Cache Management

#### registerCache

Register a cache adapter with the manager.

```java
public void registerCache(String name, CacheAdapter adapter)
```

**Parameters**:
- `name`: Cache name
- `adapter`: Cache adapter instance

**Example**:
```java
CaffeineCacheAdapter adapter = new CaffeineCacheAdapter("my-cache", config);
cacheManager.registerCache("my-cache", adapter);
```

#### unregisterCache

Unregister and close a cache adapter.

```java
public CacheAdapter unregisterCache(String name)
```

**Parameters**:
- `name`: Cache name to unregister

**Returns**: The removed adapter, or null if not found

#### getCache

Get a cache adapter by name.

```java
public CacheAdapter getCache(String name)
```

**Parameters**:
- `name`: Cache name

**Returns**: The cache adapter, or null if not found

#### getCacheNames

Get all registered cache names.

```java
public Set<String> getCacheNames()
```

**Returns**: Set of cache names

#### hasCache

Check if a cache is registered.

```java
public boolean hasCache(String name)
```

**Parameters**:
- `name`: Cache name

**Returns**: true if registered

### Health and Statistics

#### getHealth

Get health information for all or specific cache.

```java
public Flux<CacheHealth> getHealth()
public Mono<CacheHealth> getHealth(String cacheName)
```

**Returns**: Health information

**Example**:
```java
cacheManager.getHealth()
    .subscribe(health -> {
        log.info("Cache: {}, Status: {}", 
            health.getCacheName(), 
            health.getStatus());
    });
```

#### getStats

Get statistics for all or specific cache.

```java
public Flux<CacheStats> getStats()
public Mono<CacheStats> getStats(String cacheName)
```

**Returns**: Cache statistics

**Example**:
```java
cacheManager.getStats("default")
    .subscribe(stats -> {
        log.info("Hit rate: {}%", stats.getHitRate() * 100);
        log.info("Hits: {}, Misses: {}", 
            stats.getHitCount(), 
            stats.getMissCount());
    });
```

### Lifecycle

#### close

Close the cache manager and all registered caches.

```java
public void close()
```

**Example**:
```java
@PreDestroy
public void cleanup() {
    cacheManager.close();
}
```

## CacheAdapter

**Package**: `org.fireflyframework.cache.core.CacheAdapter`

Interface defining cache operations. Implemented by cache providers.

### Core Operations

```java
// Retrieve
<K, V> Mono<Optional<V>> get(K key)
<K, V> Mono<Optional<V>> get(K key, Class<V> valueType)

// Store
<K, V> Mono<Void> put(K key, V value)
<K, V> Mono<Void> put(K key, V value, Duration ttl)

// Conditional store
<K, V> Mono<Boolean> putIfAbsent(K key, V value)
<K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl)

// Remove
<K> Mono<Boolean> evict(K key)
Mono<Void> clear()

// Query
<K> Mono<Boolean> exists(K key)
<K> Mono<Set<K>> keys()
Mono<Long> size()

// Monitoring
Mono<CacheStats> getStats()
Mono<CacheHealth> getHealth()

// Metadata
CacheType getCacheType()
String getCacheName()
boolean isAvailable()

// Lifecycle
void close()
```

### Implementations

- **CaffeineCacheAdapter**: In-memory cache using Caffeine
- **RedisCacheAdapter**: Distributed cache using Redis

## Annotations

**Package**: `org.fireflyframework.cache.annotation`

> **Note**: Annotations are defined but aspect implementation is not yet complete. Use programmatic API for production.

### @EnableCaching

Enable cache annotation processing.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableCaching {
    boolean proxyTargetClass() default false;
    AdviceMode mode() default AdviceMode.PROXY;
    int order() default Integer.MAX_VALUE;
}
```

**Usage**:
```java
@Configuration
@EnableCaching
public class CacheConfig {
}
```

### @Cacheable

Mark methods whose results should be cached.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cacheable {
    String[] value() default {"default"};
    String[] cacheNames() default {};
    String key() default "";
    String keyGenerator() default "";
    String condition() default "";
    String unless() default "";
    String ttl() default "";
    boolean sync() default false;
}
```

**Attributes**:
- `value`/`cacheNames`: Cache names to use
- `key`: SpEL expression for cache key
- `condition`: SpEL condition for caching
- `unless`: SpEL condition to prevent caching
- `ttl`: Time-to-live (ISO-8601 format)
- `sync`: Synchronize concurrent calls

**Example**:
```java
@Cacheable(value = "users", key = "#userId", ttl = "PT30M")
public Mono<User> getUser(String userId) {
    return userRepository.findById(userId);
}
```

### @CacheEvict

Mark methods that should trigger cache eviction.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvict {
    String[] value() default {"default"};
    String[] cacheNames() default {};
    String key() default "";
    String condition() default "";
    boolean allEntries() default false;
    boolean beforeInvocation() default false;
}
```

**Attributes**:
- `value`/`cacheNames`: Cache names
- `key`: SpEL expression for cache key
- `condition`: SpEL condition for eviction
- `allEntries`: Evict all entries
- `beforeInvocation`: Evict before method execution

**Example**:
```java
@CacheEvict(value = "users", key = "#user.id")
public Mono<User> updateUser(User user) {
    return userRepository.save(user);
}
```

### @CachePut

Mark methods that should always update the cache.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CachePut {
    String[] value() default {"default"};
    String[] cacheNames() default {};
    String key() default "";
    String keyGenerator() default "";
    String condition() default "";
    String unless() default "";
    String ttl() default "";
}
```

**Example**:
```java
@CachePut(value = "users", key = "#result.id")
public Mono<User> createUser(User user) {
    return userRepository.save(user);
}
```

### @Caching

Group multiple cache operations.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Caching {
    Cacheable[] cacheable() default {};
    CachePut[] put() default {};
    CacheEvict[] evict() default {};
}
```

**Example**:
```java
@Caching(
    evict = {
        @CacheEvict(value = "users", key = "#user.id"),
        @CacheEvict(value = "userList", allEntries = true)
    }
)
public Mono<Void> deleteUser(User user) {
    return userRepository.delete(user);
}
```

## Configuration Classes

### CacheProperties

**Package**: `org.fireflyframework.cache.properties.CacheProperties`

Main configuration properties class.

```java
@ConfigurationProperties(prefix = "firefly.cache")
public class CacheProperties {
    private boolean enabled = true;
    private CacheType defaultCacheType = CacheType.CAFFEINE;
    private String defaultCacheName = "default";
    private boolean metricsEnabled = true;
    private boolean healthEnabled = true;
    private Map<String, CacheConfig> caches;
    private Map<String, CaffeineConfig> caffeine;
    private Map<String, RedisConfig> redis;
}
```

### CaffeineCacheConfig

**Package**: `org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig`

Caffeine cache configuration.

```java
@Builder
public class CaffeineCacheConfig {
    private final Long maximumSize;
    private final Duration expireAfterWrite;
    private final Duration expireAfterAccess;
    private final Duration refreshAfterWrite;
    private final boolean recordStats;
    private final boolean weakKeys;
    private final boolean weakValues;
    private final boolean softValues;
}
```

### RedisCacheConfig

**Package**: `org.fireflyframework.cache.adapter.redis.RedisCacheConfig`

Redis cache configuration.

```java
@Builder
public class RedisCacheConfig {
    private final String host;
    private final int port;
    private final int database;
    private final String password;
    private final String username;
    private final Duration connectionTimeout;
    private final Duration commandTimeout;
    private final String keyPrefix;
    private final Duration defaultTtl;
    private final int maxPoolSize;
    private final int minPoolSize;
    private final boolean ssl;
}
```

## Health and Statistics

### CacheHealth

**Package**: `org.fireflyframework.cache.core.CacheHealth`

Cache health information.

```java
public class CacheHealth {
    private final CacheType cacheType;
    private final String cacheName;
    private final HealthStatus status;
    private final boolean available;
    private final boolean configured;
    private final long responseTimeMs;
    private final String message;
    private final Throwable error;
}
```

### CacheStats

**Package**: `org.fireflyframework.cache.core.CacheStats`

Cache statistics.

```java
public class CacheStats {
    private final CacheType cacheType;
    private final String cacheName;
    private final long hitCount;
    private final long missCount;
    private final long loadSuccessCount;
    private final long loadFailureCount;
    private final long evictionCount;
    private final long size;
    
    public double getHitRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }
}
```

## Exceptions

### CacheException

**Package**: `org.fireflyframework.cache.exception.CacheException`

Base exception for cache operations.

```java
public class CacheException extends RuntimeException {
    public CacheException(String message)
    public CacheException(String message, Throwable cause)
}
```

### SerializationException

**Package**: `org.fireflyframework.cache.serialization.SerializationException`

Exception for serialization errors.

```java
public class SerializationException extends CacheException {
    public SerializationException(String message, Throwable cause)
}
```

