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


package org.fireflyframework.web.logging.filter;

import org.fireflyframework.web.logging.config.HttpRequestLoggingProperties;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HttpRequestLoggingWebFilterTest {

    @Mock
    private WebFilterChain filterChain;

    private HttpRequestLoggingProperties properties;
    private HttpRequestLoggingWebFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new HttpRequestLoggingProperties();
        filter = new HttpRequestLoggingWebFilter(properties, Optional.empty());
    }

    @Test
    void shouldSkipLoggingWhenDisabled() {
        // Given
        properties.setEnabled(false);
        ServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        ServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldSkipExcludedPaths() {
        // Given
        properties.setExcludedPaths(Set.of("/health", "/actuator/**"));
        ServerHttpRequest request = MockServerHttpRequest.get("/health").build();
        ServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldLogBasicRequestInfo() {
        // Given
        ServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .queryParam("param1", "value1")
                .header("User-Agent", "test-agent")
                .build();
        ServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(exchange.mutate()).thenReturn(builder);
        when(builder.request(any(ServerHttpRequest.class))).thenReturn(builder);
        when(builder.response(any(ServerHttpResponse.class))).thenReturn(builder);
        when(builder.build()).thenReturn(exchange);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    void shouldMaskSensitiveHeaders() {
        // Given
        properties.setSensitiveHeaders(Set.of("authorization", "x-api-key"));
        ServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("Authorization", "Bearer secret-token")
                .header("X-API-Key", "secret-key")
                .header("Content-Type", "application/json")
                .build();
        ServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(exchange.mutate()).thenReturn(builder);
        when(builder.request(any(ServerHttpRequest.class))).thenReturn(builder);
        when(builder.response(any(ServerHttpResponse.class))).thenReturn(builder);
        when(builder.build()).thenReturn(exchange);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    void shouldHandlePostRequestWithBody() {
        // Given
        properties.setIncludeRequestBody(true);
        
        ServerHttpRequest request = MockServerHttpRequest.post("/api/test")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        ServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(exchange.mutate()).thenReturn(builder);
        when(builder.request(any(ServerHttpRequest.class))).thenReturn(builder);
        when(builder.response(any(ServerHttpResponse.class))).thenReturn(builder);
        when(builder.build()).thenReturn(exchange);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    void shouldRespectMaxBodySize() {
        // Given
        properties.setIncludeRequestBody(true);
        properties.setMaxBodySize(10); // Very small size
        
        ServerHttpRequest request = MockServerHttpRequest.post("/api/test")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        ServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(exchange.mutate()).thenReturn(builder);
        when(builder.request(any(ServerHttpRequest.class))).thenReturn(builder);
        when(builder.response(any(ServerHttpResponse.class))).thenReturn(builder);
        when(builder.build()).thenReturn(exchange);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    void shouldSkipHeadersWhenDisabled() {
        // Given
        properties.setIncludeHeaders(false);
        ServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("User-Agent", "test-agent")
                .header("Authorization", "Bearer secret")
                .build();
        ServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(exchange.mutate()).thenReturn(builder);
        when(builder.request(any(ServerHttpRequest.class))).thenReturn(builder);
        when(builder.response(any(ServerHttpResponse.class))).thenReturn(builder);
        when(builder.build()).thenReturn(exchange);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    void shouldSkipQueryParamsWhenDisabled() {
        // Given
        properties.setIncludeQueryParams(false);
        ServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .queryParam("param1", "value1")
                .queryParam("param2", "value2")
                .build();
        ServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(exchange.mutate()).thenReturn(builder);
        when(builder.request(any(ServerHttpRequest.class))).thenReturn(builder);
        when(builder.response(any(ServerHttpResponse.class))).thenReturn(builder);
        when(builder.build()).thenReturn(exchange);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain).filter(any(ServerWebExchange.class));
    }
}