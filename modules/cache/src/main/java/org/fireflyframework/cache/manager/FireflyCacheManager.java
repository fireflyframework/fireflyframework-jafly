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

import org.fireflyframework.cache.core.CacheAdapter;
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
        this.primaryCache = primaryCache;
        this.fallbackCache = fallbackCache;
        // Logging moved to CacheManagerFactory for single consolidated log
    }

    // ================================
    // CacheAdapter Implementation
    // ================================

    @Override
    public <K, V> Mono<Optional<V>> get(K key) {
        return getActiveCache().<K, V>get(key)
                .onErrorResume(e -> {
                    log.error("Error during get operation for key {}: {}", key, e.getMessage());
                    return Mono.just(Optional.empty());
                });
    }

    @Override
    public <K, V> Mono<Optional<V>> get(K key, Class<V> valueType) {
        return getActiveCache().<K, V>get(key, valueType)
                .onErrorResume(e -> {
                    log.error("Error during get operation for key {}: {}", key, e.getMessage());
                    return Mono.just(Optional.empty());
                });
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value) {
        return getActiveCache().<K, V>put(key, value)
                .onErrorResume(e -> {
                    log.error("Error during put operation for key {}: {}", key, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value, Duration ttl) {
        return getActiveCache().<K, V>put(key, value, ttl)
                .onErrorResume(e -> {
                    log.error("Error during put operation for key {}: {}", key, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value) {
        return getActiveCache().<K, V>putIfAbsent(key, value)
                .onErrorResume(e -> {
                    log.error("Error during putIfAbsent operation for key {}: {}", key, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl) {
        return getActiveCache().<K, V>putIfAbsent(key, value, ttl)
                .onErrorResume(e -> {
                    log.error("Error during putIfAbsent operation for key {}: {}", key, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public <K> Mono<Boolean> evict(K key) {
        return getActiveCache().<K>evict(key)
                .onErrorResume(e -> {
                    log.error("Error during evict operation for key {}: {}", key, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Void> clear() {
        return getActiveCache().clear()
                .onErrorResume(e -> {
                    log.error("Error clearing cache: {}", e.getMessage(), e);
                    return Mono.empty();
                });
    }

    @Override
    public <K> Mono<Boolean> exists(K key) {
        return getActiveCache().<K>exists(key)
                .onErrorResume(e -> {
                    log.error("Error during exists operation for key {}: {}", key, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public <K> Mono<Set<K>> keys() {
        return getActiveCache().<K>keys()
                .onErrorResume(e -> {
                    log.error("Error getting keys: {}", e.getMessage(), e);
                    return Mono.just(Set.of());
                });
    }

    @Override
    public Mono<Long> size() {
        return getActiveCache().size()
                .onErrorResume(e -> {
                    log.error("Error getting size: {}", e.getMessage(), e);
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<CacheStats> getStats() {
        return getActiveCache().getStats();
    }

    @Override
    public CacheType getCacheType() {
        return getActiveCache().getCacheType();
    }

    @Override
    public String getCacheName() {
        return getActiveCache().getCacheName();
    }

    @Override
    public boolean isAvailable() {
        return !closed && getActiveCache().isAvailable();
    }

    @Override
    public Mono<CacheHealth> getHealth() {
        return getActiveCache().getHealth();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        closed = true;
        log.info("Closing Firefly Cache Manager");
        
        try {
            primaryCache.close();
            log.debug("Closed primary cache");
        } catch (Exception e) {
            log.error("Error closing primary cache: {}", e.getMessage(), e);
        }
        
        if (fallbackCache != null) {
            try {
                fallbackCache.close();
                log.debug("Closed fallback cache");
            } catch (Exception e) {
                log.error("Error closing fallback cache: {}", e.getMessage(), e);
            }
        }
        
        log.info("Firefly Cache Manager closed");
    }

    // ================================
    // Helper Methods
    // ================================

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

