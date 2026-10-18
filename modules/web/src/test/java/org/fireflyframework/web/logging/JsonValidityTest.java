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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test class to verify JSON validity with various edge cases.
 */
class JsonValidityTest {

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
    void testJsonValidityWithSpecialCharacters() throws Exception {
        // Test string with various special characters
        String testInput = "Hello\nWorld\t\"Test\"\r\n\\Backslash/Forward áéíóú ñ 中文 😊";
        
        String sanitized = invokeSanitizeForJson(testInput);
        assertNotNull(sanitized);
        
        // Verify it can be serialized to valid JSON
        Map<String, Object> testData = new LinkedHashMap<>();
        testData.put("test", sanitized);
        
        String jsonString = objectMapper.writeValueAsString(testData);
        assertNotNull(jsonString);
        
        // Verify JSON can be parsed back
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        assertTrue(jsonNode.has("test"));
    }

    @Test
    void testJsonValidityWithControlCharacters() throws Exception {
        // Test string with control characters
        String testInput = "Test\u0000\u0001\u0002\u0003\u0004\u0005String";
        
        String sanitized = invokeSanitizeForJson(testInput);
        assertNotNull(sanitized);
        assertFalse(sanitized.contains("\u0000")); // Control characters should be removed
        
        // Verify it can be serialized to valid JSON
        Map<String, Object> testData = new LinkedHashMap<>();
        testData.put("test", sanitized);
        
        String jsonString = objectMapper.writeValueAsString(testData);
        assertNotNull(jsonString);
        
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        assertTrue(jsonNode.has("test"));
    }

    @Test
    void testJsonValidityWithNullValues() throws Exception {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("type", "TEST");
        logData.put("nullValue", null);
        logData.put("normalValue", "test");
        
        String jsonMessage = invokeSerializeToJsonSafely(logData, "TEST");
        assertNotNull(jsonMessage);
        
        // Verify JSON is valid
        JsonNode jsonNode = objectMapper.readTree(jsonMessage);
        assertEquals("TEST", jsonNode.get("type").asText());
        assertTrue(jsonNode.has("normalValue"));
    }

    @Test
    void testJsonValidityWithLargeContent() throws Exception {
        // Create a large string
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 15000; i++) {
            largeString.append("This is a test string with index ").append(i).append(". ");
        }
        
        String sanitized = invokeSanitizeForJson(largeString.toString());
        assertNotNull(sanitized);
        assertTrue(sanitized.contains("[TRUNCATED]")); // Should be truncated
        
        // Verify it can be serialized to valid JSON
        Map<String, Object> testData = new LinkedHashMap<>();
        testData.put("largeContent", sanitized);
        
        String jsonString = objectMapper.writeValueAsString(testData);
        assertNotNull(jsonString);
        
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        assertTrue(jsonNode.has("largeContent"));
    }

    @Test
    void testJsonValidityWithInvalidUTF8Sequences() throws Exception {
        // Create byte array with invalid UTF-8 sequences
        byte[] invalidUtf8 = new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFD, 0x41, 0x42, 0x43};
        
        String sanitized = invokeSanitizeBodyForJson(invalidUtf8);
        assertNotNull(sanitized);
        
        // Should fallback to base64 encoding for invalid UTF-8
        // Verify the result can be included in JSON
        Map<String, Object> testData = new LinkedHashMap<>();
        testData.put("body", sanitized);
        
        String jsonString = objectMapper.writeValueAsString(testData);
        assertNotNull(jsonString);
        
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        assertTrue(jsonNode.has("body"));
    }

    @Test
    void testJsonValidityWithHeadersContainingSpecialCharacters() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("User-Agent", "Test/1.0 (Special \"Chars\" & \n\t)")
                .header("Custom-Header", "Value with 中文 and émojis 😊")
                .header("Authorization", "Bearer secret-token")
                .build();

        Method formatHeadersAsMapMethod = HttpRequestLoggingWebFilter.class
                .getDeclaredMethod("formatHeadersAsMap", HttpHeaders.class);
        formatHeadersAsMapMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) formatHeadersAsMapMethod.invoke(filter, request.getHeaders());

        // Verify headers can be serialized to JSON
        String jsonString = objectMapper.writeValueAsString(headers);
        assertNotNull(jsonString);
        
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        assertTrue(jsonNode.has("User-Agent"));
        assertTrue(jsonNode.has("Custom-Header"));
        assertEquals("***MASKED***", jsonNode.get("Authorization").asText());
    }

    @Test
    void testSerializationFailureFallback() throws Exception {
        // Create a map that might cause serialization issues
        Map<String, Object> problematicData = new LinkedHashMap<>();
        problematicData.put("type", "TEST");
        problematicData.put("requestId", "12345");
        problematicData.put("timestamp", "2025-01-01T00:00:00Z");
        
        String result = invokeSerializeToJsonSafely(problematicData, "TEST");
        assertNotNull(result);
        
        // Verify the result is valid JSON
        JsonNode jsonNode = objectMapper.readTree(result);
        assertEquals("TEST", jsonNode.get("type").asText());
    }

    @Test
    void testEmptyAndNullInputHandling() throws Exception {
        // Test null input
        String nullResult = invokeSanitizeForJson(null);
        assertNull(nullResult);
        
        // Test empty string
        String emptyResult = invokeSanitizeForJson("");
        assertEquals("", emptyResult);
        
        // Test whitespace-only string
        String whitespaceResult = invokeSanitizeForJson("   \t\n   ");
        assertNotNull(whitespaceResult);
        
        // Test empty byte array
        String emptyBodyResult = invokeSanitizeBodyForJson(new byte[0]);
        assertNull(emptyBodyResult);
        
        // Test null byte array
        String nullBodyResult = invokeSanitizeBodyForJson(null);
        assertNull(nullBodyResult);
    }

    // Helper methods to invoke private methods for testing
    private String invokeSanitizeForJson(String input) throws Exception {
        Method method = HttpRequestLoggingWebFilter.class.getDeclaredMethod("sanitizeForJson", String.class);
        method.setAccessible(true);
        return (String) method.invoke(filter, input);
    }

    private String invokeSanitizeBodyForJson(byte[] body) throws Exception {
        Method method = HttpRequestLoggingWebFilter.class.getDeclaredMethod("sanitizeBodyForJson", byte[].class);
        method.setAccessible(true);
        return (String) method.invoke(filter, body);
    }

    private String invokeSerializeToJsonSafely(Map<String, Object> logData, String logType) throws Exception {
        Method method = HttpRequestLoggingWebFilter.class.getDeclaredMethod("serializeToJsonSafely", Map.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(filter, logData, logType);
    }
}