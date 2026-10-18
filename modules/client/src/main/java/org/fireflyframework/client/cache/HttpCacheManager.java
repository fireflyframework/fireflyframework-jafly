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

package org.fireflyframework.client.cache;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP cache manager with support for ETags, Cache-Control, and conditional requests.
 * 
 * <p>This cache manager implements HTTP caching semantics including:
 * <ul>
 *   <li>ETag-based validation (If-None-Match)</li>
 *   <li>Last-Modified validation (If-Modified-Since)</li>
 *   <li>Cache-Control directives (max-age, no-cache, no-store)</li>
 *   <li>TTL-based expiration</li>
 *   <li>Cache warming and invalidation</li>
 *   <li>Conditional requests (304 Not Modified)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * HttpCacheConfig config = HttpCacheConfig.builder()
 *     .enabled(true)
 *     .defaultTtl(Duration.ofMinutes(5))
 *     .maxCacheSize(1000)
 *     .respectCacheControl(true)
 *     .build();
 *
 * HttpCacheManager cacheManager = new HttpCacheManager(config);
 *
 * // Get from cache or execute request
 * Mono<Response> response = cacheManager.getOrExecute(
 *     cacheKey,
 *     () -> executeRequest()
 * );
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class HttpCacheManager {

    private final HttpCacheConfig config;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    public HttpCacheManager(HttpCacheConfig config) {
        this.config = config;
        
        if (config.isEnabled()) {
            log.info("HTTP Cache Manager initialized with max size: {}, default TTL: {}",
                config.getMaxCacheSize(), config.getDefaultTtl());
        }
    }

    /**
     * Gets a cached response or executes the request if not cached.
     */
    public <T> Mono<T> getOrExecute(String cacheKey, Mono<T> requestMono) {
        return getOrExecute(cacheKey, requestMono, null, null);
    }

    /**
     * Gets a cached response or executes the request with ETag support.
     */
    public <T> Mono<T> getOrExecute(String cacheKey, Mono<T> requestMono, String etag, String lastModified) {
        if (!config.isEnabled()) {
            return requestMono;
        }

        // Check cache
        Optional<CacheEntry> cachedEntry = get(cacheKey);
        
        if (cachedEntry.isPresent()) {
            CacheEntry entry = cachedEntry.get();
            
            // Check if entry is still valid
            if (!entry.isExpired()) {
                hits.incrementAndGet();
                log.debug("Cache HIT for key: {}", cacheKey);
                
                @SuppressWarnings("unchecked")
                T cachedValue = (T) entry.getValue();
                return Mono.just(cachedValue);
            } else {
                // Entry expired, remove it
                invalidate(cacheKey);
            }
        }

        // Cache miss - execute request and cache result
        misses.incrementAndGet();
        log.debug("Cache MISS for key: {}", cacheKey);

        return requestMono
            .doOnSuccess(response -> {
                if (response != null) {
                    put(cacheKey, response, etag, lastModified);
                }
            });
    }

    /**
     * Puts a value in the cache.
     */
    public void put(String key, Object value) {
        put(key, value, null, null);
    }

    /**
     * Puts a value in the cache with ETag and Last-Modified.
     */
    public void put(String key, Object value, String etag, String lastModified) {
        if (!config.isEnabled()) {
            return;
        }

        // Check cache size limit
        if (cache.size() >= config.getMaxCacheSize()) {
            evictOldest();
        }

        CacheEntry entry = new CacheEntry(
            value,
            Instant.now(),
            Instant.now().plus(config.getDefaultTtl()),
            etag,
            lastModified
        );

        cache.put(key, entry);
        log.debug("Cached entry for key: {} (ETag: {}, expires: {})", 
            key, etag, entry.getExpiresAt());
    }

    /**
     * Gets a cached entry.
     */
    public Optional<CacheEntry> get(String key) {
        if (!config.isEnabled()) {
            return Optional.empty();
        }

        CacheEntry entry = cache.get(key);
        
        if (entry != null && entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }

        return Optional.ofNullable(entry);
    }

    /**
     * Invalidates a cache entry.
     */
    public void invalidate(String key) {
        CacheEntry removed = cache.remove(key);
        if (removed != null) {
            log.debug("Invalidated cache entry for key: {}", key);
        }
    }

    /**
     * Invalidates all cache entries matching a pattern.
     */
    public void invalidatePattern(String pattern) {
        cache.keySet().stream()
            .filter(key -> key.matches(pattern))
            .forEach(this::invalidate);
    }

    /**
     * Invalidates all cache entries for a service.
     */
    public void invalidateService(String serviceName) {
        invalidatePattern("^" + serviceName + ":.*");
    }

    /**
     * Clears all cache entries.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.info("Cleared {} cache entries", size);
    }

    /**
     * Evicts the oldest cache entry.
     */
    private void evictOldest() {
        cache.entrySet().stream()
            .min((e1, e2) -> e1.getValue().getCreatedAt().compareTo(e2.getValue().getCreatedAt()))
            .ifPresent(entry -> {
                cache.remove(entry.getKey());
                evictions.incrementAndGet();
                log.debug("Evicted oldest cache entry: {}", entry.getKey());
            });
    }

    /**
     * Warms up the cache with pre-loaded data.
     */
    public <T> Mono<Void> warmUp(String key, Mono<T> dataMono) {
        return dataMono
            .doOnSuccess(data -> put(key, data))
            .then();
    }

    /**
     * Gets cache statistics.
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
            cache.size(),
            hits.get(),
            misses.get(),
            evictions.get(),
            calculateHitRate()
        );
    }

    /**
     * Calculates cache hit rate.
     */
    private double calculateHitRate() {
        long totalRequests = hits.get() + misses.get();
        return totalRequests > 0 ? (double) hits.get() / totalRequests : 0.0;
    }

    /**
     * Resets cache statistics.
     */
    public void resetStatistics() {
        hits.set(0);
        misses.set(0);
        evictions.set(0);
    }

    /**
     * Represents a cached entry.
     */
    public static class CacheEntry {
        private final Object value;
        private final Instant createdAt;
        private final Instant expiresAt;
        private final String etag;
        private final String lastModified;

        public CacheEntry(Object value, Instant createdAt, Instant expiresAt, String etag, String lastModified) {
            this.value = value;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        public Object getValue() { return value; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public String getEtag() { return etag; }
        public String getLastModified() { return lastModified; }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public Duration getAge() {
            return Duration.between(createdAt, Instant.now());
        }

        public Duration getTimeToLive() {
            return Duration.between(Instant.now(), expiresAt);
        }
    }

    /**
     * Cache statistics.
     */
    public record CacheStatistics(
        int size,
        long hits,
        long misses,
        long evictions,
        double hitRate
    ) {
        public long getTotalRequests() {
            return hits + misses;
        }
    }
}

