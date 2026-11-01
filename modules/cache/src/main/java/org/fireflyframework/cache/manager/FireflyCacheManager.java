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

package org.fireflyframework.cache.manager;

import org.fireflyframework.kernel.logging.LogFields;

import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheOperationPolicy;
import org.fireflyframework.cache.exception.CacheException;
import org.fireflyframework.cache.core.CacheHealth;
import org.fireflyframework.cache.core.CacheStats;
import org.fireflyframework.cache.core.CacheType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Unified cache manager that provides a single cache interface.
 * <p>
 * This manager implements the CacheAdapter interface directly and delegates
 * to the configured cache provider (Caffeine or Redis) based on availability
 * and configuration.
 * <p>
 * Features:
 * <ul>
 *   <li>Single unified cache interface</li>
 *   <li>Automatic provider selection (Caffeine/Redis)</li>
 *   <li>Fallback support (Redis → Caffeine)</li>
 *   <li>Health monitoring</li>
 *   <li>Statistics collection</li>
 * </ul>
 */
@Slf4j
public class FireflyCacheManager implements CacheAdapter {

    private final CacheAdapter primaryCache;
    private final CacheAdapter fallbackCache;
    private final CacheOperationPolicy operationPolicy;
    private volatile boolean closed = false;

    /**
     * Creates a cache manager with a single cache provider.
     *
     * @param cache the cache adapter to use
     */
    public FireflyCacheManager(CacheAdapter cache) {
        this(cache, null);
    }

    /**
     * Creates a cache manager with primary and optional fallback cache.
     *
     * @param primaryCache the primary cache adapter
     * @param fallbackCache optional fallback cache adapter (can be null)
     */
    public FireflyCacheManager(CacheAdapter primaryCache, CacheAdapter fallbackCache) {
        this(primaryCache, fallbackCache, CacheOperationPolicy.LEGACY);
    }

    public FireflyCacheManager(CacheAdapter primaryCache, CacheAdapter fallbackCache, CacheOperationPolicy policy) {
        this.operationPolicy = java.util.Objects.requireNonNull(policy, "Cache operation policy required");
        if (policy == CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS
                && (primaryCache == null || fallbackCache != null || primaryCache.operationPolicy() != policy)) {
            throw new IllegalArgumentException("A policy-configured primary without fallback is required");
        }
        this.primaryCache = primaryCache;
        this.fallbackCache = fallbackCache;
        // Logging moved to CacheManagerFactory for single consolidated log
    }

    // ================================
    // CacheAdapter Implementation
    // ================================

    @Override
    public <K, V> Mono<Optional<V>> get(K key) {
        if (primaryOnly()) { return requiredPrimary(cache -> cache.<K, V>get(key), Optional.class); }
        return getActiveCache().<K, V>get(key)
                .onErrorResume(e -> {
                    log.error("Error during get operation for key present={}: type={}", key != null, LogFields.errorType(e));
                    return Mono.just(Optional.empty());
                });
    }

    @Override
    public <K, V> Mono<Optional<V>> get(K key, Class<V> valueType) {
        if (primaryOnly()) { return requiredPrimary(cache -> cache.<K, V>get(key, valueType), Optional.class); }
        return getActiveCache().<K, V>get(key, valueType)
                .onErrorResume(e -> {
                    log.error("Error during get operation for key present={}: type={}", key != null, LogFields.errorType(e));
                    return Mono.just(Optional.empty());
                });
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value) {
        if (primaryOnly()) { return primaryCompletion(cache -> cache.<K, V>put(key, value)); }
        return getActiveCache().<K, V>put(key, value)
                .onErrorResume(e -> {
                    log.error("Error during put operation for key present={}: type={}", key != null, LogFields.errorType(e));
                    return Mono.empty();
                });
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value, Duration ttl) {
        if (primaryOnly()) { return primaryCompletion(cache -> cache.<K, V>put(key, value, ttl)); }
        return getActiveCache().<K, V>put(key, value, ttl)
                .onErrorResume(e -> {
                    log.error("Error during put operation for key present={}: type={}", key != null, LogFields.errorType(e));
                    return Mono.empty();
                });
    }

    @Override
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value) {
        if (primaryOnly()) { return requiredPrimary(cache -> cache.<K, V>putIfAbsent(key, value), Boolean.class); }
        return getActiveCache().<K, V>putIfAbsent(key, value)
                .onErrorResume(e -> {
                    log.error("Error during putIfAbsent operation for key present={}: type={}", key != null, LogFields.errorType(e));
                    return Mono.just(false);
                });
    }

    @Override
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl) {
        if (primaryOnly()) { return requiredPrimary(cache -> cache.<K, V>putIfAbsent(key, value, ttl), Boolean.class); }
        return getActiveCache().<K, V>putIfAbsent(key, value, ttl)
                .onErrorResume(e -> {
                    log.error("Error during putIfAbsent operation for key present={}: type={}", key != null, LogFields.errorType(e));
                    return Mono.just(false);
                });
    }

    @Override
    public <K> Mono<Boolean> evict(K key) {
        if (primaryOnly()) { return requiredPrimary(cache -> cache.<K>evict(key), Boolean.class); }
        return getActiveCache().<K>evict(key)
                .onErrorResume(e -> {
                    log.error("Error during evict operation for key present={}: type={}", key != null, LogFields.errorType(e));
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Void> clear() {
        if (primaryOnly()) { return primaryCompletion(CacheAdapter::clear); }
        return getActiveCache().clear()
                .onErrorResume(e -> {
                    log.error("Error clearing cache: type={}", LogFields.errorType(e));
                    return Mono.empty();
                });
    }

    @Override
    public <K> Mono<Boolean> exists(K key) {
        if (primaryOnly()) { return requiredPrimary(cache -> cache.<K>exists(key), Boolean.class); }
        return getActiveCache().<K>exists(key)
                .onErrorResume(e -> {
                    log.error("Error during exists operation for key present={}: type={}", key != null, LogFields.errorType(e));
                    return Mono.just(false);
                });
    }

    @Override
    public <K> Mono<Set<K>> keys() {
        if (primaryOnly()) { return requiredPrimary(cache -> cache.<K>keys(), Set.class); }
        return getActiveCache().<K>keys()
                .onErrorResume(e -> {
                    log.error("Error getting keys: type={}", LogFields.errorType(e));
                    return Mono.just(Set.of());
                });
    }

    @Override
    public Mono<Long> size() {
        if (primaryOnly()) { return requiredPrimary(CacheAdapter::size, Long.class).map(this::nonnegativeCount); }
        return getActiveCache().size()
                .onErrorResume(e -> {
                    log.error("Error getting size: type={}", LogFields.errorType(e));
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<CacheStats> getStats() {
        if (primaryOnly()) { return requiredPrimary(CacheAdapter::getStats, CacheStats.class); }
        return getActiveCache().getStats();
    }

    @Override
    public CacheType getCacheType() {
        if (primaryOnly()) { return primary().getCacheType(); }
        return getActiveCache().getCacheType();
    }

    @Override
    public String getCacheName() {
        if (primaryOnly()) { return primary().getCacheName(); }
        return getActiveCache().getCacheName();
    }

    @Override
    public boolean isAvailable() {
        if (primaryOnly()) { return !closed && primaryCache.isAvailable(); }
        return !closed && getActiveCache().isAvailable();
    }

    @Override
    public Mono<CacheHealth> getHealth() {
        if (primaryOnly()) { return requiredPrimary(CacheAdapter::getHealth, CacheHealth.class); }
        return getActiveCache().getHealth();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        closed = true;
        if (primaryOnly()) {
            primaryCache.close();
            return;
        }
        log.info("Closing Firefly Cache Manager");
        
        try {
            primaryCache.close();
            log.debug("Closed primary cache");
        } catch (Exception e) {
            log.error("Error closing primary cache: type={}", LogFields.errorType(e));
        }
        
        if (fallbackCache != null) {
            try {
                fallbackCache.close();
                log.debug("Closed fallback cache");
            } catch (Exception e) {
                log.error("Error closing fallback cache: type={}", LogFields.errorType(e));
            }
        }
        
        log.info("Firefly Cache Manager closed");
    }

    // ================================
    // Helper Methods
    // ================================

    @Override
    public CacheOperationPolicy operationPolicy() {
        return operationPolicy;
    }

    @Override
    public Mono<Long> evictByPrefix(String prefix) {
        return primaryOnly()
                ? requiredPrimary(cache -> cache.evictByPrefix(prefix), Long.class).map(this::nonnegativeCount)
                : CacheAdapter.super.evictByPrefix(prefix);
    }

    private boolean primaryOnly() {
        return operationPolicy == CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS;
    }

    private CacheAdapter primary() {
        if (closed) throw new IllegalStateException("Cache manager is closed");
        return primaryCache;
    }

    private <T> Mono<T> primaryOperation(java.util.function.Function<CacheAdapter, Mono<T>> operation) {
        return Mono.defer(() -> {
            Mono<T> result = operation.apply(primary());
            return result == null ? Mono.error(new CacheException("Cache operation returned no publisher")) : result;
        });
    }

    private <T> Mono<T> requiredPrimary(java.util.function.Function<CacheAdapter, Mono<T>> operation, Class<?> resultType) {
        return primaryOperation(operation)
                .map(value -> {
                    if (!resultType.isInstance(value)) throw new CacheException("Cache operation returned an unsupported result");
                    return value;
                })
                .switchIfEmpty(Mono.error(new CacheException("Cache operation completed without a result")));
    }

    private Mono<Void> primaryCompletion(java.util.function.Function<CacheAdapter, Mono<Void>> operation) {
        Mono<?> result = primaryOperation(operation);
        return result.flatMap(value -> Mono.error(new CacheException("Cache completion emitted an unsupported result"))).then();
    }

    private long nonnegativeCount(long count) {
        if (count < 0) throw new CacheException("Cache operation returned an invalid count");
        return count;
    }

    /**
     * Gets the currently active cache, with fallback support.
     *
     * @return the active cache adapter
     */
    private CacheAdapter getActiveCache() {
        if (closed) {
            throw new IllegalStateException("Cache manager is closed");
        }

        // Try primary cache first
        if (primaryCache.isAvailable()) {
            return primaryCache;
        }

        // Fall back to fallback cache if available
        if (fallbackCache != null && fallbackCache.isAvailable()) {
            log.warn("Primary cache unavailable, using fallback cache");
            return fallbackCache;
        }

        // If primary is not available and no fallback, still return primary
        // (it will handle errors appropriately)
        return primaryCache;
    }

    /**
     * Checks if the cache manager is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }
}

