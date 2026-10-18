package org.fireflyframework.web.error.service;

import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.models.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for caching error responses to reduce load during error storms.
 * <p>
 * This service uses the Firefly Cache Manager to store error responses based on a cache key
 * that includes the error code, HTTP status, and request path. This helps prevent
 * overwhelming the system when the same error occurs repeatedly.
 * </p>
 * <p>
 * Features:
 * - Configurable TTL and max size
 * - Automatic cache key generation
 * - Cache statistics tracking
 * - Thread-safe operations
 * - Uses unified Firefly caching abstraction (supports Caffeine, Redis, etc.)
 * </p>
 *
 * @author Firefly Team
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "firefly.web.error-handling",
        name = "enable-error-caching",
        havingValue = "true"
)
public class ErrorResponseCache {

    private static final String KEY_PREFIX = ":error:";

    private final FireflyCacheManager cacheManager;
    private final ErrorHandlingProperties properties;
    private final Duration ttl;

    // Statistics tracking
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    /**
     * Creates a new ErrorResponseCache with the specified configuration.
     *
     * @param cacheManager the Firefly cache manager
     * @param properties the error handling properties
     */
    public ErrorResponseCache(FireflyCacheManager cacheManager, ErrorHandlingProperties properties) {
        this.cacheManager = cacheManager;
        this.properties = properties;
        this.ttl = Duration.ofSeconds(properties.getErrorCacheTtlSeconds());

        log.info("Error response cache initialized");
        log.info("  • Cache type: {}", cacheManager.getCacheType());
        log.info("  • TTL: {}s", properties.getErrorCacheTtlSeconds());
        log.info("  • Max size: {}", properties.getErrorCacheMaxSize());
        log.info("  • Key prefix: {}", KEY_PREFIX);
    }

    /**
     * Gets a cached error response if available.
     *
     * @param errorCode the error code
     * @param status the HTTP status code
     * @param path the request path
     * @return Mono containing the cached error response, or empty if not found
     */
    public Mono<ErrorResponse> get(String errorCode, int status, String path) {
        String cacheKey = buildKey(errorCode, status, path);
        log.debug("Getting cached error for key: {}", cacheKey);

        return cacheManager.<String, ErrorResponse>get(cacheKey, ErrorResponse.class)
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        hitCount.incrementAndGet();
                        log.debug("Cache HIT for error: code={}, status={}, path={}", errorCode, status, path);
                        return Mono.just(optional.get());
                    } else {
                        missCount.incrementAndGet();
                        log.debug("Cache MISS for error: code={}, status={}, path={}", errorCode, status, path);
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error getting cached error response for key {}: {}", cacheKey, e.getMessage(), e);
                    missCount.incrementAndGet();
                    return Mono.empty();
                });
    }

    /**
     * Caches an error response.
     *
     * @param errorResponse the error response to cache
     * @return Mono that completes when the cache operation is done
     */
    public Mono<Void> put(ErrorResponse errorResponse) {
        if (errorResponse == null || errorResponse.getCode() == null) {
            log.warn("Cannot cache error response: null or missing error code");
            return Mono.empty();
        }

        String cacheKey = buildKey(
                errorResponse.getCode(),
                errorResponse.getStatus(),
                errorResponse.getPath()
        );

        log.debug("Caching error response: code={}, status={}, path={}",
                errorResponse.getCode(),
                errorResponse.getStatus(),
                errorResponse.getPath());

        return cacheManager.put(cacheKey, errorResponse, ttl)
                .doOnSuccess(v -> log.debug("Successfully cached error response for key: {}", cacheKey))
                .onErrorResume(e -> {
                    log.error("Error caching error response for key {}: {}", cacheKey, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * Invalidates a specific cache entry.
     *
     * @param errorCode the error code
     * @param status the HTTP status code
     * @param path the request path
     * @return Mono that completes when the invalidation is done
     */
    public Mono<Void> invalidate(String errorCode, int status, String path) {
        String cacheKey = buildKey(errorCode, status, path);
        log.debug("Invalidating cache entry: code={}, status={}, path={}", errorCode, status, path);

        return cacheManager.evict(cacheKey)
                .doOnSuccess(evicted -> log.debug("Successfully invalidated cache entry for key: {} (evicted={})", cacheKey, evicted))
                .then()
                .onErrorResume(e -> {
                    log.error("Error invalidating cache entry for key {}: {}", cacheKey, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * Clears all cached error responses.
     *
     * @return Mono that completes when the cache is cleared
     */
    public Mono<Void> clear() {
        log.info("Clearing error response cache");
        return cacheManager.clear()
                .doOnSuccess(v -> {
                    hitCount.set(0);
                    missCount.set(0);
                    log.info("Error response cache cleared");
                })
                .onErrorResume(e -> {
                    log.error("Error clearing cache: {}", e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * Gets cache statistics.
     *
     * @return cache statistics
     */
    public CacheStats getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;

        return new CacheStats(hits, misses, hitRate, 0, 0);
    }

    /**
     * Builds a cache key from error code, status, and path.
     */
    private String buildKey(String errorCode, int status, String path) {
        return KEY_PREFIX + errorCode + ":" + status + ":" + (path != null ? path : "");
    }

    /**
     * Cache statistics.
     */
    public record CacheStats(
            long hitCount,
            long missCount,
            double hitRate,
            long evictionCount,
            long size
    ) {
        @Override
        public String toString() {
            return String.format(
                    "CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d, size=%d}",
                    hitCount, missCount, hitRate * 100, evictionCount, size
            );
        }
    }
}

