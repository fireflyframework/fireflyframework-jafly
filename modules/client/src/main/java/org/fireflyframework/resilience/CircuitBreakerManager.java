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

import org.fireflyframework.client.exception.CircuitBreakerOpenException;
import org.fireflyframework.client.exception.CircuitBreakerTimeoutException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Enhanced Circuit Breaker Manager with real state management and monitoring.
 * 
 * <p>This implementation provides:
 * <ul>
 *   <li>Real-time state management (CLOSED, OPEN, HALF_OPEN)</li>
 *   <li>Configurable failure detection and recovery</li>
 *   <li>Sliding window failure rate calculation</li>
 *   <li>Automatic state transitions</li>
 *   <li>Health monitoring and metrics</li>
 *   <li>Reactive programming support</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class CircuitBreakerManager {

    private final ConcurrentHashMap<String, EnhancedCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig defaultConfig;

    public CircuitBreakerManager(CircuitBreakerConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    /**
     * Gets or creates a circuit breaker for the specified service.
     */
    public EnhancedCircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, 
            name -> new EnhancedCircuitBreaker(name, defaultConfig));
    }

    /**
     * Gets or creates a circuit breaker with custom configuration.
     */
    public EnhancedCircuitBreaker getCircuitBreaker(String serviceName, CircuitBreakerConfig config) {
        return circuitBreakers.computeIfAbsent(serviceName, 
            name -> new EnhancedCircuitBreaker(name, config));
    }

    /**
     * Executes an operation with circuit breaker protection.
     */
    public <T> Mono<T> executeWithCircuitBreaker(String serviceName, Supplier<Mono<T>> operation) {
        return getCircuitBreaker(serviceName).execute(operation);
    }

    /**
     * Gets the current state of a circuit breaker.
     */
    public CircuitBreakerState getState(String serviceName) {
        EnhancedCircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
        return circuitBreaker != null ? circuitBreaker.getState() : CircuitBreakerState.CLOSED;
    }

    /**
     * Gets metrics for a circuit breaker.
     */
    public CircuitBreakerMetrics getMetrics(String serviceName) {
        EnhancedCircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
        return circuitBreaker != null ? circuitBreaker.getMetrics() : null;
    }

    /**
     * Manually transitions a circuit breaker to a specific state.
     */
    public void transitionTo(String serviceName, CircuitBreakerState state) {
        EnhancedCircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
        if (circuitBreaker != null) {
            circuitBreaker.transitionTo(state);
        }
    }

    /**
     * Resets a circuit breaker to its initial state.
     */
    public void reset(String serviceName) {
        EnhancedCircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
        }
    }

    /**
     * Enhanced Circuit Breaker implementation with real state management.
     */
    public static class EnhancedCircuitBreaker {
        private final String name;
        private final CircuitBreakerConfig config;
        private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        private final AtomicLong lastStateTransition = new AtomicLong(System.currentTimeMillis());
        private final SlidingWindow slidingWindow;
        private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong successfulCalls = new AtomicLong(0);
        private final AtomicLong failedCalls = new AtomicLong(0);

        public EnhancedCircuitBreaker(String name, CircuitBreakerConfig config) {
            this.name = name;
            this.config = config;
            this.slidingWindow = new SlidingWindow(config.getSlidingWindowSize());
            log.info("Created circuit breaker '{}' with config: {}", name, config);
        }

        /**
         * Executes an operation with circuit breaker protection.
         */
        public <T> Mono<T> execute(Supplier<Mono<T>> operation) {
            return Mono.defer(() -> {
                if (!canExecute()) {
                    return Mono.error(new CircuitBreakerOpenException(
                        String.format("Circuit breaker '%s' is OPEN", name)));
                }

                long startTime = System.currentTimeMillis();
                totalCalls.incrementAndGet();

                return operation.get()
                    .doOnSuccess(result -> onSuccess(startTime))
                    .doOnError(error -> onError(error, startTime))
                    .timeout(config.getCallTimeout())
                    .onErrorMap(java.util.concurrent.TimeoutException.class, 
                        ex -> new CircuitBreakerTimeoutException(
                            String.format("Circuit breaker '%s' call timeout", name), ex));
            });
        }

        /**
         * Checks if the circuit breaker allows execution.
         */
        private boolean canExecute() {
            CircuitBreakerState currentState = state.get();

            switch (currentState) {
                case CLOSED:
                    return true;
                case OPEN:
                    if (shouldTransitionToHalfOpen()) {
                        transitionTo(CircuitBreakerState.HALF_OPEN);
                        return true;
                    }
                    return false;
                case HALF_OPEN:
                    return halfOpenSuccessCount.get() < config.getPermittedNumberOfCallsInHalfOpenState();
                default:
                    return false;
            }
        }

        /**
         * Handles successful operation completion.
         */
        private void onSuccess(long startTime) {
            long duration = System.currentTimeMillis() - startTime;
            successfulCalls.incrementAndGet();
            slidingWindow.recordSuccess();

            CircuitBreakerState currentState = state.get();
            
            if (currentState == CircuitBreakerState.HALF_OPEN) {
                int successCount = halfOpenSuccessCount.incrementAndGet();
                if (successCount >= config.getPermittedNumberOfCallsInHalfOpenState()) {
                    transitionTo(CircuitBreakerState.CLOSED);
                    log.info("Circuit breaker '{}' transitioned from HALF_OPEN to CLOSED after {} successful calls", 
                        name, successCount);
                }
            }

            log.debug("Circuit breaker '{}' recorded success in {}ms", name, duration);
        }

        /**
         * Handles operation failure.
         */
        private void onError(Throwable error, long startTime) {
            long duration = System.currentTimeMillis() - startTime;
            failedCalls.incrementAndGet();
            slidingWindow.recordFailure();

            CircuitBreakerState currentState = state.get();

            if (currentState == CircuitBreakerState.HALF_OPEN) {
                transitionTo(CircuitBreakerState.OPEN);
                log.warn("Circuit breaker '{}' transitioned from HALF_OPEN to OPEN due to failure: {}", 
                    name, error.getMessage());
            } else if (currentState == CircuitBreakerState.CLOSED && shouldOpenCircuit()) {
                transitionTo(CircuitBreakerState.OPEN);
                log.warn("Circuit breaker '{}' transitioned from CLOSED to OPEN due to failure rate: {}%", 
                    name, slidingWindow.getFailureRate());
            }

            log.debug("Circuit breaker '{}' recorded failure in {}ms: {}", 
                name, duration, error.getMessage());
        }

        /**
         * Checks if the circuit should open based on failure rate.
         */
        private boolean shouldOpenCircuit() {
            if (slidingWindow.getTotalCalls() < config.getMinimumNumberOfCalls()) {
                return false;
            }

            double failureRate = slidingWindow.getFailureRate();
            return failureRate >= config.getFailureRateThreshold();
        }

        /**
         * Checks if the circuit should transition from OPEN to HALF_OPEN.
         */
        private boolean shouldTransitionToHalfOpen() {
            long timeSinceLastTransition = System.currentTimeMillis() - lastStateTransition.get();
            return timeSinceLastTransition >= config.getWaitDurationInOpenState().toMillis();
        }

        /**
         * Transitions the circuit breaker to a new state.
         */
        public void transitionTo(CircuitBreakerState newState) {
            CircuitBreakerState oldState = state.getAndSet(newState);
            lastStateTransition.set(System.currentTimeMillis());

            if (newState == CircuitBreakerState.CLOSED) {
                halfOpenSuccessCount.set(0);
                slidingWindow.reset();
            } else if (newState == CircuitBreakerState.HALF_OPEN) {
                halfOpenSuccessCount.set(0);
            }

            if (oldState != newState) {
                log.info("Circuit breaker '{}' state transition: {} -> {}", name, oldState, newState);
            }
        }

        /**
         * Resets the circuit breaker to its initial state.
         */
        public void reset() {
            transitionTo(CircuitBreakerState.CLOSED);
            slidingWindow.reset();
            halfOpenSuccessCount.set(0);
            totalCalls.set(0);
            successfulCalls.set(0);
            failedCalls.set(0);
            log.info("Circuit breaker '{}' has been reset", name);
        }

        /**
         * Gets the current state of the circuit breaker.
         */
        public CircuitBreakerState getState() {
            return state.get();
        }

        /**
         * Gets metrics for the circuit breaker.
         */
        public CircuitBreakerMetrics getMetrics() {
            return CircuitBreakerMetrics.builder()
                .name(name)
                .state(state.get())
                .totalCalls(totalCalls.get())
                .successfulCalls(successfulCalls.get())
                .failedCalls(failedCalls.get())
                .failureRate(slidingWindow.getFailureRate())
                .lastStateTransition(Instant.ofEpochMilli(lastStateTransition.get()))
                .build();
        }
    }

    /**
     * Sliding window implementation for tracking call results.
     */
    private static class SlidingWindow {
        private final int windowSize;
        private final boolean[] results;
        private final AtomicInteger index = new AtomicInteger(0);
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicInteger failures = new AtomicInteger(0);

        public SlidingWindow(int windowSize) {
            this.windowSize = windowSize;
            this.results = new boolean[windowSize];
        }

        public void recordSuccess() {
            recordResult(true);
        }

        public void recordFailure() {
            recordResult(false);
        }

        private void recordResult(boolean success) {
            int currentIndex = index.getAndIncrement() % windowSize;
            long totalCallsValue = totalCalls.incrementAndGet();

            // If overwriting an existing entry, adjust failure count
            if (totalCallsValue > windowSize && !results[currentIndex]) {
                failures.decrementAndGet();
            }

            results[currentIndex] = success;
            if (!success) {
                failures.incrementAndGet();
            }
        }

        public double getFailureRate() {
            long callsInWindow = Math.min(totalCalls.get(), windowSize);
            if (callsInWindow == 0) {
                return 0.0;
            }
            return (double) failures.get() / callsInWindow * 100.0;
        }

        public long getTotalCalls() {
            return Math.min(totalCalls.get(), windowSize);
        }

        public void reset() {
            index.set(0);
            totalCalls.set(0);
            failures.set(0);
            for (int i = 0; i < windowSize; i++) {
                results[i] = false;
            }
        }
    }
}
