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
 * Exception thrown when a call through a circuit breaker times out.
 * 
 * <p>This exception indicates that a call took longer than the configured
 * timeout duration and was cancelled by the circuit breaker.
 *
 * <p>This error is retryable as timeouts may be transient.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class CircuitBreakerTimeoutException extends ServiceTimeoutException {
    
    /**
     * Constructs a new CircuitBreakerTimeoutException with the specified detail message.
     *
     * @param message the detail message
     */
    public CircuitBreakerTimeoutException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new CircuitBreakerTimeoutException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public CircuitBreakerTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new CircuitBreakerTimeoutException with the specified detail message and error context.
     *
     * @param message the detail message
     * @param errorContext rich context information about the error
     */
    public CircuitBreakerTimeoutException(String message, ErrorContext errorContext) {
        super(message, errorContext);
    }

    /**
     * Constructs a new CircuitBreakerTimeoutException with the specified detail message, error context, and cause.
     *
     * @param message the detail message
     * @param errorContext rich context information about the error
     * @param cause the cause
     */
    public CircuitBreakerTimeoutException(String message, ErrorContext errorContext, Throwable cause) {
        super(message, errorContext, cause);
    }

    @Override
    public ErrorCategory getErrorCategory() {
        return ErrorCategory.CIRCUIT_BREAKER_ERROR;
    }
}

