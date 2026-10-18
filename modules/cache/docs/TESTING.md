# Testing Guide

Complete guide to testing applications that use the Firefly Common Cache Library.

## Table of Contents

- [Overview](#overview)
- [Unit Testing](#unit-testing)
- [Integration Testing](#integration-testing)
- [Testing with Caffeine](#testing-with-caffeine)
- [Testing with Redis](#testing-with-redis)
- [Mocking Strategies](#mocking-strategies)
- [Test Configuration](#test-configuration)
- [Best Practices](#best-practices)

## Overview

The Firefly Common Cache Library is designed to be testable. This guide covers:

- Unit testing with mocked caches
- Integration testing with real cache implementations
- Using TestContainers for Redis tests
- Test configuration strategies

## Unit Testing

### Testing with Mocked FireflyCacheManager

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private FireflyCacheManager cacheManager;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void shouldReturnCachedUser() {
        // Given
        String userId = "123";
        User expectedUser = new User(userId, "John");
        
        when(cacheManager.get(eq(userId), eq(User.class)))
            .thenReturn(Mono.just(Optional.of(expectedUser)));
        
        // When
        StepVerifier.create(userService.getUser(userId))
            // Then
            .expectNext(expectedUser)
            .verifyComplete();
        
        verify(userRepository, never()).findById(anyString());
    }
    
    @Test
    void shouldLoadFromDatabaseOnCacheMiss() {
        // Given
        String userId = "123";
        User expectedUser = new User(userId, "John");
        
        when(cacheManager.get(eq(userId), eq(User.class)))
            .thenReturn(Mono.just(Optional.empty()));
        when(userRepository.findById(userId))
            .thenReturn(Mono.just(expectedUser));
        when(cacheManager.put(eq(userId), eq(expectedUser), any(Duration.class)))
            .thenReturn(Mono.empty());
        
        // When
        StepVerifier.create(userService.getUser(userId))
            // Then
            .expectNext(expectedUser)
            .verifyComplete();
        
        verify(userRepository).findById(userId);
        verify(cacheManager).put(eq(userId), eq(expectedUser), any(Duration.class));
    }
    
    @Test
    void shouldEvictCacheOnUpdate() {
        // Given
        User user = new User("123", "John");
        
        when(userRepository.save(user))
            .thenReturn(Mono.just(user));
        when(cacheManager.evict(user.getId()))
            .thenReturn(Mono.just(true));
        
        // When
        StepVerifier.create(userService.updateUser(user))
            // Then
            .expectNext(user)
            .verifyComplete();
        
        verify(cacheManager).evict(user.getId());
    }
}
```

### Testing Cache Operations

```java
@ExtendWith(MockitoExtension.class)
class CacheOperationsTest {
    
    @Mock
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldPutAndGetValue() {
        // Given
        String key = "test-key";
        String value = "test-value";
        
        when(cacheManager.put(key, value))
            .thenReturn(Mono.empty());
        when(cacheManager.get(key, String.class))
            .thenReturn(Mono.just(Optional.of(value)));
        
        // When & Then
        StepVerifier.create(cacheManager.put(key, value))
            .verifyComplete();
        
        StepVerifier.create(cacheManager.get(key, String.class))
            .assertNext(result -> {
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(value);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldHandleCacheErrors() {
        // Given
        String key = "error-key";
        
        when(cacheManager.get(key, String.class))
            .thenReturn(Mono.error(new CacheException("Cache unavailable")));
        
        // When & Then
        StepVerifier.create(cacheManager.get(key, String.class))
            .expectError(CacheException.class)
            .verify();
    }
}
```

## Integration Testing

### Spring Boot Test with Caffeine

```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.cache.enabled=true",
    "firefly.cache.default-cache-type=CAFFEINE",
    "firefly.cache.caffeine.default.maximum-size=100",
    "firefly.cache.caffeine.default.expire-after-write=PT1M"
})
class CacheIntegrationTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldCacheAndRetrieveValues() {
        // Given
        String key = "integration-test-key";
        String value = "integration-test-value";
        
        // When & Then
        StepVerifier.create(cacheManager.put(key, value))
            .verifyComplete();
        
        StepVerifier.create(cacheManager.get(key, String.class))
            .assertNext(result -> {
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(value);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldRespectTTL() throws InterruptedException {
        // Given
        String key = "ttl-test-key";
        String value = "ttl-test-value";
        Duration ttl = Duration.ofMillis(100);
        
        // When
        StepVerifier.create(cacheManager.put(key, value, ttl))
            .verifyComplete();
        
        // Then - should exist immediately
        StepVerifier.create(cacheManager.exists(key))
            .expectNext(true)
            .verifyComplete();
        
        // Wait for expiration
        Thread.sleep(150);
        
        // Should be expired
        StepVerifier.create(cacheManager.get(key, String.class))
            .assertNext(result -> assertThat(result).isEmpty())
            .verifyComplete();
    }
    
    @Test
    void shouldEvictEntries() {
        // Given
        String key = "evict-test-key";
        String value = "evict-test-value";
        
        StepVerifier.create(cacheManager.put(key, value))
            .verifyComplete();
        
        // When
        StepVerifier.create(cacheManager.evict(key))
            .expectNext(true)
            .verifyComplete();
        
        // Then
        StepVerifier.create(cacheManager.get(key, String.class))
            .assertNext(result -> assertThat(result).isEmpty())
            .verifyComplete();
    }
}
```

## Testing with Caffeine

### Caffeine-Specific Tests

```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.cache.default-cache-type=CAFFEINE"
})
class CaffeineCacheTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldHandleComplexObjects() {
        // Given
        User user = new User("123", "John", "john@example.com");
        
        // When & Then
        StepVerifier.create(cacheManager.put("user:123", user))
            .verifyComplete();
        
        StepVerifier.create(cacheManager.get("user:123", User.class))
            .assertNext(result -> {
                assertThat(result).isPresent();
                User cachedUser = result.get();
                assertThat(cachedUser.getId()).isEqualTo("123");
                assertThat(cachedUser.getName()).isEqualTo("John");
                assertThat(cachedUser.getEmail()).isEqualTo("john@example.com");
            })
            .verifyComplete();
    }
    
    @Test
    void shouldGetStatistics() {
        // Given - perform some operations
        cacheManager.put("key1", "value1").block();
        cacheManager.get("key1", String.class).block();
        cacheManager.get("key2", String.class).block(); // miss
        
        // When
        StepVerifier.create(cacheManager.getStats("default"))
            .assertNext(stats -> {
                assertThat(stats.getHitCount()).isGreaterThan(0);
                assertThat(stats.getMissCount()).isGreaterThan(0);
                assertThat(stats.getHitRate()).isGreaterThan(0.0);
            })
            .verifyComplete();
    }
}
```

## Testing with Redis

### Using TestContainers

Add TestContainers dependency:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### Redis Integration Test

```java
@SpringBootTest
@Testcontainers
class RedisCacheIntegrationTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("firefly.cache.redis.default.host", redis::getHost);
        registry.add("firefly.cache.redis.default.port", 
            () -> redis.getMappedPort(6379));
    }
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldCacheInRedis() {
        // Given
        String key = "redis-test-key";
        String value = "redis-test-value";
        
        // When & Then
        StepVerifier.create(cacheManager.put(key, value))
            .verifyComplete();
        
        StepVerifier.create(cacheManager.get(key, String.class))
            .assertNext(result -> {
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(value);
            })
            .verifyComplete();
    }
    
    @Test
    void shouldPersistAcrossConnections() {
        // Given
        String key = "persist-test";
        String value = "persist-value";
        
        // When - store value
        StepVerifier.create(cacheManager.put(key, value))
            .verifyComplete();
        
        // Simulate reconnection by getting new cache instance
        // (In real scenario, this would be a new connection)
        
        // Then - value should still exist
        StepVerifier.create(cacheManager.get(key, String.class))
            .assertNext(result -> {
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(value);
            })
            .verifyComplete();
    }
}
```

## Mocking Strategies

### Mock CacheAdapter

```java
@ExtendWith(MockitoExtension.class)
class CustomCacheAdapterTest {
    
    @Mock
    private CacheAdapter cacheAdapter;
    
    @Test
    void shouldDelegateToAdapter() {
        // Given
        String key = "test-key";
        String value = "test-value";
        
        when(cacheAdapter.get(key))
            .thenReturn(Mono.just(Optional.of(value)));
        when(cacheAdapter.getCacheType())
            .thenReturn(CacheType.CAFFEINE);
        when(cacheAdapter.getCacheName())
            .thenReturn("test-cache");
        
        // When & Then
        StepVerifier.create(cacheAdapter.get(key))
            .assertNext(result -> {
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(value);
            })
            .verifyComplete();
        
        assertThat(cacheAdapter.getCacheType()).isEqualTo(CacheType.CAFFEINE);
    }
}
```

### Spy on Real Cache

```java
@SpringBootTest
class CacheSpyTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Test
    void shouldVerifyCacheInteractions() {
        // Given
        FireflyCacheManager spyManager = spy(cacheManager);
        String key = "spy-test-key";
        String value = "spy-test-value";
        
        // When
        StepVerifier.create(spyManager.put(key, value))
            .verifyComplete();
        
        StepVerifier.create(spyManager.get(key, String.class))
            .assertNext(result -> assertThat(result).isPresent())
            .verifyComplete();
        
        // Then
        verify(spyManager).put(eq(key), eq(value));
        verify(spyManager).get(eq(key), eq(String.class));
    }
}
```

## Test Configuration

### Test Application Properties

Create `src/test/resources/application-test.yml`:

```yaml
firefly:
  cache:
    enabled: true
    default-cache-type: CAFFEINE
    metrics-enabled: false
    health-enabled: false
    
    caffeine:
      default:
        maximum-size: 100
        expire-after-write: PT1M
        record-stats: true

logging:
  level:
    org.fireflyframework.cache: DEBUG
```

### Test Configuration Class

```java
@TestConfiguration
public class CacheTestConfig {
    
    @Bean
    @Primary
    public FireflyCacheManager testCacheManager() {
        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
            .maximumSize(10L)
            .expireAfterWrite(Duration.ofSeconds(30))
            .recordStats(true)
            .build();
        
        CaffeineCacheAdapter adapter = new CaffeineCacheAdapter("test-cache", config);
        
        FireflyCacheManager manager = new FireflyCacheManager();
        manager.registerCache("default", adapter);
        
        return manager;
    }
}
```

### Using Test Configuration

```java
@SpringBootTest
@Import(CacheTestConfig.class)
class ServiceWithTestCacheTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @Autowired
    private UserService userService;
    
    @Test
    void shouldUseTestCache() {
        // Test uses the test cache configuration
        // with smaller size and shorter TTL
    }
}
```

## Best Practices

### 1. Use Appropriate Test Scope

```java
// Unit test - mock the cache
@ExtendWith(MockitoExtension.class)
class UnitTest {
    @Mock
    private FireflyCacheManager cacheManager;
}

// Integration test - use real cache
@SpringBootTest
class IntegrationTest {
    @Autowired
    private FireflyCacheManager cacheManager;
}
```

### 2. Clean Up Between Tests

```java
@SpringBootTest
class CleanupTest {
    
    @Autowired
    private FireflyCacheManager cacheManager;
    
    @AfterEach
    void cleanup() {
        cacheManager.clear().block();
    }
    
    @Test
    void test1() {
        // Test with clean cache
    }
    
    @Test
    void test2() {
        // Test with clean cache
    }
}
```

### 3. Test TTL Behavior

```java
@Test
void shouldExpireAfterTTL() {
    Duration ttl = Duration.ofMillis(100);
    
    cacheManager.put("key", "value", ttl).block();
    
    // Verify exists
    assertThat(cacheManager.exists("key").block()).isTrue();
    
    // Wait for expiration
    await().atMost(200, TimeUnit.MILLISECONDS)
        .until(() -> !cacheManager.exists("key").block());
}
```

### 4. Test Error Scenarios

```java
@Test
void shouldHandleSerializationErrors() {
    // Given - object that can't be serialized
    NonSerializableObject obj = new NonSerializableObject();
    
    // When & Then
    StepVerifier.create(cacheManager.put("key", obj))
        .expectError(SerializationException.class)
        .verify();
}
```

### 5. Verify Cache Statistics

```java
@Test
void shouldTrackStatistics() {
    // Given
    cacheManager.put("key1", "value1").block();
    
    // When
    cacheManager.get("key1", String.class).block(); // hit
    cacheManager.get("key2", String.class).block(); // miss
    
    // Then
    StepVerifier.create(cacheManager.getStats("default"))
        .assertNext(stats -> {
            assertThat(stats.getHitCount()).isEqualTo(1);
            assertThat(stats.getMissCount()).isEqualTo(1);
            assertThat(stats.getHitRate()).isEqualTo(0.5);
        })
        .verifyComplete();
}
```

### 6. Use StepVerifier for Reactive Tests

```java
@Test
void shouldUseStepVerifier() {
    StepVerifier.create(cacheManager.put("key", "value"))
        .verifyComplete();
    
    StepVerifier.create(cacheManager.get("key", String.class))
        .assertNext(result -> {
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("value");
        })
        .verifyComplete();
}
```

### 7. Test Concurrent Access

```java
@Test
void shouldHandleConcurrentAccess() {
    String key = "concurrent-key";
    int threadCount = 10;
    
    Flux.range(0, threadCount)
        .parallel()
        .runOn(Schedulers.parallel())
        .flatMap(i -> cacheManager.put(key + i, "value" + i))
        .sequential()
        .blockLast();
    
    // Verify all values were cached
    for (int i = 0; i < threadCount; i++) {
        StepVerifier.create(cacheManager.get(key + i, String.class))
            .assertNext(result -> assertThat(result).isPresent())
            .verifyComplete();
    }
}
```

## Common Test Scenarios

### Testing Cache-Aside Pattern

```java
@Test
void shouldImplementCacheAsidePattern() {
    // Given
    String userId = "123";
    User user = new User(userId, "John");
    
    when(userRepository.findById(userId))
        .thenReturn(Mono.just(user));
    
    // First call - cache miss, load from DB
    StepVerifier.create(userService.getUser(userId))
        .expectNext(user)
        .verifyComplete();
    
    verify(userRepository, times(1)).findById(userId);
    
    // Second call - cache hit, no DB call
    StepVerifier.create(userService.getUser(userId))
        .expectNext(user)
        .verifyComplete();
    
    verify(userRepository, times(1)).findById(userId); // Still only 1 call
}
```

### Testing Cache Invalidation

```java
@Test
void shouldInvalidateCacheOnUpdate() {
    // Given
    User user = new User("123", "John");
    
    cacheManager.put("user:123", user).block();
    
    // When - update user
    User updatedUser = new User("123", "Jane");
    when(userRepository.save(updatedUser))
        .thenReturn(Mono.just(updatedUser));
    
    userService.updateUser(updatedUser).block();
    
    // Then - cache should be invalidated
    StepVerifier.create(cacheManager.get("user:123", User.class))
        .assertNext(result -> assertThat(result).isEmpty())
        .verifyComplete();
}
```

