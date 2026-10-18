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

import java.time.Instant;

/**
 * Metrics and statistics for circuit breaker monitoring.
 * 
 * <p>This class provides comprehensive metrics about circuit breaker
 * performance and state for monitoring and alerting purposes.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Data
@Builder
public class CircuitBreakerMetrics {
    
    /**
     * Name of the circuit breaker.
     */
    private final String name;
    
    /**
     * Current state of the circuit breaker.
     */
    private final CircuitBreakerState state;
    
    /**
     * Total number of calls made through the circuit breaker.
     */
    private final long totalCalls;
    
    /**
     * Number of successful calls.
     */
    private final long successfulCalls;
    
    /**
     * Number of failed calls.
     */
    private final long failedCalls;
    
    /**
     * Current failure rate (percentage).
     */
    private final double failureRate;
    
    /**
     * Timestamp of the last state transition.
     */
    private final Instant lastStateTransition;
    
    /**
     * Number of calls rejected due to circuit being open.
     */
    @Builder.Default
    private final long rejectedCalls = 0;
    
    /**
     * Average response time for successful calls (milliseconds).
     */
    @Builder.Default
    private final double averageResponseTime = 0.0;
    
    /**
     * Number of slow calls (calls that exceeded the slow call threshold).
     */
    @Builder.Default
    private final long slowCalls = 0;
    
    /**
     * Current slow call rate (percentage).
     */
    @Builder.Default
    private final double slowCallRate = 0.0;
    
    /**
     * Duration the circuit breaker has been in the current state (milliseconds).
     */
    public long getTimeInCurrentState() {
        return System.currentTimeMillis() - lastStateTransition.toEpochMilli();
    }
    
    /**
     * Success rate (percentage).
     */
    public double getSuccessRate() {
        if (totalCalls == 0) {
            return 0.0;
        }
        return (double) successfulCalls / totalCalls * 100.0;
    }
    
    /**
     * Checks if the circuit breaker is healthy.
     */
    public boolean isHealthy() {
        return state == CircuitBreakerState.CLOSED && failureRate < 10.0;
    }
    
    /**
     * Gets a human-readable summary of the metrics.
     */
    public String getSummary() {
        return String.format(
            "CircuitBreaker[%s] State=%s, Calls=%d, Success=%.1f%%, Failure=%.1f%%, TimeInState=%dms",
            name, state, totalCalls, getSuccessRate(), failureRate, getTimeInCurrentState()
        );
    }
}
