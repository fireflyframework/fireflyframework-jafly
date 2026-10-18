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

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for HTTP caching.
 * 
 * <p>This configuration defines caching behavior including TTL, cache size limits,
 * Cache-Control directive handling, and cache invalidation strategies.
 *
 * <p>Example usage:
 * <pre>{@code
 * HttpCacheConfig config = HttpCacheConfig.builder()
 *     .enabled(true)
 *     .defaultTtl(Duration.ofMinutes(5))
 *     .maxCacheSize(1000)
 *     .respectCacheControl(true)
 *     .cacheGetOnly(true)
 *     .build();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Data
@Builder
public class HttpCacheConfig {

    /**
     * Whether caching is enabled.
     * Default: false
     */
    @Builder.Default
    private boolean enabled = false;

    /**
     * Default TTL for cached entries.
     * Default: 5 minutes
     */
    @Builder.Default
    private Duration defaultTtl = Duration.ofMinutes(5);

    /**
     * Maximum number of entries in the cache.
     * Default: 1000
     */
    @Builder.Default
    private int maxCacheSize = 1000;

    /**
     * Whether to respect Cache-Control headers from responses.
     * Default: true
     */
    @Builder.Default
    private boolean respectCacheControl = true;

    /**
     * Whether to only cache GET requests.
     * Default: true
     */
    @Builder.Default
    private boolean cacheGetOnly = true;

    /**
     * Whether to cache responses with errors (4xx, 5xx).
     * Default: false
     */
    @Builder.Default
    private boolean cacheErrors = false;

    /**
     * Whether to use ETags for cache validation.
     * Default: true
     */
    @Builder.Default
    private boolean useEtags = true;

    /**
     * Whether to use Last-Modified for cache validation.
     * Default: true
     */
    @Builder.Default
    private boolean useLastModified = true;

    /**
     * Services to include in caching (empty = all services).
     */
    @Builder.Default
    private Set<String> includedServices = new HashSet<>();

    /**
     * Services to exclude from caching.
     */
    @Builder.Default
    private Set<String> excludedServices = new HashSet<>();

    /**
     * Endpoints to exclude from caching (regex patterns).
     */
    @Builder.Default
    private Set<String> excludedEndpoints = new HashSet<>();

    /**
     * Cache storage type.
     */
    @Builder.Default
    private CacheStorageType storageType = CacheStorageType.IN_MEMORY;

    /**
     * Redis configuration (if using Redis storage).
     */
    private RedisCacheConfig redisConfig;

    /**
     * Cache storage types.
     */
    public enum CacheStorageType {
        IN_MEMORY,      // Local in-memory cache
        REDIS,          // Distributed Redis cache
        HAZELCAST,      // Distributed Hazelcast cache
        CAFFEINE        // Caffeine cache (high-performance)
    }

    /**
     * Redis cache configuration.
     */
    @Data
    @Builder
    public static class RedisCacheConfig {
        private String host;
        private int port;
        private String password;
        private int database;
        private Duration timeout;
        private String keyPrefix;
    }

    /**
     * Checks if a service should be cached.
     */
    public boolean shouldCache(String serviceName) {
        if (!enabled) {
            return false;
        }

        if (!excludedServices.isEmpty() && excludedServices.contains(serviceName)) {
            return false;
        }

        if (!includedServices.isEmpty()) {
            return includedServices.contains(serviceName);
        }

        return true;
    }

    /**
     * Checks if an endpoint should be cached.
     */
    public boolean shouldCacheEndpoint(String endpoint) {
        if (!enabled) {
            return false;
        }

        return excludedEndpoints.stream()
            .noneMatch(endpoint::matches);
    }

    /**
     * Creates a default configuration (disabled).
     */
    public static HttpCacheConfig disabled() {
        return HttpCacheConfig.builder()
            .enabled(false)
            .build();
    }

    /**
     * Creates a configuration for aggressive caching.
     */
    public static HttpCacheConfig aggressive() {
        return HttpCacheConfig.builder()
            .enabled(true)
            .defaultTtl(Duration.ofMinutes(15))
            .maxCacheSize(5000)
            .respectCacheControl(false)
            .cacheGetOnly(true)
            .build();
    }

    /**
     * Creates a configuration for conservative caching.
     */
    public static HttpCacheConfig conservative() {
        return HttpCacheConfig.builder()
            .enabled(true)
            .defaultTtl(Duration.ofMinutes(1))
            .maxCacheSize(500)
            .respectCacheControl(true)
            .cacheGetOnly(true)
            .build();
    }
}

