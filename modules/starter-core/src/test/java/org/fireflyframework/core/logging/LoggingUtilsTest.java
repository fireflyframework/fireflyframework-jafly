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


package org.fireflyframework.core.logging;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoggingUtilsTest {

    @BeforeEach
    void setUp() {
        // Clear MDC before each test
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        // Clear MDC after each test
        MDC.clear();
    }

    @Test
    void structuredLogWithMessageAndData() {
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", 123);

        Map<String, Object> result = LoggingUtils.structuredLog("Test message", data);

        assertEquals("Test message", result.get("message"));
        assertEquals("value1", result.get("key1"));
        assertEquals(123, result.get("key2"));
    }

    @Test
    void structuredLogWithMessageAndSingleField() {
        Map<String, Object> result = LoggingUtils.structuredLog("Test message", "key1", "value1");

        assertEquals("Test message", result.get("message"));
        assertEquals("value1", result.get("key1"));
    }

    @Test
    void withMdcSingleValue() {
        String result = LoggingUtils.withMdc("testKey", "testValue", () -> {
            assertEquals("testValue", MDC.get("testKey"));
            return "success";
        });

        assertEquals("success", result);
        assertNull(MDC.get("testKey"), "MDC should be cleared after execution");
    }

    @Test
    void withMdcMultipleValues() {
        Map<String, String> context = new HashMap<>();
        context.put("key1", "value1");
        context.put("key2", "value2");

        String result = LoggingUtils.withMdc(context, () -> {
            assertEquals("value1", MDC.get("key1"));
            assertEquals("value2", MDC.get("key2"));
            return "success";
        });

        assertEquals("success", result);
        assertNull(MDC.get("key1"), "MDC should be cleared after execution");
        assertNull(MDC.get("key2"), "MDC should be cleared after execution");
    }

    @Test
    void withMdcPreservesExistingValues() {
        // Set an existing MDC value
        MDC.put("existingKey", "existingValue");

        Map<String, String> context = new HashMap<>();
        context.put("key1", "value1");
        context.put("existingKey", "newValue"); // Override existing value

        LoggingUtils.withMdc(context, () -> {
            assertEquals("value1", MDC.get("key1"));
            assertEquals("newValue", MDC.get("existingKey"));
            return null;
        });

        // Verify the existing value is restored
        assertEquals("existingValue", MDC.get("existingKey"));
        assertNull(MDC.get("key1"), "Temporary key should be removed");
    }

    @Test
    void setUserIdAddsToMdc() {
        LoggingUtils.setUserId("user123");
        assertEquals("user123", MDC.get("userId"));
    }

    @Test
    void setRequestIdAddsToMdc() {
        LoggingUtils.setRequestId("req123");
        assertEquals("req123", MDC.get("requestId"));
    }

    @Test
    void setCorrelationIdAddsToMdc() {
        LoggingUtils.setCorrelationId("corr123");
        assertEquals("corr123", MDC.get("correlationId"));
    }

    @Test
    void structuredLogBuilderCreatesCorrectStructure() {
        Map<String, Object> result = LoggingUtils.log("Test message")
                .with("key1", "value1")
                .with("key2", 123)
                .build();

        assertEquals("Test message", result.get("message"));
        assertEquals("value1", result.get("key1"));
        assertEquals(123, result.get("key2"));
    }

    @Test
    void structuredLogBuilderWithMapAddsAllEntries() {
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("key1", "value1");
        additionalData.put("key2", 123);

        Map<String, Object> result = LoggingUtils.log("Test message")
                .with(additionalData)
                .with("key3", "value3")
                .build();

        assertEquals("Test message", result.get("message"));
        assertEquals("value1", result.get("key1"));
        assertEquals(123, result.get("key2"));
        assertEquals("value3", result.get("key3"));
    }

    @Test
    void structuredLogParsesNestedJsonInMessage() {
        String nestedJson = "{\"durationMs\":119,\"statusCode\":201,\"type\":\"HTTP_RESPONSE\"}";
        
        Map<String, Object> result = LoggingUtils.structuredLog(nestedJson, "key1", "value1");
        
        // Message should be parsed as JsonNode
        assertTrue(result.get("message") instanceof JsonNode);
        JsonNode messageNode = (JsonNode) result.get("message");
        assertEquals(119, messageNode.get("durationMs").asInt());
        assertEquals(201, messageNode.get("statusCode").asInt());
        assertEquals("HTTP_RESPONSE", messageNode.get("type").asText());
        
        // Other data should remain unchanged
        assertEquals("value1", result.get("key1"));
    }

    @Test
    void structuredLogParsesNestedJsonInDataValues() {
        String nestedJson = "{\"headers\":{\"Content-Type\":\"application/json\"},\"requestId\":\"12345\"}";
        Map<String, Object> data = new HashMap<>();
        data.put("responseDetails", nestedJson);
        data.put("timestamp", "2025-08-23T11:22:05.374+0200");
        
        Map<String, Object> result = LoggingUtils.structuredLog("Processing response", data);
        
        assertEquals("Processing response", result.get("message"));
        assertEquals("2025-08-23T11:22:05.374+0200", result.get("timestamp"));
        
        // responseDetails should be parsed as JsonNode
        assertTrue(result.get("responseDetails") instanceof JsonNode);
        JsonNode responseNode = (JsonNode) result.get("responseDetails");
        assertEquals("12345", responseNode.get("requestId").asText());
        assertEquals("application/json", responseNode.get("headers").get("Content-Type").asText());
    }

    @Test
    void structuredLogBuilderParsesNestedJsonInValues() {
        String nestedJson = "{\"userId\":\"user123\",\"action\":\"login\"}";
        
        Map<String, Object> result = LoggingUtils.log("User activity")
                .with("eventData", nestedJson)
                .with("timestamp", System.currentTimeMillis())
                .build();
        
        assertEquals("User activity", result.get("message"));
        assertTrue(result.get("timestamp") instanceof Long);
        
        // eventData should be parsed as JsonNode
        assertTrue(result.get("eventData") instanceof JsonNode);
        JsonNode eventNode = (JsonNode) result.get("eventData");
        assertEquals("user123", eventNode.get("userId").asText());
        assertEquals("login", eventNode.get("action").asText());
    }

    @Test
    void structuredLogBuilderParsesNestedJsonInMapValues() {
        String nestedJson = "{\"error\":\"Connection timeout\",\"code\":500}";
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("errorDetails", nestedJson);
        additionalData.put("retryCount", 3);
        
        Map<String, Object> result = LoggingUtils.log("Request failed")
                .with(additionalData)
                .build();
        
        assertEquals("Request failed", result.get("message"));
        assertEquals(3, result.get("retryCount"));
        
        // errorDetails should be parsed as JsonNode
        assertTrue(result.get("errorDetails") instanceof JsonNode);
        JsonNode errorNode = (JsonNode) result.get("errorDetails");
        assertEquals("Connection timeout", errorNode.get("error").asText());
        assertEquals(500, errorNode.get("code").asInt());
    }

    @Test
    void structuredLogHandlesInvalidJsonGracefully() {
        String invalidJson = "{\"incomplete\": json";
        
        Map<String, Object> result = LoggingUtils.structuredLog("Test message", "data", invalidJson);
        
        assertEquals("Test message", result.get("message"));
        // Invalid JSON should remain as string
        assertEquals(invalidJson, result.get("data"));
        assertTrue(result.get("data") instanceof String);
    }

    @Test
    void structuredLogHandlesNullAndEmptyValues() {
        Map<String, Object> result = LoggingUtils.structuredLog("Test message", "nullValue", null);
        
        assertEquals("Test message", result.get("message"));
        assertNull(result.get("nullValue"));
        
        result = LoggingUtils.structuredLog("Test message", "emptyString", "");
        assertEquals("", result.get("emptyString"));
        
        result = LoggingUtils.structuredLog("Test message", "whitespace", "   ");
        assertEquals("   ", result.get("whitespace"));
    }

    @Test
    void structuredLogHandlesJsonArrays() {
        String jsonArray = "[{\"id\":1,\"name\":\"item1\"},{\"id\":2,\"name\":\"item2\"}]";
        
        Map<String, Object> result = LoggingUtils.structuredLog("Processing items", "items", jsonArray);
        
        assertEquals("Processing items", result.get("message"));
        assertTrue(result.get("items") instanceof JsonNode);
        
        JsonNode itemsNode = (JsonNode) result.get("items");
        assertTrue(itemsNode.isArray());
        assertEquals(2, itemsNode.size());
        assertEquals(1, itemsNode.get(0).get("id").asInt());
        assertEquals("item1", itemsNode.get(0).get("name").asText());
    }

    @Test
    void structuredLogHandlesComplexNestedJson() {
        // Complex nested JSON similar to the issue description
        String complexJson = "{\"durationMs\":119,\"headers\":{\"Content-Length\":\"117\",\"Content-Type\":\"application/json\",\"Vary\":[\"Origin\",\"Access-Control-Request-Method\"],\"X-Transaction-Id\":\"0c0cd4cb-34ea-456a-bd7d-601f1efc773a\"},\"requestId\":\"66632488421333\",\"statusCode\":201,\"timestamp\":\"2025-08-23T09:22:05.374424Z\",\"type\":\"HTTP_RESPONSE\"}";
        
        Map<String, Object> result = LoggingUtils.log("HTTP Request Logging")
                .with("responseData", complexJson)
                .with("logger", "org.fireflyframework.web.logging.filter.HttpRequestLoggingWebFilter")
                .with("thread", "reactor-tcp-nio-5")
                .build();
        
        assertEquals("HTTP Request Logging", result.get("message"));
        assertEquals("org.fireflyframework.web.logging.filter.HttpRequestLoggingWebFilter", result.get("logger"));
        assertEquals("reactor-tcp-nio-5", result.get("thread"));
        
        // Complex JSON should be parsed in the responseData field
        assertTrue(result.get("responseData") instanceof JsonNode);
        JsonNode messageNode = (JsonNode) result.get("responseData");
        assertEquals(119, messageNode.get("durationMs").asInt());
        assertEquals(201, messageNode.get("statusCode").asInt());
        assertEquals("HTTP_RESPONSE", messageNode.get("type").asText());
        assertEquals("66632488421333", messageNode.get("requestId").asText());
        
        // Check nested headers object
        JsonNode headersNode = messageNode.get("headers");
        assertEquals("117", headersNode.get("Content-Length").asText());
        assertEquals("application/json", headersNode.get("Content-Type").asText());
        assertEquals("0c0cd4cb-34ea-456a-bd7d-601f1efc773a", headersNode.get("X-Transaction-Id").asText());
        
        // Check array within headers
        JsonNode varyNode = headersNode.get("Vary");
        assertTrue(varyNode.isArray());
        assertEquals("Origin", varyNode.get(0).asText());
        assertEquals("Access-Control-Request-Method", varyNode.get(1).asText());
    }

    @Test
    void structuredLogHandlesRecursivelyNestedJson() {
        // Test case with JSON string containing another JSON string (escaped)
        String level2Json = "{\\\"innerKey\\\":\\\"innerValue\\\",\\\"innerNumber\\\":42}";
        String level1Json = "{\"outerKey\":\"" + level2Json + "\",\"outerArray\":[1,2,3]}";
        
        Map<String, Object> result = LoggingUtils.structuredLog("Recursive test", "nestedData", level1Json);
        
        assertEquals("Recursive test", result.get("message"));
        assertTrue(result.get("nestedData") instanceof JsonNode);
        
        JsonNode nestedNode = (JsonNode) result.get("nestedData");
        // Field names can be in any order - check that both expected fields exist
        assertTrue(nestedNode.has("outerKey"));
        assertTrue(nestedNode.has("outerArray"));
        
        // The nested JSON should be recursively parsed
        JsonNode outerKeyNode = nestedNode.get("outerKey");
        assertTrue(outerKeyNode.isObject());
        assertEquals("innerValue", outerKeyNode.get("innerKey").asText());
        assertEquals(42, outerKeyNode.get("innerNumber").asInt());
        
        // Array should remain as array
        JsonNode outerArrayNode = nestedNode.get("outerArray");
        assertTrue(outerArrayNode.isArray());
        assertEquals(3, outerArrayNode.size());
    }

    @Test
    void structuredLogHandlesDeeplyNestedJsonLevels() {
        // Test with simpler but valid 2-level nesting that will be recognized as JSON
        String level2Json = "{\\\"innerField\\\":\\\"innerValue\\\",\\\"innerNum\\\":123}";
        String level1Json = "{\"level2\":\"" + level2Json + "\"}";
        
        Map<String, Object> result = LoggingUtils.structuredLog("Deep nesting test", "deepData", level1Json);
        
        assertTrue(result.get("deepData") instanceof JsonNode);
        JsonNode level1Node = (JsonNode) result.get("deepData");
        
        // Level 2 should be parsed as object due to recursive processing
        JsonNode level2Node = level1Node.get("level2");
        assertTrue(level2Node.isObject());
        
        // Check the nested values
        assertEquals("innerValue", level2Node.get("innerField").asText());
        assertEquals(123, level2Node.get("innerNum").asInt());
    }

    @Test
    void structuredLogHandlesJsonArraysWithNestedJson() {
        String nestedJsonInArray = "[{\"item\":\"first\"},{\"item\":\"{\\\"nested\\\":\\\"value\\\"}\"}]";
        
        Map<String, Object> result = LoggingUtils.structuredLog("Array with nested JSON", "arrayData", nestedJsonInArray);
        
        assertTrue(result.get("arrayData") instanceof JsonNode);
        JsonNode arrayNode = (JsonNode) result.get("arrayData");
        assertTrue(arrayNode.isArray());
        assertEquals(2, arrayNode.size());
        
        // First item should be simple object
        JsonNode firstItem = arrayNode.get(0);
        assertEquals("first", firstItem.get("item").asText());
        
        // Second item should have nested JSON parsed
        JsonNode secondItem = arrayNode.get(1);
        JsonNode nestedItem = secondItem.get("item");
        assertTrue(nestedItem.isObject());
        assertEquals("value", nestedItem.get("nested").asText());
    }

    @Test
    void structuredLogPreservesNonJsonStringsInRecursiveProcessing() {
        String mixedJson = "{\"jsonField\":\"{\\\"inner\\\":\\\"value\\\"}\",\"plainField\":\"just text\",\"numberField\":123}";
        
        Map<String, Object> result = LoggingUtils.structuredLog("Mixed content test", "mixedData", mixedJson);
        
        assertTrue(result.get("mixedData") instanceof JsonNode);
        JsonNode mixedNode = (JsonNode) result.get("mixedData");
        
        // JSON field should be recursively parsed
        JsonNode jsonFieldNode = mixedNode.get("jsonField");
        assertTrue(jsonFieldNode.isObject());
        assertEquals("value", jsonFieldNode.get("inner").asText());
        
        // Plain text field should remain as text
        assertEquals("just text", mixedNode.get("plainField").asText());
        
        // Number field should remain as number
        assertEquals(123, mixedNode.get("numberField").asInt());
    }

    @Test
    void structuredLogHandlesInvalidJsonInRecursiveProcessing() {
        String invalidNestedJson = "{\"validField\":\"value\",\"invalidField\":\"{invalid json}\"}";
        
        Map<String, Object> result = LoggingUtils.structuredLog("Invalid nested JSON test", "data", invalidNestedJson);
        
        assertTrue(result.get("data") instanceof JsonNode);
        JsonNode dataNode = (JsonNode) result.get("data");
        
        // Valid field should be processed normally
        assertEquals("value", dataNode.get("validField").asText());
        
        // Invalid JSON should remain as string
        assertEquals("{invalid json}", dataNode.get("invalidField").asText());
    }

    @Test
    void structuredLogBuilderHandlesRecursiveNestedJson() {
        String recursiveJson = "{\"response\":\"{\\\"status\\\":\\\"success\\\",\\\"data\\\":\\\"{\\\\\\\"user\\\\\\\":\\\\\\\"john\\\\\\\"}\\\"}\"}";
        
        Map<String, Object> result = LoggingUtils.log("Builder recursive test")
                .with("recursiveData", recursiveJson)
                .with("plainData", "simple text")
                .build();
        
        assertEquals("Builder recursive test", result.get("message"));
        assertEquals("simple text", result.get("plainData"));
        
        assertTrue(result.get("recursiveData") instanceof JsonNode);
        JsonNode recursiveNode = (JsonNode) result.get("recursiveData");
        
        // First level of nesting
        JsonNode responseNode = recursiveNode.get("response");
        assertTrue(responseNode.isObject());
        assertEquals("success", responseNode.get("status").asText());
        
        // Second level of nesting  
        JsonNode dataNode = responseNode.get("data");
        assertTrue(dataNode.isObject());
        assertEquals("john", dataNode.get("user").asText());
    }

    @Test
    void structuredLogHandlesRealWorldMicroserviceExample() {
        // Real example from the issue description
        String microserviceLogMessage = "{\"durationMs\":140,\"headers\":{\"Content-Length\":\"117\",\"Content-Type\":\"application/json\",\"Vary\":[\"Origin\",\"Access-Control-Request-Method\",\"Access-Control-Request-Headers\"],\"X-Transaction-Id\":\"03132499-e3be-461c-a2b7-633d3c98c37f\",\"transfer-encoding\":\"chunked\"},\"requestId\":\"67132534563458\",\"statusCode\":201,\"timestamp\":\"2025-08-23T09:30:25.443559Z\",\"type\":\"HTTP_RESPONSE\"}";
        
        Map<String, Object> result = LoggingUtils.log(microserviceLogMessage)
                .with("logger", "org.fireflyframework.web.logging.filter.HttpRequestLoggingWebFilter")
                .with("thread", "reactor-tcp-nio-5")
                .with("level", "INFO")
                .with("HOSTNAME", "Andress-MacBook-Pro.local")
                .with("applicationName", "common-platform-customer-mgmt")
                .build();
        
        // Message should be parsed as JSON
        assertTrue(result.get("message") instanceof JsonNode);
        JsonNode messageNode = (JsonNode) result.get("message");
        
        assertEquals(140, messageNode.get("durationMs").asInt());
        assertEquals(201, messageNode.get("statusCode").asInt());
        assertEquals("HTTP_RESPONSE", messageNode.get("type").asText());
        assertEquals("67132534563458", messageNode.get("requestId").asText());
        
        // Headers should be properly structured
        JsonNode headersNode = messageNode.get("headers");
        assertTrue(headersNode.isObject());
        assertEquals("117", headersNode.get("Content-Length").asText());
        assertEquals("application/json", headersNode.get("Content-Type").asText());
        assertEquals("03132499-e3be-461c-a2b7-633d3c98c37f", headersNode.get("X-Transaction-Id").asText());
        
        // Vary header should be an array
        JsonNode varyNode = headersNode.get("Vary");
        assertTrue(varyNode.isArray());
        assertEquals(3, varyNode.size());
        assertEquals("Origin", varyNode.get(0).asText());
        assertEquals("Access-Control-Request-Method", varyNode.get(1).asText());
        assertEquals("Access-Control-Request-Headers", varyNode.get(2).asText());
        
        // Other fields should remain as strings
        assertEquals("org.fireflyframework.web.logging.filter.HttpRequestLoggingWebFilter", result.get("logger"));
        assertEquals("reactor-tcp-nio-5", result.get("thread"));
        assertEquals("INFO", result.get("level"));
    }
}
