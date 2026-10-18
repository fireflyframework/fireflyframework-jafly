package org.fireflyframework.client.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for ServiceClientException and its subclasses.
 */
@DisplayName("ServiceClientException Tests")
class ServiceClientExceptionTest {

    @Test
    @DisplayName("Should create exception with message only")
    void shouldCreateExceptionWithMessageOnly() {
        // When
        ServiceClientException exception = new ServiceClientException("Test error");

        // Then
        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getErrorContext()).isNotNull();
        assertThat(exception.getErrorContext().getServiceName()).isNull();
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.UNKNOWN_ERROR);
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateExceptionWithMessageAndCause() {
        // Given
        Throwable cause = new RuntimeException("Root cause");

        // When
        ServiceClientException exception = new ServiceClientException("Test error", cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getErrorContext()).isNotNull();
        assertThat(exception.getErrorContext().getServiceName()).isNull();
    }

    @Test
    @DisplayName("Should create exception with error context")
    void shouldCreateExceptionWithErrorContext() {
        // Given
        ErrorContext context = ErrorContext.builder()
            .serviceName("test-service")
            .endpoint("/api/test")
            .httpStatusCode(404)
            .build();

        // When
        ServiceClientException exception = new ServiceClientException("Test error", context);

        // Then
        assertThat(exception.getMessage()).contains("Test error");
        assertThat(exception.getMessage()).contains("test-service");
        assertThat(exception.getMessage()).contains("/api/test");
        assertThat(exception.getMessage()).contains("404");
        assertThat(exception.getErrorContext()).isEqualTo(context);
    }

    @Test
    @DisplayName("Should create exception with error context and cause")
    void shouldCreateExceptionWithErrorContextAndCause() {
        // Given
        Throwable cause = new RuntimeException("Root cause");
        ErrorContext context = ErrorContext.builder()
            .serviceName("test-service")
            .requestId("req-123")
            .build();

        // When
        ServiceClientException exception = new ServiceClientException("Test error", context, cause);

        // Then
        assertThat(exception.getMessage()).contains("Test error");
        assertThat(exception.getMessage()).contains("test-service");
        assertThat(exception.getMessage()).contains("req-123");
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getErrorContext()).isEqualTo(context);
    }

    @Test
    @DisplayName("ServiceNotFoundException should have CLIENT_ERROR category")
    void serviceNotFoundExceptionShouldHaveClientErrorCategory() {
        // When
        ServiceNotFoundException exception = new ServiceNotFoundException("Not found");

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.CLIENT_ERROR);
    }

    @Test
    @DisplayName("ServiceUnavailableException should have SERVER_ERROR category")
    void serviceUnavailableExceptionShouldHaveServerErrorCategory() {
        // When
        ServiceUnavailableException exception = new ServiceUnavailableException("Unavailable");

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.SERVER_ERROR);
    }

    @Test
    @DisplayName("ServiceValidationException should have VALIDATION_ERROR category")
    void serviceValidationExceptionShouldHaveValidationErrorCategory() {
        // When
        ServiceValidationException exception = new ServiceValidationException("Validation failed");

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("ServiceAuthenticationException should have AUTHENTICATION_ERROR category")
    void serviceAuthenticationExceptionShouldHaveAuthenticationErrorCategory() {
        // When
        ServiceAuthenticationException exception = new ServiceAuthenticationException("Auth failed");

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.AUTHENTICATION_ERROR);
    }

    @Test
    @DisplayName("ServiceTimeoutException should have TIMEOUT_ERROR category")
    void serviceTimeoutExceptionShouldHaveTimeoutErrorCategory() {
        // When
        ServiceTimeoutException exception = new ServiceTimeoutException("Timeout");

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.TIMEOUT_ERROR);
    }

    @Test
    @DisplayName("ServiceRateLimitException should have RATE_LIMIT_ERROR category")
    void serviceRateLimitExceptionShouldHaveRateLimitErrorCategory() {
        // When
        ServiceRateLimitException exception = new ServiceRateLimitException("Rate limited");

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.RATE_LIMIT_ERROR);
    }

    @Test
    @DisplayName("CircuitBreakerOpenException should have CIRCUIT_BREAKER_ERROR category")
    void circuitBreakerOpenExceptionShouldHaveCircuitBreakerErrorCategory() {
        // When
        CircuitBreakerOpenException exception = new CircuitBreakerOpenException("Circuit open");

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.CIRCUIT_BREAKER_ERROR);
    }

    @Test
    @DisplayName("ServiceSerializationException should have SERIALIZATION_ERROR category")
    void serviceSerializationExceptionShouldHaveSerializationErrorCategory() {
        // When
        ServiceSerializationException exception = new ServiceSerializationException("Serialization failed", "raw content");

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.SERIALIZATION_ERROR);
    }

    @Test
    @DisplayName("WsdlParsingException should have CONFIGURATION_ERROR category")
    void wsdlParsingExceptionShouldHaveConfigurationErrorCategory() {
        // When
        WsdlParsingException exception = new WsdlParsingException("WSDL parsing failed", new RuntimeException("Parse error"));

        // Then
        assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.CONFIGURATION_ERROR);
    }

    @Test
    @DisplayName("Should enrich message with error context")
    void shouldEnrichMessageWithErrorContext() {
        // Given
        ErrorContext context = ErrorContext.builder()
            .serviceName("user-service")
            .endpoint("/users/123")
            .method("GET")
            .httpStatusCode(404)
            .requestId("req-abc")
            .elapsedTime(Duration.ofMillis(150))
            .build();

        // When
        ServiceNotFoundException exception = new ServiceNotFoundException("User not found", context);

        // Then
        String message = exception.getMessage();
        assertThat(message).contains("User not found");
        assertThat(message).contains("Service: user-service");
        assertThat(message).contains("Endpoint: /users/123");
        assertThat(message).contains("HTTP Status: 404");
        assertThat(message).contains("Request ID: req-abc");
        assertThat(message).contains("Duration:");
    }

    @Test
    @DisplayName("Should handle empty error context gracefully")
    void shouldHandleEmptyErrorContextGracefully() {
        // Given
        ErrorContext emptyContext = ErrorContext.empty();

        // When
        ServiceClientException exception = new ServiceClientException("Test error", emptyContext);

        // Then
        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getErrorContext()).isNotNull();
        assertThat(exception.getErrorContext().getServiceName()).isNull();
    }

    @Test
    @DisplayName("SoapFaultException should determine category based on fault code")
    void soapFaultExceptionShouldDetermineCategoryBasedOnFaultCode() {
        // Given
        javax.xml.namespace.QName clientFault = new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/", "Client");
        javax.xml.namespace.QName serverFault = new javax.xml.namespace.QName("http://schemas.xmlsoap.org/soap/envelope/", "Server");

        // When
        SoapFaultException clientException = new SoapFaultException(clientFault, "Client fault", null, "Client error", ErrorContext.empty());
        SoapFaultException serverException = new SoapFaultException(serverFault, "Server fault", null, "Server error", ErrorContext.empty());

        // Then
        assertThat(clientException.getErrorCategory()).isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(serverException.getErrorCategory()).isEqualTo(ErrorCategory.SERVER_ERROR);
    }
}

