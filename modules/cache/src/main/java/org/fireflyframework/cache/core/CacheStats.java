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

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Statistics model for cache performance monitoring.
 * <p>
 * This class captures various metrics about cache performance including
 * hit/miss ratios, operation counts, and timing information.
 */
@Data
@Builder
public class CacheStats {

    /**
     * Total number of cache requests.
     */
    private final long requestCount;

    /**
     * Number of cache hits.
     */
    private final long hitCount;

    /**
     * Number of cache misses.
     */
    private final long missCount;

    /**
     * Number of cache loads (e.g., from a cache loader).
     */
    private final long loadCount;

    /**
     * Number of cache evictions.
     */
    private final long evictionCount;

    /**
     * Current number of entries in the cache.
     */
    private final long entryCount;

    /**
     * Average time taken for cache loads (in nanoseconds).
     */
    private final double averageLoadTime;

    /**
     * Estimated memory footprint of the cache (in bytes).
     * May not be available for all cache types.
     */
    private final long estimatedSize;

    /**
     * Timestamp when these statistics were captured.
     */
    private final Instant capturedAt;

    /**
     * Cache type these statistics belong to.
     */
    private final CacheType cacheType;

    /**
     * Cache name these statistics belong to.
     */
    private final String cacheName;

    /**
     * Calculates the cache hit rate.
     *
     * @return hit rate as a percentage (0.0 to 100.0)
     */
    public double getHitRate() {
        if (requestCount == 0) {
            return 0.0;
        }
        return (double) hitCount / requestCount * 100.0;
    }

    /**
     * Calculates the cache miss rate.
     *
     * @return miss rate as a percentage (0.0 to 100.0)
     */
    public double getMissRate() {
        if (requestCount == 0) {
            return 0.0;
        }
        return (double) missCount / requestCount * 100.0;
    }

    /**
     * Calculates the average load time in milliseconds.
     *
     * @return average load time in milliseconds
     */
    public double getAverageLoadTimeMillis() {
        return averageLoadTime / 1_000_000.0;
    }

    /**
     * Gets the estimated size in a human-readable format.
     *
     * @return formatted size string
     */
    public String getFormattedEstimatedSize() {
        if (estimatedSize < 1024) {
            return estimatedSize + " B";
        } else if (estimatedSize < 1024 * 1024) {
            return String.format("%.2f KB", estimatedSize / 1024.0);
        } else if (estimatedSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", estimatedSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", estimatedSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Creates a CacheStats with all zero values.
     *
     * @param cacheType the cache type
     * @param cacheName the cache name
     * @return empty cache stats
     */
    public static CacheStats empty(CacheType cacheType, String cacheName) {
        return CacheStats.builder()
                .requestCount(0)
                .hitCount(0)
                .missCount(0)
                .loadCount(0)
                .evictionCount(0)
                .entryCount(0)
                .averageLoadTime(0.0)
                .estimatedSize(0)
                .capturedAt(Instant.now())
                .cacheType(cacheType)
                .cacheName(cacheName)
                .build();
    }

    @Override
    public String toString() {
        return String.format(
            "CacheStats{cache='%s', type=%s, requests=%d, hits=%d (%.2f%%), misses=%d (%.2f%%), entries=%d, size=%s}",
            cacheName, cacheType.getIdentifier(), requestCount, hitCount, getHitRate(),
            missCount, getMissRate(), entryCount, getFormattedEstimatedSize()
        );
    }
}