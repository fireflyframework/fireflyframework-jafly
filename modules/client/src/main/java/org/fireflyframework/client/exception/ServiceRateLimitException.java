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

import lombok.Getter;

import java.time.Duration;

/**
 * Exception thrown when a service rate limit is exceeded.
 * This typically corresponds to HTTP 429 responses or gRPC RESOURCE_EXHAUSTED status.
 * 
 * <p>This error is retryable after the specified delay (from Retry-After header).
 * 
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Getter
public class ServiceRateLimitException extends ServiceClientException implements RetryableError {

    /**
     * Number of seconds to wait before retrying (from Retry-After header).
     */
    private final Integer retryAfterSeconds;

    /**
     * Constructs a new ServiceRateLimitException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public ServiceRateLimitException(String message) {
        this(message, null, ErrorContext.empty());
    }

    /**
     * Constructs a new ServiceRateLimitException with the specified detail message and retry-after.
     *
     * @param message the detail message explaining the cause of the exception
     * @param retryAfterSeconds number of seconds to wait before retrying
     */
    public ServiceRateLimitException(String message, Integer retryAfterSeconds) {
        this(message, retryAfterSeconds, ErrorContext.empty());
    }

    /**
     * Constructs a new ServiceRateLimitException with the specified detail message, retry-after, and error context.
     *
     * @param message the detail message explaining the cause of the exception
     * @param retryAfterSeconds number of seconds to wait before retrying
     * @param errorContext rich context information about the error
     */
    public ServiceRateLimitException(String message, Integer retryAfterSeconds, ErrorContext errorContext) {
        super(message, errorContext);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Constructs a new ServiceRateLimitException with the specified detail message, retry-after, error context, and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param retryAfterSeconds number of seconds to wait before retrying
     * @param errorContext rich context information about the error
     * @param cause the underlying cause of this exception
     */
    public ServiceRateLimitException(String message, Integer retryAfterSeconds, ErrorContext errorContext, Throwable cause) {
        super(message, errorContext, cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public boolean isRetryable() {
        return true;
    }

    @Override
    public Duration getRetryDelay() {
        return retryAfterSeconds != null 
            ? Duration.ofSeconds(retryAfterSeconds) 
            : Duration.ofSeconds(60);
    }

    @Override
    public ErrorCategory getErrorCategory() {
        return ErrorCategory.RATE_LIMIT_ERROR;
    }
}

