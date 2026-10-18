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

package org.fireflyframework.resilience;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration for circuit breaker behavior.
 * 
 * <p>This configuration class defines all the parameters that control
 * circuit breaker behavior including failure thresholds, timeouts,
 * and state transition rules.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Data
@Builder
public class CircuitBreakerConfig {
    
    /**
     * Failure rate threshold (percentage) that triggers circuit opening.
     * Default: 50.0%
     */
    @Builder.Default
    private double failureRateThreshold = 50.0;
    
    /**
     * Minimum number of calls required before failure rate calculation.
     * Default: 5 calls
     */
    @Builder.Default
    private int minimumNumberOfCalls = 5;
    
    /**
     * Size of the sliding window for failure rate calculation.
     * Default: 10 calls
     */
    @Builder.Default
    private int slidingWindowSize = 10;
    
    /**
     * Duration to wait in OPEN state before transitioning to HALF_OPEN.
     * Default: 60 seconds
     */
    @Builder.Default
    private Duration waitDurationInOpenState = Duration.ofSeconds(60);
    
    /**
     * Number of calls allowed in HALF_OPEN state to test recovery.
     * Default: 3 calls
     */
    @Builder.Default
    private int permittedNumberOfCallsInHalfOpenState = 3;
    
    /**
     * Maximum duration to wait in HALF_OPEN state.
     * Default: 30 seconds
     */
    @Builder.Default
    private Duration maxWaitDurationInHalfOpenState = Duration.ofSeconds(30);
    
    /**
     * Timeout for individual calls through the circuit breaker.
     * Default: 10 seconds
     */
    @Builder.Default
    private Duration callTimeout = Duration.ofSeconds(10);
    
    /**
     * Slow call duration threshold (calls slower than this are considered failures).
     * Default: 5 seconds
     */
    @Builder.Default
    private Duration slowCallDurationThreshold = Duration.ofSeconds(5);
    
    /**
     * Slow call rate threshold (percentage) that triggers circuit opening.
     * Default: 100.0% (disabled)
     */
    @Builder.Default
    private double slowCallRateThreshold = 100.0;
    
    /**
     * Whether to automatically transition from OPEN to HALF_OPEN.
     * Default: true
     */
    @Builder.Default
    private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
    
    /**
     * Creates a default configuration.
     */
    public static CircuitBreakerConfig defaultConfig() {
        return CircuitBreakerConfig.builder().build();
    }
    
    /**
     * Creates a configuration for high-availability services.
     */
    public static CircuitBreakerConfig highAvailabilityConfig() {
        return CircuitBreakerConfig.builder()
            .failureRateThreshold(30.0)
            .minimumNumberOfCalls(3)
            .slidingWindowSize(5)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .callTimeout(Duration.ofSeconds(5))
            .build();
    }
    
    /**
     * Creates a configuration for fault-tolerant services.
     */
    public static CircuitBreakerConfig faultTolerantConfig() {
        return CircuitBreakerConfig.builder()
            .failureRateThreshold(70.0)
            .minimumNumberOfCalls(10)
            .slidingWindowSize(20)
            .waitDurationInOpenState(Duration.ofMinutes(2))
            .permittedNumberOfCallsInHalfOpenState(5)
            .callTimeout(Duration.ofSeconds(15))
            .build();
    }
    
    /**
     * Validates the configuration parameters.
     */
    public void validate() {
        if (failureRateThreshold < 0 || failureRateThreshold > 100) {
            throw new IllegalArgumentException("Failure rate threshold must be between 0 and 100");
        }
        
        if (minimumNumberOfCalls <= 0) {
            throw new IllegalArgumentException("Minimum number of calls must be positive");
        }
        
        if (slidingWindowSize <= 0) {
            throw new IllegalArgumentException("Sliding window size must be positive");
        }
        
        if (permittedNumberOfCallsInHalfOpenState <= 0) {
            throw new IllegalArgumentException("Permitted number of calls in half-open state must be positive");
        }
        
        if (waitDurationInOpenState.isNegative() || waitDurationInOpenState.isZero()) {
            throw new IllegalArgumentException("Wait duration in open state must be positive");
        }
        
        if (callTimeout.isNegative() || callTimeout.isZero()) {
            throw new IllegalArgumentException("Call timeout must be positive");
        }
        
        if (slowCallRateThreshold < 0 || slowCallRateThreshold > 100) {
            throw new IllegalArgumentException("Slow call rate threshold must be between 0 and 100");
        }
    }
}
