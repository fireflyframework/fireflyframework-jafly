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

package org.fireflyframework.cache.adapter.caffeine;

import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheHealth;
import org.fireflyframework.cache.core.CacheStats;
import org.fireflyframework.cache.core.CacheType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine-based cache adapter implementation.
 * <p>
 * This adapter provides high-performance in-memory caching using the Caffeine library.
 * It supports time-based expiration, size-based eviction, and comprehensive statistics.
 * <p>
 * Features:
 * <ul>
 *   <li>High-performance in-memory caching</li>
 *   <li>Time-based expiration (TTL)</li>
 *   <li>Size-based eviction</li>
 *   <li>Comprehensive statistics</li>
 *   <li>Non-blocking reactive API</li>
 * </ul>
 */
@Slf4j
public class CaffeineCacheAdapter implements CacheAdapter {

    private final Cache<Object, Object> cache;
    private final String cacheName;
    private final String keyPrefix;
    private final CaffeineCacheConfig config;
    private final ConcurrentHashMap<Object, Instant> expirationTimes;

    public CaffeineCacheAdapter(String cacheName, CaffeineCacheConfig config) {
        this.cacheName = cacheName;
        this.keyPrefix = buildKeyPrefix(cacheName, config.getKeyPrefix());
        this.config = config;
        this.expirationTimes = new ConcurrentHashMap<>();
        this.cache = buildCache(config);
    }

    private String buildKeyPrefix(String cacheName, String configPrefix) {
        if (configPrefix != null && !configPrefix.trim().isEmpty()) {
            return configPrefix + ":" + cacheName + ":";
        }
        return "cache:" + cacheName + ":";
    }

    private Cache<Object, Object> buildCache(CaffeineCacheConfig config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();

        // Configure size limits
        if (config.getMaximumSize() != null) {
            builder.maximumSize(config.getMaximumSize());
        }

        // Configure expiration
        if (config.getExpireAfterWrite() != null) {
            builder.expireAfterWrite(config.getExpireAfterWrite().toNanos(), TimeUnit.NANOSECONDS);
        }
        if (config.getExpireAfterAccess() != null) {
            builder.expireAfterAccess(config.getExpireAfterAccess().toNanos(), TimeUnit.NANOSECONDS);
        }

        // Configure refresh
        if (config.getRefreshAfterWrite() != null) {
            builder.refreshAfterWrite(config.getRefreshAfterWrite().toNanos(), TimeUnit.NANOSECONDS);
        }

        // Enable statistics
        if (config.isRecordStats()) {
            builder.recordStats();
        }

        // Configure weakness
        if (config.isWeakKeys()) {
            builder.weakKeys();
        }
        if (config.isWeakValues()) {
            builder.weakValues();
        }
        if (config.isSoftValues()) {
            builder.softValues();
        }

        return builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Mono<Optional<V>> get(K key) {
        return Mono.fromCallable(() -> {
            try {
                String prefixedKey = buildKey(key);

                // Check custom TTL expiration first
                Instant expiration = expirationTimes.get(prefixedKey);
                if (expiration != null && Instant.now().isAfter(expiration)) {
                    cache.invalidate(prefixedKey);
                    expirationTimes.remove(prefixedKey);
                    return Optional.<V>empty();
                }

                V value = (V) cache.getIfPresent(prefixedKey);
                return Optional.ofNullable(value);
            } catch (Exception e) {
                log.warn("Error getting value from cache '{}' for key '{}': {}",
                        cacheName, key, e.getMessage());
                return Optional.<V>empty();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Mono<Optional<V>> get(K key, Class<V> valueType) {
        return get(key).map(optional -> 
            optional.filter(valueType::isInstance).map(valueType::cast)
        );
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value) {
        return Mono.fromRunnable(() -> {
            try {
                String prefixedKey = buildKey(key);
                cache.put(prefixedKey, value);
                // Remove any custom expiration time
                expirationTimes.remove(prefixedKey);
                log.debug("Put value in cache '{}' for key '{}'", cacheName, key);
            } catch (Exception e) {
                log.error("Error putting value in cache '{}' for key '{}': {}",
                         cacheName, key, e.getMessage(), e);
                throw new RuntimeException("Failed to put value in cache", e);
            }
        });
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value, Duration ttl) {
        return Mono.fromRunnable(() -> {
            try {
                String prefixedKey = buildKey(key);
                cache.put(prefixedKey, value);
                // Track custom expiration time
                expirationTimes.put(prefixedKey, Instant.now().plus(ttl));
                log.debug("Put value in cache '{}' for key '{}' with TTL {}", cacheName, key, ttl);
            } catch (Exception e) {
                log.error("Error putting value in cache '{}' for key '{}' with TTL: {}",
                         cacheName, key, e.getMessage(), e);
                throw new RuntimeException("Failed to put value in cache with TTL", e);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value) {
        return Mono.fromCallable(() -> {
            try {
                String prefixedKey = buildKey(key);

                // Check if key exists (considering custom TTL)
                Instant expiration = expirationTimes.get(prefixedKey);
                if (expiration != null && Instant.now().isAfter(expiration)) {
                    cache.invalidate(prefixedKey);
                    expirationTimes.remove(prefixedKey);
                }

                V existingValue = (V) cache.getIfPresent(prefixedKey);
                if (existingValue == null) {
                    cache.put(prefixedKey, value);
                    expirationTimes.remove(prefixedKey); // No custom TTL
                    log.debug("Put new value in cache '{}' for key '{}'", cacheName, key);
                    return true;
                } else {
                    log.debug("Key '{}' already exists in cache '{}'", key, cacheName);
                    return false;
                }
            } catch (Exception e) {
                log.error("Error in putIfAbsent for cache '{}' and key '{}': {}",
                         cacheName, key, e.getMessage(), e);
                throw new RuntimeException("Failed to put value if absent", e);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl) {
        return Mono.fromCallable(() -> {
            try {
                String prefixedKey = buildKey(key);

                // Check if key exists (considering custom TTL)
                Instant expiration = expirationTimes.get(prefixedKey);
                if (expiration != null && Instant.now().isAfter(expiration)) {
                    cache.invalidate(prefixedKey);
                    expirationTimes.remove(prefixedKey);
                }

                V existingValue = (V) cache.getIfPresent(prefixedKey);
                if (existingValue == null) {
                    cache.put(prefixedKey, value);
                    expirationTimes.put(prefixedKey, Instant.now().plus(ttl));
                    log.debug("Put new value in cache '{}' for key '{}' with TTL {}",
                             cacheName, key, ttl);
                    return true;
                } else {
                    log.debug("Key '{}' already exists in cache '{}'", key, cacheName);
                    return false;
                }
            } catch (Exception e) {
                log.error("Error in putIfAbsent with TTL for cache '{}' and key '{}': {}",
                         cacheName, key, e.getMessage(), e);
                throw new RuntimeException("Failed to put value if absent with TTL", e);
            }
        });
    }

    @Override
    public <K> Mono<Boolean> evict(K key) {
        return Mono.fromCallable(() -> {
            try {
                String prefixedKey = buildKey(key);
                boolean existed = cache.getIfPresent(prefixedKey) != null || expirationTimes.containsKey(prefixedKey);
                cache.invalidate(prefixedKey);
                expirationTimes.remove(prefixedKey);
                log.debug("Evicted key '{}' from cache '{}': {}", key, cacheName, existed);
                return existed;
            } catch (Exception e) {
                log.error("Error evicting key '{}' from cache '{}': {}",
                         key, cacheName, e.getMessage(), e);
                throw new RuntimeException("Failed to evict key from cache", e);
            }
        });
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> {
            try {
                cache.invalidateAll();
                expirationTimes.clear();
                log.debug("Cleared cache '{}'", cacheName);
            } catch (Exception e) {
                log.error("Error clearing cache '{}': {}", cacheName, e.getMessage(), e);
                throw new RuntimeException("Failed to clear cache", e);
            }
        });
    }

    @Override
    public <K> Mono<Boolean> exists(K key) {
        return Mono.fromCallable(() -> {
            try {
                String prefixedKey = buildKey(key);

                // Check custom TTL expiration first
                Instant expiration = expirationTimes.get(prefixedKey);
                if (expiration != null && Instant.now().isAfter(expiration)) {
                    cache.invalidate(prefixedKey);
                    expirationTimes.remove(prefixedKey);
                    return false;
                }

                return cache.getIfPresent(prefixedKey) != null;
            } catch (Exception e) {
                log.warn("Error checking existence of key '{}' in cache '{}': {}",
                        key, cacheName, e.getMessage());
                return false;
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K> Mono<Set<K>> keys() {
        return Mono.fromCallable(() -> {
            try {
                // Clean up expired keys first
                cleanupExpiredKeys();

                // Remove prefix from all keys before returning
                Set<K> unprefixedKeys = cache.asMap().keySet().stream()
                        .map(key -> (K) extractOriginalKey(key.toString()))
                        .collect(java.util.stream.Collectors.toSet());

                return unprefixedKeys;
            } catch (Exception e) {
                log.error("Error getting keys from cache '{}': {}", cacheName, e.getMessage(), e);
                throw new RuntimeException("Failed to get cache keys", e);
            }
        });
    }

    @Override
    public Mono<Long> size() {
        return Mono.fromCallable(() -> {
            try {
                // Clean up expired keys first
                cleanupExpiredKeys();
                return cache.estimatedSize();
            } catch (Exception e) {
                log.error("Error getting size of cache '{}': {}", cacheName, e.getMessage(), e);
                throw new RuntimeException("Failed to get cache size", e);
            }
        });
    }

    @Override
    public Mono<CacheStats> getStats() {
        return Mono.fromCallable(() -> {
            try {
                com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats = cache.stats();
                
                return CacheStats.builder()
                        .requestCount(caffeineStats.requestCount())
                        .hitCount(caffeineStats.hitCount())
                        .missCount(caffeineStats.missCount())
                        .loadCount(caffeineStats.loadCount())
                        .evictionCount(caffeineStats.evictionCount())
                        .entryCount(cache.estimatedSize())
                        .averageLoadTime(caffeineStats.averageLoadPenalty())
                        .estimatedSize(cache.estimatedSize() * 64) // Rough estimate
                        .capturedAt(Instant.now())
                        .cacheType(CacheType.CAFFEINE)
                        .cacheName(cacheName)
                        .build();
            } catch (Exception e) {
                log.error("Error getting stats for cache '{}': {}", cacheName, e.getMessage(), e);
                return CacheStats.empty(CacheType.CAFFEINE, cacheName);
            }
        });
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.CAFFEINE;
    }

    @Override
    public String getCacheName() {
        return cacheName;
    }

    @Override
    public boolean isAvailable() {
        return cache != null;
    }

    @Override
    public Mono<CacheHealth> getHealth() {
        return Mono.fromCallable(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // Perform a simple health check
                String testKey = "__health_check__" + System.currentTimeMillis();
                cache.put(testKey, "test");
                Object value = cache.getIfPresent(testKey);
                cache.invalidate(testKey);
                
                long responseTime = System.currentTimeMillis() - startTime;
                
                if ("test".equals(value)) {
                    return CacheHealth.healthy(CacheType.CAFFEINE, cacheName, responseTime);
                } else {
                    return CacheHealth.unhealthy(CacheType.CAFFEINE, cacheName, 
                            "Health check failed: test value mismatch", null);
                }
            } catch (Exception e) {
                log.error("Health check failed for cache '{}': {}", cacheName, e.getMessage(), e);
                return CacheHealth.unhealthy(CacheType.CAFFEINE, cacheName, 
                        "Health check failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void close() {
        try {
            cache.invalidateAll();
            cache.cleanUp();
            expirationTimes.clear();
            log.info("Closed Caffeine cache adapter '{}'", cacheName);
        } catch (Exception e) {
            log.error("Error closing cache '{}': {}", cacheName, e.getMessage(), e);
        }
    }

    /**
     * Clean up expired keys with custom TTL.
     */
    private void cleanupExpiredKeys() {
        Instant now = Instant.now();
        expirationTimes.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue())) {
                cache.invalidate(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Gets the underlying Caffeine cache configuration.
     *
     * @return the cache configuration
     */
    public CaffeineCacheConfig getConfig() {
        return config;
    }

    /**
     * Builds the full cache key by adding the prefix.
     * Format: keyPrefix + key (where keyPrefix already includes cacheName)
     *
     * @param key the original key
     * @return the full cache key
     */
    private String buildKey(Object key) {
        return keyPrefix + key.toString();
    }

    /**
     * Extracts the original key from a cache key by removing the prefix.
     *
     * @param cacheKey the full cache key
     * @return the original key
     */
    private String extractOriginalKey(String cacheKey) {
        if (cacheKey.startsWith(keyPrefix)) {
            return cacheKey.substring(keyPrefix.length());
        }
        return cacheKey;
    }
}