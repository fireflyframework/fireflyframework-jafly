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
 * Exception thrown when a usage quota has been exceeded.
 * Results in a 429 TOO MANY REQUESTS response.
 * 
 * <p>This exception is typically thrown when:</p>
 * <ul>
 *   <li>API usage quota has been exceeded</li>
 *   <li>Storage quota has been exceeded</li>
 *   <li>Resource allocation limits have been reached</li>
 *   <li>Subscription tier limits have been exceeded</li>
 * </ul>
 *
 * <p>The exception includes metadata about the quota limits,
 * current usage, and when the quota resets.</p>
 */
public class QuotaExceededException extends BusinessException {

    /**
     * Creates a new QuotaExceededException with the given message.
     *
     * @param message the error message
     */
    public QuotaExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", message);
    }

    /**
     * Creates a new QuotaExceededException with the given message and metadata.
     *
     * @param message the error message
     * @param metadata additional metadata about the quota
     */
    public QuotaExceededException(String message, Map<String, Object> metadata) {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", message, metadata);
    }

    /**
     * Creates a new QuotaExceededException with the given code, message, and metadata.
     *
     * @param code the error code
     * @param message the error message
     * @param metadata additional metadata about the quota
     */
    public QuotaExceededException(String code, String message, Map<String, Object> metadata) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message, metadata);
    }

    /**
     * Creates a new QuotaExceededException with the given message and cause.
     *
     * @param message the error message
     * @param cause the cause of this exception
     */
    public QuotaExceededException(String message, Throwable cause) {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", message, cause);
    }

    /**
     * Creates a new QuotaExceededException with the given message, cause, and metadata.
     *
     * @param message the error message
     * @param cause the cause of this exception
     * @param metadata additional metadata about the quota
     */
    public QuotaExceededException(String message, Throwable cause, Map<String, Object> metadata) {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", message, cause, metadata);
    }
}

