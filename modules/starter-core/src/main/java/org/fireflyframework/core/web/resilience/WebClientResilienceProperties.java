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


package org.fireflyframework.core.web.resilience;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for WebClient resilience patterns.
 * <p>
 * This class defines properties for configuring resilience patterns for WebClient operations:
 * <ul>
 *   <li><strong>Circuit Breaker:</strong> Prevents cascading failures by stopping calls to failing services</li>
 *   <li><strong>Retry:</strong> Automatically retries failed operations with configurable backoff</li>
 *   <li><strong>Timeout:</strong> Sets maximum duration for operations to prevent blocked threads</li>
 *   <li><strong>Bulkhead:</strong> Limits the number of concurrent calls to a service</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "firefly.webclient.resilience")
@Getter
@Setter
public class WebClientResilienceProperties {

    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    private RetryProperties retry = new RetryProperties();
    private TimeoutProperties timeout = new TimeoutProperties();
    private BulkheadProperties bulkhead = new BulkheadProperties();
    private boolean enabled = true;

    /**
     * Properties for circuit breaker pattern.
     */
    @Getter
    @Setter
    public static class CircuitBreakerProperties {
        /**
         * Failure rate threshold in percentage above which the circuit breaker should trip open.
         */
        private float failureRateThreshold = 50;

        /**
         * Duration the circuit breaker should stay open before switching to half-open.
         */
        private long waitDurationInOpenStateMs = 10000;

        /**
         * Number of permitted calls when the circuit breaker is half-open.
         */
        private int permittedNumberOfCallsInHalfOpenState = 5;

        /**
         * Size of the sliding window used to record the outcome of calls when the circuit breaker is closed.
         */
        private int slidingWindowSize = 10;
    }

    /**
     * Properties for retry pattern.
     */
    @Getter
    @Setter
    public static class RetryProperties {
        /**
         * Maximum number of retry attempts.
         */
        private int maxAttempts = 3;

        /**
         * Initial backoff duration in milliseconds.
         */
        private long initialBackoffMs = 500;

        /**
         * Maximum backoff duration in milliseconds.
         */
        private long maxBackoffMs = 5000;

        /**
         * Backoff multiplier for exponential backoff.
         */
        private double backoffMultiplier = 2.0;
    }

    /**
     * Properties for timeout pattern.
     */
    @Getter
    @Setter
    public static class TimeoutProperties {
        /**
         * Timeout duration in milliseconds.
         */
        private long timeoutMs = 5000;
    }

    /**
     * Properties for bulkhead pattern.
     */
    @Getter
    @Setter
    public static class BulkheadProperties {
        /**
         * Maximum number of concurrent calls permitted.
         */
        private int maxConcurrentCalls = 25;

        /**
         * Maximum amount of time a thread should wait to enter a bulkhead.
         */
        private long maxWaitDurationMs = 0;
    }
}
