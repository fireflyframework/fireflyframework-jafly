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

package org.fireflyframework.webhooks.processor.config;

import org.fireflyframework.cache.config.CacheAutoConfiguration;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.factory.CacheManagerFactory;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.webhooks.processor.idempotency.CacheBasedWebhookIdempotencyService;
import org.fireflyframework.webhooks.processor.port.WebhookIdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Auto-configuration for webhook idempotency using a dedicated cache.
 * <p>
 * This configuration creates a dedicated cache manager specifically for webhook event idempotency,
 * completely isolated from other caches (like HTTP idempotency) to avoid conflicts.
 * <p>
 * The webhook idempotency cache uses:
 * <ul>
 *   <li>Key prefix: {@code firefly:webhooks:idempotency}</li>
 *   <li>TTL: 7 days (to track processed events)</li>
 *   <li>Preferred type: REDIS (for distributed workers)</li>
 *   <li>Fallback: Caffeine (for single-instance deployments)</li>
 * </ul>
 * <p>
 * <b>Example Configuration:</b>
 * <pre>
 * firefly:
 *   cache:
 *     enabled: true
 *     default-cache-type: REDIS
 *     redis:
 *       host: ${REDIS_HOST:localhost}
 *       port: ${REDIS_PORT:6379}
 *       database: 0
 * </pre>
 */
@AutoConfiguration
@AutoConfigureAfter(CacheAutoConfiguration.class)
@ConditionalOnClass({FireflyCacheManager.class, CacheManagerFactory.class})
@Slf4j
public class WebhookIdempotencyAutoConfiguration {

    private static final String WEBHOOK_CACHE_KEY_PREFIX = "firefly:webhooks:idempotency";
    private static final Duration WEBHOOK_CACHE_TTL = Duration.ofDays(7);

    // No-arg constructor

    /**
     * Creates a dedicated cache manager for webhook event idempotency.
     * <p>
     * This cache manager is independent from:
     * <ul>
     *   <li>HTTP idempotency cache (firefly:http:idempotency)</li>
     *   <li>Default application cache (firefly:cache:default)</li>
     *   <li>Any other application-specific caches</li>
     * </ul>
     * <p>
     * This isolation prevents cache key collisions and allows independent configuration
     * and scaling of different cache concerns.
     *
     * @param factory the cache manager factory
     * @return a dedicated cache manager for webhook idempotency
     */
    @Bean("webhookIdempotencyCacheManager")
    @ConditionalOnMissingBean(name = "webhookIdempotencyCacheManager")
    public FireflyCacheManager webhookIdempotencyCacheManager(CacheManagerFactory factory) {
        return factory.createCacheManager(
                "webhook-idempotency",
                CacheType.AUTO,
                WEBHOOK_CACHE_KEY_PREFIX,
                WEBHOOK_CACHE_TTL,
                "Webhook idempotency cache",
                "webhooks"
        );
    }

    /**
     * Creates the WebhookIdempotencyService using the dedicated webhook cache manager.
     * <p>
     * This service provides idempotency guarantees for webhook event processing,
     * ensuring events are processed exactly once even in distributed worker scenarios.
     *
     * @param cacheManager the dedicated webhook idempotency cache manager
     * @return the webhook idempotency service
     */
    @Bean
    @ConditionalOnBean(name = "webhookIdempotencyCacheManager")
    @ConditionalOnMissingBean(WebhookIdempotencyService.class)
    public WebhookIdempotencyService webhookIdempotencyService(
            @Qualifier("webhookIdempotencyCacheManager") FireflyCacheManager cacheManager) {
        return new CacheBasedWebhookIdempotencyService(cacheManager);
    }
}
