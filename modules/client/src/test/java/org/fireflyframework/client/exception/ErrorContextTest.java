package org.fireflyframework.client.exception;

import org.fireflyframework.client.ClientType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for ErrorContext.
 */
@DisplayName("ErrorContext Tests")
class ErrorContextTest {

    @Test
    @DisplayName("Should create empty error context")
    void shouldCreateEmptyErrorContext() {
        // When
        ErrorContext context = ErrorContext.empty();

        // Then
        assertThat(context).isNotNull();
        assertThat(context.getServiceName()).isNull();
        assertThat(context.getEndpoint()).isNull();
        assertThat(context.getMethod()).isNull();
        assertThat(context.getClientType()).isNull();
        assertThat(context.getRequestId()).isNull();
        assertThat(context.getCorrelationId()).isNull();
        assertThat(context.getTimestamp()).isNotNull(); // Default timestamp is set
        assertThat(context.getHttpStatusCode()).isNull();
        assertThat(context.getGrpcStatusCode()).isNull();
        assertThat(context.getResponseBody()).isNull();
        assertThat(context.getHeaders()).isEmpty(); // Default empty map
        assertThat(context.getElapsedTime()).isNull();
        assertThat(context.getRetryAttempt()).isNull();
        assertThat(context.getAdditionalContext()).isEmpty(); // Default empty map
    }

    @Test
    @DisplayName("Should build error context with all fields")
    void shouldBuildErrorContextWithAllFields() {
        // Given
        Instant now = Instant.now();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-ID", "test-123");
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("key", "value");

        // When
        ErrorContext context = ErrorContext.builder()
            .serviceName("test-service")
            .endpoint("/api/test")
            .method("GET")
            .clientType(ClientType.REST)
            .requestId("req-123")
            .correlationId("corr-456")
            .timestamp(now)
            .httpStatusCode(404)
            .grpcStatusCode("NOT_FOUND")
            .responseBody("{\"error\":\"Not found\"}")
            .headers(headers)
            .elapsedTime(Duration.ofMillis(100))
            .retryAttempt(2)
            .additionalContext(additionalContext)
            .build();

        // Then
        assertThat(context.getServiceName()).isEqualTo("test-service");
        assertThat(context.getEndpoint()).isEqualTo("/api/test");
        assertThat(context.getMethod()).isEqualTo("GET");
        assertThat(context.getClientType()).isEqualTo(ClientType.REST);
        assertThat(context.getRequestId()).isEqualTo("req-123");
        assertThat(context.getCorrelationId()).isEqualTo("corr-456");
        assertThat(context.getTimestamp()).isEqualTo(now);
        assertThat(context.getHttpStatusCode()).isEqualTo(404);
        assertThat(context.getGrpcStatusCode()).isEqualTo("NOT_FOUND");
        assertThat(context.getResponseBody()).isEqualTo("{\"error\":\"Not found\"}");
        assertThat(context.getHeaders()).containsEntry("X-Request-ID", "test-123");
        assertThat(context.getElapsedTime()).isEqualTo(Duration.ofMillis(100));
        assertThat(context.getRetryAttempt()).isEqualTo(2);
        assertThat(context.getAdditionalContext()).containsEntry("key", "value");
    }

    @Test
    @DisplayName("Should check if HTTP status code is present")
    void shouldCheckIfHttpStatusCodeIsPresent() {
        // Given
        ErrorContext withStatus = ErrorContext.builder()
            .httpStatusCode(404)
            .build();
        ErrorContext withoutStatus = ErrorContext.builder()
            .build();

        // Then
        assertThat(withStatus.hasHttpStatusCode()).isTrue();
        assertThat(withoutStatus.hasHttpStatusCode()).isFalse();
    }

    @Test
    @DisplayName("Should check if gRPC status code is present")
    void shouldCheckIfGrpcStatusCodeIsPresent() {
        // Given
        ErrorContext withStatus = ErrorContext.builder()
            .grpcStatusCode("NOT_FOUND")
            .build();
        ErrorContext withoutStatus = ErrorContext.builder()
            .build();

        // Then
        assertThat(withStatus.hasGrpcStatusCode()).isTrue();
        assertThat(withoutStatus.hasGrpcStatusCode()).isFalse();
    }

    @Test
    @DisplayName("Should check if elapsed time is present")
    void shouldCheckIfElapsedTimeIsPresent() {
        // Given
        ErrorContext withTime = ErrorContext.builder()
            .elapsedTime(Duration.ofMillis(100))
            .build();
        ErrorContext withoutTime = ErrorContext.builder()
            .build();

        // Then
        assertThat(withTime.hasElapsedTime()).isTrue();
        assertThat(withoutTime.hasElapsedTime()).isFalse();
    }

    @Test
    @DisplayName("Should check if it's a retry")
    void shouldCheckIfIsRetry() {
        // Given
        ErrorContext retry = ErrorContext.builder()
            .retryAttempt(2)
            .build();
        ErrorContext firstAttempt = ErrorContext.builder()
            .retryAttempt(0)
            .build();
        ErrorContext noAttempt = ErrorContext.builder()
            .build();

        // Then
        assertThat(retry.isRetry()).isTrue();
        assertThat(firstAttempt.isRetry()).isFalse();
        assertThat(noAttempt.isRetry()).isFalse();
    }

    @Test
    @DisplayName("Should format context as string")
    void shouldFormatContextAsString() {
        // Given
        ErrorContext context = ErrorContext.builder()
            .serviceName("test-service")
            .endpoint("/api/test")
            .httpStatusCode(404)
            .requestId("req-123")
            .elapsedTime(Duration.ofMillis(100))
            .build();

        // When
        String formatted = context.toString();

        // Then
        assertThat(formatted).contains("test-service");
        assertThat(formatted).contains("/api/test");
        assertThat(formatted).contains("404");
        assertThat(formatted).contains("req-123");
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void shouldHandleNullValuesGracefully() {
        // When
        ErrorContext context = ErrorContext.builder()
            .serviceName(null)
            .endpoint(null)
            .httpStatusCode(null)
            .build();

        // Then
        assertThat(context.getServiceName()).isNull();
        assertThat(context.getEndpoint()).isNull();
        assertThat(context.getHttpStatusCode()).isNull();
        assertThat(context.hasHttpStatusCode()).isFalse();
    }

    @Test
    @DisplayName("Should build minimal error context")
    void shouldBuildMinimalErrorContext() {
        // When
        ErrorContext context = ErrorContext.builder()
            .serviceName("test-service")
            .build();

        // Then
        assertThat(context.getServiceName()).isEqualTo("test-service");
        assertThat(context.getEndpoint()).isNull();
        assertThat(context.getMethod()).isNull();
    }

    @Test
    @DisplayName("Should support builder pattern chaining")
    void shouldSupportBuilderPatternChaining() {
        // When
        ErrorContext context = ErrorContext.builder()
            .serviceName("service1")
            .endpoint("/api/v1")
            .method("POST")
            .httpStatusCode(500)
            .build();

        // Then
        assertThat(context).isNotNull();
        assertThat(context.getServiceName()).isEqualTo("service1");
        assertThat(context.getEndpoint()).isEqualTo("/api/v1");
        assertThat(context.getMethod()).isEqualTo("POST");
        assertThat(context.getHttpStatusCode()).isEqualTo(500);
    }
}

