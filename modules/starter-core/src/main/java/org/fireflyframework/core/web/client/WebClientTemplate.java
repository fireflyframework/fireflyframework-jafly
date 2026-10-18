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


package org.fireflyframework.core.web.client;

import org.fireflyframework.core.config.WebClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
public class WebClientTemplate {
    private final WebClient webClient;
    private final WebClientProperties properties;

    /**
     * Adds headers from the incoming request to the outgoing request, excluding headers
     * specified in the skip headers list of the current properties configuration.
     *
     * @param requestSpec the WebClient.RequestHeadersSpec to which headers will be added
     * @param exchange the ServerWebExchange containing the incoming request and its headers
     * @return a Mono wrapping the WebClient.ResponseSpec created after adding the headers
     */
    private Mono<WebClient.ResponseSpec> addHeaders(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ServerWebExchange exchange) {
        exchange.getRequest().getHeaders().forEach((name, values) -> {
            if (properties.getSkipHeaders() == null || !properties.getSkipHeaders().contains(name.toLowerCase())) {
                values.forEach(value -> requestSpec.header(name, value));
            }
        });
        return Mono.just(requestSpec.retrieve());
    }


    /**
     * Performs a HTTP GET request to the specified URL and returns the response mapped to the provided type.
     *
     * @param baseUrl the base URL of the request
     * @param path the path to append to the base URL
     * @param responseType the class type to which the response should be mapped
     * @param exchange the server web exchange to provide additional context and headers for the request
     * @param <T> the type of the response expected
     * @return a Mono emitting the response mapped to the specified type, or an error if the request fails
     */
    public <T> Mono<T> get(String baseUrl, String path,
                           Class<T> responseType,
                           ServerWebExchange exchange) {
        return addHeaders(
                webClient.get()
                        .uri(baseUrl + path),
                exchange)
                .flatMap(spec -> spec.bodyToMono(responseType))
                .doOnSubscribe(s -> log.debug("GET request to: {}", path))
                .doOnSuccess(r -> log.debug("GET success: {}", path))
                .doOnError(e -> log.error("GET failed {}: {}", path, e.getMessage()));
    }


    /**
     * Sends a GET request to the specified URL and processes the response.
     *
     * @param <T> the type of the response body
     * @param baseUrl the base URL for the request
     * @param path the specific path to append to the base URL for the request
     * @param responseType the expected type of the response body
     * @param exchange the current server web exchange, used to extract and apply headers
     * @return a {@code Mono} representing the asynchronous result of the GET request,
     *         including the parsed response body of type {@code T}
     */
    public <T> Mono<T> get(String baseUrl, String path,
                           ParameterizedTypeReference<T> responseType,
                           ServerWebExchange exchange) {
        return addHeaders(
                webClient.get()
                        .uri(baseUrl + path),
                exchange)
                .flatMap(spec -> spec.bodyToMono(responseType))
                .doOnSubscribe(s -> log.debug("GET request to: {}", path))
                .doOnSuccess(r -> log.debug("GET success: {}", path))
                .doOnError(e -> log.error("GET failed {}: {}", path, e.getMessage()));
    }


    /**
     * Sends a GET request to the specified URL and returns the response as a Flux of the specified type.
     *
     * @param <T> the type of the response elements
     * @param baseUrl the base URL of the server
     * @param path the path to the resource to be accessed
     * @param responseType the class type of the response elements
     * @param exchange the server web exchange for request context and headers
     * @return a Flux containing response elements of the specified type
     */
    public <T> Flux<T> getFlux(String baseUrl, String path,
                               Class<T> responseType,
                               ServerWebExchange exchange) {
        return addHeaders(
                webClient.get()
                        .uri(baseUrl + path),
                exchange)
                .flatMapMany(spec -> spec.bodyToFlux(responseType))
                .doOnSubscribe(s -> log.debug("GET Flux request to: {}", path))
                .doOnComplete(() -> log.debug("GET Flux complete: {}", path))
                .doOnError(e -> log.error("GET Flux failed {}: {}", path, e.getMessage()));
    }


    /**
     * Performs a POST request to the specified URL with the given request body and response type.
     * Adds necessary headers from the provided {@code ServerWebExchange}.
     *
     * @param <T> the type of the request body
     * @param <R> the type of the response body
     * @param baseUrl the base URL to which the request is sent
     * @param path the specific path to append to the base URL
     * @param requestBody the object to be sent as the request body
     * @param responseType the class type of the expected response
     * @param exchange the {@code ServerWebExchange} containing headers to be added to the request
     * @return a {@code Mono} emitting the response of type {@code R}, or an error if the request fails
     */
    public <T, R> Mono<R> post(String baseUrl, String path,
                               T requestBody,
                               Class<R> responseType,
                               ServerWebExchange exchange) {
        return addHeaders(
                webClient.post()
                        .uri(baseUrl + path)
                        .bodyValue(requestBody),
                exchange)
                .flatMap(spec -> spec.bodyToMono(responseType))
                .doOnSubscribe(s -> log.debug("POST request to: {}", path))
                .doOnSuccess(r -> log.debug("POST success: {}", path))
                .doOnError(e -> log.error("POST failed {}: {}", path, e.getMessage()));
    }


    /**
     * Sends a POST request to the specified URL with a provided request body, processes the response, and handles logging.
     *
     * @param baseUrl       the base URL to send the POST request to
     * @param path          the specific path appended to the base URL for the request
     * @param requestBody   the body of the request to be sent
     * @param responseType  the expected type of the response wrapped in a {@link ParameterizedTypeReference}
     * @param exchange      the {@link ServerWebExchange} providing the request context and headers
     * @return a {@link Mono} emitting the response of type R upon successful completion
     */
    public <T, R> Mono<R> post(String baseUrl, String path,
                               T requestBody,
                               ParameterizedTypeReference<R> responseType,
                               ServerWebExchange exchange) {
        return addHeaders(
                webClient.post()
                        .uri(baseUrl + path)
                        .bodyValue(requestBody),
                exchange)
                .flatMap(spec -> spec.bodyToMono(responseType))
                .doOnSubscribe(s -> log.debug("POST request to: {}", path))
                .doOnSuccess(r -> log.debug("POST success: {}", path))
                .doOnError(e -> log.error("POST failed {}: {}", path, e.getMessage()));
    }


    /**
     * Sends an HTTP PUT request to the specified URL with the given request body and retrieves a response of the specified type.
     *
     * @param <T> the type of the request body
     * @param <R> the type of the response body
     * @param baseUrl the base URL of the endpoint
     * @param path the path to append to the base URL for the PUT request
     * @param requestBody the request body to be sent in the PUT request
     * @param responseType the class type of the expected response
     * @param exchange the server web exchange containing request details
     * @return a Mono emitting the response of type R
     */
    public <T, R> Mono<R> put(String baseUrl, String path,
                              T requestBody,
                              Class<R> responseType,
                              ServerWebExchange exchange) {
        return addHeaders(
                webClient.put()
                        .uri(baseUrl + path)
                        .bodyValue(requestBody),
                exchange)
                .flatMap(spec -> spec.bodyToMono(responseType))
                .doOnSubscribe(s -> log.debug("PUT request to: {}", path))
                .doOnSuccess(r -> log.debug("PUT success: {}", path))
                .doOnError(e -> log.error("PUT failed {}: {}", path, e.getMessage()));
    }


    /**
     * Sends an HTTP PUT request to the specified URL with the provided request body and headers.
     *
     * @param <T>           the type of the request body.
     * @param <R>           the type of the response body.
     * @param baseUrl       the base URL of the endpoint.
     * @param path          the URI path to append to the base URL.
     * @param requestBody   the request body to include in the PUT request.
     * @param responseType  the expected type of the response body wrapped in a {@code ParameterizedTypeReference}.
     * @param exchange      the {@link ServerWebExchange} containing headers and context-related information.
     * @return a {@link Mono} emitting the response body of type {@code R}, or an error if the request fails.
     */
    public <T, R> Mono<R> put(String baseUrl, String path,
                              T requestBody,
                              ParameterizedTypeReference<R> responseType,
                              ServerWebExchange exchange) {
        return addHeaders(
                webClient.put()
                        .uri(baseUrl + path)
                        .bodyValue(requestBody),
                exchange)
                .flatMap(spec -> spec.bodyToMono(responseType))
                .doOnSubscribe(s -> log.debug("PUT request to: {}", path))
                .doOnSuccess(r -> log.debug("PUT success: {}", path))
                .doOnError(e -> log.error("PUT failed {}: {}", path, e.getMessage()));
    }


    /**
     * Sends a DELETE HTTP request to the specified URL with the provided path
     * and exchange information. Additionally logs the request, success,
     * or failure events.
     *
     * @param baseUrl the base URL to which the DELETE request is made
     * @param path the specific path to append to the base URL for the DELETE request
     * @param exchange the {@link ServerWebExchange} that provides exchange-specific
     *                 information for processing the request
     * @return a {@link Mono} that completes when the DELETE operation is finished
     *         or emits an error if the operation fails
     */
    public Mono<Void> delete(String baseUrl, String path,
                             ServerWebExchange exchange) {
        return addHeaders(
                webClient.delete()
                        .uri(baseUrl + path),
                exchange)
                .flatMap(spec -> spec.bodyToMono(Void.class))
                .doOnSubscribe(s -> log.debug("DELETE request to: {}", path))
                .doOnSuccess(r -> log.debug("DELETE success: {}", path))
                .doOnError(e -> log.error("DELETE failed {}: {}", path, e.getMessage()));
    }
}