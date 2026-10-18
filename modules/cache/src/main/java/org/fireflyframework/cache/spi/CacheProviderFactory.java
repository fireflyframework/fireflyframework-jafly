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

/*
 * CacheProviderFactory SPI for pluggable cache providers.
 */
package org.fireflyframework.cache.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheAdapter;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.properties.CacheProperties;

import java.time.Duration;

/**
 * Service Provider Interface for creating cache adapters.
 * Implementations are discovered via ServiceLoader and used by CacheManagerFactory.
 */
public interface CacheProviderFactory {

    /** Provider type. */
    CacheType getType();

    /** Lower is higher priority (e.g., Redis 10, Hazelcast 20, JCache 30, Caffeine 40). */
    int priority();

    /**
     * Checks availability given the context (dependencies/beans may be null when not present).
     */
    boolean isAvailable(ProviderContext ctx);

    /**
     * Creates a cache adapter instance.
     */
    CacheAdapter create(String cacheName, String keyPrefix, Duration defaultTtl, ProviderContext ctx);

    /** Context passed to providers. */
    class ProviderContext {
        public final CacheProperties properties;
        public final ObjectMapper objectMapper;
        public final Object redisConnectionFactory; // ReactiveRedisConnectionFactory
        public final Object hazelcastInstance;      // HazelcastInstance
        public final Object jcacheManager;          // javax/jakarta CacheManager
        public final Object r2dbcConnectionFactory; // io.r2dbc.spi.ConnectionFactory (Postgres)

        public ProviderContext(CacheProperties properties,
                               ObjectMapper objectMapper,
                               Object redisConnectionFactory,
                               Object hazelcastInstance,
                               Object jcacheManager) {
            this(properties, objectMapper, redisConnectionFactory, hazelcastInstance, jcacheManager, null);
        }

        public ProviderContext(CacheProperties properties,
                               ObjectMapper objectMapper,
                               Object redisConnectionFactory,
                               Object hazelcastInstance,
                               Object jcacheManager,
                               Object r2dbcConnectionFactory) {
            this.properties = properties;
            this.objectMapper = objectMapper;
            this.redisConnectionFactory = redisConnectionFactory;
            this.hazelcastInstance = hazelcastInstance;
            this.jcacheManager = jcacheManager;
            this.r2dbcConnectionFactory = r2dbcConnectionFactory;
        }
    }
}
