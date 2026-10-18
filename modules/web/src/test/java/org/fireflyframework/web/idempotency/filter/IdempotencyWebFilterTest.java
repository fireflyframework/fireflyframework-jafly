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

import org.fireflyframework.web.idempotency.cache.IdempotencyCache;
import org.fireflyframework.web.idempotency.cache.TestIdempotencyCache;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.fireflyframework.web.idempotency.model.CachedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for the IdempotencyWebFilter class.
 * These tests verify that the idempotency filter correctly prevents duplicate
 * requests with the same idempotency key from reaching the controller.
 */
class IdempotencyWebFilterTest {

    private IdempotencyWebFilter idempotencyWebFilter;
    private IdempotencyProperties properties;
    private TestIdempotencyCache idempotencyCache;

    private WebFilterChain filterChain;

    @BeforeEach
    void setUp() {
        // Mock the filter chain
        filterChain = mock(WebFilterChain.class);
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // Create a real cache implementation for testing
        idempotencyCache = new TestIdempotencyCache();

        // Clear the cache before each test
        idempotencyCache.clear();

        // Create properties
        properties = new IdempotencyProperties();
        properties.getCache().setTtlHours(24);

        // Create the filter
        idempotencyWebFilter = new IdempotencyWebFilter(properties, idempotencyCache);
    }

    @Test
    void shouldAllowRequestWithoutIdempotencyKey() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<Void> result = idempotencyWebFilter.filter(exchange, filterChain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();
        verify(filterChain, times(1)).filter(exchange);
    }

    @Test
    void shouldAllowFirstRequestWithIdempotencyKey() {
        // Arrange
        String idempotencyKey = "test-key-1";
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<Void> result = idempotencyWebFilter.filter(exchange, filterChain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();
        verify(filterChain, times(1)).filter(any());
    }

    @Test
    void shouldNotAllowSecondRequestWithSameIdempotencyKey() {
        // Arrange
        String idempotencyKey = "test-key-2";

        // Create a cached response
        byte[] responseBody = "{\"message\":\"Test response\"}".getBytes();
        CachedResponse cachedResponse = new CachedResponse(
                HttpStatus.OK.value(),
                responseBody,
                MediaType.APPLICATION_JSON.toString()
        );

        // Put the response in the cache directly
        idempotencyCache.putSync(idempotencyKey, cachedResponse);

        // Create a request with the same idempotency key
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act and wait for completion
        idempotencyWebFilter.filter(exchange, filterChain).block();

        // Verify that the filter chain was not called (request didn't reach the controller)
        verify(filterChain, never()).filter(any());

        // Verify that the response status is OK (from the cached response)
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());

        // Verify that the response content type is APPLICATION_JSON (from the cached response)
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void shouldApplyIdempotencyToGetMethod() {
        // Arrange
        String idempotencyKey = "test-key-3";
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/test")
                .header("X-Idempotency-Key", idempotencyKey)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<Void> result = idempotencyWebFilter.filter(exchange, filterChain);

        // Assert - First request should go through the filter chain
        StepVerifier.create(result)
                .verifyComplete();
        verify(filterChain, times(1)).filter(any());
    }

    @Test
    void shouldApplyIdempotencyToDeleteMethod() {
        // Arrange
        String idempotencyKey = "test-key-delete";
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.DELETE, "/test/123")
                .header("X-Idempotency-Key", idempotencyKey)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<Void> result = idempotencyWebFilter.filter(exchange, filterChain);

        // Assert - First request should go through the filter chain
        StepVerifier.create(result)
                .verifyComplete();
        verify(filterChain, times(1)).filter(any());
    }

    @Test
    void shouldRejectEmptyIdempotencyKey() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .header("X-Idempotency-Key", "")
                .contentType(MediaType.APPLICATION_JSON)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<Void> result = idempotencyWebFilter.filter(exchange, filterChain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        // Verify that the filter chain was not called (request was rejected)
        verify(filterChain, never()).filter(any());

        // Verify that the response status is BAD_REQUEST
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    private void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
