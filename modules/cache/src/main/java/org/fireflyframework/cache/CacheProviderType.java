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
 * Enumeration of supported cache provider types.
 *
 * @author Firefly Team
 * @since 1.0.0
 */
public enum CacheProviderType {
    
    /**
     * Caffeine - High-performance, in-memory cache.
     */
    CAFFEINE("caffeine", "In-memory cache with advanced eviction policies"),
    
    /**
     * Redis - Distributed cache with persistence capabilities.
     */
    REDIS("redis", "Distributed cache with persistence and pub/sub"),
    
    /**
     * Auto - Automatically select the best available provider.
     */
    AUTO("auto", "Automatically select best available cache provider");

    private final String name;
    private final String description;

    CacheProviderType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }
}
