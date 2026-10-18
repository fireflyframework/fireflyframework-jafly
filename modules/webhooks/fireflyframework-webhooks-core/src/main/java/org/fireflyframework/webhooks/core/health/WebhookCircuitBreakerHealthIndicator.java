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
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for webhook circuit breaker.
 * Reports the state of the circuit breaker protecting Kafka publishing.
 */
@Component
@RequiredArgsConstructor
public class WebhookCircuitBreakerHealthIndicator implements HealthIndicator {

    private final ResilientWebhookProcessingService resilientService;

    @Override
    public Health health() {
        CircuitBreaker.State state = resilientService.getCircuitBreakerState();
        CircuitBreaker.Metrics metrics = resilientService.getCircuitBreakerMetrics();

        Health.Builder builder = switch (state) {
            case CLOSED, DISABLED -> Health.up();
            case HALF_OPEN -> Health.status("HALF_OPEN");
            case OPEN, FORCED_OPEN -> Health.down();
            default -> Health.unknown();
        };

        return builder
                .withDetail("state", state.name())
                .withDetail("failureRate", String.format("%.2f%%", metrics.getFailureRate()))
                .withDetail("slowCallRate", String.format("%.2f%%", metrics.getSlowCallRate()))
                .withDetail("numberOfBufferedCalls", metrics.getNumberOfBufferedCalls())
                .withDetail("numberOfFailedCalls", metrics.getNumberOfFailedCalls())
                .withDetail("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls())
                .withDetail("numberOfSlowCalls", metrics.getNumberOfSlowCalls())
                .build();
    }
}

