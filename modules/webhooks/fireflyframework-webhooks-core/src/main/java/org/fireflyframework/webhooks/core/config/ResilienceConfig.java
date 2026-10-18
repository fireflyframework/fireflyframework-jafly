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

package org.fireflyframework.webhooks.core.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Configuration for Resilience4j patterns: Circuit Breaker, Rate Limiter, Time Limiter, Retry.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class ResilienceConfig {

    private final RetryProperties retryProperties;

    /**
     * Circuit Breaker Registry with default configuration.
     * Used for protecting Kafka publishing operations.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open circuit if 50% of calls fail
                .slowCallRateThreshold(50) // Open circuit if 50% of calls are slow
                .slowCallDurationThreshold(Duration.ofSeconds(5)) // Call is slow if > 5s
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before half-open
                .permittedNumberOfCallsInHalfOpenState(5) // Allow 5 calls in half-open
                .minimumNumberOfCalls(10) // Need 10 calls before calculating rates
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20) // Use last 20 calls for rate calculation
                .recordExceptions(Exception.class) // Record all exceptions
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        
        // Add event listeners for monitoring
        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    CircuitBreaker cb = event.getAddedEntry();
                    cb.getEventPublisher()
                            .onStateTransition(e -> log.warn("Circuit Breaker '{}' state changed: {} -> {}",
                                    cb.getName(), e.getStateTransition().getFromState(), 
                                    e.getStateTransition().getToState()))
                            .onError(e -> log.error("Circuit Breaker '{}' recorded error: {}",
                                    cb.getName(), e.getThrowable().getMessage()))
                            .onSuccess(e -> log.debug("Circuit Breaker '{}' recorded success",
                                    cb.getName()));
                });

        return registry;
    }

    /**
     * Rate Limiter Registry with default configuration.
     * Used for limiting webhook ingestion rate per provider.
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100) // Allow 100 requests
                .limitRefreshPeriod(Duration.ofSeconds(1)) // Per second
                .timeoutDuration(Duration.ofMillis(500)) // Wait max 500ms for permission
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        
        // Add event listeners for monitoring
        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    RateLimiter rl = event.getAddedEntry();
                    rl.getEventPublisher()
                            .onSuccess(e -> log.debug("Rate Limiter '{}' allowed request", rl.getName()))
                            .onFailure(e -> log.warn("Rate Limiter '{}' rejected request", rl.getName()));
                });

        return registry;
    }

    /**
     * Time Limiter Registry with default configuration.
     * Used for enforcing timeouts on Kafka publishing operations.
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10)) // Timeout after 10 seconds
                .cancelRunningFuture(true) // Cancel the running future on timeout
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(config);
        
        // Add event listeners for monitoring
        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    TimeLimiter tl = event.getAddedEntry();
                    tl.getEventPublisher()
                            .onSuccess(e -> log.debug("Time Limiter '{}' completed successfully", tl.getName()))
                            .onError(e -> log.error("Time Limiter '{}' failed: {}",
                                    tl.getName(), e.getThrowable().getMessage()))
                            .onTimeout(e -> log.warn("Time Limiter '{}' timed out", tl.getName()));
                });

        return registry;
    }

    /**
     * Retry Registry with configurable exponential backoff and jitter.
     * Used for retrying failed webhook processing operations.
     */
    @Bean
    public RetryRegistry retryRegistry() {
        // Get default retry configuration
        RetryProperties.RetryConfig defaultConfig = retryProperties.getDefaults();

        // Build Resilience4j RetryConfig from our properties
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(defaultConfig.getMaxAttempts())
                .intervalFunction(attempt -> {
                    // Exponential backoff with jitter
                    long baseDelay = (long) (defaultConfig.getInitialDelay().toMillis() *
                            Math.pow(defaultConfig.getMultiplier(), attempt - 1));

                    // Apply max delay cap
                    baseDelay = Math.min(baseDelay, defaultConfig.getMaxDelay().toMillis());

                    // Apply jitter if enabled
                    if (defaultConfig.isEnableJitter()) {
                        double jitter = Math.random() * defaultConfig.getJitterFactor();
                        baseDelay = (long) (baseDelay * (1 + jitter));
                    }

                    return baseDelay;
                })
                .retryExceptions(
                        TimeoutException.class,
                        java.net.ConnectException.class,
                        java.io.IOException.class
                )
                .build();

        RetryRegistry registry = RetryRegistry.of(config);

        // Add event listeners for monitoring
        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    Retry retry = event.getAddedEntry();
                    retry.getEventPublisher()
                            .onRetry(e -> log.warn("Retry '{}' attempt #{}: {}",
                                    retry.getName(), e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()))
                            .onSuccess(e -> log.debug("Retry '{}' succeeded after {} attempts",
                                    retry.getName(), e.getNumberOfRetryAttempts()))
                            .onError(e -> log.error("Retry '{}' exhausted after {} attempts: {}",
                                    retry.getName(), e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()));
                });

        return registry;
    }

    /**
     * Gets or creates a Retry instance for a specific provider.
     *
     * @param retryRegistry the retry registry
     * @param providerName the provider name
     * @return the retry instance
     */
    public Retry getRetryForProvider(RetryRegistry retryRegistry, String providerName) {
        String retryName = "webhook-" + providerName;

        // Check if retry already exists
        if (retryRegistry.getAllRetries().stream().anyMatch(r -> r.getName().equals(retryName))) {
            return retryRegistry.retry(retryName);
        }

        // Get provider-specific configuration
        RetryProperties.RetryConfig providerConfig = retryProperties.getConfigForProvider(providerName);

        // Build provider-specific RetryConfig
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(providerConfig.getMaxAttempts())
                .intervalFunction(attempt -> {
                    long baseDelay = (long) (providerConfig.getInitialDelay().toMillis() *
                            Math.pow(providerConfig.getMultiplier(), attempt - 1));
                    baseDelay = Math.min(baseDelay, providerConfig.getMaxDelay().toMillis());

                    if (providerConfig.isEnableJitter()) {
                        double jitter = Math.random() * providerConfig.getJitterFactor();
                        baseDelay = (long) (baseDelay * (1 + jitter));
                    }

                    return baseDelay;
                })
                .retryExceptions(
                        TimeoutException.class,
                        java.net.ConnectException.class,
                        java.io.IOException.class
                )
                .build();

        return retryRegistry.retry(retryName, config);
    }
}

