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
package org.fireflyframework.client.exception;

import java.time.Duration;

/**
 * Exception thrown when a circuit breaker is in OPEN state and rejects calls.
 * 
 * <p>This exception indicates that the circuit breaker has detected too many
 * failures and is currently rejecting all calls to protect the downstream service
 * and prevent cascading failures.
 *
 * <p>This error is retryable after the circuit breaker transitions to HALF_OPEN.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class CircuitBreakerOpenException extends ServiceClientException implements RetryableError {
    
    /**
     * Constructs a new CircuitBreakerOpenException with the specified detail message.
     *
     * @param message the detail message
     */
    public CircuitBreakerOpenException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new CircuitBreakerOpenException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public CircuitBreakerOpenException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new CircuitBreakerOpenException with the specified detail message and error context.
     *
     * @param message the detail message
     * @param errorContext rich context information about the error
     */
    public CircuitBreakerOpenException(String message, ErrorContext errorContext) {
        super(message, errorContext);
    }

    /**
     * Constructs a new CircuitBreakerOpenException with the specified detail message, error context, and cause.
     *
     * @param message the detail message
     * @param errorContext rich context information about the error
     * @param cause the cause
     */
    public CircuitBreakerOpenException(String message, ErrorContext errorContext, Throwable cause) {
        super(message, errorContext, cause);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }

    @Override
    public Duration getRetryDelay() {
        return Duration.ofSeconds(5);
    }

    @Override
    public ErrorCategory getErrorCategory() {
        return ErrorCategory.CIRCUIT_BREAKER_ERROR;
    }
}

