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
 * Interface for errors that can be retried.
 * 
 * <p>Exceptions implementing this interface indicate that the operation
 * that caused the error may succeed if retried. This allows for smart
 * retry logic that only retries errors that make sense to retry.
 *
 * <p>Example usage:
 * <pre>{@code
 * .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
 *     .filter(throwable -> 
 *         throwable instanceof RetryableError && 
 *         ((RetryableError) throwable).isRetryable()))
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public interface RetryableError {
    
    /**
     * Indicates if this error should trigger a retry.
     * 
     * <p>Implementations should return true if the operation is likely
     * to succeed on retry (e.g., temporary network issues, rate limiting,
     * server errors). Return false if the error is permanent (e.g., 
     * validation errors, not found errors).
     *
     * @return true if the error is retryable, false otherwise
     */
    boolean isRetryable();
    
    /**
     * Suggested delay before retry.
     * 
     * <p>This provides a hint for how long to wait before retrying.
     * For rate limit errors, this might be based on the Retry-After header.
     * For other errors, this might be a default backoff duration.
     *
     * @return suggested duration to wait before retry
     */
    default Duration getRetryDelay() {
        return Duration.ofSeconds(1);
    }
}

