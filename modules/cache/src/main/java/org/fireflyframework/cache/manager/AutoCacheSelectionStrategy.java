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
import org.fireflyframework.cache.core.CacheType;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Automatic cache selection strategy that chooses the best available cache based on priority.
 * <p>
 * This strategy selects caches in the following priority order:
 * <ol>
 *   <li>Caffeine (fast, in-memory, default choice)</li>
 *   <li>Redis (distributed, persistent)</li>
 *   <li>Any other available cache</li>
 *   <li>No-op cache (fallback)</li>
 * </ol>
 * <p>
 * The strategy also considers cache availability - unhealthy caches are deprioritized.
 * By default, Caffeine is preferred for its performance and simplicity.
 */
@Slf4j
public class AutoCacheSelectionStrategy implements CacheSelectionStrategy {

    @Override
    public Optional<CacheAdapter> selectCache(Collection<CacheAdapter> availableCaches) {
        if (availableCaches.isEmpty()) {
            return Optional.empty();
        }

        // Find the best available cache based on priority and health
        return availableCaches.stream()
                .filter(CacheAdapter::isAvailable)
                .min(this::compareCaches)
                .or(() -> {
                    // If no healthy cache is found, select any cache
                    log.warn("No healthy cache found, selecting first available cache");
                    return availableCaches.stream().findFirst();
                });
    }

    /**
     * Compares two caches to determine which is better.
     * Lower values indicate higher priority.
     *
     * @param cache1 first cache to compare
     * @param cache2 second cache to compare
     * @return comparison result
     */
    private int compareCaches(CacheAdapter cache1, CacheAdapter cache2) {
        // First, compare by type priority
        int typePriority1 = getTypePriority(cache1.getCacheType());
        int typePriority2 = getTypePriority(cache2.getCacheType());
        
        if (typePriority1 != typePriority2) {
            return Integer.compare(typePriority1, typePriority2);
        }

        // If same type, compare by availability (healthy caches first)
        boolean available1 = cache1.isAvailable();
        boolean available2 = cache2.isAvailable();
        
        if (available1 && !available2) {
            return -1; // cache1 is better
        } else if (!available1 && available2) {
            return 1; // cache2 is better
        }

        // If same availability, compare by cache name for consistency
        return cache1.getCacheName().compareTo(cache2.getCacheName());
    }

    /**
     * Gets the priority value for a cache type.
     * Lower values indicate higher priority.
     *
     * @param cacheType the cache type
     * @return priority value
     */
    private int getTypePriority(CacheType cacheType) {
        return switch (cacheType) {
            case REDIS -> 1;       // Distributed and persistent
            case POSTGRES -> 1;    // Distributed and persistent
            case HAZELCAST -> 2;   // Distributed in-memory grid
            case JCACHE -> 3;      // Standard JCache provider
            case CAFFEINE -> 4;    // Fast in-memory (default when no distributed provider configured)
            case NOOP -> 999;      // Lowest priority - fallback only
            case AUTO -> 100;      // Should not happen, but handle gracefully
        };
    }

    @Override
    public String getStrategyName() {
        return "AutoCacheSelectionStrategy";
    }

    @Override
    public String toString() {
        return "AutoCacheSelectionStrategy{priority: Caffeine > Redis > Others > NoOp}";
    }
}