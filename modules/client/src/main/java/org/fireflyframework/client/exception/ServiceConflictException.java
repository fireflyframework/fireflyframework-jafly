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

/**
 * Exception thrown when a service request conflicts with the current state.
 * This typically corresponds to HTTP 409 responses or gRPC ABORTED status.
 * 
 * <p>This error is NOT retryable as it requires conflict resolution.
 * 
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class ServiceConflictException extends ServiceClientException {

    /**
     * Constructs a new ServiceConflictException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public ServiceConflictException(String message) {
        super(message);
    }

    /**
     * Constructs a new ServiceConflictException with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the underlying cause of this exception
     */
    public ServiceConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ServiceConflictException with the specified detail message and error context.
     *
     * @param message the detail message explaining the cause of the exception
     * @param errorContext rich context information about the error
     */
    public ServiceConflictException(String message, ErrorContext errorContext) {
        super(message, errorContext);
    }

    /**
     * Constructs a new ServiceConflictException with the specified detail message, error context, and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param errorContext rich context information about the error
     * @param cause the underlying cause of this exception
     */
    public ServiceConflictException(String message, ErrorContext errorContext, Throwable cause) {
        super(message, errorContext, cause);
    }

    @Override
    public ErrorCategory getErrorCategory() {
        return ErrorCategory.CLIENT_ERROR;
    }
}

