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

package org.fireflyframework.eventsourcing.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Configuration for Circuit Breaker pattern using Resilience4j.
 * Provides fault tolerance for event store operations, outbox processing, and projections.
 */
@AutoConfiguration
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(prefix = "firefly.eventsourcing.resilience.circuit-breaker", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class CircuitBreakerAutoConfiguration {

    /**
     * Creates a circuit breaker registry with default configurations.
     */
    @ConditionalOnMissingBean
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.info("Initializing Circuit Breaker Registry for Event Sourcing");
        return CircuitBreakerRegistry.ofDefaults();
    }

    /**
     * Circuit breaker for event store operations (append, load, query).
     */
    @ConditionalOnMissingBean
    @Bean
    public CircuitBreaker eventStoreCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open circuit if 50% of calls fail
                .slowCallRateThreshold(50) // Open circuit if 50% of calls are slow
                .slowCallDurationThreshold(Duration.ofSeconds(5)) // Call is slow if > 5s
                .waitDurationInOpenState(Duration.ofSeconds(60)) // Wait 60s before half-open
                .permittedNumberOfCallsInHalfOpenState(10) // Allow 10 calls in half-open
                .minimumNumberOfCalls(20) // Need 20 calls before calculating rates
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(100) // Use last 100 calls for rate calculation
                .recordExceptions(
                        Exception.class // Record all exceptions
                )
                .ignoreExceptions(
                        IllegalArgumentException.class, // Don't count validation errors
                        IllegalStateException.class
                )
                .build();

        CircuitBreaker circuitBreaker = registry.circuitBreaker("eventStore", config);
        
        // Add event listeners for monitoring
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                        log.warn("Event Store Circuit Breaker state transition: {} -> {}", 
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onError(event -> 
                        log.error("Event Store Circuit Breaker recorded error: {}", 
                                event.getThrowable().getMessage()))
                .onSuccess(event -> 
                        log.debug("Event Store Circuit Breaker recorded success"))
                .onCallNotPermitted(event -> 
                        log.warn("Event Store Circuit Breaker call not permitted - circuit is OPEN"));

        log.info("Created Event Store Circuit Breaker with config: failureRate={}%, slowCallRate={}%, waitDuration={}ms",
                config.getFailureRateThreshold(),
                config.getSlowCallRateThreshold(),
                config.getWaitIntervalFunctionInOpenState().apply(1));

        return circuitBreaker;
    }

    /**
     * Circuit breaker for event outbox processing.
     */
    @ConditionalOnMissingBean
    @Bean
    public CircuitBreaker outboxCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60) // More tolerant for outbox (async)
                .slowCallRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Shorter wait for outbox
                .permittedNumberOfCallsInHalfOpenState(5)
                .minimumNumberOfCalls(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(120) // 2 minutes window
                .build();

        CircuitBreaker circuitBreaker = registry.circuitBreaker("outbox", config);
        
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                        log.warn("Outbox Circuit Breaker state transition: {} -> {}", 
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onCallNotPermitted(event -> 
                        log.warn("Outbox Circuit Breaker call not permitted - circuit is OPEN"));

        log.info("Created Outbox Circuit Breaker with config: failureRate={}%, waitDuration={}ms",
                config.getFailureRateThreshold(),
                config.getWaitIntervalFunctionInOpenState().apply(1));

        return circuitBreaker;
    }

    /**
     * Circuit breaker for projection processing.
     */
    @ConditionalOnMissingBean
    @Bean
    public CircuitBreaker projectionCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(70) // Very tolerant for projections (eventual consistency)
                .slowCallRateThreshold(70)
                .slowCallDurationThreshold(Duration.ofSeconds(15))
                .waitDurationInOpenState(Duration.ofSeconds(45))
                .permittedNumberOfCallsInHalfOpenState(5)
                .minimumNumberOfCalls(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(300) // 5 minutes window
                .build();

        CircuitBreaker circuitBreaker = registry.circuitBreaker("projection", config);
        
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                        log.warn("Projection Circuit Breaker state transition: {} -> {}", 
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onCallNotPermitted(event -> 
                        log.warn("Projection Circuit Breaker call not permitted - circuit is OPEN"));

        log.info("Created Projection Circuit Breaker with config: failureRate={}%, waitDuration={}ms",
                config.getFailureRateThreshold(),
                config.getWaitIntervalFunctionInOpenState().apply(1));

        return circuitBreaker;
    }
}

