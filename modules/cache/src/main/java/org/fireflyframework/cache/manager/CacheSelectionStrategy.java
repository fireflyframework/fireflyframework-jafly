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

import java.util.Collection;
import java.util.Optional;

/**
 * Strategy interface for selecting the best cache from available options.
 * <p>
 * Implementations of this interface define different algorithms for choosing
 * which cache to use when multiple cache adapters are available.
 */
public interface CacheSelectionStrategy {

    /**
     * Selects the best cache from the available options.
     *
     * @param availableCaches collection of available cache adapters
     * @return the selected cache, or empty if no suitable cache is found
     */
    Optional<CacheAdapter> selectCache(Collection<CacheAdapter> availableCaches);

    /**
     * Gets the strategy name for identification purposes.
     *
     * @return the strategy name
     */
    default String getStrategyName() {
        return getClass().getSimpleName();
    }
}