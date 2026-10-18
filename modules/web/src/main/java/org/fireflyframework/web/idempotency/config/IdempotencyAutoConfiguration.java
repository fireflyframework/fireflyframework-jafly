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

package org.fireflyframework.web.idempotency.config;

import org.fireflyframework.cache.config.CacheAutoConfiguration;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.factory.CacheManagerFactory;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.web.idempotency.cache.FireflyCacheIdempotencyAdapter;
import org.fireflyframework.web.idempotency.cache.IdempotencyCache;
import org.fireflyframework.web.idempotency.filter.IdempotencyWebFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * Auto-configuration for the idempotency module using fireflyframework-cache.
 *
 * <p>This configuration automatically sets up idempotency support when fireflyframework-cache
 * is available on the classpath. It creates the necessary beans to enable request
 * idempotency using the X-Idempotency-Key header.</p>
 *
 * <p>The cache provider (Caffeine, Redis, etc.) is configured through fireflyframework-cache
 * properties (firefly.cache.*). This configuration only handles the idempotency-specific
 * setup.</p>
 *
 * <p><strong>Cache Isolation:</strong> This creates a dedicated cache named "http-idempotency"
 * with its own isolated key prefix "firefly:web:idempotency". This is distinct from webhook
 * idempotency caches (e.g., "webhook-idempotency") that may be created by microservices.
 * Each cache serves a specific purpose and they coexist safely without collision.</p>
 *
 * <p>OpenAPI customizers are configured separately in {@link IdempotencyOpenAPIAutoConfiguration}
 * so they can be available even when caching is not configured.</p>
 *
 * <p>Configuration example:</p>
 * <pre>
 * firefly:
 *   web:
 *     idempotency:
 *       header-name: X-Idempotency-Key
 *       cache:
 *         ttl-hours: 24
 *
 *   cache:
 *     enabled: true
 *     default-cache-type: CAFFEINE  # or REDIS
 *     caffeine:
 *       default:
 *         maximum-size: 10000
 *         expire-after-write: PT24H
 *         record-stats: true
 * </pre>
 */
@AutoConfiguration(after = CacheAutoConfiguration.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnClass({FireflyCacheManager.class, CacheManagerFactory.class})
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "firefly.web.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAutoConfiguration.class);

    // No-arg constructor

    /**
     * Creates a dedicated cache manager for HTTP idempotency.
     * <p>
     * This cache manager is independent from other cache managers in the application,
     * with its own key prefix "firefly:web:idempotency" to avoid collisions.
     * <p>
     * Uses the CacheManagerFactory to create the cache with proper isolation.
     * <p>
     * <strong>Important:</strong> This HTTP idempotency cache is distinct from webhook
     * idempotency caches (e.g., "webhook-idempotency" created by webhooks microservices).
     * Each cache serves a different purpose:
     * <ul>
     *   <li>http-idempotency: Generic HTTP request deduplication for all endpoints</li>
     *   <li>webhook-idempotency: Webhook-specific event deduplication</li>
     * </ul>
     * Both caches can coexist safely due to unique names and key prefixes.
     *
     * @param factory the cache manager factory
     * @param properties the idempotency configuration properties
     * @return a dedicated cache manager for HTTP idempotency
     */
    @Bean("httpIdempotencyCacheManager")
    @ConditionalOnMissingBean(name = "httpIdempotencyCacheManager")
    public FireflyCacheManager httpIdempotencyCacheManager(
            CacheManagerFactory factory,
            IdempotencyProperties properties) {

        Duration ttl = Duration.ofHours(properties.getCache().getTtlHours());
        return factory.createCacheManager(
                "http-idempotency",
                CacheType.AUTO,
                "firefly:web:idempotency",
                ttl,
                "HTTP idempotency cache",
                "fireflyframework-web"
        );
    }

    /**
     * Creates the IdempotencyCache bean using the dedicated HTTP idempotency cache manager.
     * <p>
     * This ensures HTTP idempotency is completely isolated from other caches in the application.
     *
     * @param cacheManager the dedicated HTTP idempotency cache manager
     * @param properties the idempotency configuration properties
     * @return the idempotency cache implementation
     */
    @Bean
    @ConditionalOnBean(name = "httpIdempotencyCacheManager")
    @ConditionalOnMissingBean(IdempotencyCache.class)
    public IdempotencyCache idempotencyCache(
            @Qualifier("httpIdempotencyCacheManager") FireflyCacheManager cacheManager,
            IdempotencyProperties properties) {
        return new FireflyCacheIdempotencyAdapter(cacheManager, properties);
    }

    /**
     * Creates the IdempotencyWebFilter bean.
     *
     * <p>This filter intercepts HTTP requests and provides idempotency support
     * for POST, PUT, and PATCH requests that include the X-Idempotency-Key header.</p>
     *
     * <p>This bean is only created when an IdempotencyCache bean exists.</p>
     *
     * @param properties the idempotency configuration properties
     * @param cache the idempotency cache implementation
     * @return the idempotency web filter
     */
    @Bean
    @ConditionalOnBean(IdempotencyCache.class)
    @ConditionalOnMissingBean(IdempotencyWebFilter.class)
    public IdempotencyWebFilter idempotencyWebFilter(
            IdempotencyProperties properties,
            IdempotencyCache cache) {
        return new IdempotencyWebFilter(properties, cache);
    }
}

