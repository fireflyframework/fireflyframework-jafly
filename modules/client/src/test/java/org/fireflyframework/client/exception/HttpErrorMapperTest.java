package org.fireflyframework.client.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for HttpErrorMapper.
 */
@DisplayName("HttpErrorMapper Tests")
class HttpErrorMapperTest {

    private ClientResponse mockResponse;
    private String serviceName;
    private String endpoint;
    private String method;
    private String requestId;
    private Instant startTime;

    @BeforeEach
    void setUp() {
        mockResponse = mock(ClientResponse.class);
        serviceName = "test-service";
        endpoint = "/api/test";
        method = "GET";
        requestId = "req-123";
        startTime = Instant.now().minusMillis(100);
    }

    private void setupMockResponse(HttpStatus status, String body) {
        setupMockResponse(status, body, new HttpHeaders());
    }

    private void setupMockResponse(HttpStatus status, String body, HttpHeaders headers) {
        ClientResponse.Headers responseHeaders = mock(ClientResponse.Headers.class);
        when(responseHeaders.asHttpHeaders()).thenReturn(headers);

        when(mockResponse.statusCode()).thenReturn(status);
        when(mockResponse.headers()).thenReturn(responseHeaders);
        when(mockResponse.bodyToMono(String.class)).thenReturn(Mono.just(body));
    }

    @Test
    @DisplayName("Should map 400 Bad Request to ServiceValidationException")
    void shouldMap400ToServiceValidationException() {
        // Given
        setupMockResponse(HttpStatus.BAD_REQUEST, "{\"error\":\"Invalid input\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceValidationException.class);
                ServiceValidationException exception = (ServiceValidationException) throwable;
                assertThat(exception.getErrorContext().getServiceName()).isEqualTo(serviceName);
                assertThat(exception.getErrorContext().getEndpoint()).isEqualTo(endpoint);
                assertThat(exception.getErrorContext().getHttpStatusCode()).isEqualTo(400);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 401 Unauthorized to ServiceAuthenticationException")
    void shouldMap401ToServiceAuthenticationException() {
        // Given
        setupMockResponse(HttpStatus.UNAUTHORIZED, "{\"error\":\"Unauthorized\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceAuthenticationException.class);
                assertThat(throwable.getMessage()).contains("Unauthorized");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 403 Forbidden to ServiceAuthenticationException")
    void shouldMap403ToServiceAuthenticationException() {
        // Given
        setupMockResponse(HttpStatus.FORBIDDEN, "{\"error\":\"Forbidden\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceAuthenticationException.class);
                assertThat(throwable.getMessage()).contains("Forbidden");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 404 Not Found to ServiceNotFoundException")
    void shouldMap404ToServiceNotFoundException() {
        // Given
        setupMockResponse(HttpStatus.NOT_FOUND, "{\"error\":\"Not found\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceNotFoundException.class);
                assertThat(throwable.getMessage()).contains("Not found");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 408 Request Timeout to ServiceTimeoutException")
    void shouldMap408ToServiceTimeoutException() {
        // Given
        setupMockResponse(HttpStatus.REQUEST_TIMEOUT, "{\"error\":\"Timeout\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceTimeoutException.class);
                assertThat(throwable).isInstanceOf(RetryableError.class);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 409 Conflict to ServiceConflictException")
    void shouldMap409ToServiceConflictException() {
        // Given
        setupMockResponse(HttpStatus.CONFLICT, "{\"error\":\"Conflict\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceConflictException.class);
                assertThat(throwable).isNotInstanceOf(RetryableError.class);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 422 Unprocessable Entity to ServiceUnprocessableEntityException")
    void shouldMap422ToServiceUnprocessableEntityException() {
        // Given
        setupMockResponse(HttpStatus.UNPROCESSABLE_ENTITY, "{\"error\":\"Validation failed\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceUnprocessableEntityException.class);
                assertThat(throwable).isInstanceOf(ServiceValidationException.class);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 429 Too Many Requests to ServiceRateLimitException")
    void shouldMap429ToServiceRateLimitException() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "60");
        ClientResponse.Headers responseHeaders = mock(ClientResponse.Headers.class);
        when(responseHeaders.asHttpHeaders()).thenReturn(headers);
        
        when(mockResponse.statusCode()).thenReturn(HttpStatus.TOO_MANY_REQUESTS);
        when(mockResponse.headers()).thenReturn(responseHeaders);
        when(mockResponse.bodyToMono(String.class)).thenReturn(Mono.just("{\"error\":\"Rate limited\"}"));

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceRateLimitException.class);
                assertThat(throwable).isInstanceOf(RetryableError.class);
                ServiceRateLimitException exception = (ServiceRateLimitException) throwable;
                assertThat(exception.getRetryAfterSeconds()).isEqualTo(60);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 500 Internal Server Error to ServiceInternalErrorException")
    void shouldMap500ToServiceInternalErrorException() {
        // Given
        setupMockResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"Internal error\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceInternalErrorException.class);
                assertThat(throwable).isInstanceOf(RetryableError.class);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 502 Bad Gateway to ServiceTemporarilyUnavailableException")
    void shouldMap502ToServiceTemporarilyUnavailableException() {
        // Given
        setupMockResponse(HttpStatus.BAD_GATEWAY, "{\"error\":\"Bad gateway\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceTemporarilyUnavailableException.class);
                assertThat(throwable).isInstanceOf(RetryableError.class);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 503 Service Unavailable to ServiceTemporarilyUnavailableException")
    void shouldMap503ToServiceTemporarilyUnavailableException() {
        // Given
        setupMockResponse(HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"Service unavailable\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceTemporarilyUnavailableException.class);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map 504 Gateway Timeout to ServiceTemporarilyUnavailableException")
    void shouldMap504ToServiceTemporarilyUnavailableException() {
        // Given
        setupMockResponse(HttpStatus.GATEWAY_TIMEOUT, "{\"error\":\"Gateway timeout\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceTemporarilyUnavailableException.class);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should include error context in mapped exceptions")
    void shouldIncludeErrorContextInMappedExceptions() {
        // Given
        setupMockResponse(HttpStatus.NOT_FOUND, "{\"error\":\"Not found\"}");

        // When
        Mono<? extends Throwable> result = HttpErrorMapper.mapHttpError(
            mockResponse, serviceName, endpoint, method, requestId, startTime
        );

        // Then
        StepVerifier.create(result)
            .assertNext(throwable -> {
                ServiceClientException exception = (ServiceClientException) throwable;
                ErrorContext context = exception.getErrorContext();

                assertThat(context.getServiceName()).isEqualTo(serviceName);
                assertThat(context.getEndpoint()).isEqualTo(endpoint);
                assertThat(context.getMethod()).isEqualTo(method);
                assertThat(context.getRequestId()).isEqualTo(requestId);
                assertThat(context.getHttpStatusCode()).isEqualTo(404);
                assertThat(context.hasElapsedTime()).isTrue();
            })
            .verifyComplete();
    }
}

