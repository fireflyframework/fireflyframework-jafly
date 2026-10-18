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


package org.fireflyframework.web.error.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Exception thrown when a circuit breaker is open and prevents execution.
 * Results in a 503 SERVICE UNAVAILABLE response.
 * 
 * <p>This exception is typically thrown when:</p>
 * <ul>
 *   <li>A circuit breaker is in OPEN state due to too many failures</li>
 *   <li>The failure rate threshold has been exceeded</li>
 *   <li>The service is temporarily unavailable due to circuit breaker protection</li>
 * </ul>
 *
 * <p>The exception includes metadata about the circuit breaker state,
 * failure rate, and suggested retry time.</p>
 */
public class CircuitBreakerException extends BusinessException {

    /**
     * Creates a new CircuitBreakerException with the given message.
     *
     * @param message the error message
     */
    public CircuitBreakerException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_BREAKER_OPEN", message);
    }

    /**
     * Creates a new CircuitBreakerException with the given message and metadata.
     *
     * @param message the error message
     * @param metadata additional metadata about the circuit breaker state
     */
    public CircuitBreakerException(String message, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_BREAKER_OPEN", message, metadata);
    }

    /**
     * Creates a new CircuitBreakerException with the given code, message, and metadata.
     *
     * @param code the error code
     * @param message the error message
     * @param metadata additional metadata about the circuit breaker state
     */
    public CircuitBreakerException(String code, String message, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message, metadata);
    }

    /**
     * Creates a new CircuitBreakerException with the given message and cause.
     *
     * @param message the error message
     * @param cause the cause of this exception
     */
    public CircuitBreakerException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_BREAKER_OPEN", message, cause);
    }

    /**
     * Creates a new CircuitBreakerException with the given message, cause, and metadata.
     *
     * @param message the error message
     * @param cause the cause of this exception
     * @param metadata additional metadata about the circuit breaker state
     */
    public CircuitBreakerException(String message, Throwable cause, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_BREAKER_OPEN", message, cause, metadata);
    }
}

