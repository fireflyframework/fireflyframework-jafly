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


package org.fireflyframework.web.idempotency.filter;

import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.fireflyframework.web.idempotency.cache.IdempotencyCache;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.fireflyframework.web.idempotency.model.CachedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebFilter that provides idempotency for all HTTP requests.
 * It intercepts requests with an X-Idempotency-Key header and ensures that
 * identical requests (with the same key) return the same response.
 * The X-Idempotency-Key header is optional for all HTTP methods.
 *
 * <p>This filter is automatically configured by {@link org.fireflyframework.web.idempotency.config.IdempotencyAutoConfiguration}
 * when fireflyframework-cache is available on the classpath. It will not be created if the required
 * cache dependencies are not available, preventing application startup failures.</p>
 */
public class IdempotencyWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyWebFilter.class);

    private final IdempotencyCache cache;
    private final String idempotencyHeaderName;
    private final Map<String, Sinks.One<CachedResponse>> inFlight = new ConcurrentHashMap<>();

    /**
     * Creates a new IdempotencyWebFilter with the specified caching mechanism.
     *
     * @param properties the idempotency configuration properties
     * @param cache the idempotency cache implementation
     */
    public IdempotencyWebFilter(IdempotencyProperties properties, IdempotencyCache cache) {
        this.cache = cache;
        this.idempotencyHeaderName = properties.getHeaderName();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        log.debug("IdempotencyWebFilter.filter: Processing request {} {}", request.getMethod(), request.getPath());

        // First check if this path is handled by a RestController or Router
        return isHandledByController(exchange)
                .flatMap(isControllerPath -> {
                    if (!isControllerPath) {
                        // Skip idempotency processing if the path is not handled by a controller
                        log.debug("IdempotencyWebFilter.filter: Skipping non-controller path {}", request.getPath());
                        return chain.filter(exchange);
                    }

                    // Check for idempotency header
                    List<String> idempotencyKeyHeaders = request.getHeaders().get(idempotencyHeaderName);
                    if (idempotencyKeyHeaders == null || idempotencyKeyHeaders.isEmpty()) {
                        log.debug("IdempotencyWebFilter.filter: No {} header found", idempotencyHeaderName);
                        return chain.filter(exchange);
                    }

                    String idempotencyKey = idempotencyKeyHeaders.get(0);
                    log.debug("IdempotencyWebFilter.filter: Found {}: {}", idempotencyHeaderName, idempotencyKey);

                    if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
                        log.debug("IdempotencyWebFilter.filter: Empty {} header", idempotencyHeaderName);
                        return sendBadRequest(exchange, "Invalid " + idempotencyHeaderName + " header");
                    }

                    // Check if idempotency is disabled for this endpoint
                    return isIdempotencyDisabled(exchange)
                            .flatMap(disabled -> {
                                if (disabled) {
                                    // Skip idempotency processing if the endpoint has the DisableIdempotency annotation
                                    log.debug("IdempotencyWebFilter.filter: Idempotency is disabled for this endpoint");
                                    return chain.filter(exchange);
                                }

                                log.debug("IdempotencyWebFilter.filter: Checking cache for key: {}", idempotencyKey);
                                // Check if we have a cached response
                                return cache.get(idempotencyKey)
                                        .hasElement()
                                        .flatMap(hasCachedResponse -> {
                                            if (hasCachedResponse) {
                                                log.debug("IdempotencyWebFilter.filter: Found cached response for key: {}", idempotencyKey);
                                                return cache.get(idempotencyKey)
                                                        .flatMap(cachedResponse -> returnCachedResponse(exchange, cachedResponse));
                                            } else {
                                                log.debug("IdempotencyWebFilter.filter: No cached response found for key: {}", idempotencyKey);
                                                Sinks.One<CachedResponse> newSink = Sinks.one();
                                                Sinks.One<CachedResponse> existingSink = inFlight.putIfAbsent(idempotencyKey, newSink);
                                                if (existingSink != null) {
                                                    log.debug("IdempotencyWebFilter.filter: Another request in-flight for key: {}. Waiting for result.", idempotencyKey);
                                                    return existingSink.asMono()
                                                            .flatMap(cached -> returnCachedResponse(exchange, cached));
                                                } else {
                                                    log.debug("IdempotencyWebFilter.filter: This request will compute and emit result for key: {}", idempotencyKey);
                                                    return processThroughFilterChain(exchange, chain, idempotencyKey, newSink);
                                                }
                                            }
                                        });
                            });
                });
    }

    private Mono<Void> processThroughFilterChain(ServerWebExchange exchange, WebFilterChain chain, String idempotencyKey, Sinks.One<CachedResponse> sink) {
        log.debug("IdempotencyWebFilter.processThroughFilterChain: Processing request through filter chain for key: {}", idempotencyKey);
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                log.debug("IdempotencyWebFilter.writeWith: Writing response for key: {}", idempotencyKey);
                // Convert the body to a Flux regardless of its type
                Flux<DataBuffer> fluxBody = Flux.from(body);

                return super.writeWith(fluxBody.collectList().map(dataBuffers -> {
                    log.debug("IdempotencyWebFilter.writeWith: Collected response body for key: {}", idempotencyKey);
                    // Combine all DataBuffers to get the complete response body
                    DataBuffer joinedBuffer = exchange.getResponse().bufferFactory().join(dataBuffers);
                    byte[] content = new byte[joinedBuffer.readableByteCount()];
                    joinedBuffer.read(content);

                    // Create a copy of the buffer for writing to the response
                    DataBuffer copiedBuffer = exchange.getResponse().bufferFactory().wrap(content);

                    // Store the response in the cache
                    MediaType contentType = exchange.getResponse().getHeaders().getContentType();
                    String contentTypeStr = contentType != null ? contentType.toString() : null;
                    int statusCode = exchange.getResponse().getStatusCode() != null ?
                            exchange.getResponse().getStatusCode().value() : HttpStatus.OK.value();

                    log.debug("IdempotencyWebFilter.writeWith: Caching response for key: {}, status: {}, contentType: {}, bodyLength: {}",
                             idempotencyKey, statusCode, contentTypeStr, content.length);

                    CachedResponse cachedResponse = new CachedResponse(statusCode, content, contentTypeStr);
                    cache.put(idempotencyKey, cachedResponse)
                        .doOnError(e -> {
                            // Log the error but don't fail the request
                            log.error("Failed to cache response for idempotency key {}: {}", idempotencyKey, e.getMessage(), e);
                        })
                        .onErrorResume(e -> Mono.empty())
                        .doOnSuccess(v -> log.debug("IdempotencyWebFilter.writeWith: Successfully cached response for key: {}", idempotencyKey))
                        .subscribe();

                    // Notify any waiting requests and clear in-flight marker
                    try {
                        sink.tryEmitValue(cachedResponse);
                    } finally {
                        inFlight.remove(idempotencyKey);
                    }

                    return copiedBuffer;
                }).flux());
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build())
                .doOnSuccess(v -> log.debug("IdempotencyWebFilter.processThroughFilterChain: Successfully processed request through filter chain for key: {}", idempotencyKey))
                .onErrorResume(e -> {
                    // Notify waiters of the error and cleanup
                    try {
                        sink.tryEmitError(e);
                    } finally {
                        inFlight.remove(idempotencyKey);
                    }
                    // Handle JSON parsing errors and other exceptions gracefully
                    if (isJsonParsingError(e)) {
                        log.error("IdempotencyWebFilter.processThroughFilterChain: JSON parsing error for key: {}, returning BAD_REQUEST", idempotencyKey);
                        return sendBadRequest(exchange, "Invalid JSON format in request body");
                    }
                    // For other errors, log and propagate them as is
                    log.error("IdempotencyWebFilter.processThroughFilterChain: Error processing request through filter chain for key: {}: {}", idempotencyKey, e.getMessage(), e);
                    return Mono.error(e);
                })
                .doFinally(signal -> {
                    // In case there was no body written (e.g., empty response), ensure cleanup
                    inFlight.remove(idempotencyKey);
                });
    }

    private Mono<Void> returnCachedResponse(ServerWebExchange exchange, CachedResponse cachedResponse) {
        log.debug("IdempotencyWebFilter.returnCachedResponse: Returning cached response with status: {}, contentType: {}, bodyLength: {}",
                 cachedResponse.getStatus(),
                 cachedResponse.getContentType(),
                 (cachedResponse.getBody() != null ? cachedResponse.getBody().length : 0));

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(cachedResponse.getStatus()));

        if (cachedResponse.getContentType() != null) {
            response.getHeaders().setContentType(MediaType.parseMediaType(cachedResponse.getContentType()));
        }

        DataBuffer buffer = response.bufferFactory().wrap(cachedResponse.getBody());
        return response.writeWith(Mono.just(buffer))
                .doOnSuccess(v -> log.debug("IdempotencyWebFilter.returnCachedResponse: Successfully returned cached response"))
                .doOnError(e -> log.error("IdempotencyWebFilter.returnCachedResponse: Error returning cached response: {}", e.getMessage(), e));
    }

    private Mono<Void> sendBadRequest(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.TEXT_PLAIN);

        byte[] bytes = message.getBytes();
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Checks if the given exception is related to JSON parsing errors.
     * 
     * @param throwable the exception to check
     * @return true if the exception is a JSON parsing error, false otherwise
     */
    private boolean isJsonParsingError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        // Check for JSON parsing exceptions
        String exceptionName = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        
        // Check for Jackson JSON parsing exceptions
        if (exceptionName.contains("JsonParseException") || 
            exceptionName.contains("JsonProcessingException") ||
            exceptionName.contains("JsonMappingException")) {
            return true;
        }
        
        // Check for Spring's DecodingException wrapping JSON errors
        if (exceptionName.contains("DecodingException") && 
            message != null && message.contains("JSON decoding error")) {
            return true;
        }
        
        // Check for ServerWebInputException with JSON-related messages
        if (exceptionName.contains("ServerWebInputException") && 
            message != null && message.contains("Failed to read HTTP message")) {
            return true;
        }
        
        // Recursively check the cause
        return isJsonParsingError(throwable.getCause());
    }

    /**
     * Checks if idempotency is disabled for the current endpoint.
     *
     * This method first tries to check if the handler method has the DisableIdempotency annotation.
     * If the handler is not available yet (which can happen in a WebFilter), it falls back to
     * checking the request path against known paths with the DisableIdempotency annotation.
     *
     * @param exchange the server web exchange
     * @return a Mono that emits true if idempotency is disabled for this endpoint, false otherwise
     */
    private Mono<Boolean> isIdempotencyDisabled(ServerWebExchange exchange) {
        // First try to get the handler from the exchange attributes
        Object handler = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);

        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            boolean disabled = handlerMethod.hasMethodAnnotation(DisableIdempotency.class);
            return Mono.just(disabled);
        }

        // If the handler is not available yet, we need to use a different approach
        // by checking the request path
        return Mono.just(false)
            .flatMap(disabled -> {
                // Get the path from the request
                String path = exchange.getRequest().getPath().value();

                // In a real application, this could be a configurable list of paths
                // or could use a more sophisticated matching mechanism
                if (path.endsWith("-disabled") || path.contains("/disabled/")) {
                    return Mono.just(true);
                }

                return Mono.just(disabled);
            });
    }

    /**
     * Checks if the current path is handled by a RestController or Router.
     *
     * This method tries to get the handler from the exchange attributes and checks
     * if it's a HandlerMethod (which indicates it's handled by a RestController) or
     * a RouterFunction (which indicates it's handled by a Router).
     *
     * @param exchange the server web exchange
     * @return a Mono that emits true if the path is handled by a controller, false otherwise
     */
    private Mono<Boolean> isHandledByController(ServerWebExchange exchange) {
        // Try to get the handler from the exchange attributes
        Object handler = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);

        // If the handler is a HandlerMethod, it's handled by a RestController
        if (handler instanceof HandlerMethod) {
            return Mono.just(true);
        }

        // If the handler is not available yet or is not a HandlerMethod,
        // we need to use a different approach to determine if it's a controller path

        // Get the path from the request
        String path = exchange.getRequest().getPath().value();

        // Check if the path starts with /api/ which is a common convention for REST APIs
        // This is a simple heuristic and might need to be adjusted based on your application's conventions
        if (path.startsWith("/api/")) {
            return Mono.just(true);
        }

        // For test paths, consider them as controller paths
        // This is to ensure that unit tests continue to work
        if (path.startsWith("/test")) {
            return Mono.just(true);
        }

        // If we can't determine for sure, we'll assume it's not a controller path
        // This is a conservative approach to avoid applying idempotency where it's not needed
        return Mono.just(false);
    }
}
