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

package org.fireflyframework.cqrs.config;

import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.factory.CacheManagerFactory;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.cqrs.cache.QueryCacheAdapter;

import java.time.Duration;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.command.CommandHandlerRegistry;
import org.fireflyframework.cqrs.command.CommandMetricsService;
import org.fireflyframework.cqrs.command.CommandValidationService;
import org.fireflyframework.cqrs.command.DefaultCommandBus;
import org.fireflyframework.cqrs.query.DefaultQueryBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.cqrs.tracing.CorrelationContext;
import org.fireflyframework.cqrs.validation.AutoValidationProcessor;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for CQRS framework components.
 * Provides automatic setup of CommandBus, QueryBus, and related infrastructure.
 * Integrates with fireflyframework-cache for query result caching.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(CqrsProperties.class)
@ConditionalOnProperty(prefix = "firefly.cqrs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CqrsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AutoValidationProcessor autoValidationProcessor(@Autowired(required = false) Validator validator) {
        if (validator != null) {
            log.info("Configuring Jakarta validation processor for CQRS framework");
            return new AutoValidationProcessor(validator);
        } else {
            log.warn("Jakarta Validator not available - creating no-op validation processor");
            return new AutoValidationProcessor(null);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public io.micrometer.core.instrument.MeterRegistry meterRegistry() {
        log.info("Auto-configuring default SimpleMeterRegistry for CQRS metrics");
        return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationContext correlationContext() {
        log.info("Auto-configuring CorrelationContext for CQRS distributed tracing");
        return new CorrelationContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandHandlerRegistry commandHandlerRegistry(ApplicationContext applicationContext) {
        log.info("Configuring CQRS Command Handler Registry (auto-configured)");
        return new CommandHandlerRegistry(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandValidationService commandValidationService(AutoValidationProcessor autoValidationProcessor) {
        log.info("Configuring CQRS Command Validation Service (auto-configured)");
        return new CommandValidationService(autoValidationProcessor);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthorizationProperties authorizationProperties() {
        log.info("Configuring CQRS Authorization Properties (auto-configured)");
        return new AuthorizationProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        name = "firefly.cqrs.authorization.enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public org.fireflyframework.cqrs.authorization.AuthorizationService authorizationService(
            AuthorizationProperties authorizationProperties,
            @Autowired(required = false) org.fireflyframework.cqrs.authorization.AuthorizationMetrics authorizationMetrics) {
        log.info("Configuring CQRS Authorization Service (auto-configured)");
        return new org.fireflyframework.cqrs.authorization.AuthorizationService(
            authorizationProperties,
            java.util.Optional.ofNullable(authorizationMetrics)
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandMetricsService commandMetricsService(@Autowired(required = false) io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        log.info("Configuring CQRS Command Metrics Service (auto-configured)");
        return new CommandMetricsService(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandBus commandBus(CommandHandlerRegistry handlerRegistry,
                               CommandValidationService validationService,
                               @Autowired(required = false) org.fireflyframework.cqrs.authorization.AuthorizationService authorizationService,
                               CommandMetricsService metricsService,
                               CorrelationContext correlationContext) {
        if (authorizationService != null) {
            log.info("Configuring CQRS Command Bus with authorization enabled (auto-configured)");
        } else {
            log.info("Configuring CQRS Command Bus with authorization disabled (auto-configured)");
        }
        return new DefaultCommandBus(handlerRegistry, validationService, authorizationService, metricsService, correlationContext);
    }

    /**
     * Creates a dedicated cache manager for CQRS query results.
     * <p>
     * This cache manager is independent from other application caches,
     * with its own key prefix to avoid collisions.
     */
    @Bean("cqrsQueryCacheManager")
    @ConditionalOnBean(CacheManagerFactory.class)
    @ConditionalOnMissingBean(name = "cqrsQueryCacheManager")
    public FireflyCacheManager cqrsQueryCacheManager(CacheManagerFactory factory, CqrsProperties properties) {
        log.info("Creating dedicated CQRS query cache manager");
        
        String description = "CQRS Query Results Cache - Caches query handler results for improved performance";
        
        // Use AUTO to let fireflyframework-cache select the best available provider (Redis, Hazelcast, JCache, or Caffeine)
        // Default TTL comes from CQRS properties (firefly.cqrs.query.cache-ttl)
        Duration ttl = properties.getQuery() != null && properties.getQuery().getCacheTtl() != null
                ? properties.getQuery().getCacheTtl()
                : Duration.ofMinutes(15);
        
        return factory.createCacheManager(
                "cqrs-queries",
                CacheType.AUTO,
                "firefly:cqrs:queries",
                ttl,
                description,
                "fireflyframework-cqrs.CqrsAutoConfiguration"
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "cqrsQueryCacheManager")
    public QueryCacheAdapter queryCacheAdapter(
            @Qualifier("cqrsQueryCacheManager") FireflyCacheManager cacheManager) {
        log.info("Configuring CQRS Query Cache Adapter with dedicated cache manager");
        return new QueryCacheAdapter(cacheManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryBus queryBus(ApplicationContext applicationContext,
                           CorrelationContext correlationContext,
                           AutoValidationProcessor autoValidationProcessor,
                           @Autowired(required = false) org.fireflyframework.cqrs.authorization.AuthorizationService authorizationService,
                           @Autowired(required = false) QueryCacheAdapter cacheAdapter,
                           io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        if (authorizationService != null) {
            log.info("Configuring CQRS Query Bus with authorization enabled (auto-configured)");
        } else {
            log.info("Configuring CQRS Query Bus with authorization disabled (auto-configured)");
        }

        if (cacheAdapter != null) {
            log.info("CQRS Query Bus configured with cache support via fireflyframework-cache");
        } else {
            log.info("CQRS Query Bus configured without cache support (fireflyframework-cache not available)");
        }

        return new DefaultQueryBus(applicationContext, correlationContext, autoValidationProcessor,
                authorizationService, cacheAdapter, meterRegistry);
    }
}