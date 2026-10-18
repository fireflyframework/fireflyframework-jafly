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

package org.fireflyframework.webhooks.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.webhooks.core.config.CompressionProperties;
import org.fireflyframework.webhooks.core.services.impl.WebhookCompressionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebhookCompressionService.
 */
@DisplayName("WebhookCompressionService Tests")
class WebhookCompressionServiceTest {

    private WebhookCompressionServiceImpl compressionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        CompressionProperties properties = new CompressionProperties();
        properties.setEnabled(true);
        properties.setAlgorithm(CompressionProperties.CompressionAlgorithm.GZIP);
        properties.setMinSize(100); // 100 bytes minimum

        objectMapper = new ObjectMapper();
        compressionService = new WebhookCompressionServiceImpl(properties, objectMapper);
    }

    @Test
    @DisplayName("Should compress large payload")
    void shouldCompressLargePayload() throws Exception {
        // Arrange - Create a payload larger than minSize
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeData.append("This is a test payload that should be compressed. ");
        }

        JsonNode payload = objectMapper.readTree("{\"id\": \"evt_123\", \"data\": \"" + largeData + "\"}");

        // Act
        byte[] compressed = compressionService.compress(payload);

        // Assert
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);

        // Verify compression actually reduced size
        byte[] original = objectMapper.writeValueAsBytes(payload);
        println("Original size: " + original.length + " bytes");
        println("Compressed size: " + compressed.length + " bytes");
        println("Compression ratio: " + String.format("%.2f%%", (1 - (double) compressed.length / original.length) * 100));

        assertTrue(compressed.length < original.length, "Compressed size should be smaller than original");
    }

    @Test
    @DisplayName("Should decompress payload correctly")
    void shouldDecompressPayloadCorrectly() throws Exception {
        // Arrange
        JsonNode originalPayload = objectMapper.readTree(
                "{\"id\": \"evt_123\", \"type\": \"payment_intent.succeeded\", " +
                "\"data\": {\"amount\": 1000, \"currency\": \"usd\", " +
                "\"description\": \"Test payment with a reasonably long description to ensure compression works properly\"}}"
        );

        // Act
        byte[] compressed = compressionService.compress(originalPayload);
        JsonNode decompressed = compressionService.decompress(compressed);

        // Assert
        assertNotNull(decompressed);
        assertEquals(originalPayload, decompressed);
        println("Decompression successful - payload matches original");
    }

    @Test
    @DisplayName("Should not compress small payload")
    void shouldNotCompressSmallPayload() throws Exception {
        // Arrange - Create a payload smaller than minSize
        JsonNode smallPayload = objectMapper.readTree("{\"id\": \"123\"}");

        // Act
        byte[] result = compressionService.compress(smallPayload);

        // Assert
        assertNull(result, "Small payloads should not be compressed");
        println("Small payload correctly skipped compression");
    }

    @Test
    @DisplayName("Should handle null payload gracefully")
    void shouldHandleNullPayloadGracefully() {
        // Act
        byte[] result = compressionService.compress(null);

        // Assert
        assertNull(result);
        println("Null payload handled gracefully");
    }

    @Test
    @DisplayName("Should handle decompression of invalid data")
    void shouldHandleDecompressionOfInvalidData() {
        // Arrange
        byte[] invalidData = "not compressed data".getBytes();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> compressionService.decompress(invalidData));
        println("Invalid compressed data handled with exception");
    }

    @Test
    @DisplayName("Should compress and decompress complex nested payload")
    void shouldCompressAndDecompressComplexNestedPayload() throws Exception {
        // Arrange
        String complexJson = """
                {
                    "id": "evt_complex_123",
                    "type": "order.created",
                    "data": {
                        "order_id": "ord_456",
                        "customer": {
                            "id": "cus_789",
                            "name": "John Doe",
                            "email": "john.doe@example.com",
                            "address": {
                                "street": "123 Main St",
                                "city": "San Francisco",
                                "state": "CA",
                                "zip": "94105"
                            }
                        },
                        "items": [
                            {"id": "item_1", "name": "Product A", "price": 29.99, "quantity": 2},
                            {"id": "item_2", "name": "Product B", "price": 49.99, "quantity": 1}
                        ],
                        "total": 109.97,
                        "currency": "usd"
                    }
                }
                """;
        JsonNode complexPayload = objectMapper.readTree(complexJson);

        // Act
        byte[] compressed = compressionService.compress(complexPayload);
        JsonNode decompressed = compressionService.decompress(compressed);

        // Assert
        assertNotNull(compressed);
        assertNotNull(decompressed);
        assertEquals(complexPayload, decompressed);
        println("Complex nested payload compressed and decompressed successfully");
    }

    @Test
    @DisplayName("Should achieve good compression ratio for repetitive data")
    void shouldAchieveGoodCompressionRatioForRepetitiveData() throws Exception {
        // Arrange - Create highly repetitive data
        StringBuilder repetitiveData = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            repetitiveData.append("REPETITIVE_DATA_PATTERN_");
        }

        JsonNode payload = objectMapper.readTree("{\"data\": \"" + repetitiveData + "\"}");

        // Act
        byte[] compressed = compressionService.compress(payload);
        byte[] original = objectMapper.writeValueAsBytes(payload);

        // Assert
        assertNotNull(compressed);
        double compressionRatio = (1 - (double) compressed.length / original.length) * 100;

        println("Original size: " + original.length + " bytes");
        println("Compressed size: " + compressed.length + " bytes");
        println("Compression ratio: " + String.format("%.2f%%", compressionRatio));

        assertTrue(compressionRatio > 50, "Repetitive data should achieve >50% compression ratio");
    }

    @Test
    @DisplayName("Should handle special characters in payload")
    void shouldHandleSpecialCharactersInPayload() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree(
                "{\"message\": \"Special chars: émojis 🎉🚀, unicode: ñ, symbols: @#$%^&*()\", " +
                "\"data\": \"Multi-line\\ntext\\twith\\ttabs\\nand newlines\"}"
        );

        // Act
        byte[] compressed = compressionService.compress(payload);
        JsonNode decompressed = compressionService.decompress(compressed);

        // Assert
        assertNotNull(compressed);
        assertEquals(payload, decompressed);
        println("Special characters handled correctly");
    }

    @Test
    @DisplayName("Should check if payload should be compressed")
    void shouldCheckIfPayloadShouldBeCompressed() throws Exception {
        // Arrange
        JsonNode largePayload = objectMapper.readTree("{\"data\": \"" + "x".repeat(200) + "\"}");
        JsonNode smallPayload = objectMapper.readTree("{\"id\": \"123\"}");

        // Act & Assert
        assertTrue(compressionService.shouldCompress(largePayload));
        assertFalse(compressionService.shouldCompress(smallPayload));
        assertFalse(compressionService.shouldCompress(null));
        println("Payload size check working correctly");
    }

    /**
     * Helper method to print test output
     */
    private void println(String message) {
        System.out.println("[WebhookCompressionServiceTest] " + message);
    }
}

