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
 * Base exception for all ServiceClient-related errors.
 *
 * <p>This exception serves as the parent class for all service communication failures.
 * It includes rich error context with metadata about the service, request, and error details.
 *
 * <p>Example usage:
 * <pre>{@code
 * try {
 *     User user = client.get("/users/123", User.class).execute().block();
 * } catch (ServiceClientException e) {
 *     ErrorContext ctx = e.getErrorContext();
 *     log.error("Error: {} | Service: {} | Status: {} | RequestID: {}",
 *         e.getMessage(), ctx.getServiceName(),
 *         ctx.getHttpStatusCode(), ctx.getRequestId());
 * }
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Getter
public class ServiceClientException extends org.fireflyframework.kernel.exception.FireflyInfrastructureException {

    /**
     * Rich context information about the error.
     */
    private final ErrorContext errorContext;

    /**
     * Constructs a new ServiceClientException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public ServiceClientException(String message) {
        this(message, ErrorContext.empty());
    }

    /**
     * Constructs a new ServiceClientException with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the underlying cause of this exception
     */
    public ServiceClientException(String message, Throwable cause) {
        this(message, ErrorContext.empty(), cause);
    }

    /**
     * Constructs a new ServiceClientException with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public ServiceClientException(Throwable cause) {
        this(cause.getMessage(), ErrorContext.empty(), cause);
    }

    /**
     * Constructs a new ServiceClientException with the specified detail message and error context.
     *
     * @param message the detail message explaining the cause of the exception
     * @param errorContext rich context information about the error
     */
    public ServiceClientException(String message, ErrorContext errorContext) {
        super(enrichMessage(message, errorContext));
        this.errorContext = errorContext != null ? errorContext : ErrorContext.empty();
    }

    /**
     * Constructs a new ServiceClientException with the specified detail message, error context, and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param errorContext rich context information about the error
     * @param cause the underlying cause of this exception
     */
    public ServiceClientException(String message, ErrorContext errorContext, Throwable cause) {
        super(enrichMessage(message, errorContext), cause);
        this.errorContext = errorContext != null ? errorContext : ErrorContext.empty();
    }

    /**
     * Enriches the error message with context information.
     *
     * @param message the base error message
     * @param context the error context
     * @return enriched error message
     */
    private static String enrichMessage(String message, ErrorContext context) {
        if (context == null || context == ErrorContext.empty()) {
            return message;
        }

        StringBuilder enriched = new StringBuilder(message != null ? message : "Service client error");

        if (context.getServiceName() != null) {
            enriched.append(" | Service: ").append(context.getServiceName());
        }

        if (context.getEndpoint() != null) {
            enriched.append(" | Endpoint: ").append(context.getEndpoint());
        }

        if (context.getHttpStatusCode() != null) {
            enriched.append(" | HTTP Status: ").append(context.getHttpStatusCode());
        }

        if (context.getGrpcStatusCode() != null) {
            enriched.append(" | gRPC Status: ").append(context.getGrpcStatusCode());
        }

        if (context.getRequestId() != null) {
            enriched.append(" | Request ID: ").append(context.getRequestId());
        }

        if (context.getElapsedTime() != null) {
            enriched.append(" | Duration: ").append(context.getElapsedTime().toMillis()).append("ms");
        }

        if (context.isRetry()) {
            enriched.append(" | Retry: ").append(context.getRetryAttempt());
        }

        return enriched.toString();
    }

    /**
     * Gets the error category for this exception.
     *
     * @return the error category
     */
    public ErrorCategory getErrorCategory() {
        return ErrorCategory.UNKNOWN_ERROR;
    }
}
