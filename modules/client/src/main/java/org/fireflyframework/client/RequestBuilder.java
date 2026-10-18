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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Fluent request builder interface for ServiceClient operations.
 * 
 * <p>This interface provides a fluent API for building and executing service requests
 * with support for path parameters, query parameters, headers, request bodies, and timeouts.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Simple GET request
 * Mono<User> user = client.get("/users/{id}", User.class)
 *     .withPathParam("id", "123")
 *     .execute();
 * 
 * // POST request with body and headers
 * Mono<User> created = client.post("/users", User.class)
 *     .withBody(newUser)
 *     .withHeader("Content-Type", "application/json")
 *     .withTimeout(Duration.ofSeconds(30))
 *     .execute();
 * 
 * // GET with query parameters
 * Mono<List<User>> users = client.get("/users", new TypeReference<List<User>>() {})
 *     .withQueryParam("status", "active")
 *     .withQueryParam("limit", 10)
 *     .execute();
 * }</pre>
 *
 * @param <R> the response type
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public interface RequestBuilder<R> {

    /**
     * Sets the request body for POST, PUT, and PATCH operations.
     *
     * @param body the request body object
     * @return this builder for method chaining
     */
    RequestBuilder<R> withBody(Object body);

    /**
     * Sets a single path parameter for URL template substitution.
     * 
     * <p>Path parameters are used to replace placeholders in the endpoint URL.
     * For example, with endpoint "/users/{id}" and pathParam("id", "123"),
     * the final URL becomes "/users/123".
     *
     * @param name the parameter name (without braces)
     * @param value the parameter value
     * @return this builder for method chaining
     */
    RequestBuilder<R> withPathParam(String name, Object value);

    /**
     * Sets multiple path parameters for URL template substitution.
     *
     * @param pathParams map of parameter names to values
     * @return this builder for method chaining
     */
    RequestBuilder<R> withPathParams(Map<String, Object> pathParams);

    /**
     * Sets a single query parameter.
     * 
     * <p>Query parameters are appended to the URL as key-value pairs.
     * Multiple values for the same parameter name will be combined.
     *
     * @param name the parameter name
     * @param value the parameter value
     * @return this builder for method chaining
     */
    RequestBuilder<R> withQueryParam(String name, Object value);

    /**
     * Sets multiple query parameters.
     *
     * @param queryParams map of parameter names to values
     * @return this builder for method chaining
     */
    RequestBuilder<R> withQueryParams(Map<String, Object> queryParams);

    /**
     * Sets a single HTTP header.
     *
     * @param name the header name
     * @param value the header value
     * @return this builder for method chaining
     */
    RequestBuilder<R> withHeader(String name, String value);

    /**
     * Sets multiple HTTP headers.
     *
     * @param headers map of header names to values
     * @return this builder for method chaining
     */
    RequestBuilder<R> withHeaders(Map<String, String> headers);

    /**
     * Sets the request timeout, overriding the client's default timeout.
     *
     * @param timeout the timeout duration
     * @return this builder for method chaining
     */
    RequestBuilder<R> withTimeout(Duration timeout);

    /**
     * Executes the request and returns a Mono with the response.
     * 
     * <p>This method triggers the actual HTTP/gRPC/SDK call and applies
     * all configured parameters, headers, and settings.
     *
     * @return a Mono containing the response
     */
    Mono<R> execute();

    /**
     * Executes the request as a streaming operation.
     * 
     * <p>This method is useful for server-sent events, streaming responses,
     * or when the response is expected to be a collection that should be
     * processed as a stream.
     *
     * @return a Flux containing the streaming response elements
     */
    Flux<R> stream();
}
