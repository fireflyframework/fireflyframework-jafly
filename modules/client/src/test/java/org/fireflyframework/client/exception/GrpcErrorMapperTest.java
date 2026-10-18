package org.fireflyframework.client.exception;

import org.fireflyframework.client.ClientType;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for GrpcErrorMapper.
 */
@DisplayName("GrpcErrorMapper Tests")
class GrpcErrorMapperTest {

    private String serviceName;
    private String operation;
    private String requestId;
    private Instant startTime;

    @BeforeEach
    void setUp() {
        serviceName = "test-service";
        operation = "testOperation";
        requestId = "req-123";
        startTime = Instant.now().minusMillis(100);
    }

    @Test
    @DisplayName("Should map INVALID_ARGUMENT to ServiceValidationException")
    void shouldMapInvalidArgumentToServiceValidationException() {
        // Given
        StatusRuntimeException grpcException = Status.INVALID_ARGUMENT
            .withDescription("Invalid input")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceValidationException.class);
        ServiceValidationException exception = (ServiceValidationException) result;
        assertThat(exception.getMessage()).contains("Invalid input");
        assertThat(exception.getErrorContext().getServiceName()).isEqualTo(serviceName);
        assertThat(exception.getErrorContext().getGrpcStatusCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    @DisplayName("Should map FAILED_PRECONDITION to ServiceValidationException")
    void shouldMapFailedPreconditionToServiceValidationException() {
        // Given
        StatusRuntimeException grpcException = Status.FAILED_PRECONDITION
            .withDescription("Precondition failed")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceValidationException.class);
        assertThat(result.getMessage()).contains("Precondition failed");
    }

    @Test
    @DisplayName("Should map OUT_OF_RANGE to ServiceValidationException")
    void shouldMapOutOfRangeToServiceValidationException() {
        // Given
        StatusRuntimeException grpcException = Status.OUT_OF_RANGE
            .withDescription("Out of range")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceValidationException.class);
    }

    @Test
    @DisplayName("Should map UNAUTHENTICATED to ServiceAuthenticationException")
    void shouldMapUnauthenticatedToServiceAuthenticationException() {
        // Given
        StatusRuntimeException grpcException = Status.UNAUTHENTICATED
            .withDescription("Authentication required")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceAuthenticationException.class);
        assertThat(result.getMessage()).contains("Authentication required");
    }

    @Test
    @DisplayName("Should map PERMISSION_DENIED to ServiceAuthenticationException")
    void shouldMapPermissionDeniedToServiceAuthenticationException() {
        // Given
        StatusRuntimeException grpcException = Status.PERMISSION_DENIED
            .withDescription("Permission denied")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceAuthenticationException.class);
    }

    @Test
    @DisplayName("Should map NOT_FOUND to ServiceNotFoundException")
    void shouldMapNotFoundToServiceNotFoundException() {
        // Given
        StatusRuntimeException grpcException = Status.NOT_FOUND
            .withDescription("Resource not found")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceNotFoundException.class);
        assertThat(result.getMessage()).contains("Resource not found");
    }

    @Test
    @DisplayName("Should map DEADLINE_EXCEEDED to ServiceTimeoutException")
    void shouldMapDeadlineExceededToServiceTimeoutException() {
        // Given
        StatusRuntimeException grpcException = Status.DEADLINE_EXCEEDED
            .withDescription("Deadline exceeded")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceTimeoutException.class);
        assertThat(result).isInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("Should map ABORTED to ServiceConflictException")
    void shouldMapAbortedToServiceConflictException() {
        // Given
        StatusRuntimeException grpcException = Status.ABORTED
            .withDescription("Operation aborted")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceConflictException.class);
    }

    @Test
    @DisplayName("Should map ALREADY_EXISTS to ServiceConflictException")
    void shouldMapAlreadyExistsToServiceConflictException() {
        // Given
        StatusRuntimeException grpcException = Status.ALREADY_EXISTS
            .withDescription("Resource already exists")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceConflictException.class);
    }

    @Test
    @DisplayName("Should map RESOURCE_EXHAUSTED to ServiceRateLimitException")
    void shouldMapResourceExhaustedToServiceRateLimitException() {
        // Given
        StatusRuntimeException grpcException = Status.RESOURCE_EXHAUSTED
            .withDescription("Resource exhausted")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceRateLimitException.class);
        assertThat(result).isInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("Should map INTERNAL to ServiceInternalErrorException")
    void shouldMapInternalToServiceInternalErrorException() {
        // Given
        StatusRuntimeException grpcException = Status.INTERNAL
            .withDescription("Internal error")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceInternalErrorException.class);
        assertThat(result).isInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("Should map DATA_LOSS to ServiceInternalErrorException")
    void shouldMapDataLossToServiceInternalErrorException() {
        // Given
        StatusRuntimeException grpcException = Status.DATA_LOSS
            .withDescription("Data loss")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceInternalErrorException.class);
    }

    @Test
    @DisplayName("Should map UNKNOWN to ServiceInternalErrorException")
    void shouldMapUnknownToServiceInternalErrorException() {
        // Given
        StatusRuntimeException grpcException = Status.UNKNOWN
            .withDescription("Unknown error")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceInternalErrorException.class);
    }

    @Test
    @DisplayName("Should map UNAVAILABLE to ServiceTemporarilyUnavailableException")
    void shouldMapUnavailableToServiceTemporarilyUnavailableException() {
        // Given
        StatusRuntimeException grpcException = Status.UNAVAILABLE
            .withDescription("Service unavailable")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceTemporarilyUnavailableException.class);
        assertThat(result).isInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("Should include error context in mapped exceptions")
    void shouldIncludeErrorContextInMappedExceptions() {
        // Given
        StatusRuntimeException grpcException = Status.NOT_FOUND
            .withDescription("Resource not found")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        ServiceClientException exception = (ServiceClientException) result;
        ErrorContext context = exception.getErrorContext();

        assertThat(context.getServiceName()).isEqualTo(serviceName);
        assertThat(context.getMethod()).isEqualTo(operation);
        assertThat(context.getRequestId()).isEqualTo(requestId);
        assertThat(context.getGrpcStatusCode()).isEqualTo("NOT_FOUND");
        assertThat(context.hasElapsedTime()).isTrue();
        assertThat(context.getClientType()).isEqualTo(ClientType.GRPC);
    }

    @Test
    @DisplayName("Should handle non-StatusRuntimeException")
    void shouldHandleNonStatusRuntimeException() {
        // Given
        RuntimeException genericException = new RuntimeException("Generic error");

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            genericException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceClientException.class);
        assertThat(result.getMessage()).contains("Generic error");
        assertThat(result.getCause()).isEqualTo(genericException);
    }

    @Test
    @DisplayName("Should preserve original exception as cause")
    void shouldPreserveOriginalExceptionAsCause() {
        // Given
        StatusRuntimeException grpcException = Status.NOT_FOUND
            .withDescription("Not found")
            .asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result.getCause()).isEqualTo(grpcException);
    }

    @Test
    @DisplayName("Should handle null description gracefully")
    void shouldHandleNullDescriptionGracefully() {
        // Given
        StatusRuntimeException grpcException = Status.NOT_FOUND.asRuntimeException();

        // When
        Throwable result = GrpcErrorMapper.mapGrpcError(
            grpcException, serviceName, operation, requestId, startTime
        );

        // Then
        assertThat(result).isInstanceOf(ServiceNotFoundException.class);
        assertThat(result.getMessage()).isNotNull();
    }
}

