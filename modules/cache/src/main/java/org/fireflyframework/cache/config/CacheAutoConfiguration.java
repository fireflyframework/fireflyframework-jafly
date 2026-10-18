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

package org.fireflyframework.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheAdapter;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.cache.properties.CacheProperties;
import org.fireflyframework.cache.serialization.CacheSerializer;
import org.fireflyframework.cache.serialization.JsonCacheSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Core auto-configuration for the Firefly Cache library.
 * <p>
 * This configuration class automatically sets up the cache library when included
 * in a Spring Boot application. It enables configuration properties, sets up
 * component scanning, and provides default beans where needed.
 * <p>
 * Components automatically discovered and configured:
 * <ul>
 *   <li>Caffeine cache adapter (always available)</li>
 *   <li>Cache manager with selection strategy</li>
 *   <li>Serialization support</li>
 *   <li>Health indicators for Spring Boot Actuator</li>
 *   <li>Metrics collection via Micrometer</li>
 * </ul>
 * <p>
 * <b>Note:</b> Distributed providers (Redis, Hazelcast, JCache, Postgres) are
 * optional and shipped as separate adapter modules. When their dependencies/beans
 * are present on the classpath, they are discovered via the {@code CacheProviderFactory}
 * SPI and the reflectively-resolved provider beans wired by this configuration.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheProperties.class)
@EnableAsync
@Slf4j
public class CacheAutoConfiguration {

    // No-arg constructor

    /**
     * Provides a default ObjectMapper for JSON serialization if none exists.
     */
    @Bean("cacheObjectMapper")
    @ConditionalOnMissingBean(name = "cacheObjectMapper")
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Provides a default JSON cache serializer.
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheSerializer cacheSerializer(@Qualifier("cacheObjectMapper") ObjectMapper objectMapper) {
        log.debug("Creating JSON cache serializer");
        return new JsonCacheSerializer(objectMapper);
    }

    /**
     * Creates the CacheManagerFactory that can create multiple independent cache managers.
     * <p>
     * This factory is used by other modules (like fireflyframework-web, microservices, etc.) to create
     * their own cache managers with independent configurations and key prefixes.
     * <p>
     * Distributed provider beans (Redis, Hazelcast, JCache, R2DBC/Postgres) are resolved
     * reflectively so that core has no compile-time dependency on any provider. When an
     * adapter module is present on the classpath and contributes the corresponding bean,
     * the matching {@code CacheProviderFactory} SPI implementation participates automatically.
     */
    @Bean
    @ConditionalOnMissingBean
    public org.fireflyframework.cache.factory.CacheManagerFactory cacheManagerFactory(
            CacheProperties properties,
            @Qualifier("cacheObjectMapper") ObjectMapper objectMapper,
            org.springframework.context.ApplicationContext applicationContext) {
        log.info("Creating CacheManagerFactory");

        // Resolve optional Redis ReactiveRedisConnectionFactory via reflection
        Object redisConnectionFactory = null;
        try {
            Class<?> redisClazz = Class.forName("org.springframework.data.redis.connection.ReactiveRedisConnectionFactory");
            redisConnectionFactory = applicationContext.getBeanProvider(redisClazz).getIfAvailable();
        } catch (ClassNotFoundException ignored) { }

        // Resolve optional HazelcastInstance via reflection
        Object hazelcastInstance = null;
        try {
            Class<?> hzClazz = Class.forName("com.hazelcast.core.HazelcastInstance");
            hazelcastInstance = applicationContext.getBeanProvider(hzClazz).getIfAvailable();
        } catch (ClassNotFoundException ignored) { }

        // Resolve optional JCache CacheManager (javax or jakarta)
        Object jcacheManager = null;
        try {
            Class<?> jcacheClazz;
            try {
                jcacheClazz = Class.forName("javax.cache.CacheManager");
            } catch (ClassNotFoundException ex) {
                jcacheClazz = Class.forName("jakarta.cache.CacheManager");
            }
            jcacheManager = applicationContext.getBeanProvider(jcacheClazz).getIfAvailable();
        } catch (ClassNotFoundException ignored) { }

        // Resolve optional R2DBC ConnectionFactory (Postgres) via reflection
        Object r2dbcConnectionFactory = null;
        try {
            Class<?> cf = Class.forName("io.r2dbc.spi.ConnectionFactory");
            r2dbcConnectionFactory = applicationContext.getBeanProvider(cf).getIfAvailable();
        } catch (ClassNotFoundException ignored) { }

        return new org.fireflyframework.cache.factory.CacheManagerFactory(
                properties,
                objectMapper,
                redisConnectionFactory,
                hazelcastInstance,
                jcacheManager,
                r2dbcConnectionFactory
        );
    }

    /**
     * Creates a default FireflyCacheManager bean using the factory.
     * <p>
     * This provides a default cache manager that applications can use out of the box.
     * Applications can also inject the CacheManagerFactory to create additional cache managers.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public FireflyCacheManager fireflyCacheManager(org.fireflyframework.cache.factory.CacheManagerFactory factory) {
        log.info("Creating default FireflyCacheManager");
        return factory.createDefaultCacheManager("default");
    }

}
