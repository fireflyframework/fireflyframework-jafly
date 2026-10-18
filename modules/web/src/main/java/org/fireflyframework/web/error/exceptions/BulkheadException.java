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
 * Exception thrown when a bulkhead is full and cannot accept more requests.
 * Results in a 503 SERVICE UNAVAILABLE response.
 * 
 * <p>This exception is typically thrown when:</p>
 * <ul>
 *   <li>The maximum number of concurrent calls has been reached</li>
 *   <li>The bulkhead queue is full</li>
 *   <li>The service is at capacity and cannot handle more requests</li>
 * </ul>
 *
 * <p>The exception includes metadata about the bulkhead state,
 * current capacity, and suggested retry time.</p>
 */
public class BulkheadException extends BusinessException {

    /**
     * Creates a new BulkheadException with the given message.
     *
     * @param message the error message
     */
    public BulkheadException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "BULKHEAD_FULL", message);
    }

    /**
     * Creates a new BulkheadException with the given message and metadata.
     *
     * @param message the error message
     * @param metadata additional metadata about the bulkhead state
     */
    public BulkheadException(String message, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "BULKHEAD_FULL", message, metadata);
    }

    /**
     * Creates a new BulkheadException with the given code, message, and metadata.
     *
     * @param code the error code
     * @param message the error message
     * @param metadata additional metadata about the bulkhead state
     */
    public BulkheadException(String code, String message, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message, metadata);
    }

    /**
     * Creates a new BulkheadException with the given message and cause.
     *
     * @param message the error message
     * @param cause the cause of this exception
     */
    public BulkheadException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "BULKHEAD_FULL", message, cause);
    }

    /**
     * Creates a new BulkheadException with the given message, cause, and metadata.
     *
     * @param message the error message
     * @param cause the cause of this exception
     * @param metadata additional metadata about the bulkhead state
     */
    public BulkheadException(String message, Throwable cause, Map<String, Object> metadata) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "BULKHEAD_FULL", message, cause, metadata);
    }
}

