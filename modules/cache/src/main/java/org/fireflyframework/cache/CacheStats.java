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

package org.fireflyframework.cache;

/**
 * Immutable statistics about a cache.
 *
 * @param hitCount        the number of cache hits
 * @param missCount       the number of cache misses
 * @param loadSuccessCount the number of successful cache loads
 * @param loadFailureCount the number of failed cache loads
 * @param evictionCount   the number of cache evictions
 * @param size            the current cache size
 *
 * @author Firefly Team
 * @since 1.0.0
 */
public record CacheStats(
    long hitCount,
    long missCount,
    long loadSuccessCount,
    long loadFailureCount,
    long evictionCount,
    long size
) {
    
    /**
     * Calculates the hit rate.
     *
     * @return the hit rate (0.0 to 1.0), or 0.0 if no requests
     */
    public double hitRate() {
        long totalRequests = hitCount + missCount;
        return totalRequests == 0 ? 0.0 : (double) hitCount / totalRequests;
    }

    /**
     * Calculates the miss rate.
     *
     * @return the miss rate (0.0 to 1.0), or 0.0 if no requests
     */
    public double missRate() {
        long totalRequests = hitCount + missCount;
        return totalRequests == 0 ? 0.0 : (double) missCount / totalRequests;
    }

    /**
     * Returns the total number of requests (hits + misses).
     *
     * @return the total request count
     */
    public long requestCount() {
        return hitCount + missCount;
    }

    /**
     * Creates an empty statistics object.
     *
     * @return empty cache statistics
     */
    public static CacheStats empty() {
        return new CacheStats(0, 0, 0, 0, 0, 0);
    }
}
