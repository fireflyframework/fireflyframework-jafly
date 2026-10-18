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


package org.fireflyframework.web.logging;

import org.fireflyframework.web.logging.config.HttpRequestLoggingProperties;
import org.fireflyframework.web.logging.filter.HttpRequestLoggingWebFilter;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class to verify JSON log format validity and structure.
 */
class JsonLogValidationTest {

    private HttpRequestLoggingProperties properties;
    private HttpRequestLoggingWebFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new HttpRequestLoggingProperties();
        properties.setEnabled(true);
        filter = new HttpRequestLoggingWebFilter(properties, Optional.empty());
        objectMapper = new ObjectMapper();
    }

    @Test
    void testJsonLogStructureForRequest() throws Exception {
        // Test that we can create a valid JSON structure for HTTP request logging
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .queryParam("param1", "value1")
                .header("User-Agent", "test-agent")
                .header("Authorization", "Bearer secret")
                .build();

        // Use reflection to access private methods for testing
        Method formatHeadersAsMapMethod = HttpRequestLoggingWebFilter.class
                .getDeclaredMethod("formatHeadersAsMap", HttpHeaders.class);
        formatHeadersAsMapMethod.setAccessible(true);

        Method formatQueryParamsAsMapMethod = HttpRequestLoggingWebFilter.class
                .getDeclaredMethod("formatQueryParamsAsMap", org.springframework.util.MultiValueMap.class);
        formatQueryParamsAsMapMethod.setAccessible(true);

        // Test header formatting
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) formatHeadersAsMapMethod.invoke(filter, request.getHeaders());
        assertNotNull(headers);
        assertTrue(headers.containsKey("User-Agent"));
        assertEquals("***MASKED***", headers.get("Authorization")); // Should be masked

        // Test query params formatting
        @SuppressWarnings("unchecked")
        Map<String, Object> queryParams = (Map<String, Object>) formatQueryParamsAsMapMethod.invoke(filter, request.getQueryParams());
        assertNotNull(queryParams);
        assertTrue(queryParams.containsKey("param1"));
        assertEquals("value1", queryParams.get("param1"));

        // Test complete JSON structure
        Map<String, Object> logData = new java.util.LinkedHashMap<>();
        logData.put("type", "HTTP_REQUEST");
        logData.put("requestId", "test-123");
        logData.put("timestamp", java.time.Instant.now().toString());
        logData.put("method", request.getMethod().toString());
        logData.put("path", request.getPath().value());
        logData.put("queryParams", queryParams);
        logData.put("headers", headers);

        // Verify JSON serialization works
        String jsonString = objectMapper.writeValueAsString(logData);
        assertNotNull(jsonString);
        assertTrue(jsonString.startsWith("{"));
        assertTrue(jsonString.endsWith("}"));

        // Verify JSON is valid and can be parsed back
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        assertEquals("HTTP_REQUEST", jsonNode.get("type").asText());
        assertEquals("test-123", jsonNode.get("requestId").asText());
        assertEquals("GET", jsonNode.get("method").asText());
        assertEquals("/api/test", jsonNode.get("path").asText());
        assertTrue(jsonNode.has("timestamp"));
        assertTrue(jsonNode.has("queryParams"));
        assertTrue(jsonNode.has("headers"));
    }

    @Test
    void testJsonLogStructureForResponse() throws Exception {
        // Test that we can create a valid JSON structure for HTTP response logging
        MockServerHttpResponse response = new MockServerHttpResponse();
        response.setStatusCode(org.springframework.http.HttpStatus.OK);
        response.getHeaders().add("Content-Type", "application/json");

        // Test complete JSON structure for response
        Map<String, Object> logData = new java.util.LinkedHashMap<>();
        logData.put("type", "HTTP_RESPONSE");
        logData.put("requestId", "test-123");
        logData.put("timestamp", java.time.Instant.now().toString());
        logData.put("statusCode", response.getStatusCode().value());
        logData.put("durationMs", 250L);

        // Use reflection to test header formatting
        Method formatHeadersAsMapMethod = HttpRequestLoggingWebFilter.class
                .getDeclaredMethod("formatHeadersAsMap", HttpHeaders.class);
        formatHeadersAsMapMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) formatHeadersAsMapMethod.invoke(filter, response.getHeaders());
        logData.put("headers", headers);

        // Verify JSON serialization works
        String jsonString = objectMapper.writeValueAsString(logData);
        assertNotNull(jsonString);

        // Verify JSON is valid and can be parsed back
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        assertEquals("HTTP_RESPONSE", jsonNode.get("type").asText());
        assertEquals("test-123", jsonNode.get("requestId").asText());
        assertEquals(200, jsonNode.get("statusCode").asInt());
        assertEquals(250, jsonNode.get("durationMs").asLong());
        assertTrue(jsonNode.has("timestamp"));
        assertTrue(jsonNode.has("headers"));
    }

    @Test
    void testJsonLogStructureForBody() throws Exception {
        // Test that we can create a valid JSON structure for body logging
        String testBody = "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}";

        Map<String, Object> logData = new java.util.LinkedHashMap<>();
        logData.put("type", "HTTP_REQUEST_BODY");
        logData.put("requestId", "test-123");
        logData.put("timestamp", java.time.Instant.now().toString());
        logData.put("body", testBody);

        // Verify JSON serialization works
        String jsonString = objectMapper.writeValueAsString(logData);
        assertNotNull(jsonString);

        // Verify JSON is valid and can be parsed back
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        assertEquals("HTTP_REQUEST_BODY", jsonNode.get("type").asText());
        assertEquals("test-123", jsonNode.get("requestId").asText());
        assertEquals(testBody, jsonNode.get("body").asText());
        assertTrue(jsonNode.has("timestamp"));
    }

    @Test
    void testSensitiveHeaderMasking() throws Exception {
        // Test that sensitive headers are properly masked
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("Authorization", "Bearer secret-token")
                .header("X-API-Key", "secret-key")
                .header("Content-Type", "application/json")
                .build();

        Method formatHeadersAsMapMethod = HttpRequestLoggingWebFilter.class
                .getDeclaredMethod("formatHeadersAsMap", HttpHeaders.class);
        formatHeadersAsMapMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) formatHeadersAsMapMethod.invoke(filter, request.getHeaders());

        // Verify sensitive headers are masked
        assertEquals("***MASKED***", headers.get("Authorization"));
        assertEquals("***MASKED***", headers.get("X-API-Key"));
        // Non-sensitive headers should remain unchanged
        assertEquals("application/json", headers.get("Content-Type"));
    }
}