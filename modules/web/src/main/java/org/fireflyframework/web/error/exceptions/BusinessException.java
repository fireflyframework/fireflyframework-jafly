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

import lombok.Getter;
import org.fireflyframework.kernel.exception.FireflyException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base exception class for all business exceptions in the application.
 * Provides standardized error handling with HTTP status codes and error codes.
 * Supports additional metadata and nested errors.
 */
@Getter
public class BusinessException extends FireflyException {
    /**
     * The HTTP status code to be returned in the response.
     */
    private final HttpStatus status;

    /**
     * The error code that identifies the type of error.
     */
    private final String code;

    /**
     * Additional metadata about the error.
     */
    private final Map<String, Object> metadata;

    /**
     * The error that caused this exception, if any.
     */
    private final BusinessException cause;

    /**
     * Creates a new BusinessException with BAD_REQUEST status and the given message.
     *
     * @param message the error message
     */
    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Creates a new BusinessException with the given status and message.
     *
     * @param status the HTTP status code
     * @param message the error message
     */
    public BusinessException(HttpStatus status, String message) {
        this(status, null, message);
    }

    /**
     * Creates a new BusinessException with the given status, code, and message.
     *
     * @param status the HTTP status code
     * @param code the error code
     * @param message the error message
     */
    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, null, Collections.emptyMap());
    }

    /**
     * Creates a new BusinessException with the given status, code, message, and cause.
     *
     * @param status the HTTP status code
     * @param code the error code
     * @param message the error message
     * @param cause the cause of this exception
     */
    public BusinessException(HttpStatus status, String code, String message, Throwable cause) {
        this(status, code, message, cause, Collections.emptyMap());
    }

    /**
     * Creates a new BusinessException with the given status, code, message, and metadata.
     *
     * @param status the HTTP status code
     * @param code the error code
     * @param message the error message
     * @param metadata additional metadata about the error
     */
    public BusinessException(HttpStatus status, String code, String message, Map<String, Object> metadata) {
        this(status, code, message, null, metadata);
    }

    /**
     * Creates a new BusinessException with the given status, code, message, cause, and metadata.
     *
     * @param status the HTTP status code
     * @param code the error code
     * @param message the error message
     * @param cause the cause of this exception
     * @param metadata additional metadata about the error
     */
    public BusinessException(HttpStatus status, String code, String message, Throwable cause, Map<String, Object> metadata) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(metadata != null ? metadata : Collections.emptyMap()));
        this.cause = (cause instanceof BusinessException) ? (BusinessException) cause : null;
    }

    /**
     * Returns a new exception with the given metadata added.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return a new exception with the metadata added
     */
    public BusinessException withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put(key, value);
        return new BusinessException(this.status, this.code, this.getMessage(), this.getCause(), newMetadata);
    }

    /**
     * Returns a new exception with the given metadata added.
     *
     * @param metadata the metadata to add
     * @return a new exception with the metadata added
     */
    public BusinessException withMetadata(Map<String, Object> metadata) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.putAll(metadata);
        return new BusinessException(this.status, this.code, this.getMessage(), this.getCause(), newMetadata);
    }
}