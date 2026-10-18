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

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when a service request fails validation.
 * This typically corresponds to HTTP 422 responses.
 * 
 * <p>This error is NOT retryable as it requires fixing the request data.
 * 
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Getter
public class ServiceUnprocessableEntityException extends ServiceValidationException {

    /**
     * List of validation errors.
     */
    private final List<ValidationError> validationErrors;

    /**
     * Constructs a new ServiceUnprocessableEntityException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public ServiceUnprocessableEntityException(String message) {
        this(message, Collections.emptyList(), ErrorContext.empty());
    }

    /**
     * Constructs a new ServiceUnprocessableEntityException with the specified detail message and validation errors.
     *
     * @param message the detail message explaining the cause of the exception
     * @param validationErrors list of validation errors
     */
    public ServiceUnprocessableEntityException(String message, List<ValidationError> validationErrors) {
        this(message, validationErrors, ErrorContext.empty());
    }

    /**
     * Constructs a new ServiceUnprocessableEntityException with the specified detail message, validation errors, and error context.
     *
     * @param message the detail message explaining the cause of the exception
     * @param validationErrors list of validation errors
     * @param errorContext rich context information about the error
     */
    public ServiceUnprocessableEntityException(String message, List<ValidationError> validationErrors, ErrorContext errorContext) {
        super(message, errorContext);
        this.validationErrors = validationErrors != null ? validationErrors : Collections.emptyList();
    }

    /**
     * Constructs a new ServiceUnprocessableEntityException with the specified detail message, validation errors, error context, and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param validationErrors list of validation errors
     * @param errorContext rich context information about the error
     * @param cause the underlying cause of this exception
     */
    public ServiceUnprocessableEntityException(String message, List<ValidationError> validationErrors, ErrorContext errorContext, Throwable cause) {
        super(message, errorContext, cause);
        this.validationErrors = validationErrors != null ? validationErrors : Collections.emptyList();
    }

    /**
     * Gets an unmodifiable view of the validation errors.
     *
     * @return unmodifiable list of validation errors
     */
    public List<ValidationError> getValidationErrors() {
        return Collections.unmodifiableList(validationErrors);
    }
}

