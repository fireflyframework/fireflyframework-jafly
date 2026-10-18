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

package org.fireflyframework.webhooks.core.health;

import org.fireflyframework.webhooks.core.resilience.ResilientWebhookProcessingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness health indicator for Kubernetes readiness probes.
 * <p>
 * This indicator checks if the application is ready to accept traffic.
 * It checks external dependencies (Kafka via circuit breaker state).
 * <p>
 * Use this for Kubernetes readiness probes:
 * <pre>
 * readinessProbe:
 *   httpGet:
 *     path: /actuator/health/readiness
 *     port: 8080
 *   initialDelaySeconds: 10
 *   periodSeconds: 5
 * </pre>
 */
@Component("webhookReadiness")
@RequiredArgsConstructor
@Slf4j
public class WebhookPlatformReadinessIndicator implements HealthIndicator {

    private final ResilientWebhookProcessingService resilientService;

    @Override
    public Health health() {
        // Readiness check: verify the application can process webhooks
        // Check circuit breaker state - if OPEN, we're not ready
        
        CircuitBreaker.State circuitBreakerState = resilientService.getCircuitBreakerState();
        
        // Application is ready if circuit breaker is CLOSED or HALF_OPEN
        // Not ready if OPEN or FORCED_OPEN
        boolean isReady = circuitBreakerState == CircuitBreaker.State.CLOSED ||
                         circuitBreakerState == CircuitBreaker.State.HALF_OPEN ||
                         circuitBreakerState == CircuitBreaker.State.DISABLED;
        
        if (isReady) {
            return Health.up()
                    .withDetail("status", "Ready to accept traffic")
                    .withDetail("circuitBreakerState", circuitBreakerState.name())
                    .build();
        } else {
            log.warn("Application not ready: circuit breaker is {}", circuitBreakerState);
            return Health.down()
                    .withDetail("status", "Not ready - Kafka unavailable")
                    .withDetail("circuitBreakerState", circuitBreakerState.name())
                    .build();
        }
    }
}

