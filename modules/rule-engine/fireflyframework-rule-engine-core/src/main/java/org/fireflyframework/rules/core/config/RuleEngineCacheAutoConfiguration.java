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

package org.fireflyframework.rules.core.config;

import org.fireflyframework.cache.config.CacheAutoConfiguration;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.factory.CacheManagerFactory;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Auto-configuration for rule engine cache.
 * <p>
 * Creates a dedicated cache manager for rule definitions, AST models, constants, and validation results.
 * <p>
 * The rule engine cache uses:
 * <ul>
 *   <li>Key prefix: {@code firefly:rules:engine}</li>
 *   <li>TTL: 2 hours (rules are relatively stable but need periodic refresh)</li>
 *   <li>Preferred type: REDIS (for distributed rule evaluation)</li>
 *   <li>Fallback: Caffeine (for single-instance deployments)</li>
 * </ul>
 */
@AutoConfiguration
@AutoConfigureAfter(CacheAutoConfiguration.class)
@ConditionalOnClass({FireflyCacheManager.class, CacheManagerFactory.class})
@Slf4j
public class RuleEngineCacheAutoConfiguration {

    private static final String RULE_CACHE_KEY_PREFIX = "firefly:rules:engine";
    private static final Duration RULE_CACHE_TTL = Duration.ofHours(2);

    public RuleEngineCacheAutoConfiguration() {
        log.info("RuleEngineCacheAutoConfiguration loaded");
    }

    /**
     * Creates a dedicated cache manager for rule engine data.
     * <p>
     * This cache manager is independent from other application caches,
     * providing isolation for rule definitions, AST models, and validation results.
     *
     * @param factory the cache manager factory
     * @return a dedicated cache manager for rule engine
     */
    @Bean("ruleEngineCacheManager")
    @ConditionalOnBean(CacheManagerFactory.class)
    @ConditionalOnMissingBean(name = "ruleEngineCacheManager")
    public FireflyCacheManager ruleEngineCacheManager(CacheManagerFactory factory) {
        String description = String.format(
                "Rule Engine Cache - Stores rule definitions, AST models, constants, and validation results (TTL: %d hours)",
                RULE_CACHE_TTL.toHours()
        );

        // Use AUTO to select the best available provider (Redis, Hazelcast, JCache, or Caffeine)
        return factory.createCacheManager(
                "rule-engine",
                CacheType.AUTO,
                RULE_CACHE_KEY_PREFIX,
                RULE_CACHE_TTL,
                description,
                "rule-engine-core.RuleEngineCacheAutoConfiguration"
        );
    }
}
