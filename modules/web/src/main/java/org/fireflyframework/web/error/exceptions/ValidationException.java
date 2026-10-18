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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when validation fails for a request.
 * Results in a 400 BAD REQUEST response with detailed validation errors.
 * Supports nested object validation and error codes per field.
 */
public class ValidationException extends BusinessException {

    /**
     * The list of validation errors that occurred.
     * Each error contains information about a specific field that failed validation.
     */
    private final List<ValidationError> validationErrors;

    /**
     * Creates a new ValidationException with the given message.
     *
     * @param message the error message
     */
    public ValidationException(String message) {
        this(message, Collections.emptyList());
    }

    /**
     * Creates a new ValidationException with the given message and validation errors.
     *
     * @param message the error message
     * @param validationErrors the validation errors
     */
    public ValidationException(String message, List<ValidationError> validationErrors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
        this.validationErrors = new ArrayList<>(validationErrors);
    }

    /**
     * Creates a new ValidationException with the given code, message, and validation errors.
     *
     * @param code the error code
     * @param message the error message
     * @param validationErrors the validation errors
     */
    public ValidationException(String code, String message, List<ValidationError> validationErrors) {
        super(HttpStatus.BAD_REQUEST, code, message);
        this.validationErrors = new ArrayList<>(validationErrors);
    }

    /**
     * Returns the validation errors.
     *
     * @return the validation errors
     */
    public List<ValidationError> getValidationErrors() {
        return Collections.unmodifiableList(validationErrors);
    }

    /**
     * Builder for creating ValidationException instances.
     * This class provides a fluent API for building ValidationException objects
     * with multiple validation errors.
     */
    public static class Builder {
        private final List<ValidationError> errors = new ArrayList<>();
        private String code = "VALIDATION_ERROR";
        private String message = "Validation failed";

        /**
         * Creates a new Builder for ValidationException.
         */
        public Builder() {
            // Default constructor
        }

        /**
         * Sets the error code for the exception.
         *
         * @param code the error code
         * @return this builder
         */
        public Builder withCode(String code) {
            this.code = code;
            return this;
        }

        /**
         * Sets the error message for the exception.
         *
         * @param message the error message
         * @return this builder
         */
        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        /**
         * Adds a validation error.
         *
         * @param field the field that failed validation
         * @param message the validation error message
         * @return this builder
         */
        public Builder addError(String field, String message) {
            errors.add(new ValidationError(field, message));
            return this;
        }

        /**
         * Adds a validation error with a code.
         *
         * @param field the field that failed validation
         * @param code the error code
         * @param message the validation error message
         * @return this builder
         */
        public Builder addError(String field, String code, String message) {
            errors.add(new ValidationError(field, code, message));
            return this;
        }

        /**
         * Adds a validation error with a code and metadata.
         *
         * @param field the field that failed validation
         * @param code the error code
         * @param message the validation error message
         * @param metadata additional metadata about the error
         * @return this builder
         */
        public Builder addError(String field, String code, String message, Map<String, Object> metadata) {
            errors.add(new ValidationError(field, code, message, metadata));
            return this;
        }

        /**
         * Adds a nested validation error.
         *
         * @param parentField the parent field
         * @param field the field that failed validation
         * @param message the validation error message
         * @return this builder
         */
        public Builder addNestedError(String parentField, String field, String message) {
            errors.add(new ValidationError(parentField + "." + field, message));
            return this;
        }

        /**
         * Adds a nested validation error with a code.
         *
         * @param parentField the parent field
         * @param field the field that failed validation
         * @param code the error code
         * @param message the validation error message
         * @return this builder
         */
        public Builder addNestedError(String parentField, String field, String code, String message) {
            errors.add(new ValidationError(parentField + "." + field, code, message));
            return this;
        }

        /**
         * Adds all errors from another validation exception.
         *
         * @param exception the validation exception to add errors from
         * @return this builder
         */
        public Builder addErrors(ValidationException exception) {
            errors.addAll(exception.getValidationErrors());
            return this;
        }

        /**
         * Adds all errors from another validation exception with a parent field prefix.
         *
         * @param parentField the parent field to prefix all errors with
         * @param exception the validation exception to add errors from
         * @return this builder
         */
        public Builder addNestedErrors(String parentField, ValidationException exception) {
            for (ValidationError error : exception.getValidationErrors()) {
                errors.add(new ValidationError(
                    parentField + "." + error.getField(),
                    error.getCode(),
                    error.getMessage(),
                    error.getMetadata()
                ));
            }
            return this;
        }

        /**
         * Builds a ValidationException with the accumulated errors.
         *
         * @return a new ValidationException
         */
        public ValidationException build() {
            return new ValidationException(code, message, errors);
        }
    }

    /**
     * Represents a single validation error.
     */
    public static class ValidationError {
        private final String field;
        private final String code;
        private final String message;
        private final Map<String, Object> metadata;

        /**
         * Creates a new ValidationError.
         *
         * @param field the field that failed validation
         * @param message the validation error message
         */
        public ValidationError(String field, String message) {
            this(field, null, message);
        }

        /**
         * Creates a new ValidationError with a code.
         *
         * @param field the field that failed validation
         * @param code the error code
         * @param message the validation error message
         */
        public ValidationError(String field, String code, String message) {
            this(field, code, message, Collections.emptyMap());
        }

        /**
         * Creates a new ValidationError with a code and metadata.
         *
         * @param field the field that failed validation
         * @param code the error code
         * @param message the validation error message
         * @param metadata additional metadata about the error
         */
        public ValidationError(String field, String code, String message, Map<String, Object> metadata) {
            this.field = field;
            this.code = code;
            this.message = message;
            this.metadata = Collections.unmodifiableMap(new HashMap<>(metadata));
        }

        /**
         * Returns the field that failed validation.
         *
         * @return the field
         */
        public String getField() {
            return field;
        }

        /**
         * Returns the error code.
         *
         * @return the code, or null if not specified
         */
        public String getCode() {
            return code;
        }

        /**
         * Returns the validation error message.
         *
         * @return the message
         */
        public String getMessage() {
            return message;
        }

        /**
         * Returns additional metadata about the error.
         *
         * @return the metadata
         */
        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
