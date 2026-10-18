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

package org.fireflyframework.client;

import com.fasterxml.jackson.core.type.TypeReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * REST-specific service client interface with HTTP semantics.
 * 
 * <p>Provides natural HTTP verb methods and streaming support for REST APIs.
 * Built on Spring WebFlux's WebClient with reactive, non-blocking operations.
 *
 * <p>Example usage:
 * <pre>{@code
 * RestClient client = ServiceClient.rest("user-service")
 *     .baseUrl("http://localhost:8080")
 *     .timeout(Duration.ofSeconds(30))
 *     .defaultHeader("X-API-Key", "secret")
 *     .build();
 *
 * // GET request
 * Mono<User> user = client.get("/users/{id}", User.class)
 *     .withPathParam("id", "123")
 *     .execute();
 *
 * // POST request
 * Mono<User> created = client.post("/users", User.class)
 *     .withBody(newUser)
 *     .execute();
 *
 * // Streaming
 * Flux<Event> events = client.stream("/events", Event.class);
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public interface RestClient extends ServiceClient {

    // ========================================
    // HTTP Verb Methods
    // ========================================

    /**
     * Creates a GET request builder.
     *
     * @param endpoint the endpoint path
     * @param responseType the expected response type
     * @param <R> the response type
     * @return a request builder for GET operations
     */
    <R> RequestBuilder<R> get(String endpoint, Class<R> responseType);

    /**
     * Creates a GET request builder with TypeReference for generic types.
     *
     * @param endpoint the endpoint path
     * @param typeReference the type reference for generic response types
     * @param <R> the response type
     * @return a request builder for GET operations
     */
    <R> RequestBuilder<R> get(String endpoint, TypeReference<R> typeReference);

    /**
     * Creates a POST request builder.
     *
     * @param endpoint the endpoint path
     * @param responseType the expected response type
     * @param <R> the response type
     * @return a request builder for POST operations
     */
    <R> RequestBuilder<R> post(String endpoint, Class<R> responseType);

    /**
     * Creates a POST request builder with TypeReference for generic types.
     *
     * @param endpoint the endpoint path
     * @param typeReference the type reference for generic response types
     * @param <R> the response type
     * @return a request builder for POST operations
     */
    <R> RequestBuilder<R> post(String endpoint, TypeReference<R> typeReference);

    /**
     * Creates a PUT request builder.
     *
     * @param endpoint the endpoint path
     * @param responseType the expected response type
     * @param <R> the response type
     * @return a request builder for PUT operations
     */
    <R> RequestBuilder<R> put(String endpoint, Class<R> responseType);

    /**
     * Creates a PUT request builder with TypeReference for generic types.
     *
     * @param endpoint the endpoint path
     * @param typeReference the type reference for generic response types
     * @param <R> the response type
     * @return a request builder for PUT operations
     */
    <R> RequestBuilder<R> put(String endpoint, TypeReference<R> typeReference);

    /**
     * Creates a DELETE request builder.
     *
     * @param endpoint the endpoint path
     * @param responseType the expected response type
     * @param <R> the response type
     * @return a request builder for DELETE operations
     */
    <R> RequestBuilder<R> delete(String endpoint, Class<R> responseType);

    /**
     * Creates a DELETE request builder with TypeReference for generic types.
     *
     * @param endpoint the endpoint path
     * @param typeReference the type reference for generic response types
     * @param <R> the response type
     * @return a request builder for DELETE operations
     */
    <R> RequestBuilder<R> delete(String endpoint, TypeReference<R> typeReference);

    /**
     * Creates a PATCH request builder.
     *
     * @param endpoint the endpoint path
     * @param responseType the expected response type
     * @param <R> the response type
     * @return a request builder for PATCH operations
     */
    <R> RequestBuilder<R> patch(String endpoint, Class<R> responseType);

    /**
     * Creates a PATCH request builder with TypeReference for generic types.
     *
     * @param endpoint the endpoint path
     * @param typeReference the type reference for generic response types
     * @param <R> the response type
     * @return a request builder for PATCH operations
     */
    <R> RequestBuilder<R> patch(String endpoint, TypeReference<R> typeReference);

    // ========================================
    // Streaming Methods
    // ========================================

    /**
     * Creates a streaming request for server-sent events or similar streaming responses.
     *
     * @param endpoint the endpoint path
     * @param responseType the expected response type for each stream element
     * @param <R> the response type
     * @return a Flux containing the streaming response
     */
    <R> Flux<R> stream(String endpoint, Class<R> responseType);

    /**
     * Creates a streaming request with TypeReference for generic types.
     *
     * @param endpoint the endpoint path
     * @param typeReference the type reference for generic response types
     * @param <R> the response type
     * @return a Flux containing the streaming response
     */
    <R> Flux<R> stream(String endpoint, TypeReference<R> typeReference);

    // ========================================
    // REST-Specific Metadata
    // ========================================

    /**
     * Returns the base URL for this REST client.
     *
     * @return the base URL
     */
    String getBaseUrl();

    /**
     * Fluent request builder for REST operations.
     *
     * @param <R> the response type
     */
    interface RequestBuilder<R> {
        /**
         * Sets the request body.
         *
         * @param body the request body
         * @return this builder
         */
        RequestBuilder<R> withBody(Object body);

        /**
         * Sets a path parameter.
         *
         * @param name the parameter name
         * @param value the parameter value
         * @return this builder
         */
        RequestBuilder<R> withPathParam(String name, Object value);

        /**
         * Sets multiple path parameters.
         *
         * @param pathParams the path parameters
         * @return this builder
         */
        RequestBuilder<R> withPathParams(Map<String, Object> pathParams);

        /**
         * Sets a query parameter.
         *
         * @param name the parameter name
         * @param value the parameter value
         * @return this builder
         */
        RequestBuilder<R> withQueryParam(String name, Object value);

        /**
         * Sets multiple query parameters.
         *
         * @param queryParams the query parameters
         * @return this builder
         */
        RequestBuilder<R> withQueryParams(Map<String, Object> queryParams);

        /**
         * Sets a header.
         *
         * @param name the header name
         * @param value the header value
         * @return this builder
         */
        RequestBuilder<R> withHeader(String name, String value);

        /**
         * Sets multiple headers.
         *
         * @param headers the headers
         * @return this builder
         */
        RequestBuilder<R> withHeaders(Map<String, String> headers);

        /**
         * Sets the request timeout.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        RequestBuilder<R> withTimeout(Duration timeout);

        /**
         * Executes the request.
         *
         * @return a Mono containing the response
         */
        Mono<R> execute();

        /**
         * Executes the request as a stream.
         *
         * @return a Flux containing the streaming response
         */
        Flux<R> stream();
    }
}

