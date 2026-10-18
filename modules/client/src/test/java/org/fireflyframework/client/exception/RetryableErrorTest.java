package org.fireflyframework.client.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for RetryableError interface and retryable exceptions.
 */
@DisplayName("RetryableError Tests")
class RetryableErrorTest {

    @Test
    @DisplayName("ServiceTimeoutException should be retryable with 2 second delay")
    void serviceTimeoutExceptionShouldBeRetryable() {
        // When
        ServiceTimeoutException exception = new ServiceTimeoutException("Timeout");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("ServiceRateLimitException should be retryable with 60 second delay")
    void serviceRateLimitExceptionShouldBeRetryable() {
        // When
        ServiceRateLimitException exception = new ServiceRateLimitException("Rate limited");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("ServiceRateLimitException should use Retry-After header if available")
    void serviceRateLimitExceptionShouldUseRetryAfterHeader() {
        // Given
        ErrorContext context = ErrorContext.builder()
            .serviceName("test-service")
            .build();

        // When
        ServiceRateLimitException exception = new ServiceRateLimitException("Rate limited", Integer.valueOf(120), context);

        // Then
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(120));
        assertThat(exception.getRetryAfterSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("ServiceInternalErrorException should be retryable with 2 second delay")
    void serviceInternalErrorExceptionShouldBeRetryable() {
        // When
        ServiceInternalErrorException exception = new ServiceInternalErrorException("Internal error");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("ServiceTemporarilyUnavailableException should be retryable with 5 second delay")
    void serviceTemporarilyUnavailableExceptionShouldBeRetryable() {
        // When
        ServiceTemporarilyUnavailableException exception = new ServiceTemporarilyUnavailableException("Temporarily unavailable");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("ServiceConnectionException should be retryable with 1 second delay")
    void serviceConnectionExceptionShouldBeRetryable() {
        // When
        ServiceConnectionException exception = new ServiceConnectionException("Connection failed");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("CircuitBreakerOpenException should be retryable with 5 second delay")
    void circuitBreakerOpenExceptionShouldBeRetryable() {
        // When
        CircuitBreakerOpenException exception = new CircuitBreakerOpenException("Circuit open");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("CircuitBreakerTimeoutException should be retryable with 2 second delay")
    void circuitBreakerTimeoutExceptionShouldBeRetryable() {
        // When
        CircuitBreakerTimeoutException exception = new CircuitBreakerTimeoutException("Circuit timeout");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("LoadSheddingException should be retryable with 3 second delay")
    void loadSheddingExceptionShouldBeRetryable() {
        // When
        LoadSheddingException exception = new LoadSheddingException("Load shedding active");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("RateLimitExceededException should be retryable with 1 second delay")
    void rateLimitExceededExceptionShouldBeRetryable() {
        // When
        RateLimitExceededException exception = new RateLimitExceededException("Rate limit exceeded");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("BulkheadFullException should be retryable with 500ms delay")
    void bulkheadFullExceptionShouldBeRetryable() {
        // When
        BulkheadFullException exception = new BulkheadFullException("Bulkhead full");

        // Then
        assertThat(exception).isInstanceOf(RetryableError.class);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("ServiceNotFoundException should NOT be retryable")
    void serviceNotFoundExceptionShouldNotBeRetryable() {
        // When
        ServiceNotFoundException exception = new ServiceNotFoundException("Not found");

        // Then
        assertThat(exception).isNotInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("ServiceValidationException should NOT be retryable")
    void serviceValidationExceptionShouldNotBeRetryable() {
        // When
        ServiceValidationException exception = new ServiceValidationException("Validation failed");

        // Then
        assertThat(exception).isNotInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("ServiceAuthenticationException should NOT be retryable")
    void serviceAuthenticationExceptionShouldNotBeRetryable() {
        // When
        ServiceAuthenticationException exception = new ServiceAuthenticationException("Auth failed");

        // Then
        assertThat(exception).isNotInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("ServiceConflictException should NOT be retryable")
    void serviceConflictExceptionShouldNotBeRetryable() {
        // When
        ServiceConflictException exception = new ServiceConflictException("Conflict");

        // Then
        assertThat(exception).isNotInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("ServiceSerializationException should NOT be retryable")
    void serviceSerializationExceptionShouldNotBeRetryable() {
        // When
        ServiceSerializationException exception = new ServiceSerializationException("Serialization failed", "raw");

        // Then
        assertThat(exception).isNotInstanceOf(RetryableError.class);
    }

    @Test
    @DisplayName("Should have different retry delays for different exception types")
    void shouldHaveDifferentRetryDelaysForDifferentExceptionTypes() {
        // Given
        ServiceTimeoutException timeout = new ServiceTimeoutException("Timeout");
        ServiceRateLimitException rateLimit = new ServiceRateLimitException("Rate limited");
        ServiceConnectionException connection = new ServiceConnectionException("Connection failed");
        BulkheadFullException bulkhead = new BulkheadFullException("Bulkhead full");

        // Then
        assertThat(timeout.getRetryDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(rateLimit.getRetryDelay()).isEqualTo(Duration.ofSeconds(60));
        assertThat(connection.getRetryDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(bulkhead.getRetryDelay()).isEqualTo(Duration.ofMillis(500));
    }
}

