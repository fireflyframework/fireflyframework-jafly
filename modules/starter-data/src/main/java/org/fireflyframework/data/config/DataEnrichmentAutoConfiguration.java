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

package org.fireflyframework.data.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.data.cache.EnrichmentCacheKeyGenerator;
import org.fireflyframework.data.cache.EnrichmentCacheService;
import org.fireflyframework.data.cache.OperationCacheService;
import org.fireflyframework.data.cost.EnrichmentCostTracker;
import org.fireflyframework.data.enrichment.FallbackEnrichmentExecutor;
import org.fireflyframework.data.event.EnrichmentEventPublisher;
import org.fireflyframework.data.event.JobEventPublisher;
import org.fireflyframework.data.event.OperationEventPublisher;
import org.fireflyframework.data.health.JobOrchestratorHealthIndicator;
import org.fireflyframework.data.mapper.JobResultMapper;
import org.fireflyframework.data.mapper.JobResultMapperRegistry;
import org.fireflyframework.data.observability.JobMetricsService;
import org.fireflyframework.data.observability.JobTracingService;
import org.fireflyframework.data.operation.schema.JsonSchemaGenerator;
import org.fireflyframework.data.orchestration.port.JobOrchestrator;
import org.fireflyframework.data.resiliency.ProviderResiliencyRegistry;
import org.fireflyframework.data.resiliency.ResiliencyDecoratorService;
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataEnricherRegistry;
import org.fireflyframework.data.service.DataJobDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

/**
 * Auto-configuration for data enrichment components.
 *
 * <p>This configuration automatically sets up:</p>
 * <ul>
 *   <li>Data enrichment properties</li>
 *   <li>Data enricher registry for discovering enrichers</li>
 *   <li>Enrichment cache service (when cache is enabled and CacheAdapter is available)</li>
 *   <li>Cache key generator for tenant-isolated caching</li>
 *   <li>Job metrics and tracing services</li>
 *   <li>Event publishers for enrichment, operation, and job events</li>
 *   <li>Job result mapper registry</li>
 *   <li>Job orchestrator health indicator</li>
 *   <li>Data job discovery service</li>
 *   <li>JSON schema generator</li>
 * </ul>
 *
 * <p>The configuration is activated when:</p>
 * <ul>
 *   <li>The property {@code firefly.data.enrichment.enabled} is true (default)</li>
 *   <li>Or the property is not set (enabled by default)</li>
 * </ul>
 *
 * <p><b>Example Configuration:</b></p>
 * <pre>{@code
 * firefly:
 *   data:
 *     enrichment:
 *       enabled: true
 *       publish-events: true
 *       cache-enabled: true
 *       cache-ttl-seconds: 3600
 *       default-timeout-seconds: 30
 *       max-batch-size: 100
 *       batch-parallelism: 10
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(DataEnrichmentProperties.class)
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DataEnrichmentAutoConfiguration {

    public DataEnrichmentAutoConfiguration() {
        log.info("Initializing Data Enrichment Auto-Configuration");
    }

    /**
     * Creates the data enricher registry bean.
     *
     * <p>This registry automatically discovers all DataEnricher beans
     * and provides methods to look them up by provider name or enrichment type.</p>
     *
     * <p>Spring will inject all DataEnricher beans into the List parameter.</p>
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(
        prefix = "firefly.data.enrichment",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public DataEnricherRegistry dataEnricherRegistry(List<DataEnricher<?, ?, ?>> enrichers) {
        log.info("Creating DataEnricherRegistry bean with {} enrichers", enrichers.size());
        return new DataEnricherRegistry(enrichers);
    }

    /**
     * Creates the enrichment cache key generator bean.
     *
     * <p>This generator creates tenant-isolated cache keys for enrichment requests.</p>
     */
    @ConditionalOnMissingBean
    @Bean
    public EnrichmentCacheKeyGenerator enrichmentCacheKeyGenerator(ObjectMapper objectMapper) {
        log.info("Creating EnrichmentCacheKeyGenerator bean");
        return new EnrichmentCacheKeyGenerator(objectMapper);
    }

    /**
     * Creates the enrichment cache service bean.
     *
     * <p>This service is only created when:</p>
     * <ul>
     *   <li>Cache is enabled via firefly.data.enrichment.cache-enabled=true</li>
     *   <li>A CacheAdapter bean is available (from fireflyframework-cache)</li>
     * </ul>
     *
     * <p>The cache service provides tenant-isolated caching of enrichment results.</p>
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(
        prefix = "firefly.data.enrichment",
        name = "cache-enabled",
        havingValue = "true"
    )
    @ConditionalOnBean(org.fireflyframework.cache.manager.FireflyCacheManager.class)
    public EnrichmentCacheService enrichmentCacheService(
            org.fireflyframework.cache.manager.FireflyCacheManager cacheManager,
            EnrichmentCacheKeyGenerator keyGenerator,
            DataEnrichmentProperties properties) {
        log.info("Creating EnrichmentCacheService bean with cache type: {}", cacheManager.getCacheType());
        return new EnrichmentCacheService(cacheManager, keyGenerator, properties);
    }

    /**
     * Creates the operation cache service bean.
     *
     * <p>This service is only created when:</p>
     * <ul>
     *   <li>Operation cache is enabled via firefly.data.enrichment.operations.cache-enabled=true</li>
     *   <li>A CacheAdapter bean is available (from fireflyframework-cache)</li>
     * </ul>
     *
     * <p>The cache service provides tenant-isolated caching of provider operation results.</p>
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(
        prefix = "firefly.data.enrichment.operations",
        name = "cache-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnBean(org.fireflyframework.cache.manager.FireflyCacheManager.class)
    public OperationCacheService operationCacheService(
            org.fireflyframework.cache.manager.FireflyCacheManager cacheManager,
            ObjectMapper objectMapper,
            DataEnrichmentProperties properties) {
        log.info("Creating OperationCacheService bean with cache type: {}", cacheManager.getCacheType());
        return new OperationCacheService(cacheManager, objectMapper, properties);
    }

    /**
     * Creates the job metrics service bean.
     *
     * <p>This service records job-related metrics using Micrometer.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public JobMetricsService jobMetricsService(MeterRegistry meterRegistry,
                                               JobOrchestrationProperties properties) {
        log.info("Creating JobMetricsService bean");
        return new JobMetricsService(meterRegistry, properties);
    }

    /**
     * Creates the job tracing service bean.
     *
     * <p>This service adds distributed tracing to job operations.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public JobTracingService jobTracingService(ObservationRegistry observationRegistry,
                                               JobOrchestrationProperties properties) {
        log.info("Creating JobTracingService bean");
        return new JobTracingService(observationRegistry, properties);
    }

    /**
     * Creates the JSON schema generator bean.
     *
     * <p>This component generates JSON Schemas and example objects from Java classes.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaGenerator jsonSchemaGenerator(ObjectMapper objectMapper) {
        log.info("Creating JsonSchemaGenerator bean");
        return new JsonSchemaGenerator(objectMapper);
    }

    /**
     * Creates the job event publisher bean.
     *
     * <p>This service publishes job lifecycle events through Spring's event mechanism.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public JobEventPublisher jobEventPublisher(ApplicationEventPublisher eventPublisher,
                                               JobOrchestrationProperties properties) {
        log.info("Creating JobEventPublisher bean");
        return new JobEventPublisher(eventPublisher, properties);
    }

    /**
     * Creates the enrichment event publisher bean.
     *
     * <p>This service publishes enrichment lifecycle events through Spring's event mechanism.
     * It is only created when {@code firefly.data.enrichment.publish-events} is true (default).</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "firefly.data.enrichment",
        name = "publish-events",
        havingValue = "true",
        matchIfMissing = true
    )
    public EnrichmentEventPublisher enrichmentEventPublisher(ApplicationEventPublisher eventPublisher) {
        log.info("Creating EnrichmentEventPublisher bean");
        return new EnrichmentEventPublisher(eventPublisher);
    }

    /**
     * Creates the operation event publisher bean.
     *
     * <p>This service publishes provider operation lifecycle events through Spring's event mechanism.
     * It is only created when {@code firefly.data.enrichment.operations.publish-events} is true (default).</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "firefly.data.enrichment.operations",
        name = "publish-events",
        havingValue = "true",
        matchIfMissing = true
    )
    public OperationEventPublisher operationEventPublisher(ApplicationEventPublisher eventPublisher) {
        log.info("Creating OperationEventPublisher bean");
        return new OperationEventPublisher(eventPublisher);
    }

    /**
     * Creates the data job discovery service bean.
     *
     * <p>This service discovers and logs all registered DataJobs at application startup.
     * It uses {@code @EventListener} to react to {@code ApplicationReadyEvent}.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public DataJobDiscoveryService dataJobDiscoveryService(ApplicationContext applicationContext) {
        log.info("Creating DataJobDiscoveryService bean");
        return new DataJobDiscoveryService(applicationContext);
    }

    /**
     * Creates the job result mapper registry bean.
     *
     * <p>This registry automatically discovers all {@link JobResultMapper} beans and makes them
     * available for the RESULT stage transformation.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public JobResultMapperRegistry jobResultMapperRegistry(List<JobResultMapper<?, ?>> mappers) {
        log.info("Creating JobResultMapperRegistry bean with {} mapper(s)", mappers.size());
        return new JobResultMapperRegistry(mappers);
    }

    /**
     * Creates the job orchestrator health indicator bean.
     *
     * <p>This health indicator reports the status of the job orchestrator.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public JobOrchestratorHealthIndicator jobOrchestratorHealthIndicator(
            Optional<JobOrchestrator> orchestrator,
            JobOrchestrationProperties properties) {
        log.info("Creating JobOrchestratorHealthIndicator bean");
        return new JobOrchestratorHealthIndicator(orchestrator, properties);
    }

    /**
     * Creates the fallback enrichment executor bean.
     *
     * <p>This executor supports automatic fallback chain execution when a primary
     * enricher fails or returns empty results, as declared via {@code @EnricherFallback}.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public FallbackEnrichmentExecutor fallbackEnrichmentExecutor(DataEnricherRegistry registry) {
        log.info("Creating FallbackEnrichmentExecutor bean");
        return new FallbackEnrichmentExecutor(registry);
    }

    /**
     * Creates the provider resiliency registry bean.
     *
     * <p>This registry manages per-provider Resilience4j instances, allowing each data
     * provider to have independent circuit breaker, retry, rate limiter, bulkhead, and
     * timeout configurations. Providers without explicit configuration fall back to the
     * global {@link ResiliencyDecoratorService}.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public ProviderResiliencyRegistry providerResiliencyRegistry(
            DataEnrichmentProperties properties,
            ResiliencyDecoratorService defaultService) {
        log.info("Creating ProviderResiliencyRegistry bean with {} provider configs",
                properties.getProviders().size());
        return new ProviderResiliencyRegistry(properties.getProviders(), defaultService);
    }

    /**
     * Creates the enrichment cost tracker bean.
     *
     * <p>This tracker records call counts and costs per provider for enrichment
     * operations. Provider costs can be registered dynamically at runtime via
     * {@code EnrichmentCostTracker#registerProvider}.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public EnrichmentCostTracker enrichmentCostTracker() {
        log.info("Creating EnrichmentCostTracker bean");
        return new EnrichmentCostTracker();
    }
}
