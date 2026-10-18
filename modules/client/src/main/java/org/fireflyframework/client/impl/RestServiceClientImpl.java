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

package org.fireflyframework.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.client.ClientType;
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.exception.HttpErrorMapper;
import org.fireflyframework.client.exception.RetryableError;
import org.fireflyframework.client.exception.ServiceClientException;
import org.fireflyframework.client.exception.ServiceSerializationException;
import org.fireflyframework.client.exception.ErrorContext;
import org.fireflyframework.resilience.CircuitBreakerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REST implementation of ServiceClient using WebClient.
 * 
 * <p>This implementation provides a simplified, unified interface for REST service
 * communication while maintaining all the power and flexibility of WebClient under the hood.
 *
 * <p>Key features:
 * <ul>
 *   <li>Fluent request builder API</li>
 *   <li>Built-in circuit breaker and retry mechanisms</li>
 *   <li>Automatic error handling and mapping</li>
 *   <li>Support for streaming responses</li>
 *   <li>Path parameter substitution</li>
 *   <li>Query parameter handling</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class RestServiceClientImpl implements RestClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String serviceName;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxConnections;
    private final Map<String, String> defaultHeaders;
    private final WebClient webClient;
    private final CircuitBreakerManager circuitBreakerManager;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    // Retry configuration
    private final boolean retryEnabled;
    private final int retryMaxAttempts;
    private final Duration retryInitialBackoff;
    private final Duration retryMaxBackoff;
    private final boolean retryJitterEnabled;

    /**
     * Creates a new REST service client implementation.
     */
    public RestServiceClientImpl(String serviceName,
                                String baseUrl,
                                Duration timeout,
                                int maxConnections,
                                Map<String, String> defaultHeaders,
                                WebClient webClient,
                                CircuitBreakerManager circuitBreakerManager) {
        this(serviceName, baseUrl, timeout, maxConnections, defaultHeaders, webClient,
                circuitBreakerManager, true, 3, Duration.ofMillis(500), Duration.ofSeconds(10), true);
    }

    /**
     * Creates a new REST service client implementation with retry configuration.
     */
    public RestServiceClientImpl(String serviceName,
                                String baseUrl,
                                Duration timeout,
                                int maxConnections,
                                Map<String, String> defaultHeaders,
                                WebClient webClient,
                                CircuitBreakerManager circuitBreakerManager,
                                boolean retryEnabled,
                                int retryMaxAttempts,
                                Duration retryInitialBackoff,
                                Duration retryMaxBackoff,
                                boolean retryJitterEnabled) {
        this.serviceName = serviceName;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.maxConnections = maxConnections;
        this.defaultHeaders = Map.copyOf(defaultHeaders);
        this.webClient = webClient != null ? webClient : createDefaultWebClient();
        this.circuitBreakerManager = circuitBreakerManager;
        this.retryEnabled = retryEnabled;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryInitialBackoff = retryInitialBackoff;
        this.retryMaxBackoff = retryMaxBackoff;
        this.retryJitterEnabled = retryJitterEnabled;

        log.info("Initialized REST service client for '{}' with base URL '{}', retry={} (maxAttempts={}, backoff={})",
                serviceName, baseUrl, retryEnabled, retryMaxAttempts, retryInitialBackoff);
    }

    // ========================================
    // Request Builder Methods
    // ========================================

    @Override
    public <R> RequestBuilder<R> get(String endpoint, Class<R> responseType) {
        return new RestRequestBuilder<>("GET", endpoint, responseType, null);
    }

    @Override
    public <R> RequestBuilder<R> get(String endpoint, TypeReference<R> typeReference) {
        return new RestRequestBuilder<>("GET", endpoint, null, typeReference);
    }

    @Override
    public <R> RequestBuilder<R> post(String endpoint, Class<R> responseType) {
        return new RestRequestBuilder<>("POST", endpoint, responseType, null);
    }

    @Override
    public <R> RequestBuilder<R> post(String endpoint, TypeReference<R> typeReference) {
        return new RestRequestBuilder<>("POST", endpoint, null, typeReference);
    }

    @Override
    public <R> RequestBuilder<R> put(String endpoint, Class<R> responseType) {
        return new RestRequestBuilder<>("PUT", endpoint, responseType, null);
    }

    @Override
    public <R> RequestBuilder<R> put(String endpoint, TypeReference<R> typeReference) {
        return new RestRequestBuilder<>("PUT", endpoint, null, typeReference);
    }

    @Override
    public <R> RequestBuilder<R> delete(String endpoint, Class<R> responseType) {
        return new RestRequestBuilder<>("DELETE", endpoint, responseType, null);
    }

    @Override
    public <R> RequestBuilder<R> delete(String endpoint, TypeReference<R> typeReference) {
        return new RestRequestBuilder<>("DELETE", endpoint, null, typeReference);
    }

    @Override
    public <R> RequestBuilder<R> patch(String endpoint, Class<R> responseType) {
        return new RestRequestBuilder<>("PATCH", endpoint, responseType, null);
    }

    @Override
    public <R> RequestBuilder<R> patch(String endpoint, TypeReference<R> typeReference) {
        return new RestRequestBuilder<>("PATCH", endpoint, null, typeReference);
    }


    // ========================================
    // Streaming Methods
    // ========================================

    @Override
    public <R> Flux<R> stream(String endpoint, Class<R> responseType) {
        return get(endpoint, responseType).stream();
    }

    @Override
    public <R> Flux<R> stream(String endpoint, TypeReference<R> typeReference) {
        return get(endpoint, typeReference).stream();
    }

    // ========================================
    // Client Metadata and Lifecycle
    // ========================================

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public boolean isReady() {
        return !isShutdown.get();
    }

    @Override
    public Mono<Void> healthCheck() {
        if (isShutdown.get()) {
            return Mono.error(new IllegalStateException("Client has been shut down"));
        }
        
        return webClient.get()
            .uri(baseUrl + "/health")
            .retrieve()
            .toBodilessEntity()
            .then()
            .timeout(Duration.ofSeconds(5))
            .onErrorMap(throwable -> new RuntimeException("Health check failed for service: " + serviceName, throwable));
    }

    @Override
    public ClientType getClientType() {
        return ClientType.REST;
    }

    @Override
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            log.info("Shutting down REST service client for service '{}'", serviceName);
            // WebClient doesn't require explicit shutdown, but we mark as shutdown
        }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private WebClient createDefaultWebClient() {
        WebClient.Builder builder = WebClient.builder()
            .baseUrl(baseUrl);

        // Add default headers
        defaultHeaders.forEach(builder::defaultHeader);

        return builder.build();
    }

    // ========================================
    // Inner RequestBuilder Implementation
    // ========================================

    private class RestRequestBuilder<R> implements RestClient.RequestBuilder<R> {
        private final String method;
        private final String endpoint;
        private final Class<R> responseType;
        private final TypeReference<R> typeReference;

        private Object body;
        private Map<String, Object> pathParams = new java.util.HashMap<>();
        private Map<String, Object> queryParams = new java.util.HashMap<>();
        private Map<String, String> headers = new java.util.HashMap<>();
        private Duration requestTimeout = timeout;

        public RestRequestBuilder(String method, String endpoint, Class<R> responseType, TypeReference<R> typeReference) {
            this.method = method;
            this.endpoint = endpoint;
            this.responseType = responseType;
            this.typeReference = typeReference;
        }

        @Override
        public RequestBuilder<R> withBody(Object body) {
            this.body = body;
            return this;
        }

        @Override
        public RequestBuilder<R> withPathParam(String name, Object value) {
            if (name != null && value != null) {
                pathParams.put(name, value);
            }
            return this;
        }

        @Override
        public RequestBuilder<R> withPathParams(Map<String, Object> pathParams) {
            if (pathParams != null) {
                this.pathParams.putAll(pathParams);
            }
            return this;
        }

        @Override
        public RequestBuilder<R> withQueryParam(String name, Object value) {
            if (name != null && value != null) {
                queryParams.put(name, value);
            }
            return this;
        }

        @Override
        public RequestBuilder<R> withQueryParams(Map<String, Object> queryParams) {
            if (queryParams != null) {
                this.queryParams.putAll(queryParams);
            }
            return this;
        }

        @Override
        public RequestBuilder<R> withHeader(String name, String value) {
            if (name != null && value != null) {
                headers.put(name, value);
            }
            return this;
        }

        @Override
        public RequestBuilder<R> withHeaders(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        @Override
        public RequestBuilder<R> withTimeout(Duration timeout) {
            if (timeout != null && !timeout.isNegative()) {
                this.requestTimeout = timeout;
            }
            return this;
        }

        @Override
        public Mono<R> execute() {
            if (isShutdown.get()) {
                return Mono.error(new IllegalStateException("Client has been shut down"));
            }

            return buildRequest()
                .timeout(requestTimeout)
                .doOnSubscribe(subscription ->
                    log.debug("Executing {} request to {} for service '{}'", method, endpoint, serviceName))
                .doOnSuccess(result ->
                    log.debug("Successfully completed {} request to {} for service '{}'", method, endpoint, serviceName))
                .doOnError(error ->
                    log.error("Failed {} request to {} for service '{}': {}", method, endpoint, serviceName, error.getMessage()));
        }

        @Override
        public Flux<R> stream() {
            if (isShutdown.get()) {
                return Flux.error(new IllegalStateException("Client has been shut down"));
            }

            // For streaming, we expect the response to be a collection or stream
            return buildRequest()
                .flatMapMany(response -> {
                    if (response instanceof Iterable) {
                        return Flux.fromIterable((Iterable<R>) response);
                    } else {
                        return Flux.just(response);
                    }
                })
                .timeout(requestTimeout)
                .doOnSubscribe(subscription ->
                    log.debug("Executing streaming {} request to {} for service '{}'", method, endpoint, serviceName));
        }

        private Mono<R> buildRequest() {
            // Build the URI with path parameters
            String uri = buildUri();

            // Create the request spec
            WebClient.RequestHeadersSpec<?> requestSpec = createRequestSpec(uri);

            // Add headers
            headers.forEach(requestSpec::header);

            // Execute and retrieve response
            return executeRequest(requestSpec);
        }

        private String buildUri() {
            String uri = endpoint;

            // Replace path parameters
            for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
                uri = uri.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }

            // Add query parameters
            if (!queryParams.isEmpty()) {
                StringBuilder queryString = new StringBuilder();
                queryParams.forEach((key, value) -> {
                    if (queryString.length() > 0) {
                        queryString.append("&");
                    }
                    queryString.append(key).append("=").append(value);
                });
                uri += "?" + queryString.toString();
            }

            return uri;
        }

        private WebClient.RequestHeadersSpec<?> createRequestSpec(String uri) {
            switch (method.toUpperCase()) {
                case "GET":
                    return webClient.get().uri(uri);
                case "POST":
                    return body != null ? webClient.post().uri(uri).bodyValue(body) : webClient.post().uri(uri);
                case "PUT":
                    return body != null ? webClient.put().uri(uri).bodyValue(body) : webClient.put().uri(uri);
                case "DELETE":
                    return webClient.delete().uri(uri);
                case "PATCH":
                    return body != null ? webClient.patch().uri(uri).bodyValue(body) : webClient.patch().uri(uri);
                default:
                    throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }
        }

        @SuppressWarnings("unchecked")
        private Mono<R> executeRequest(WebClient.RequestHeadersSpec<?> requestSpec) {
            // Generate request ID for tracking
            String requestId = UUID.randomUUID().toString();
            Instant startTime = Instant.now();

            // Add request ID header
            requestSpec.header("X-Request-ID", requestId);

            Mono<R> baseRequest = requestSpec.exchangeToMono(response -> {
                if (response.statusCode().isError()) {
                    // Map error response to typed exception
                    return HttpErrorMapper.mapHttpError(
                        response,
                        serviceName,
                        endpoint,
                        method,
                        requestId,
                        startTime
                    ).flatMap(Mono::error);
                }

                // Success response - deserialize
                if (responseType != null) {
                    // Special handling for DynamicJsonResponse
                    if (responseType.getName().equals("org.fireflyframework.client.dynamic.DynamicJsonResponse")) {
                        return response.bodyToMono(String.class)
                            .map(json -> {
                                try {
                                    // Use reflection to call DynamicJsonResponse.fromJson(String)
                                    Class<?> dynamicClass = Class.forName("org.fireflyframework.client.dynamic.DynamicJsonResponse");
                                    java.lang.reflect.Method fromJsonMethod = dynamicClass.getMethod("fromJson", String.class);
                                    return (R) fromJsonMethod.invoke(null, json);
                                } catch (Exception e) {
                                    ErrorContext context = ErrorContext.builder()
                                        .serviceName(serviceName)
                                        .endpoint(endpoint)
                                        .method(method)
                                        .clientType(ClientType.REST)
                                        .requestId(requestId)
                                        .elapsedTime(Duration.between(startTime, Instant.now()))
                                        .build();
                                    throw new ServiceSerializationException(
                                        "Failed to create DynamicJsonResponse: " + e.getMessage(),
                                        json,
                                        context,
                                        e);
                                }
                            });
                    }
                    return response.bodyToMono(responseType);
                } else if (typeReference != null) {
                    // For TypeReference, use shared ObjectMapper instance
                    return response.bodyToMono(String.class)
                        .map(json -> {
                            try {
                                return OBJECT_MAPPER.readValue(json, typeReference);
                            } catch (Exception e) {
                                ErrorContext context = ErrorContext.builder()
                                    .serviceName(serviceName)
                                    .endpoint(endpoint)
                                    .method(method)
                                    .clientType(ClientType.REST)
                                    .requestId(requestId)
                                    .elapsedTime(Duration.between(startTime, Instant.now()))
                                    .build();
                                throw new ServiceSerializationException(
                                    "Failed to deserialize response: " + e.getMessage(),
                                    json,
                                    context,
                                    e);
                            }
                        });
                } else {
                    throw new IllegalStateException("Either responseType or typeReference must be provided");
                }
            });

            // Apply retry (inside circuit breaker scope), then circuit breaker protection
            Mono<R> retriedRequest = applyRetry(baseRequest);
            return applyCircuitBreakerProtection(retriedRequest);
        }

        private Mono<R> applyRetry(Mono<R> operation) {
            if (!retryEnabled || retryMaxAttempts <= 0) {
                return operation;
            }

            Retry retrySpec = Retry.backoff(retryMaxAttempts, retryInitialBackoff)
                    .maxBackoff(retryMaxBackoff)
                    .jitter(retryJitterEnabled ? 0.5 : 0.0)
                    .filter(throwable -> throwable instanceof RetryableError
                            && ((RetryableError) throwable).isRetryable())
                    .doBeforeRetry(signal -> log.warn(
                            "Retrying request for service '{}' (attempt {}/{}): {}",
                            serviceName, signal.totalRetries() + 1, retryMaxAttempts,
                            signal.failure().getMessage()));

            return operation.retryWhen(retrySpec)
                    .onErrorMap(ex -> ex instanceof IllegalStateException && ex.getCause() != null
                                    && ex.getMessage() != null && ex.getMessage().contains("Retries exhausted"),
                            ex -> {
                                log.error("All {} retry attempts exhausted for service '{}': {}",
                                        retryMaxAttempts, serviceName, ex.getCause().getMessage());
                                return ex.getCause();
                            });
        }

        private Mono<R> applyCircuitBreakerProtection(Mono<R> operation) {
            // Use enhanced circuit breaker
            if (circuitBreakerManager != null) {
                return circuitBreakerManager.executeWithCircuitBreaker(serviceName, () -> operation)
                    .doOnError(error -> log.warn("Circuit breaker detected failure for service '{}': {}",
                        serviceName, error.getMessage()))
                    .doOnSuccess(result -> log.debug("Circuit breaker allowed successful request for service '{}'",
                        serviceName));
            } else {
                // No circuit breaker protection (should not happen with auto-configuration)
                log.warn("No circuit breaker configured for service '{}'", serviceName);
                return operation;
            }
        }
    }
}
