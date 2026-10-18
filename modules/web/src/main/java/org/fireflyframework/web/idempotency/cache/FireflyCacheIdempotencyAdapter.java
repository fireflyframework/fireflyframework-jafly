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

package org.fireflyframework.web.idempotency.cache;

import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.fireflyframework.web.idempotency.model.CachedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Adapter that bridges the IdempotencyCache interface with the Firefly Common Cache library.
 * This adapter wraps the FireflyCacheManager to provide idempotency caching functionality
 * using the unified caching abstraction from fireflyframework-cache.
 *
 * <p>This implementation delegates all cache operations to the underlying FireflyCacheManager,
 * which can be configured to use different cache providers (Caffeine, Redis, etc.) through
 * the fireflyframework-cache configuration.</p>
 *
 * <p>All cache keys are prefixed with ":idempotency:" which, combined with the cache's own
 * prefix (e.g., "firefly:cache:default:"), results in keys like "firefly:cache:default::idempotency:{key}".</p>
 */
public class FireflyCacheIdempotencyAdapter implements IdempotencyCache {

    private static final Logger log = LoggerFactory.getLogger(FireflyCacheIdempotencyAdapter.class);
    private static final String KEY_PREFIX = ":idempotency:";

    private final FireflyCacheManager cacheManager;
    private final Duration ttl;

    /**
     * Creates a new FireflyCacheIdempotencyAdapter.
     *
     * @param cacheManager the Firefly cache manager
     * @param properties the idempotency configuration properties
     */
    public FireflyCacheIdempotencyAdapter(FireflyCacheManager cacheManager, IdempotencyProperties properties) {
        this.cacheManager = cacheManager;
        this.ttl = Duration.ofHours(properties.getCache().getTtlHours());
    }

    @Override
    public Mono<CachedResponse> get(String key) {
        String prefixedKey = buildKey(key);
        log.debug("Getting cached response for key: {} (prefixed: {})", key, prefixedKey);

        return cacheManager.<String, CachedResponse>get(prefixedKey, CachedResponse.class)
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        log.debug("Cache hit for key: {}", key);
                        return Mono.just(optional.get());
                    } else {
                        log.debug("Cache miss for key: {}", key);
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error getting cached response for key {}: {}", key, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> put(String key, CachedResponse response) {
        String prefixedKey = buildKey(key);
        log.debug("Putting cached response for key: {} (prefixed: {})", key, prefixedKey);

        return cacheManager.put(prefixedKey, response, ttl)
                .doOnSuccess(v -> log.debug("Successfully cached response for key: {}", key))
                .onErrorResume(e -> {
                    log.error("Error caching response for key {}: {}", key, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * Builds a prefixed cache key for idempotency.
     * The cache adapter will add its own prefix (e.g., "firefly:cache:default:"), so this adds ":idempotency:"
     * resulting in final keys like: "firefly:cache:default::idempotency:{idempotencyKey}"
     *
     * @param key the original idempotency key
     * @return the prefixed cache key
     */
    private String buildKey(String key) {
        return KEY_PREFIX + key;
    }
}

