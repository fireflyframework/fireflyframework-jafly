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
 * Exception thrown when a request is rejected due to load shedding.
 * 
 * <p>This exception indicates that the system is under high load and
 * is shedding requests to maintain stability and prevent overload.
 *
 * <p>This error is retryable after a delay.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class LoadSheddingException extends ServiceClientException implements RetryableError {
    
    /**
     * Constructs a new LoadSheddingException with the specified detail message.
     *
     * @param message the detail message
     */
    public LoadSheddingException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new LoadSheddingException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public LoadSheddingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new LoadSheddingException with the specified detail message and error context.
     *
     * @param message the detail message
     * @param errorContext rich context information about the error
     */
    public LoadSheddingException(String message, ErrorContext errorContext) {
        super(message, errorContext);
    }

    /**
     * Constructs a new LoadSheddingException with the specified detail message, error context, and cause.
     *
     * @param message the detail message
     * @param errorContext rich context information about the error
     * @param cause the cause
     */
    public LoadSheddingException(String message, ErrorContext errorContext, Throwable cause) {
        super(message, errorContext, cause);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }

    @Override
    public Duration getRetryDelay() {
        return Duration.ofSeconds(3);
    }

    @Override
    public ErrorCategory getErrorCategory() {
        return ErrorCategory.RATE_LIMIT_ERROR;
    }
}

