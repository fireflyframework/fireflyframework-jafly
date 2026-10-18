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

/**
 * Enumeration of supported cache types.
 * <p>
 * This enum identifies different cache implementations available in the system.
 * Each cache type has different characteristics in terms of:
 * <ul>
 *   <li>Storage location (in-memory vs distributed)</li>
 *   <li>Persistence (volatile vs durable)</li>
 *   <li>Performance characteristics</li>
 *   <li>Scalability properties</li>
 * </ul>
 */
public enum CacheType {

    /**
     * Caffeine cache - High-performance in-memory cache.
     * <p>
     * Characteristics:
     * <ul>
     *   <li>In-memory only</li>
     *   <li>Very fast access times</li>
     *   <li>Not distributed</li>
     *   <li>Size-based and time-based eviction</li>
     *   <li>Supports async refresh</li>
     * </ul>
     */
    CAFFEINE("caffeine", "Caffeine In-Memory Cache"),

    /**
     * Redis cache - Distributed cache with persistence options.
     * <p>
     * Characteristics:
     * <ul>
     *   <li>Distributed/networked</li>
     *   <li>Optional persistence</li>
     *   <li>Supports clustering</li>
     *   <li>Rich data structures</li>
     *   <li>TTL support</li>
     * </ul>
     */
    REDIS("redis", "Redis Distributed Cache"),

    /**
     * Hazelcast cache - Distributed in-memory data grid.
     */
    HAZELCAST("hazelcast", "Hazelcast Distributed Cache"),

    /**
     * JCache (JSR-107) provider - Generic cache API (backed by Ehcache, Infinispan, etc.).
     */
    JCACHE("jcache", "JCache (JSR-107) Cache"),

    /**
     * PostgreSQL-backed distributed cache with persistence.
     */
    POSTGRES("postgres", "PostgreSQL Distributed Cache"),

    /**
     * No-operation cache - Disables caching.
     * <p>
     * Characteristics:
     * <ul>
     *   <li>No actual caching</li>
     *   <li>Always returns cache misses</li>
     *   <li>Useful for testing or disabling cache</li>
     * </ul>
     */
    NOOP("noop", "No-Op Cache"),

    /**
     * Automatic cache selection based on availability.
     * <p>
     * The system will automatically choose the best available cache:
     * <ol>
     *   <li>Redis if configured and available</li>
     *   <li>Hazelcast if available</li>
     *   <li>JCache if available</li>
     *   <li>Caffeine if none of the above are available</li>
     *   <li>No-Op if no cache is available</li>
     * </ol>
     */
    AUTO("auto", "Automatic Cache Selection");

    private final String identifier;
    private final String displayName;

    CacheType(String identifier, String displayName) {
        this.identifier = identifier;
        this.displayName = displayName;
    }

    /**
     * Gets the string identifier for this cache type.
     *
     * @return the cache type identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Gets the human-readable display name for this cache type.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this cache type represents a distributed cache.
     *
     * @return true if this is a distributed cache type
     */
    public boolean isDistributed() {
        return this == REDIS || this == POSTGRES;
    }

    /**
     * Checks if this cache type supports persistence.
     *
     * @return true if this cache type can persist data
     */
    public boolean supportsPersistence() {
        return this == REDIS || this == POSTGRES;
    }

    /**
     * Finds a cache type by its identifier.
     *
     * @param identifier the identifier to search for
     * @return the matching cache type, or null if not found
     */
    public static CacheType fromIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        
        for (CacheType type : values()) {
            if (type.identifier.equalsIgnoreCase(identifier)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName + " (" + identifier + ")";
    }
}