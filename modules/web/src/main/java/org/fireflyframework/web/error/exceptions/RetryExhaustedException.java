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
 * Exception thrown when all retry attempts have been exhausted.
 * Results in a 503 SERVICE UNAVAILABLE response.
 * 
 * <p>This exception is typically thrown when:</p>
 * <ul>
 *   <li>All configured retry attempts have failed</li>
 *   <li>The maximum retry count has been reached</li>
 *   <li>The operation cannot be completed despite multiple attempts</li>
 * </ul>
 *
 * <p>The exception includes metadata about the retry attempts,
 * last error, and suggested next steps.</p>
 */
public class RetryExhaustedException extends BusinessException {

    /**
     * Creates a new RetryExhaustedException with the given message.
     *
     * @param message the error message
     */
    public RetryExhaustedException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_EXHAUSTED", message);
    }

    /**
     * Creates a new RetryExhaustedException with the given message and metadata.
     *
     * @param message the error message
     * @param metadata additional metadata about the retry attempts
     */
    public RetryExhaustedException(String message, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_EXHAUSTED", message, metadata);
    }

    /**
     * Creates a new RetryExhaustedException with the given code, message, and metadata.
     *
     * @param code the error code
     * @param message the error message
     * @param metadata additional metadata about the retry attempts
     */
    public RetryExhaustedException(String code, String message, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message, metadata);
    }

    /**
     * Creates a new RetryExhaustedException with the given message and cause.
     *
     * @param message the error message
     * @param cause the cause of this exception (typically the last failed attempt)
     */
    public RetryExhaustedException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_EXHAUSTED", message, cause);
    }

    /**
     * Creates a new RetryExhaustedException with the given message, cause, and metadata.
     *
     * @param message the error message
     * @param cause the cause of this exception (typically the last failed attempt)
     * @param metadata additional metadata about the retry attempts
     */
    public RetryExhaustedException(String message, Throwable cause, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_EXHAUSTED", message, cause, metadata);
    }
}

