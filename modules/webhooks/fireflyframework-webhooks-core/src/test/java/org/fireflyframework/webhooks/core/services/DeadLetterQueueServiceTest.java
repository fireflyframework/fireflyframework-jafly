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
import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.fireflyframework.webhooks.core.domain.events.WebhookRejectedEvent;
import org.fireflyframework.webhooks.core.services.impl.DeadLetterQueueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeadLetterQueueService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeadLetterQueueService Tests")
class DeadLetterQueueServiceTest {

    @Mock
    private EventPublisherFactory eventPublisherFactory;

    @Mock
    private EventPublisher eventPublisher;

    private DeadLetterQueueServiceImpl dlqService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        dlqService = new DeadLetterQueueServiceImpl(eventPublisherFactory);
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(dlqService, "dlqTopic", "webhooks.dlq");
        ReflectionTestUtils.setField(dlqService, "dlqEnabled", true);
    }

    @Test
    @DisplayName("Should publish rejected webhook to DLQ")
    void shouldPublishRejectedWebhookToDlq() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"test\": \"data\"}");
        Instant now = Instant.now();
        WebhookRejectedEvent rejectedEvent = WebhookRejectedEvent.builder()
                .eventId(UUID.randomUUID())
                .providerName("stripe")
                .payload(payload)
                .rejectionReason("Validation failed")
                .rejectionCategory(WebhookRejectedEvent.RejectionCategory.VALIDATION_FAILURE)
                .receivedAt(now)
                .rejectedAt(now)
                .build();

        when(eventPublisherFactory.getDefaultPublisher()).thenReturn(eventPublisher);
        when(eventPublisher.publish(any(), anyString(), anyMap())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(dlqService.publishToDeadLetterQueue(rejectedEvent))
                .verifyComplete();

        verify(eventPublisher).publish(eq(rejectedEvent), eq("webhooks.dlq"), anyMap());
        println("Rejected webhook published to DLQ successfully");
    }

    @Test
    @DisplayName("Should include metadata in DLQ message headers")
    void shouldIncludeMetadataInDlqMessageHeaders() throws Exception {
        // Arrange
        UUID eventId = UUID.randomUUID();
        JsonNode payload = objectMapper.readTree("{\"event\": \"push\"}");
        Instant now = Instant.now();
        WebhookRejectedEvent rejectedEvent = WebhookRejectedEvent.builder()
                .eventId(eventId)
                .providerName("github")
                .payload(payload)
                .rejectionReason("Signature validation failed")
                .rejectionCategory(WebhookRejectedEvent.RejectionCategory.VALIDATION_FAILURE)
                .receivedAt(now)
                .rejectedAt(now)
                .build();

        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        when(eventPublisherFactory.getDefaultPublisher()).thenReturn(eventPublisher);
        when(eventPublisher.publish(any(), anyString(), headersCaptor.capture())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(dlqService.publishToDeadLetterQueue(rejectedEvent))
                .verifyComplete();

        // Assert
        Map<String, Object> headers = headersCaptor.getValue();
        assertEquals("github", headers.get("provider"));
        assertEquals(eventId.toString(), headers.get("eventId"));
        assertEquals("VALIDATION_FAILURE", headers.get("rejectionCategory"));
        assertNotNull(headers.get("rejectedAt"));
        println("DLQ message headers validated successfully");
    }

    @Test
    @DisplayName("Should not publish when DLQ is disabled")
    void shouldNotPublishWhenDlqDisabled() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(dlqService, "dlqEnabled", false);

        JsonNode payload = objectMapper.readTree("{\"test\": \"data\"}");
        Instant now = Instant.now();
        WebhookRejectedEvent rejectedEvent = WebhookRejectedEvent.builder()
                .eventId(UUID.randomUUID())
                .providerName("stripe")
                .payload(payload)
                .rejectionReason("Test")
                .rejectionCategory(WebhookRejectedEvent.RejectionCategory.VALIDATION_FAILURE)
                .receivedAt(now)
                .rejectedAt(now)
                .build();

        // Act & Assert
        StepVerifier.create(dlqService.publishToDeadLetterQueue(rejectedEvent))
                .verifyComplete();

        verify(eventPublisher, never()).publish(any(), anyString(), anyMap());
        println("DLQ disabled - no publish occurred");
    }

    @Test
    @DisplayName("Should handle publisher errors gracefully")
    void shouldHandlePublisherErrorsGracefully() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"test\": \"data\"}");
        Instant now = Instant.now();
        WebhookRejectedEvent rejectedEvent = WebhookRejectedEvent.builder()
                .eventId(UUID.randomUUID())
                .providerName("stripe")
                .payload(payload)
                .rejectionReason("Test")
                .rejectionCategory(WebhookRejectedEvent.RejectionCategory.VALIDATION_FAILURE)
                .receivedAt(now)
                .rejectedAt(now)
                .build();

        when(eventPublisherFactory.getDefaultPublisher()).thenReturn(eventPublisher);
        when(eventPublisher.publish(any(), anyString(), anyMap()))
                .thenReturn(Mono.error(new RuntimeException("Kafka unavailable")));

        // Act & Assert - The service handles errors gracefully and returns Mono.empty()
        StepVerifier.create(dlqService.publishToDeadLetterQueue(rejectedEvent))
                .verifyComplete();
        println("Publisher error handled correctly");
    }

    @Test
    @DisplayName("Should handle null publisher gracefully")
    void shouldHandleNullPublisherGracefully() throws Exception {
        // Arrange
        when(eventPublisherFactory.getDefaultPublisher()).thenReturn(null);

        JsonNode payload = objectMapper.readTree("{\"test\": \"data\"}");
        Instant now = Instant.now();
        WebhookRejectedEvent rejectedEvent = WebhookRejectedEvent.builder()
                .eventId(UUID.randomUUID())
                .providerName("stripe")
                .payload(payload)
                .rejectionReason("Test")
                .rejectionCategory(WebhookRejectedEvent.RejectionCategory.VALIDATION_FAILURE)
                .receivedAt(now)
                .rejectedAt(now)
                .build();

        // Act & Assert
        StepVerifier.create(dlqService.publishToDeadLetterQueue(rejectedEvent))
                .expectError(IllegalStateException.class)
                .verify();
        println("Null publisher handled with exception");
    }

    @Test
    @DisplayName("Should handle all rejection categories")
    void shouldHandleAllRejectionCategories() throws Exception {
        when(eventPublisherFactory.getDefaultPublisher()).thenReturn(eventPublisher);
        when(eventPublisher.publish(any(), anyString(), anyMap())).thenReturn(Mono.empty());

        JsonNode payload = objectMapper.readTree("{\"test\": \"data\"}");
        Instant now = Instant.now();

        for (WebhookRejectedEvent.RejectionCategory category : WebhookRejectedEvent.RejectionCategory.values()) {
            WebhookRejectedEvent rejectedEvent = WebhookRejectedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .providerName("test-provider")
                    .payload(payload)
                    .rejectionReason("Test rejection for " + category)
                    .rejectionCategory(category)
                    .receivedAt(now)
                    .rejectedAt(now)
                    .build();

            StepVerifier.create(dlqService.publishToDeadLetterQueue(rejectedEvent))
                    .verifyComplete();
        }

        verify(eventPublisher, times(WebhookRejectedEvent.RejectionCategory.values().length))
                .publish(any(), eq("webhooks.dlq"), anyMap());
        println("All rejection categories handled successfully");
    }

    @Test
    @DisplayName("Should preserve original payload in rejected event")
    void shouldPreserveOriginalPayloadInRejectedEvent() throws Exception {
        // Arrange
        JsonNode originalPayload = objectMapper.readTree(
                "{\"id\": \"evt_123\", \"type\": \"payment_intent.succeeded\", \"data\": {\"amount\": 1000}}"
        );

        Instant now = Instant.now();
        WebhookRejectedEvent rejectedEvent = WebhookRejectedEvent.builder()
                .eventId(UUID.randomUUID())
                .providerName("stripe")
                .payload(originalPayload)
                .rejectionReason("Test")
                .rejectionCategory(WebhookRejectedEvent.RejectionCategory.VALIDATION_FAILURE)
                .receivedAt(now)
                .rejectedAt(now)
                .build();

        ArgumentCaptor<WebhookRejectedEvent> eventCaptor = ArgumentCaptor.forClass(WebhookRejectedEvent.class);
        when(eventPublisherFactory.getDefaultPublisher()).thenReturn(eventPublisher);
        when(eventPublisher.publish(eventCaptor.capture(), anyString(), anyMap())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(dlqService.publishToDeadLetterQueue(rejectedEvent))
                .verifyComplete();

        // Assert
        WebhookRejectedEvent capturedEvent = eventCaptor.getValue();
        assertEquals(originalPayload, capturedEvent.getPayload());
        println("Original payload preserved in rejected event");
    }

    /**
     * Helper method to print test output
     */
    private void println(String message) {
        System.out.println("[DeadLetterQueueServiceTest] " + message);
    }
}

