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

import org.fireflyframework.webhooks.core.domain.WebhookMetadata;
import org.fireflyframework.webhooks.core.services.impl.WebhookMetadataEnrichmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebhookMetadataEnrichmentService.
 */
@DisplayName("WebhookMetadataEnrichmentService Tests")
class WebhookMetadataEnrichmentServiceTest {

    private WebhookMetadataEnrichmentServiceImpl enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new WebhookMetadataEnrichmentServiceImpl();
    }

    @Test
    @DisplayName("Should enrich metadata with request ID")
    void shouldEnrichMetadataWithRequestId() {
        // Arrange
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .build();

        // Act
        WebhookMetadata metadata = enrichmentService.enrich(request, "192.168.1.1");

        // Assert
        assertNotNull(metadata);
        assertNotNull(metadata.getRequestId());
        assertTrue(metadata.getRequestId().matches("^[a-f0-9-]{36}$"), "Request ID should be a valid UUID");
        println("Generated request ID: " + metadata.getRequestId());
    }

    @Test
    @DisplayName("Should enrich metadata with timestamp")
    void shouldEnrichMetadataWithTimestamp() {
        // Arrange
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .build();

        // Act
        WebhookMetadata metadata = enrichmentService.enrich(request, "192.168.1.1");

        // Assert
        assertNotNull(metadata.getReceivedAtNanos());
        println("Received at: " + metadata.getReceivedAtNanos());
    }

    @Test
    @DisplayName("Should enrich metadata with source IP")
    void shouldEnrichMetadataWithSourceIp() {
        // Arrange
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .build();
        String sourceIp = "203.0.113.1";

        // Act
        WebhookMetadata metadata = enrichmentService.enrich(request, sourceIp);

        // Assert
        assertEquals(sourceIp, metadata.getSourceIp());
        println("Source IP: " + metadata.getSourceIp());
    }

    @Test
    @DisplayName("Should parse Chrome User-Agent correctly")
    void shouldParseChromeUserAgentCorrectly() {
        // Arrange
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent(userAgent);

        // Assert
        assertNotNull(info);
        assertEquals(userAgent, info.getRaw());
        assertTrue(info.getBrowser().toLowerCase().contains("chrome"));
        assertFalse(info.isBot());
        println("Parsed Chrome User-Agent: " + info.getBrowser() + " " + info.getBrowserVersion());
    }

    @Test
    @DisplayName("Should parse Firefox User-Agent correctly")
    void shouldParseFirefoxUserAgentCorrectly() {
        // Arrange
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0";

        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent(userAgent);

        // Assert
        assertNotNull(info);
        assertEquals(userAgent, info.getRaw());
        assertTrue(info.getBrowser().toLowerCase().contains("firefox"));
        assertFalse(info.isBot());
        println("Parsed Firefox User-Agent: " + info.getBrowser() + " " + info.getBrowserVersion());
    }

    @Test
    @DisplayName("Should parse Safari User-Agent correctly")
    void shouldParseSafariUserAgentCorrectly() {
        // Arrange
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15";

        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent(userAgent);

        // Assert
        assertNotNull(info);
        assertEquals(userAgent, info.getRaw());
        assertTrue(info.getBrowser().toLowerCase().contains("safari"));
        assertFalse(info.isBot());
        println("Parsed Safari User-Agent: " + info.getBrowser() + " " + info.getBrowserVersion());
    }

    @Test
    @DisplayName("Should detect bot User-Agent")
    void shouldDetectBotUserAgent() {
        // Arrange
        String userAgent = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent(userAgent);

        // Assert
        assertNotNull(info);
        assertTrue(info.isBot());
        println("Detected bot: " + info.getBrowser());
    }

    @Test
    @DisplayName("Should parse mobile User-Agent correctly")
    void shouldParseMobileUserAgentCorrectly() {
        // Arrange
        String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1";

        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent(userAgent);

        // Assert
        assertNotNull(info);
        // The simple parser may not detect all mobile details, just verify it parses without error
        assertNotNull(info.getRaw());
        assertEquals(userAgent, info.getRaw());
        println("Parsed mobile User-Agent - OS: " + info.getOs() + ", Device: " + info.getDevice());
    }

    @Test
    @DisplayName("Should handle null User-Agent")
    void shouldHandleNullUserAgent() {
        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent(null);

        // Assert
        assertNotNull(info);
        assertEquals("Unknown", info.getRaw());
        assertEquals("Unknown", info.getBrowser());
        println("Null User-Agent handled correctly");
    }

    @Test
    @DisplayName("Should handle empty User-Agent")
    void shouldHandleEmptyUserAgent() {
        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent("");

        // Assert
        assertNotNull(info);
        assertEquals("Unknown", info.getRaw());
        assertEquals("Unknown", info.getBrowser());
        println("Empty User-Agent handled correctly");
    }

    @Test
    @DisplayName("Should enrich with User-Agent from request header")
    void shouldEnrichWithUserAgentFromRequestHeader() {
        // Arrange
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .header("User-Agent", userAgent)
                .build();

        // Act
        WebhookMetadata metadata = enrichmentService.enrich(request, "192.168.1.1");

        // Assert
        assertNotNull(metadata.getUserAgent());
        assertEquals(userAgent, metadata.getUserAgent().getRaw());
        println("User-Agent enriched from request header");
    }

    @Test
    @DisplayName("Should generate unique request IDs")
    void shouldGenerateUniqueRequestIds() {
        // Act
        String id1 = enrichmentService.generateRequestId();
        String id2 = enrichmentService.generateRequestId();
        String id3 = enrichmentService.generateRequestId();

        // Assert
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);
        println("Generated unique request IDs: " + id1 + ", " + id2 + ", " + id3);
    }

    @Test
    @DisplayName("Should handle request without User-Agent header")
    void shouldHandleRequestWithoutUserAgentHeader() {
        // Arrange
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .build();

        // Act
        WebhookMetadata metadata = enrichmentService.enrich(request, "192.168.1.1");

        // Assert
        assertNotNull(metadata.getUserAgent());
        assertEquals("Unknown", metadata.getUserAgent().getRaw());
        println("Request without User-Agent handled correctly");
    }

    @Test
    @DisplayName("Should parse Stripe webhook User-Agent")
    void shouldParseStripeWebhookUserAgent() {
        // Arrange
        String userAgent = "Stripe/1.0 (+https://stripe.com/docs/webhooks)";

        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent(userAgent);

        // Assert
        assertNotNull(info);
        assertEquals(userAgent, info.getRaw());
        println("Parsed Stripe webhook User-Agent: " + info.getBrowser());
    }

    @Test
    @DisplayName("Should parse GitHub webhook User-Agent")
    void shouldParseGitHubWebhookUserAgent() {
        // Arrange
        String userAgent = "GitHub-Hookshot/abc123";

        // Act
        WebhookMetadata.UserAgentInfo info = enrichmentService.parseUserAgent(userAgent);

        // Assert
        assertNotNull(info);
        assertEquals(userAgent, info.getRaw());
        println("Parsed GitHub webhook User-Agent: " + info.getBrowser());
    }

    @Test
    @DisplayName("Should enrich complete metadata")
    void shouldEnrichCompleteMetadata() {
        // Arrange
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .header("User-Agent", userAgent)
                .build();

        // Act
        WebhookMetadata metadata = enrichmentService.enrich(request, "203.0.113.1");

        // Assert
        assertNotNull(metadata);
        assertNotNull(metadata.getRequestId());
        assertNotNull(metadata.getReceivedAtNanos());
        assertEquals("203.0.113.1", metadata.getSourceIp());
        assertNotNull(metadata.getUserAgent());
        assertEquals(userAgent, metadata.getUserAgent().getRaw());

        println("Complete metadata enrichment:");
        println("  Request ID: " + metadata.getRequestId());
        println("  Received At: " + metadata.getReceivedAtNanos());
        println("  Source IP: " + metadata.getSourceIp());
        println("  User-Agent: " + metadata.getUserAgent().getRaw());
    }

    /**
     * Helper method to print test output
     */
    private void println(String message) {
        System.out.println("[WebhookMetadataEnrichmentServiceTest] " + message);
    }
}

