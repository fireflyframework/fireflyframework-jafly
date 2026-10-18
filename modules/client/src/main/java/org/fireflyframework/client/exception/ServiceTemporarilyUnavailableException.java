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
 * Exception thrown when a service is temporarily unavailable.
 * This typically corresponds to HTTP 502/503/504 responses.
 * 
 * <p>This error is retryable as the service may recover.
 * 
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class ServiceTemporarilyUnavailableException extends ServiceUnavailableException implements RetryableError {

    /**
     * Constructs a new ServiceTemporarilyUnavailableException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public ServiceTemporarilyUnavailableException(String message) {
        super(message);
    }

    /**
     * Constructs a new ServiceTemporarilyUnavailableException with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the underlying cause of this exception
     */
    public ServiceTemporarilyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ServiceTemporarilyUnavailableException with the specified detail message and error context.
     *
     * @param message the detail message explaining the cause of the exception
     * @param errorContext rich context information about the error
     */
    public ServiceTemporarilyUnavailableException(String message, ErrorContext errorContext) {
        super(message, errorContext);
    }

    /**
     * Constructs a new ServiceTemporarilyUnavailableException with the specified detail message, error context, and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param errorContext rich context information about the error
     * @param cause the underlying cause of this exception
     */
    public ServiceTemporarilyUnavailableException(String message, ErrorContext errorContext, Throwable cause) {
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
}

