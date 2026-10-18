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
 * Exception thrown when a service is operating in degraded mode.
 * Results in a 206 PARTIAL CONTENT or 200 OK response with warning headers.
 * 
 * <p>This exception is typically thrown when:</p>
 * <ul>
 *   <li>Some features are unavailable but core functionality works</li>
 *   <li>Fallback mechanisms are being used</li>
 *   <li>The service is operating with reduced capacity</li>
 *   <li>Non-critical dependencies are unavailable</li>
 * </ul>
 *
 * <p>The exception includes metadata about which features are degraded
 * and what fallback mechanisms are in use.</p>
 */
public class DegradedServiceException extends BusinessException {

    /**
     * Creates a new DegradedServiceException with the given message.
     *
     * @param message the error message
     */
    public DegradedServiceException(String message) {
        super(HttpStatus.OK, "SERVICE_DEGRADED", message);
    }

    /**
     * Creates a new DegradedServiceException with the given message and metadata.
     *
     * @param message the error message
     * @param metadata additional metadata about the degraded state
     */
    public DegradedServiceException(String message, Map<String, Object> metadata) {
        super(HttpStatus.OK, "SERVICE_DEGRADED", message, metadata);
    }

    /**
     * Creates a new DegradedServiceException with the given code, message, and metadata.
     *
     * @param code the error code
     * @param message the error message
     * @param metadata additional metadata about the degraded state
     */
    public DegradedServiceException(String code, String message, Map<String, Object> metadata) {
        super(HttpStatus.OK, code, message, metadata);
    }

    /**
     * Creates a new DegradedServiceException with the given message and cause.
     *
     * @param message the error message
     * @param cause the cause of the degradation
     */
    public DegradedServiceException(String message, Throwable cause) {
        super(HttpStatus.OK, "SERVICE_DEGRADED", message, cause);
    }

    /**
     * Creates a new DegradedServiceException with the given message, cause, and metadata.
     *
     * @param message the error message
     * @param cause the cause of the degradation
     * @param metadata additional metadata about the degraded state
     */
    public DegradedServiceException(String message, Throwable cause, Map<String, Object> metadata) {
        super(HttpStatus.OK, "SERVICE_DEGRADED", message, cause, metadata);
    }
}

