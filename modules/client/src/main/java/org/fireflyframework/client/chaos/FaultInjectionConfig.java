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

package org.fireflyframework.client.chaos;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuration for fault injection in Chaos Engineering.
 * 
 * <p>This configuration defines various fault injection strategies including:
 * <ul>
 *   <li>Latency injection - Add artificial delays to requests</li>
 *   <li>Error injection - Throw exceptions or return error responses</li>
 *   <li>Timeout injection - Force requests to timeout</li>
 *   <li>Network failure simulation - Simulate connection failures</li>
 *   <li>Response corruption - Corrupt response data</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * FaultInjectionConfig config = FaultInjectionConfig.builder()
 *     .enabled(true)
 *     .latencyInjectionEnabled(true)
 *     .latencyProbability(0.2) // 20% of requests
 *     .minLatency(Duration.ofMillis(100))
 *     .maxLatency(Duration.ofSeconds(2))
 *     .errorInjectionEnabled(true)
 *     .errorProbability(0.1) // 10% of requests
 *     .build();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Data
@Builder
public class FaultInjectionConfig {

    /**
     * Whether fault injection is enabled globally.
     * Default: false (disabled in production)
     */
    @Builder.Default
    private boolean enabled = false;

    /**
     * Services to include in fault injection (empty = all services).
     */
    @Builder.Default
    private Set<String> includedServices = new HashSet<>();

    /**
     * Services to exclude from fault injection.
     */
    @Builder.Default
    private Set<String> excludedServices = new HashSet<>();

    // ========================================
    // Latency Injection
    // ========================================

    /**
     * Whether latency injection is enabled.
     */
    @Builder.Default
    private boolean latencyInjectionEnabled = false;

    /**
     * Probability of injecting latency (0.0 to 1.0).
     * Default: 0.1 (10% of requests)
     */
    @Builder.Default
    private double latencyProbability = 0.1;

    /**
     * Minimum latency to inject.
     */
    @Builder.Default
    private Duration minLatency = Duration.ofMillis(100);

    /**
     * Maximum latency to inject.
     */
    @Builder.Default
    private Duration maxLatency = Duration.ofSeconds(2);

    /**
     * Fixed latency (if set, overrides min/max).
     */
    private Duration fixedLatency;

    // ========================================
    // Error Injection
    // ========================================

    /**
     * Whether error injection is enabled.
     */
    @Builder.Default
    private boolean errorInjectionEnabled = false;

    /**
     * Probability of injecting errors (0.0 to 1.0).
     * Default: 0.05 (5% of requests)
     */
    @Builder.Default
    private double errorProbability = 0.05;

    /**
     * Type of error to inject.
     */
    @Builder.Default
    private ErrorType errorType = ErrorType.RANDOM;

    /**
     * HTTP status code to return for error injection (REST only).
     */
    @Builder.Default
    private int errorStatusCode = 500;

    /**
     * Error message to include.
     */
    @Builder.Default
    private String errorMessage = "Chaos Engineering: Injected fault";

    // ========================================
    // Timeout Injection
    // ========================================

    /**
     * Whether timeout injection is enabled.
     */
    @Builder.Default
    private boolean timeoutInjectionEnabled = false;

    /**
     * Probability of injecting timeouts (0.0 to 1.0).
     * Default: 0.05 (5% of requests)
     */
    @Builder.Default
    private double timeoutProbability = 0.05;

    /**
     * Timeout duration to inject.
     */
    @Builder.Default
    private Duration timeoutDuration = Duration.ofSeconds(30);

    // ========================================
    // Network Failure Injection
    // ========================================

    /**
     * Whether network failure injection is enabled.
     */
    @Builder.Default
    private boolean networkFailureInjectionEnabled = false;

    /**
     * Probability of injecting network failures (0.0 to 1.0).
     * Default: 0.03 (3% of requests)
     */
    @Builder.Default
    private double networkFailureProbability = 0.03;

    // ========================================
    // Response Corruption
    // ========================================

    /**
     * Whether response corruption is enabled.
     */
    @Builder.Default
    private boolean responseCorruptionEnabled = false;

    /**
     * Probability of corrupting responses (0.0 to 1.0).
     * Default: 0.02 (2% of requests)
     */
    @Builder.Default
    private double responseCorruptionProbability = 0.02;

    // ========================================
    // Circuit Breaker Testing
    // ========================================

    /**
     * Whether to force circuit breaker to open.
     */
    @Builder.Default
    private boolean forceCircuitBreakerOpen = false;

    /**
     * Whether to force circuit breaker to half-open.
     */
    @Builder.Default
    private boolean forceCircuitBreakerHalfOpen = false;

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Checks if a service should have faults injected.
     */
    public boolean shouldInjectFault(String serviceName) {
        if (!enabled) {
            return false;
        }

        if (!excludedServices.isEmpty() && excludedServices.contains(serviceName)) {
            return false;
        }

        if (!includedServices.isEmpty()) {
            return includedServices.contains(serviceName);
        }

        return true;
    }

    /**
     * Determines if latency should be injected based on probability.
     */
    public boolean shouldInjectLatency() {
        return latencyInjectionEnabled && 
               ThreadLocalRandom.current().nextDouble() < latencyProbability;
    }

    /**
     * Determines if error should be injected based on probability.
     */
    public boolean shouldInjectError() {
        return errorInjectionEnabled && 
               ThreadLocalRandom.current().nextDouble() < errorProbability;
    }

    /**
     * Determines if timeout should be injected based on probability.
     */
    public boolean shouldInjectTimeout() {
        return timeoutInjectionEnabled && 
               ThreadLocalRandom.current().nextDouble() < timeoutProbability;
    }

    /**
     * Determines if network failure should be injected based on probability.
     */
    public boolean shouldInjectNetworkFailure() {
        return networkFailureInjectionEnabled && 
               ThreadLocalRandom.current().nextDouble() < networkFailureProbability;
    }

    /**
     * Determines if response should be corrupted based on probability.
     */
    public boolean shouldCorruptResponse() {
        return responseCorruptionEnabled && 
               ThreadLocalRandom.current().nextDouble() < responseCorruptionProbability;
    }

    /**
     * Gets a random latency duration between min and max.
     */
    public Duration getRandomLatency() {
        if (fixedLatency != null) {
            return fixedLatency;
        }

        long minMillis = minLatency.toMillis();
        long maxMillis = maxLatency.toMillis();
        long randomMillis = ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
        return Duration.ofMillis(randomMillis);
    }

    /**
     * Error types for fault injection.
     */
    public enum ErrorType {
        RANDOM,              // Random error type
        TIMEOUT,             // Timeout exception
        CONNECTION_REFUSED,  // Connection refused
        SERVICE_UNAVAILABLE, // Service unavailable (503)
        INTERNAL_ERROR,      // Internal server error (500)
        BAD_GATEWAY,         // Bad gateway (502)
        RATE_LIMIT           // Rate limit exceeded (429)
    }

    /**
     * Creates a default configuration (disabled).
     */
    public static FaultInjectionConfig disabled() {
        return FaultInjectionConfig.builder()
            .enabled(false)
            .build();
    }

    /**
     * Creates a configuration for testing latency resilience.
     */
    public static FaultInjectionConfig latencyTesting() {
        return FaultInjectionConfig.builder()
            .enabled(true)
            .latencyInjectionEnabled(true)
            .latencyProbability(0.3)
            .minLatency(Duration.ofMillis(500))
            .maxLatency(Duration.ofSeconds(3))
            .build();
    }

    /**
     * Creates a configuration for testing error resilience.
     */
    public static FaultInjectionConfig errorTesting() {
        return FaultInjectionConfig.builder()
            .enabled(true)
            .errorInjectionEnabled(true)
            .errorProbability(0.2)
            .errorType(ErrorType.RANDOM)
            .build();
    }
}

