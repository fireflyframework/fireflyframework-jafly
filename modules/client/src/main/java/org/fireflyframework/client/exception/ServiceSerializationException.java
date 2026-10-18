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

/**
 * Exception thrown when serialization or deserialization fails.
 * This includes JSON/XML parsing errors, schema mismatches, etc.
 * 
 * <p>This error is NOT retryable as it indicates a data format problem.
 * 
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Getter
public class ServiceSerializationException extends ServiceClientException {

    /**
     * The raw content that failed to serialize/deserialize.
     */
    private final String rawContent;

    /**
     * Constructs a new ServiceSerializationException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public ServiceSerializationException(String message) {
        this(message, null, ErrorContext.empty());
    }

    /**
     * Constructs a new ServiceSerializationException with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the underlying cause of this exception
     */
    public ServiceSerializationException(String message, Throwable cause) {
        this(message, null, ErrorContext.empty(), cause);
    }

    /**
     * Constructs a new ServiceSerializationException with the specified detail message and raw content.
     *
     * @param message the detail message explaining the cause of the exception
     * @param rawContent the raw content that failed to serialize/deserialize
     */
    public ServiceSerializationException(String message, String rawContent) {
        this(message, rawContent, ErrorContext.empty());
    }

    /**
     * Constructs a new ServiceSerializationException with the specified detail message, raw content, and error context.
     *
     * @param message the detail message explaining the cause of the exception
     * @param rawContent the raw content that failed to serialize/deserialize
     * @param errorContext rich context information about the error
     */
    public ServiceSerializationException(String message, String rawContent, ErrorContext errorContext) {
        super(message, errorContext);
        this.rawContent = rawContent;
    }

    /**
     * Constructs a new ServiceSerializationException with the specified detail message, raw content, error context, and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param rawContent the raw content that failed to serialize/deserialize
     * @param errorContext rich context information about the error
     * @param cause the underlying cause of this exception
     */
    public ServiceSerializationException(String message, String rawContent, ErrorContext errorContext, Throwable cause) {
        super(message, errorContext, cause);
        this.rawContent = rawContent;
    }

    @Override
    public ErrorCategory getErrorCategory() {
        return ErrorCategory.SERIALIZATION_ERROR;
    }
}

