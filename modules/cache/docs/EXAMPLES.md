# Examples

Practical examples and common use cases for the Firefly Common Cache Library.

## Table of Contents

- [Basic Usage](#basic-usage)
- [User Service Example](#user-service-example)
- [Product Catalog Example](#product-catalog-example)
- [Session Management](#session-management)
- [Rate Limiting](#rate-limiting)
- [Cache-Aside Pattern](#cache-aside-pattern)
- [Write-Through Pattern](#write-through-pattern)
- [Cache Warming](#cache-warming)
- [Multi-Level Caching](#multi-level-caching)
- [Error Handling](#error-handling)

## Basic Usage

### Simple Get and Put

```java
@Service
@RequiredArgsConstructor
public class BasicCacheExample {
    
    private final FireflyCacheManager cacheManager;
    
    public Mono<String> getValue(String key) {
        return cacheManager.get(key, String.class)
            .map(optional -> optional.orElse("default-value"));
    }
    
    public Mono<Void> setValue(String key, String value) {
        return cacheManager.put(key, value, Duration.ofMinutes(10));
    }
}
```

### Check and Evict

```java
public Mono<Boolean> removeIfExists(String key) {
    return cacheManager.exists(key)
        .flatMap(exists -> {
            if (exists) {
                return cacheManager.evict(key);
            }
            return Mono.just(false);
        });
}
```

## User Service Example

Complete example of caching user data.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final FireflyCacheManager cacheManager;
    private final UserRepository userRepository;
    
@Qualifier("userCacheManager")
private final FireflyCacheManager userCacheManager; // dedicated cache manager for users
private static final Duration USER_TTL = Duration.ofMinutes(30);
 
/**
 * Get user by ID with caching
 */
public Mono<User> getUser(String userId) {
    String cacheKey = "user:" + userId;
    
    return userCacheManager.get(cacheKey, User.class)
        .flatMap(cachedUser -> {
            if (cachedUser.isPresent()) {
                log.debug("Cache hit for user: {}", userId);
                return Mono.just(cachedUser.get());
            }
            
            log.debug("Cache miss for user: {}", userId);
            return loadAndCacheUser(userId);
        });
}
    
    /**
     * Update user and invalidate cache
     */
    public Mono<User> updateUser(User user) {
        String cacheKey = "user:" + user.getId();
        
return userRepository.save(user)
    .flatMap(savedUser -> 
        userCacheManager.evict(cacheKey)
            .thenReturn(savedUser)
    )
    .doOnSuccess(u -> log.info("Updated and evicted cache for user: {}", u.getId()));
    }
    
    /**
     * Create user and cache immediately
     */
    public Mono<User> createUser(User user) {
return userRepository.save(user)
    .flatMap(savedUser -> {
        String cacheKey = "user:" + savedUser.getId();
        return userCacheManager.put(cacheKey, savedUser, USER_TTL)
            .thenReturn(savedUser);
    })
    .doOnSuccess(u -> log.info("Created and cached user: {}", u.getId()));
    }
    
    /**
     * Delete user and remove from cache
     */
    public Mono<Void> deleteUser(String userId) {
        String cacheKey = "user:" + userId;
        
return userRepository.deleteById(userId)
    .then(userCacheManager.evict(cacheKey))
    .then()
    .doOnSuccess(v -> log.info("Deleted user and cache: {}", userId));
    }
    
    /**
     * Get multiple users efficiently
     */
    public Flux<User> getUsers(List<String> userIds) {
        return Flux.fromIterable(userIds)
            .flatMap(this::getUser);
    }
    
    private Mono<User> loadAndCacheUser(String userId) {
        return userRepository.findById(userId)
            .flatMap(user -> {
                String cacheKey = "user:" + userId;
                return cacheManager.put(USER_CACHE, cacheKey, user, USER_TTL)
                    .thenReturn(user);
            })
            .switchIfEmpty(Mono.error(
                new UserNotFoundException("User not found: " + userId)
            ));
    }
}
```

## Product Catalog Example

Caching product information with different TTLs.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    
    private final FireflyCacheManager cacheManager;
    private final ProductRepository productRepository;
    
    private static final String PRODUCT_CACHE = "products";
    private static final String CATEGORY_CACHE = "categories";
    
    /**
     * Get product with long TTL (products change infrequently)
     */
    public Mono<Product> getProduct(String productId) {
        String cacheKey = "product:" + productId;
        
return productCacheManager.get(cacheKey, Product.class)
    .flatMap(cached -> cached
        .map(Mono::just)
        .orElseGet(() -> loadAndCacheProduct(productId))
    );
    }
    
    /**
     * Get products by category with shorter TTL
     */
    public Flux<Product> getProductsByCategory(String categoryId) {
        String cacheKey = "category:" + categoryId + ":products";
        
return productCacheManager.get(cacheKey, ProductList.class)
    .flatMapMany(cached -> {
        if (cached.isPresent()) {
            return Flux.fromIterable(cached.get().getProducts());
        }
        return loadAndCacheProductsByCategory(categoryId);
    });
    }
    
    /**
     * Update product price and refresh cache
     */
    public Mono<Product> updateProductPrice(String productId, BigDecimal newPrice) {
        return productRepository.findById(productId)
            .flatMap(product -> {
                product.setPrice(newPrice);
                return productRepository.save(product);
            })
            .flatMap(updatedProduct -> {
                String cacheKey = "product:" + productId;
                // Update cache with new data
return productCacheManager.put(cacheKey, updatedProduct, Duration.ofHours(24))
    .thenReturn(updatedProduct);
            });
    }
    
    private Mono<Product> loadAndCacheProduct(String productId) {
        return productRepository.findById(productId)
            .flatMap(product -> {
                String cacheKey = "product:" + productId;
return productCacheManager.put(cacheKey, product, Duration.ofHours(24))
    .thenReturn(product);
            });
    }
    
    private Flux<Product> loadAndCacheProductsByCategory(String categoryId) {
return productRepository.findByCategory(categoryId)
    .collectList()
    .flatMapMany(products -> {
        String cacheKey = "category:" + categoryId + ":products";
        ProductList productList = new ProductList(products);
        return productCacheManager.put(cacheKey, productList, Duration.ofMinutes(15))
            .thenMany(Flux.fromIterable(products));
    });
    }
}
```

## Session Management

Using cache for session storage.

```java
@Service
@RequiredArgsConstructor
public class SessionService {
    
    private final FireflyCacheManager cacheManager;
    
    private static final String SESSION_CACHE = "sessions";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    
    /**
     * Create a new session
     */
    public Mono<Session> createSession(String userId) {
        Session session = new Session(
            UUID.randomUUID().toString(),
            userId,
            Instant.now()
        );
        
        String cacheKey = "session:" + session.getId();
        
        return cacheManager.put(SESSION_CACHE, cacheKey, session, SESSION_TTL)
            .thenReturn(session);
    }
    
    /**
     * Get session and extend TTL
     */
    public Mono<Session> getSession(String sessionId) {
        String cacheKey = "session:" + sessionId;
        
        return cacheManager.get(SESSION_CACHE, cacheKey, Session.class)
            .flatMap(cached -> {
                if (cached.isPresent()) {
                    Session session = cached.get();
                    // Extend session TTL on access
                    return cacheManager.put(SESSION_CACHE, cacheKey, session, SESSION_TTL)
                        .thenReturn(session);
                }
                return Mono.error(new SessionExpiredException("Session not found or expired"));
            });
    }
    
    /**
     * Invalidate session (logout)
     */
    public Mono<Void> invalidateSession(String sessionId) {
        String cacheKey = "session:" + sessionId;
        return cacheManager.evict(SESSION_CACHE, cacheKey).then();
    }
    
    /**
     * Update session data
     */
    public Mono<Session> updateSession(Session session) {
        String cacheKey = "session:" + session.getId();
        session.setLastAccessed(Instant.now());
        
        return cacheManager.put(SESSION_CACHE, cacheKey, session, SESSION_TTL)
            .thenReturn(session);
    }
}
```

## Rate Limiting

Implementing rate limiting with cache.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {
    
    private final FireflyCacheManager cacheManager;
    
    private static final String RATE_LIMIT_CACHE = "rate-limits";
    private static final int MAX_REQUESTS = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    
    /**
     * Check if request is allowed
     */
    public Mono<Boolean> isAllowed(String clientId) {
        String cacheKey = "rate:" + clientId;
        
        return cacheManager.get(RATE_LIMIT_CACHE, cacheKey, RateLimitInfo.class)
            .flatMap(cached -> {
                if (cached.isEmpty()) {
                    // First request in window
                    return createRateLimit(cacheKey);
                }
                
                RateLimitInfo info = cached.get();
                if (info.getCount() >= MAX_REQUESTS) {
                    log.warn("Rate limit exceeded for client: {}", clientId);
                    return Mono.just(false);
                }
                
                // Increment counter
                info.incrementCount();
                return cacheManager.put(RATE_LIMIT_CACHE, cacheKey, info, WINDOW)
                    .thenReturn(true);
            });
    }
    
    /**
     * Get remaining requests
     */
    public Mono<Integer> getRemainingRequests(String clientId) {
        String cacheKey = "rate:" + clientId;
        
        return cacheManager.get(RATE_LIMIT_CACHE, cacheKey, RateLimitInfo.class)
            .map(cached -> {
                if (cached.isEmpty()) {
                    return MAX_REQUESTS;
                }
                return Math.max(0, MAX_REQUESTS - cached.get().getCount());
            });
    }
    
    private Mono<Boolean> createRateLimit(String cacheKey) {
        RateLimitInfo info = new RateLimitInfo(1);
        return cacheManager.put(RATE_LIMIT_CACHE, cacheKey, info, WINDOW)
            .thenReturn(true);
    }
}
```

## Cache-Aside Pattern

The most common caching pattern.

```java
@Service
@RequiredArgsConstructor
public class CacheAsideExample {
    
    private final FireflyCacheManager cacheManager;
    private final DataRepository repository;
    
    /**
     * Cache-aside pattern implementation
     */
    public Mono<Data> getData(String id) {
        String cacheKey = "data:" + id;
        
        // 1. Try to get from cache
        return cacheManager.get(cacheKey, Data.class)
            .flatMap(cached -> {
                if (cached.isPresent()) {
                    // 2. Cache hit - return cached data
                    return Mono.just(cached.get());
                }
                
                // 3. Cache miss - load from database
                return repository.findById(id)
                    .flatMap(data -> 
                        // 4. Store in cache
                        cacheManager.put(cacheKey, data, Duration.ofMinutes(15))
                            .thenReturn(data)
                    );
            });
    }
}
```

## Write-Through Pattern

Update cache and database together.

```java
@Service
@RequiredArgsConstructor
public class WriteThroughExample {
    
    private final FireflyCacheManager cacheManager;
    private final DataRepository repository;
    
    /**
     * Write-through pattern implementation
     */
    public Mono<Data> saveData(Data data) {
        String cacheKey = "data:" + data.getId();
        
        // 1. Save to database
        return repository.save(data)
            .flatMap(savedData -> 
                // 2. Update cache
                cacheManager.put(cacheKey, savedData, Duration.ofMinutes(15))
                    .thenReturn(savedData)
            );
    }
}
```

## Cache Warming

Pre-populate cache on startup.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmer {
    
    private final FireflyCacheManager cacheManager;
    private final ProductRepository productRepository;
    
    @EventListener(ApplicationReadyEvent.class)
    public void warmCache() {
        log.info("Starting cache warming...");
        
        // Load popular products into cache
        productRepository.findPopularProducts()
            .flatMap(product -> {
                String cacheKey = "product:" + product.getId();
                return cacheManager.put("products", cacheKey, product, Duration.ofHours(24));
            })
            .doOnComplete(() -> log.info("Cache warming completed"))
            .subscribe();
    }
}
```

## Multi-Level Caching

Using multiple caches for different data types.

```java
@Service
@RequiredArgsConstructor
public class MultiLevelCacheExample {
    
    private final FireflyCacheManager cacheManager;
    
    /**
     * Use different caches for different data
     */
    public Mono<User> getUser(String userId) {
        // Use fast local cache for users
        return cacheManager.get("local-cache", "user:" + userId, User.class)
            .flatMap(cached -> cached
                .map(Mono::just)
                .orElseGet(() -> loadFromDistributedCache(userId))
            );
    }
    
    private Mono<User> loadFromDistributedCache(String userId) {
        // Fallback to distributed cache
        return cacheManager.get("distributed-cache", "user:" + userId, User.class)
            .flatMap(cached -> cached
                .map(user -> {
                    // Promote to local cache
                    return cacheManager.put("local-cache", "user:" + userId, user, Duration.ofMinutes(5))
                        .thenReturn(user);
                })
                .orElseGet(() -> loadFromDatabase(userId))
            );
    }
    
    private Mono<User> loadFromDatabase(String userId) {
        // Load from database and cache at both levels
        return Mono.just(new User(userId, "John"))
            .flatMap(user -> 
                Mono.when(
                    cacheManager.put("local-cache", "user:" + userId, user, Duration.ofMinutes(5)),
                    cacheManager.put("distributed-cache", "user:" + userId, user, Duration.ofHours(1))
                ).thenReturn(user)
            );
    }
}
```

## Error Handling

Graceful error handling with caching.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorHandlingExample {
    
    private final FireflyCacheManager cacheManager;
    private final ExternalApiClient apiClient;
    
    /**
     * Fallback to cache on error
     */
    public Mono<ApiResponse> getDataWithFallback(String key) {
        return apiClient.fetchData(key)
            .flatMap(response -> 
                // Cache successful response
                cacheManager.put(key, response, Duration.ofMinutes(30))
                    .thenReturn(response)
            )
            .onErrorResume(error -> {
                log.warn("API call failed, trying cache: {}", error.getMessage());
                
                // Fallback to cached data
                return cacheManager.get(key, ApiResponse.class)
                    .flatMap(cached -> cached
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(error))
                    );
            });
    }
    
    /**
     * Continue on cache errors
     */
    public Mono<Data> getDataIgnoreCacheErrors(String id) {
        return cacheManager.get(id, Data.class)
            .onErrorResume(cacheError -> {
                log.error("Cache error, loading from DB: {}", cacheError.getMessage());
                return Mono.just(Optional.empty());
            })
            .flatMap(cached -> cached
                .map(Mono::just)
                .orElseGet(() -> loadFromDatabase(id))
            );
    }
    
    private Mono<Data> loadFromDatabase(String id) {
        return Mono.just(new Data(id, "value"));
    }
}
```

