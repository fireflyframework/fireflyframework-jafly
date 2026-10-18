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

import org.fireflyframework.client.ClientType;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.time.Duration;
import java.time.Instant;

/**
 * Maps gRPC status codes to typed service client exceptions.
 * 
 * <p>This class provides automatic error mapping for gRPC service clients,
 * converting gRPC errors into appropriate exception types with rich error context.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class GrpcErrorMapper {

    /**
     * Maps a gRPC error to a typed exception.
     *
     * @param throwable the gRPC error
     * @param serviceName the name of the service
     * @param method the gRPC method
     * @param requestId the request ID
     * @param startTime the request start time
     * @return the appropriate exception
     */
    public static Throwable mapGrpcError(
            Throwable throwable,
            String serviceName,
            String method,
            String requestId,
            Instant startTime) {
        
        if (!(throwable instanceof StatusRuntimeException)) {
            // Not a gRPC error, wrap it
            ErrorContext context = buildErrorContext(
                null, serviceName, method, requestId, startTime, throwable.getMessage());
            return new ServiceClientException("gRPC error: " + throwable.getMessage(), context, throwable);
        }
        
        StatusRuntimeException statusException = (StatusRuntimeException) throwable;
        Status status = statusException.getStatus();
        String description = status.getDescription();
        
        ErrorContext context = buildErrorContext(
            status, serviceName, method, requestId, startTime, description);
        
        return createException(status.getCode(), description, context, throwable);
    }

    /**
     * Creates the appropriate exception based on gRPC status code.
     */
    private static ServiceClientException createException(
            Status.Code code,
            String description,
            ErrorContext context,
            Throwable cause) {

        String message = description != null ? description : code.name();

        return switch (code) {
            case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE ->
                new ServiceValidationException(message, context, cause);
            case UNAUTHENTICATED, PERMISSION_DENIED ->
                new ServiceAuthenticationException(message, context, cause);
            case NOT_FOUND ->
                new ServiceNotFoundException(message, context, cause);
            case DEADLINE_EXCEEDED ->
                new ServiceTimeoutException(message, context, cause);
            case ABORTED ->
                new ServiceConflictException(message, context, cause);
            case RESOURCE_EXHAUSTED ->
                new ServiceRateLimitException(message, null, context, cause);
            case INTERNAL, DATA_LOSS, UNKNOWN ->
                new ServiceInternalErrorException(message, context, cause);
            case UNAVAILABLE ->
                new ServiceTemporarilyUnavailableException(message, context, cause);
            case UNIMPLEMENTED ->
                new ServiceClientException("Method not implemented: " + message, context, cause);
            case CANCELLED ->
                new ServiceClientException("Request cancelled: " + message, context, cause);
            case ALREADY_EXISTS ->
                new ServiceConflictException("Resource already exists: " + message, context, cause);
            default ->
                new ServiceClientException("gRPC error [" + code + "]: " + message, context, cause);
        };
    }

    /**
     * Builds error context from the gRPC status.
     */
    private static ErrorContext buildErrorContext(
            Status status,
            String serviceName,
            String method,
            String requestId,
            Instant startTime,
            String description) {
        
        Duration elapsedTime = startTime != null ? Duration.between(startTime, Instant.now()) : null;
        
        return ErrorContext.builder()
            .serviceName(serviceName)
            .method(method)
            .clientType(ClientType.GRPC)
            .requestId(requestId)
            .grpcStatusCode(status != null ? status.getCode().name() : "UNKNOWN")
            .responseBody(description)
            .elapsedTime(elapsedTime)
            .build();
    }
}

