package org.fireflyframework.client.exception;

import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for error handling across the service client.
 */
@DisplayName("Error Handling Integration Tests")
class ErrorHandlingIntegrationTest {

    private static WireMockServer wireMockServer;
    private String baseUrl;
    private RestClient client;

    @BeforeAll
    static void setUpClass() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void tearDownClass() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        baseUrl = "http://localhost:" + wireMockServer.port();
        client = ServiceClient.rest("test-service")
            .baseUrl(baseUrl)
            .build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Should handle 404 with proper error context")
    void shouldHandle404WithProperErrorContext() {
        // Given
        wireMockServer.stubFor(get(urlEqualTo("/users/999"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"User not found\"}")));

        // When
        Mono<String> response = client.get("/users/{id}", String.class)
            .withPathParam("id", "999")
            .execute();

        // Then
        StepVerifier.create(response)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceNotFoundException.class);
                ServiceNotFoundException exception = (ServiceNotFoundException) throwable;
                
                // Verify error context
                ErrorContext context = exception.getErrorContext();
                assertThat(context.getServiceName()).isEqualTo("test-service");
                assertThat(context.getEndpoint()).isEqualTo("/users/{id}");
                assertThat(context.getMethod()).isEqualTo("GET");
                assertThat(context.getHttpStatusCode()).isEqualTo(404);
                assertThat(context.getRequestId()).isNotNull();
                assertThat(context.hasElapsedTime()).isTrue();
                
                // Verify error category
                assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.CLIENT_ERROR);
                
                // Verify message contains context
                assertThat(exception.getMessage()).contains("User not found");
                assertThat(exception.getMessage()).contains("test-service");
                assertThat(exception.getMessage()).contains("404");
            })
            .verify();
    }

    @Test
    @DisplayName("Should handle 500 as retryable error")
    void shouldHandle500AsRetryableError() {
        // Given
        wireMockServer.stubFor(get(urlEqualTo("/api/error"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Internal server error\"}")));

        // When
        Mono<String> response = client.get("/api/error", String.class)
            .execute();

        // Then
        StepVerifier.create(response)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceInternalErrorException.class);
                assertThat(throwable).isInstanceOf(RetryableError.class);
                
                ServiceInternalErrorException exception = (ServiceInternalErrorException) throwable;
                assertThat(exception.isRetryable()).isTrue();
                assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(2));
                assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.SERVER_ERROR);
            })
            .verify();
    }

    @Test
    @DisplayName("Should handle 429 with Retry-After header")
    void shouldHandle429WithRetryAfterHeader() {
        // Given
        wireMockServer.stubFor(get(urlEqualTo("/api/limited"))
            .willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withHeader("Retry-After", "120")
                .withBody("{\"error\":\"Rate limit exceeded\"}")));

        // When
        Mono<String> response = client.get("/api/limited", String.class)
            .execute();

        // Then
        StepVerifier.create(response)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceRateLimitException.class);
                
                ServiceRateLimitException exception = (ServiceRateLimitException) throwable;
                assertThat(exception.isRetryable()).isTrue();
                assertThat(exception.getRetryAfterSeconds()).isEqualTo(120);
                assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(120));
                assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.RATE_LIMIT_ERROR);
            })
            .verify();
    }

    @Test
    @DisplayName("Should handle 401 as non-retryable authentication error")
    void shouldHandle401AsNonRetryableAuthenticationError() {
        // Given
        wireMockServer.stubFor(get(urlEqualTo("/api/secure"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Unauthorized\"}")));

        // When
        Mono<String> response = client.get("/api/secure", String.class)
            .execute();

        // Then
        StepVerifier.create(response)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceAuthenticationException.class);
                assertThat(throwable).isNotInstanceOf(RetryableError.class);
                
                ServiceAuthenticationException exception = (ServiceAuthenticationException) throwable;
                assertThat(exception.getErrorCategory()).isEqualTo(ErrorCategory.AUTHENTICATION_ERROR);
            })
            .verify();
    }

    @Test
    @DisplayName("Should handle 422 with validation errors")
    void shouldHandle422WithValidationErrors() {
        // Given
        String validationResponse = """
            {
                "error": "Validation failed",
                "errors": [
                    {
                        "field": "email",
                        "message": "Invalid email format",
                        "code": "email.invalid"
                    },
                    {
                        "field": "age",
                        "message": "Must be at least 18",
                        "code": "age.min"
                    }
                ]
            }
            """;
        
        wireMockServer.stubFor(post(urlEqualTo("/api/users"))
            .willReturn(aResponse()
                .withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody(validationResponse)));

        // When
        Mono<String> response = client.post("/api/users", String.class)
            .withBody("{}")
            .execute();

        // Then
        StepVerifier.create(response)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceUnprocessableEntityException.class);
                
                ServiceUnprocessableEntityException exception = (ServiceUnprocessableEntityException) throwable;
                assertThat(exception.getValidationErrors()).hasSize(2);
                assertThat(exception.getValidationErrors().get(0).getField()).isEqualTo("email");
                assertThat(exception.getValidationErrors().get(0).getMessage()).isEqualTo("Invalid email format");
                assertThat(exception.getValidationErrors().get(1).getField()).isEqualTo("age");
            })
            .verify();
    }

    @Test
    @DisplayName("Should handle 503 as temporarily unavailable and retryable")
    void shouldHandle503AsTemporarilyUnavailableAndRetryable() {
        // Given
        wireMockServer.stubFor(get(urlEqualTo("/api/unavailable"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Service temporarily unavailable\"}")));

        // When
        Mono<String> response = client.get("/api/unavailable", String.class)
            .execute();

        // Then
        StepVerifier.create(response)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(ServiceTemporarilyUnavailableException.class);
                assertThat(throwable).isInstanceOf(ServiceUnavailableException.class);
                assertThat(throwable).isInstanceOf(RetryableError.class);
                
                ServiceTemporarilyUnavailableException exception = (ServiceTemporarilyUnavailableException) throwable;
                assertThat(exception.isRetryable()).isTrue();
                assertThat(exception.getRetryDelay()).isEqualTo(Duration.ofSeconds(5));
            })
            .verify();
    }

    @Test
    @DisplayName("Should include request ID in all error contexts")
    void shouldIncludeRequestIdInAllErrorContexts() {
        // Given
        wireMockServer.stubFor(get(urlEqualTo("/api/test"))
            .willReturn(aResponse()
                .withStatus(404)
                .withBody("{\"error\":\"Not found\"}")));

        // When
        Mono<String> response = client.get("/api/test", String.class)
            .execute();

        // Then
        StepVerifier.create(response)
            .expectErrorSatisfies(throwable -> {
                ServiceClientException exception = (ServiceClientException) throwable;
                ErrorContext context = exception.getErrorContext();
                
                assertThat(context.getRequestId()).isNotNull();
                assertThat(context.getRequestId()).isNotEmpty();
                assertThat(exception.getMessage()).contains(context.getRequestId());
            })
            .verify();
    }

    @Test
    @DisplayName("Should measure and include elapsed time in error context")
    void shouldMeasureAndIncludeElapsedTimeInErrorContext() {
        // Given
        wireMockServer.stubFor(get(urlEqualTo("/api/slow"))
            .willReturn(aResponse()
                .withStatus(500)
                .withFixedDelay(100)
                .withBody("{\"error\":\"Error\"}")));

        // When
        Mono<String> response = client.get("/api/slow", String.class)
            .execute();

        // Then
        StepVerifier.create(response)
            .expectErrorSatisfies(throwable -> {
                ServiceClientException exception = (ServiceClientException) throwable;
                ErrorContext context = exception.getErrorContext();

                assertThat(context.hasElapsedTime()).isTrue();
                assertThat(context.getElapsedTime()).isGreaterThanOrEqualTo(Duration.ofMillis(100));
                assertThat(exception.getMessage()).contains("Duration:");
            })
            .verify();
    }

    static class TestDto {
        private String name;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}

