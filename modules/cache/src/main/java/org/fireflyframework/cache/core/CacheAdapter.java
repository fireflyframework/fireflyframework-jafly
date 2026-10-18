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

package org.fireflyframework.cache.core;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Unified interface for cache operations across different cache providers.
 * <p>
 * This interface provides a reactive API for caching operations that can be
 * implemented by various cache providers including Caffeine, Redis, and others.
 * <p>
 * The interface is designed to be:
 * <ul>
 *   <li>Reactive - Returns Mono for non-blocking operations</li>
 *   <li>Type-safe - Uses generics for key and value types</li>
 *   <li>Flexible - Supports TTL and conditional operations</li>
 *   <li>Observable - Provides hooks for metrics and monitoring</li>
 * </ul>
 */
public interface CacheAdapter {

    /**
     * Retrieves a value from the cache.
     *
     * @param key the cache key
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing the value if present, or empty if not found
     */
    <K, V> Mono<Optional<V>> get(K key);

    /**
     * Retrieves a value from the cache with a specific value type.
     *
     * @param key the cache key
     * @param valueType the expected value type
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing the value if present and of correct type, or empty
     */
    <K, V> Mono<Optional<V>> get(K key, Class<V> valueType);

    /**
     * Stores a value in the cache.
     *
     * @param key the cache key
     * @param value the value to store
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono that completes when the value is stored
     */
    <K, V> Mono<Void> put(K key, V value);

    /**
     * Stores a value in the cache with a time-to-live.
     *
     * @param key the cache key
     * @param value the value to store
     * @param ttl the time-to-live for the entry
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono that completes when the value is stored
     */
    <K, V> Mono<Void> put(K key, V value, Duration ttl);

    /**
     * Stores a value in the cache only if the key is not already present.
     *
     * @param key the cache key
     * @param value the value to store
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing true if the value was stored, false if key already existed
     */
    <K, V> Mono<Boolean> putIfAbsent(K key, V value);

    /**
     * Stores a value in the cache only if the key is not already present, with TTL.
     *
     * @param key the cache key
     * @param value the value to store
     * @param ttl the time-to-live for the entry
     * @param <K> the key type
     * @param <V> the value type
     * @return a Mono containing true if the value was stored, false if key already existed
     */
    <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl);

    /**
     * Removes a value from the cache.
     *
     * @param key the cache key
     * @param <K> the key type
     * @return a Mono containing true if the key was removed, false if it wasn't present
     */
    <K> Mono<Boolean> evict(K key);

    /**
     * Removes all entries from the cache.
     *
     * @return a Mono that completes when the cache is cleared
     */
    Mono<Void> clear();

    /**
     * Evicts all cache entries whose keys start with the given prefix.
     * <p>
     * Default implementation scans all keys and evicts matching ones.
     * Cache implementations (e.g., Redis) should override this with
     * native pattern-based eviction for better performance.
     *
     * @param keyPrefix the key prefix to match
     * @return a Mono containing the number of evicted entries
     */
    default Mono<Long> evictByPrefix(String keyPrefix) {
        return this.<String>keys()
            .flatMap(allKeys -> {
                var matching = allKeys.stream()
                    .filter(k -> k.toString().startsWith(keyPrefix))
                    .toList();
                if (matching.isEmpty()) {
                    return Mono.just(0L);
                }
                return reactor.core.publisher.Flux.fromIterable(matching)
                    .flatMap(this::evict)
                    .filter(Boolean::booleanValue)
                    .count();
            });
    }

    /**
     * Checks if a key exists in the cache.
     *
     * @param key the cache key
     * @param <K> the key type
     * @return a Mono containing true if the key exists, false otherwise
     */
    <K> Mono<Boolean> exists(K key);

    /**
     * Gets all keys in the cache.
     * Note: This operation may be expensive for distributed caches.
     *
     * @param <K> the key type
     * @return a Mono containing a set of all keys
     */
    <K> Mono<Set<K>> keys();

    /**
     * Gets the size of the cache.
     * Note: This operation may be expensive for distributed caches.
     *
     * @return a Mono containing the number of entries in the cache
     */
    Mono<Long> size();

    /**
     * Gets statistics about the cache performance.
     *
     * @return a Mono containing cache statistics
     */
    Mono<CacheStats> getStats();

    /**
     * Gets the cache type identifier.
     *
     * @return the cache type
     */
    CacheType getCacheType();

    /**
     * Gets the cache name.
     *
     * @return the cache name
     */
    String getCacheName();

    /**
     * Checks if this cache adapter is available and properly configured.
     *
     * @return true if the cache is ready for use
     */
    boolean isAvailable();

    /**
     * Gets health information about this cache.
     *
     * @return a Mono containing health status
     */
    Mono<CacheHealth> getHealth();

    /**
     * Performs any necessary cleanup when the cache is no longer needed.
     */
    void close();
}