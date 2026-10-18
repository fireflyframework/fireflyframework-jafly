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

package org.fireflyframework.data.resiliency;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Registry that manages per-provider Resilience4j instances.
 *
 * <p>Each data provider can have independent resilience configurations
 * (circuit breaker, retry, rate limiter, bulkhead, timeout). Providers
 * without explicit configuration fall back to the global
 * {@link ResiliencyDecoratorService}.</p>
 *
 * <p>Decoration is applied in order: bulkhead, rate limiter, circuit breaker,
 * retry, timeout.</p>
 */
@Slf4j
public class ProviderResiliencyRegistry {

    private final ResiliencyDecoratorService defaultService;
    private final Map<String, ProviderResiliencyInstances> providerInstances;

    public ProviderResiliencyRegistry(Map<String, ProviderResiliencyConfig> providerConfigs,
                                      ResiliencyDecoratorService defaultService) {
        this.defaultService = defaultService;
        this.providerInstances = new ConcurrentHashMap<>();

        providerConfigs.forEach((providerName, config) -> {
            ProviderResiliencyInstances instances = createInstances(providerName, config);
            providerInstances.put(providerName, instances);
            log.info("Registered resilience configuration for provider '{}': "
                            + "circuitBreaker={}, retry={}, rateLimiter={}, bulkhead={}, timeoutMs={}",
                    providerName,
                    config.isCircuitBreakerEnabled(),
                    config.isRetryEnabled(),
                    config.isRateLimiterEnabled(),
                    config.isBulkheadEnabled(),
                    config.getTimeoutMs());
        });

        log.info("Initialized ProviderResiliencyRegistry with {} provider-specific configurations", providerInstances.size());
    }

    /**
     * Decorates an operation with provider-specific resiliency patterns.
     *
     * <p>If the provider has explicit configuration, its dedicated Resilience4j
     * instances are applied. Otherwise, the global default service is used.</p>
     *
     * @param providerName the name of the data provider
     * @param operation    the reactive operation to decorate
     * @param <T>          the return type
     * @return the decorated operation
     */
    public <T> Mono<T> decorate(String providerName, Mono<T> operation) {
        ProviderResiliencyInstances instances = providerInstances.get(providerName);

        if (instances == null) {
            log.debug("No provider-specific config for '{}', falling back to default service", providerName);
            return defaultService.decorate(operation);
        }

        return applyProviderResiliency(providerName, operation, instances);
    }

    /**
     * Returns whether a provider-specific configuration exists.
     *
     * @param providerName the name of the data provider
     * @return true if the provider has explicit resilience configuration
     */
    public boolean hasProviderConfig(String providerName) {
        return providerInstances.containsKey(providerName);
    }

    private <T> Mono<T> applyProviderResiliency(String providerName, Mono<T> operation,
                                                  ProviderResiliencyInstances instances) {
        Mono<T> decorated = operation;

        // Apply bulkhead first (controls concurrency)
        if (instances.bulkhead() != null) {
            log.debug("Applying bulkhead to provider '{}' operation", providerName);
            decorated = decorated.transformDeferred(BulkheadOperator.of(instances.bulkhead()));
        }

        // Apply rate limiter
        if (instances.rateLimiter() != null) {
            log.debug("Applying rate limiter to provider '{}' operation", providerName);
            decorated = decorated.transformDeferred(RateLimiterOperator.of(instances.rateLimiter()));
        }

        // Apply circuit breaker
        if (instances.circuitBreaker() != null) {
            log.debug("Applying circuit breaker to provider '{}' operation", providerName);
            decorated = decorated.transformDeferred(CircuitBreakerOperator.of(instances.circuitBreaker()));
        }

        // Apply retry
        if (instances.retry() != null) {
            log.debug("Applying retry to provider '{}' operation", providerName);
            decorated = decorated.transformDeferred(RetryOperator.of(instances.retry()));
        }

        // Apply timeout last
        decorated = decorated.timeout(Duration.ofMillis(instances.timeoutMs()));

        return decorated;
    }

    private ProviderResiliencyInstances createInstances(String providerName, ProviderResiliencyConfig config) {
        CircuitBreaker circuitBreaker = null;
        Retry retry = null;
        RateLimiter rateLimiter = null;
        Bulkhead bulkhead = null;

        if (config.isCircuitBreakerEnabled()) {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(config.getCircuitBreakerFailureRateThreshold())
                    .slidingWindowSize(config.getCircuitBreakerSlidingWindowSize())
                    .waitDurationInOpenState(Duration.ofMillis(config.getCircuitBreakerWaitDurationInOpenStateMs()))
                    .build();
            circuitBreaker = CircuitBreaker.of(providerName, cbConfig);
        }

        if (config.isRetryEnabled()) {
            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(config.getRetryMaxAttempts())
                    .waitDuration(Duration.ofMillis(config.getRetryWaitDurationMs()))
                    .retryExceptions(Exception.class)
                    .ignoreExceptions(TimeoutException.class)
                    .build();
            retry = Retry.of(providerName, retryConfig);
        }

        if (config.isRateLimiterEnabled()) {
            RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                    .limitForPeriod(config.getRateLimitForPeriod())
                    .limitRefreshPeriod(Duration.ofMillis(config.getRateLimitRefreshPeriodMs()))
                    .timeoutDuration(Duration.ZERO)
                    .build();
            rateLimiter = RateLimiter.of(providerName, rlConfig);
        }

        if (config.isBulkheadEnabled()) {
            BulkheadConfig bhConfig = BulkheadConfig.custom()
                    .maxConcurrentCalls(config.getBulkheadMaxConcurrentCalls())
                    .build();
            bulkhead = Bulkhead.of(providerName, bhConfig);
        }

        return new ProviderResiliencyInstances(
                circuitBreaker, retry, rateLimiter, bulkhead, config.getTimeoutMs()
        );
    }

    /**
     * Holds the Resilience4j instances for a single provider.
     */
    private record ProviderResiliencyInstances(
            CircuitBreaker circuitBreaker,
            Retry retry,
            RateLimiter rateLimiter,
            Bulkhead bulkhead,
            long timeoutMs
    ) {}
}
