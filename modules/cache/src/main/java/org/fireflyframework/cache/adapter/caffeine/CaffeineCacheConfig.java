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

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration class for Caffeine cache adapter.
 * <p>
 * This class encapsulates all configuration options available for the Caffeine cache,
 * including size limits, expiration policies, and performance tuning options.
 */
@Data
@Builder
public class CaffeineCacheConfig {

    /**
     * Key prefix for all cache entries.
     * This prefix will be prepended to all keys stored in the cache.
     */
    @Builder.Default
    private final String keyPrefix = "firefly:cache";

    /**
     * Maximum number of entries the cache may contain.
     * If not set, the cache will grow without size-based bounds.
     */
    private final Long maximumSize;

    /**
     * Specifies that entries should be automatically removed from the cache
     * once a fixed duration has elapsed after the entry's creation, or the
     * most recent replacement of its value.
     */
    private final Duration expireAfterWrite;

    /**
     * Specifies that entries should be automatically removed from the cache
     * once a fixed duration has elapsed after the entry's creation, the most
     * recent replacement of its value, or its last read.
     */
    private final Duration expireAfterAccess;

    /**
     * Specifies that active entries are eligible for automatic refresh once
     * a fixed duration has elapsed after the entry's creation or the most
     * recent replacement of its value.
     */
    private final Duration refreshAfterWrite;

    /**
     * Enables the accumulation of cache statistics during the operation
     * of the cache. Without this, the cache statistics will be disabled.
     */
    @Builder.Default
    private final boolean recordStats = true;

    /**
     * Specifies that each key (not value) stored in the cache should be
     * wrapped in a WeakReference.
     */
    @Builder.Default
    private final boolean weakKeys = false;

    /**
     * Specifies that each value (not key) stored in the cache should be
     * wrapped in a WeakReference.
     */
    @Builder.Default
    private final boolean weakValues = false;

    /**
     * Specifies that each value (not key) stored in the cache should be
     * wrapped in a SoftReference.
     */
    @Builder.Default
    private final boolean softValues = false;

    /**
     * Creates a default Caffeine cache configuration.
     *
     * @return default configuration with sensible defaults
     */
    public static CaffeineCacheConfig defaultConfig() {
        return CaffeineCacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats(true)
                .build();
    }

    /**
     * Creates a high-performance Caffeine cache configuration.
     * <p>
     * This configuration is optimized for speed with larger size limits
     * and longer expiration times.
     *
     * @return high-performance configuration
     */
    public static CaffeineCacheConfig highPerformanceConfig() {
        return CaffeineCacheConfig.builder()
                .maximumSize(10000L)
                .expireAfterAccess(Duration.ofHours(2))
                .recordStats(true)
                .build();
    }

    /**
     * Creates a memory-efficient Caffeine cache configuration.
     * <p>
     * This configuration is optimized for low memory usage with smaller
     * size limits and shorter expiration times.
     *
     * @return memory-efficient configuration
     */
    public static CaffeineCacheConfig memoryEfficientConfig() {
        return CaffeineCacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(Duration.ofMinutes(15))
                .softValues(true)
                .recordStats(true)
                .build();
    }

    /**
     * Creates a configuration for long-lived cache entries.
     * <p>
     * This configuration is suitable for data that doesn't change often
     * and can be cached for extended periods.
     *
     * @return long-lived cache configuration
     */
    public static CaffeineCacheConfig longLivedConfig() {
        return CaffeineCacheConfig.builder()
                .maximumSize(5000L)
                .expireAfterWrite(Duration.ofDays(1))
                .recordStats(true)
                .build();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CaffeineCacheConfig{");
        
        if (maximumSize != null) {
            sb.append("maxSize=").append(maximumSize);
        }
        
        if (expireAfterWrite != null) {
            sb.append(", expireAfterWrite=").append(expireAfterWrite);
        }
        
        if (expireAfterAccess != null) {
            sb.append(", expireAfterAccess=").append(expireAfterAccess);
        }
        
        if (refreshAfterWrite != null) {
            sb.append(", refreshAfterWrite=").append(refreshAfterWrite);
        }
        
        sb.append(", recordStats=").append(recordStats);
        
        if (weakKeys) {
            sb.append(", weakKeys=true");
        }
        
        if (weakValues) {
            sb.append(", weakValues=true");
        }
        
        if (softValues) {
            sb.append(", softValues=true");
        }
        
        sb.append("}");
        return sb.toString();
    }
}