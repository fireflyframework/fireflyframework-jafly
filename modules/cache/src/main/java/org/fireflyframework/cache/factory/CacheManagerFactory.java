/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.cache.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheAdapter;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.cache.properties.CacheProperties;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Factory for creating multiple independent CacheManager instances.
 * <p>
 * This factory allows creating cache managers with different configurations,
 * enabling multiple cache contexts within the same application without conflicts.
 * Each cache manager has its own key prefix and configuration.
 * <p>
 * <b>Example Usage:</b>
 * <pre>
 * // Create HTTP idempotency cache
 * FireflyCacheManager httpIdempotencyCache = factory.createCacheManager(
 *     "http-idempotency",
 *     CacheType.REDIS,
 *     "firefly:http:idempotency",
 *     Duration.ofHours(24)
 * );
 *
 * // Create webhook event cache
 * FireflyCacheManager webhookCache = factory.createCacheManager(
 *     "webhook-events",
 *     CacheType.REDIS,
 *     "firefly:webhooks:events",
 *     Duration.ofDays(7)
 * );
 * </pre>
 */
@Slf4j
public class CacheManagerFactory {

    private java.util.List<org.fireflyframework.cache.spi.CacheProviderFactory> providerFactories;

    private final CacheProperties properties;
    private final ObjectMapper objectMapper;
    private final Object redisConnectionFactory; // Use Object to avoid loading Redis classes
    private final Object hazelcastInstance;      // Optional HazelcastInstance
    private final Object jcacheManager;          // Optional JCache CacheManager
    private final Object r2dbcConnectionFactory; // Optional R2DBC ConnectionFactory (Postgres)
    private final boolean redisAvailable;
    private final boolean hazelcastAvailable;
    private final boolean jcacheAvailable;

    /**
     * Creates a new CacheManagerFactory.
     *
     * @param properties the global cache properties
     * @param objectMapper the object mapper for serialization
     * @param redisConnectionFactory optional Redis connection factory (can be null)
     * @param hazelcastInstance optional Hazelcast instance (can be null)
     * @param jcacheManager optional JCache CacheManager (can be null)
     */
    public CacheManagerFactory(CacheProperties properties,
                                ObjectMapper objectMapper,
                                Object redisConnectionFactory,
                                Object hazelcastInstance,
                                Object jcacheManager) {
        this(properties, objectMapper, redisConnectionFactory, hazelcastInstance, jcacheManager, null);
    }

    /**
     * Creates a new CacheManagerFactory.
     *
     * @param properties the global cache properties
     * @param objectMapper the object mapper for serialization
     * @param redisConnectionFactory optional Redis connection factory (can be null)
     * @param hazelcastInstance optional Hazelcast instance (can be null)
     * @param jcacheManager optional JCache CacheManager (can be null)
     * @param r2dbcConnectionFactory optional R2DBC ConnectionFactory for Postgres (can be null)
     */
    public CacheManagerFactory(CacheProperties properties,
                                ObjectMapper objectMapper,
                                Object redisConnectionFactory,
                                Object hazelcastInstance,
                                Object jcacheManager,
                                Object r2dbcConnectionFactory) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisConnectionFactory = redisConnectionFactory;
        this.hazelcastInstance = hazelcastInstance;
        this.jcacheManager = jcacheManager;
        this.r2dbcConnectionFactory = r2dbcConnectionFactory;
        this.redisAvailable = checkRedisAvailable();
        this.hazelcastAvailable = checkHazelcastAvailable();
        this.jcacheAvailable = checkJCacheAvailable();
        this.providerFactories = loadProviderFactories();
        log.info("CacheManagerFactory initialized (Redis: {}, Hazelcast: {}, JCache: {})",
                redisAvailable, hazelcastAvailable, jcacheAvailable);
    }

    /**
     * Checks if Redis classes are available on the classpath.
     */
    private boolean checkRedisAvailable() {
        try {
            Class.forName("org.springframework.data.redis.connection.ReactiveRedisConnectionFactory");
            Class.forName("org.fireflyframework.cache.factory.RedisCacheHelper");
            return redisConnectionFactory != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean checkHazelcastAvailable() {
        try {
            Class.forName("com.hazelcast.core.HazelcastInstance");
            Class.forName("org.fireflyframework.cache.factory.HazelcastCacheHelper");
            return hazelcastInstance != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean checkJCacheAvailable() {
        try {
            // Try both javax and jakarta
            try {
                Class.forName("javax.cache.CacheManager");
            } catch (ClassNotFoundException ex) {
                Class.forName("jakarta.cache.CacheManager");
            }
            Class.forName("org.fireflyframework.cache.factory.JCacheCacheHelper");
            return jcacheManager != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Creates a new cache manager with custom configuration.
     */
    public FireflyCacheManager createCacheManager(String cacheName,
                                                   CacheType cacheType,
                                                   String keyPrefix,
                                                   Duration defaultTtl) {
        return createCacheManager(cacheName, cacheType, keyPrefix, defaultTtl, "General purpose cache", null);
    }

    /**
     * Creates a new cache manager with custom configuration and tracking information.
     *
     * @param cacheName the name of the cache
     * @param cacheType the type of cache (CAFFEINE or REDIS)
     * @param keyPrefix the key prefix for this cache
     * @param defaultTtl the default TTL for cache entries
     * @param description a description of what this cache is used for
     * @param requestedBy the component/module requesting this cache (optional, will be auto-detected)
     * @return a configured FireflyCacheManager
     */
    public FireflyCacheManager createCacheManager(String cacheName,
                                                   CacheType cacheType,
                                                   String keyPrefix,
                                                   Duration defaultTtl,
                                                   String description,
                                                   String requestedBy) {
        // Check what's available and enabled
        boolean caffeineEnabled = properties.getCaffeine().isEnabled();
        boolean redisEnabled = properties.getRedis().isEnabled() && redisAvailable;
        
        // Resolve AUTO to a provider
        CacheType resolvedType = cacheType;
        if (cacheType == CacheType.AUTO) {
            resolvedType = selectBestProviderType();
        }

        CacheAdapter primaryCache = null;
        CacheAdapter fallbackCache = null;

        // Prefer SPI providers if available
        var ctx = new org.fireflyframework.cache.spi.CacheProviderFactory.ProviderContext(
                properties, objectMapper, redisConnectionFactory, hazelcastInstance, jcacheManager, r2dbcConnectionFactory);
        var provider = findProvider(resolvedType);
        boolean providerCreated = false;
        if (provider != null && provider.isAvailable(ctx)) {
            try {
                primaryCache = provider.create(cacheName, keyPrefix, defaultTtl, ctx);
                providerCreated = true;
            } catch (Exception e) {
                log.warn("SPI provider {} failed for cache '{}': {}. Falling back.", resolvedType, cacheName, e.getMessage());
                primaryCache = null;
            }
        }
        if (!providerCreated) {
            primaryCache = null;
        }
        if (primaryCache == null) {
            // Fallback to built-in creation methods (backward compatibility)
            if (resolvedType == CacheType.REDIS && redisEnabled) {
                primaryCache = createRedisCacheAdapterViaReflection(cacheName, keyPrefix, defaultTtl);
            } else if (resolvedType == CacheType.HAZELCAST && hazelcastAvailable) {
                primaryCache = createHazelcastCacheAdapterViaReflection(cacheName, keyPrefix, defaultTtl);
            } else if (resolvedType == CacheType.JCACHE && jcacheAvailable) {
                try {
                    primaryCache = createJCacheAdapterViaReflection(cacheName, keyPrefix, defaultTtl);
                } catch (Exception e2) {
                    log.warn("Built-in JCache creation also failed for cache '{}': {}. Falling back to Caffeine.", cacheName, e2.getMessage());
                    if (caffeineEnabled) {
                        primaryCache = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
                    } else {
                        throw e2;
                    }
                }
            } else if (resolvedType == CacheType.CAFFEINE && caffeineEnabled) {
                primaryCache = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
            } else if (caffeineEnabled) {
                log.warn("Requested cache type {} not available for cache '{}', falling back to Caffeine", resolvedType, cacheName);
                primaryCache = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
            } else {
                throw new IllegalStateException(String.format("Cannot create cache of type %s: provider not available or not enabled", resolvedType));
            }
        }

        // If smart composite is enabled and we have L2 + L1, wrap as Smart
        if (resolvedType != CacheType.CAFFEINE && properties.getSmart().isEnabled() && properties.getCaffeine().isEnabled()) {
            CacheAdapter l1 = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
            primaryCache = new org.fireflyframework.cache.adapter.smart.SmartCacheAdapter(
                    cacheName, l1, primaryCache, defaultTtl, properties.getSmart().isBackfillL1OnRead());
            fallbackCache = null; // Smart includes both
            log.info("Cache '{}' created: SMART(L1+L2) over {} (TTL: {})", cacheName, resolvedType, defaultTtl);
        } else if (resolvedType != CacheType.CAFFEINE && properties.getCaffeine().isEnabled()) {
            // Legacy fallback
            fallbackCache = createCaffeineCacheAdapter(cacheName, keyPrefix, defaultTtl);
            log.info("Cache '{}' created: {} + Caffeine fallback (TTL: {})", cacheName, resolvedType, defaultTtl);
        } else {
            log.info("Cache '{}' created: {} (TTL: {})", cacheName, resolvedType, defaultTtl);
        }

        return new FireflyCacheManager(primaryCache, fallbackCache);
    }

    /**
     * Detects the caller class from the stack trace.
     */
    private String detectCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Skip: getStackTrace, detectCaller, createCacheManager (x2), and factory method
        for (int i = 5; i < Math.min(stackTrace.length, 10); i++) {
            String className = stackTrace[i].getClassName();
            // Skip internal factory classes
            if (!className.contains("CacheManagerFactory") && 
                !className.contains("CacheAutoConfiguration") &&
                !className.contains("java.lang.reflect") &&
                !className.contains("org.springframework")) {
                return className + "." + stackTrace[i].getMethodName() + "()";
            }
        }
        return "Unknown caller";
    }

    /**
     * Creates a Caffeine cache adapter.
     */
    private CacheAdapter createCaffeineCacheAdapter(String cacheName, String keyPrefix, Duration defaultTtl) {
        log.debug("  → Configuring Caffeine adapter with prefix '{}'", keyPrefix);

        CacheProperties.CaffeineConfig caffeineProps = properties.getCaffeine();

        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .keyPrefix(keyPrefix)
                .maximumSize(caffeineProps.getMaximumSize())
                .expireAfterWrite(defaultTtl != null ? defaultTtl : caffeineProps.getExpireAfterWrite())
                .expireAfterAccess(caffeineProps.getExpireAfterAccess())
                .refreshAfterWrite(caffeineProps.getRefreshAfterWrite())
                .recordStats(caffeineProps.isRecordStats())
                .weakKeys(caffeineProps.isWeakKeys())
                .weakValues(caffeineProps.isWeakValues())
                .softValues(caffeineProps.isSoftValues())
                .build();

        return new CaffeineCacheAdapter(cacheName, config);
    }

    /**
     * Creates a Redis cache adapter using reflection to avoid loading Redis classes.
     * <p>
     * This approach ensures that Redis classes are only loaded when Redis is actually available.
     */
    // ---------------- SPI helpers ----------------

    private java.util.List<org.fireflyframework.cache.spi.CacheProviderFactory> loadProviderFactories() {
        java.util.List<org.fireflyframework.cache.spi.CacheProviderFactory> list = new java.util.ArrayList<>();
        try {
            var loader = java.util.ServiceLoader.load(org.fireflyframework.cache.spi.CacheProviderFactory.class);
            loader.forEach(list::add);
            list.sort(java.util.Comparator.comparingInt(org.fireflyframework.cache.spi.CacheProviderFactory::priority));
        } catch (Throwable ignored) { }
        return list;
    }

    private CacheType selectBestProviderType() {
        for (var factory : providerFactories) {
            var ctx = new org.fireflyframework.cache.spi.CacheProviderFactory.ProviderContext(
                    properties, objectMapper, redisConnectionFactory, hazelcastInstance, jcacheManager, r2dbcConnectionFactory);
            if (factory.isAvailable(ctx)) {
                return factory.getType();
            }
        }
        // Fallback priorities
        if (redisAvailable) return CacheType.REDIS;
        if (hazelcastAvailable) return CacheType.HAZELCAST;
        if (jcacheAvailable) return CacheType.JCACHE;
        if (properties.getCaffeine().isEnabled()) return CacheType.CAFFEINE;
        throw new IllegalStateException("No cache adapters available. At least one cache type must be enabled.");
    }

    private org.fireflyframework.cache.spi.CacheProviderFactory findProvider(CacheType type) {
        return providerFactories.stream().filter(p -> p.getType() == type).findFirst().orElse(null);
    }

    private CacheAdapter createRedisCacheAdapterViaReflection(String cacheName, String keyPrefix, Duration defaultTtl) {
        try {
            log.debug("  → Configuring Redis adapter with prefix '{}' (via reflection)", keyPrefix);

            // Load RedisCacheHelper class dynamically
            Class<?> helperClass = Class.forName("org.fireflyframework.cache.factory.RedisCacheHelper");
            Method createMethod = helperClass.getMethod(
                "createRedisCacheAdapter",
                String.class,
                String.class,
                Duration.class,
                Class.forName("org.springframework.data.redis.connection.ReactiveRedisConnectionFactory"),
                CacheProperties.class,
                ObjectMapper.class
            );

            // Invoke the static method
            return (CacheAdapter) createMethod.invoke(
                null,
                cacheName,
                keyPrefix,
                defaultTtl,
                redisConnectionFactory,
                properties,
                objectMapper
            );
        } catch (Exception e) {
            log.error("Failed to create Redis cache adapter via reflection", e);
            throw new IllegalStateException("Failed to create Redis cache: " + e.getMessage(), e);
        }
    }

    private CacheAdapter createHazelcastCacheAdapterViaReflection(String cacheName, String keyPrefix, Duration defaultTtl) {
        try {
            log.debug("  → Configuring Hazelcast adapter with prefix '{}' (via reflection)", keyPrefix);

            Class<?> helperClass = Class.forName("org.fireflyframework.cache.factory.HazelcastCacheHelper");
            Method createMethod = helperClass.getMethod(
                "createHazelcastCacheAdapter",
                String.class,
                String.class,
                Duration.class,
                Class.forName("com.hazelcast.core.HazelcastInstance")
            );

            return (CacheAdapter) createMethod.invoke(
                null,
                cacheName,
                keyPrefix,
                defaultTtl,
                hazelcastInstance
            );
        } catch (Exception e) {
            log.error("Failed to create Hazelcast cache adapter via reflection", e);
            throw new IllegalStateException("Failed to create Hazelcast cache: " + e.getMessage(), e);
        }
    }

    private CacheAdapter createJCacheAdapterViaReflection(String cacheName, String keyPrefix, Duration defaultTtl) {
        try {
            log.debug("  → Configuring JCache adapter with prefix '{}' (via reflection)", keyPrefix);

            Class<?> helperClass = Class.forName("org.fireflyframework.cache.factory.JCacheCacheHelper");
            // Try javax first, then jakarta for the CacheManager parameter type
            Class<?> cacheManagerClass;
            try {
                cacheManagerClass = Class.forName("javax.cache.CacheManager");
            } catch (ClassNotFoundException ex) {
                cacheManagerClass = Class.forName("jakarta.cache.CacheManager");
            }
            Method createMethod = helperClass.getMethod(
                "createJCacheAdapter",
                String.class,
                String.class,
                Duration.class,
                Object.class
            );

            return (CacheAdapter) createMethod.invoke(
                null,
                cacheName,
                keyPrefix,
                defaultTtl,
                jcacheManager
            );
        } catch (Exception e) {
            log.error("Failed to create JCache adapter via reflection", e);
            throw new IllegalStateException("Failed to create JCache cache: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a cache manager using default configuration from properties.
     *
     * @param cacheName the name of the cache
     * @return a configured FireflyCacheManager
     */
    public FireflyCacheManager createDefaultCacheManager(String cacheName) {
        // Validate that at least one cache type is available and enabled
        boolean caffeineEnabled = properties.getCaffeine().isEnabled();
        boolean redisEnabled = properties.getRedis().isEnabled() && redisAvailable;
        
        if (!caffeineEnabled && !redisEnabled) {
            throw new IllegalStateException(
                "No cache adapters available. At least one cache type (Caffeine or Redis) must be enabled."
            );
        }
        
        CacheType cacheType = properties.getDefaultCacheType();
        
        // Determine the actual cache name from properties
        String actualCacheName = cacheName;
        if (cacheType == CacheType.CAFFEINE || (cacheType == CacheType.AUTO && !redisEnabled)) {
            // Use Caffeine cache name if available and not default
            String caffeineCacheName = properties.getCaffeine().getCacheName();
            if (caffeineCacheName != null && !"default".equals(caffeineCacheName)) {
                actualCacheName = caffeineCacheName;
            }
        } else if (cacheType == CacheType.REDIS || (cacheType == CacheType.AUTO && redisEnabled)) {
            // Use Redis cache name if available and not default
            String redisCacheName = properties.getRedis().getCacheName();
            if (redisCacheName != null && !"default".equals(redisCacheName)) {
                actualCacheName = redisCacheName;
            }
        }
        
        String keyPrefix = "firefly:cache:" + actualCacheName;
        Duration defaultTtl = cacheType == CacheType.REDIS
                ? properties.getRedis().getDefaultTtl()
                : properties.getCaffeine().getExpireAfterWrite();

        return createCacheManager(actualCacheName, cacheType, keyPrefix, defaultTtl);
    }
}
