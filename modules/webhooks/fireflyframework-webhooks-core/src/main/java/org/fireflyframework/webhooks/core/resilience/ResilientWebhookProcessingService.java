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

package org.fireflyframework.webhooks.core.resilience;

import org.fireflyframework.webhooks.core.services.WebhookProcessingService;
import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.fireflyframework.webhooks.interfaces.dto.WebhookResponseDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

/**
 * Decorator service that adds resilience patterns (Circuit Breaker, Time Limiter) 
 * to the webhook processing service.
 * <p>
 * This service wraps the core WebhookProcessingService and adds:
 * - Circuit Breaker: Prevents cascading failures when Kafka is down
 * - Time Limiter: Enforces timeout on Kafka publish operations
 * <p>
 * Note: We don't implement fallback mechanisms here because we rely on lib-common-eda
 * for message broker abstraction. The circuit breaker will fail fast when Kafka is unhealthy,
 * and the application should handle this gracefully (e.g., return 503 Service Unavailable).
 */
@Service
@Primary
@Slf4j
public class ResilientWebhookProcessingService implements WebhookProcessingService {

    private static final String CIRCUIT_BREAKER_NAME = "webhookKafkaPublisher";
    private static final String TIME_LIMITER_NAME = "webhookKafkaPublisher";

    private final WebhookProcessingService delegate;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    public ResilientWebhookProcessingService(
            @Qualifier("webhookProcessingServiceImpl") WebhookProcessingService delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(TIME_LIMITER_NAME);
        
        log.info("Initialized ResilientWebhookProcessingService with Circuit Breaker and Time Limiter");
    }

    @Override
    public Mono<WebhookResponseDTO> processWebhook(WebhookEventDTO eventDto) {
        log.debug("Processing webhook with resilience patterns: eventId={}, provider={}",
                eventDto.getEventId(), eventDto.getProviderName());

        return delegate.processWebhook(eventDto)
                // Apply time limiter first (inner decorator)
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                // Then apply circuit breaker (outer decorator)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                // Handle resilience-specific errors
                .onErrorResume(this::handleResilienceError);
    }

    /**
     * Handles errors from resilience patterns.
     * Converts resilience4j exceptions to appropriate HTTP responses.
     *
     * @param error the error
     * @return Mono with error response
     */
    private Mono<WebhookResponseDTO> handleResilienceError(Throwable error) {
        if (error instanceof TimeoutException) {
            log.error("Webhook processing timed out: {}", error.getMessage());
            return Mono.error(new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Webhook processing timed out. The message broker may be slow or unavailable."
            ));
        }

        if (error instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            log.error("Circuit breaker is OPEN - rejecting webhook: {}", error.getMessage());
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Webhook processing is temporarily unavailable. The message broker is experiencing issues. Please retry later."
            ));
        }

        // For other errors, let them propagate
        log.error("Error in resilient webhook processing: {}", error.getMessage(), error);
        return Mono.error(error);
    }

    /**
     * Gets the current state of the circuit breaker.
     * Useful for health checks and monitoring.
     *
     * @return the circuit breaker state
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    /**
     * Gets circuit breaker metrics.
     * Useful for monitoring dashboards.
     *
     * @return the circuit breaker metrics
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }
}

