/*
 * Copyright 2024-2026 Firefly Software Foundation
 */

package org.fireflyframework.webhooks.core.mappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.webhooks.core.domain.events.WebhookReceivedEvent;
import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookEventMapper Unit Tests")
class WebhookEventMapperTest {

    private WebhookEventMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(WebhookEventMapper.class);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should map WebhookEventDto to WebhookReceivedEvent")
    void shouldMapDtoToDomainEvent() throws Exception {
        // Given
        UUID eventId = UUID.randomUUID();
        Instant receivedAt = Instant.now();
        JsonNode payload = objectMapper.readTree("{\"type\":\"payment.succeeded\",\"amount\":1000}");
        
        WebhookEventDTO dto = WebhookEventDTO.builder()
                .eventId(eventId)
                .providerName("stripe")
                .payload(payload)
                .headers(Map.of("stripe-signature", "sig123"))
                .queryParams(Map.of("test", "true"))
                .receivedAt(receivedAt)
                .sourceIp("192.168.1.1")
                .httpMethod("POST")
                .build();

        // When
        WebhookReceivedEvent event = mapper.toDomainEvent(dto);

        // Then
        assertThat(event).isNotNull();
        assertThat(event.getEventId()).isEqualTo(eventId);
        assertThat(event.getProviderName()).isEqualTo("stripe");
        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(event.getHeaders()).containsEntry("stripe-signature", "sig123");
        assertThat(event.getQueryParams()).containsEntry("test", "true");
        assertThat(event.getReceivedAt()).isEqualTo(receivedAt);
        assertThat(event.getSourceIp()).isEqualTo("192.168.1.1");
        assertThat(event.getHttpMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("Should map WebhookReceivedEvent to WebhookEventDto")
    void shouldMapDomainEventToDto() throws Exception {
        // Given
        UUID eventId = UUID.randomUUID();
        Instant receivedAt = Instant.now();
        JsonNode payload = objectMapper.readTree("{\"event\":\"test\"}");
        
        WebhookReceivedEvent event = WebhookReceivedEvent.builder()
                .eventId(eventId)
                .providerName("paypal")
                .payload(payload)
                .headers(Map.of("paypal-auth", "token"))
                .queryParams(Map.of())
                .receivedAt(receivedAt)
                .sourceIp("10.0.0.1")
                .httpMethod("POST")
                .build();

        // When
        WebhookEventDTO dto = mapper.toDto(event);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getEventId()).isEqualTo(eventId);
        assertThat(dto.getProviderName()).isEqualTo("paypal");
        assertThat(dto.getPayload()).isEqualTo(payload);
        assertThat(dto.getHeaders()).containsEntry("paypal-auth", "token");
        assertThat(dto.getReceivedAt()).isEqualTo(receivedAt);
    }

    @Test
    @DisplayName("Should handle null optional fields")
    void shouldHandleNullOptionalFields() throws Exception {
        // Given
        JsonNode payload = objectMapper.readTree("{}");
        
        WebhookEventDTO dto = WebhookEventDTO.builder()
                .eventId(UUID.randomUUID())
                .providerName("github")
                .payload(payload)
                .receivedAt(Instant.now())
                .build();

        // When
        WebhookReceivedEvent event = mapper.toDomainEvent(dto);

        // Then
        assertThat(event).isNotNull();
        assertThat(event.getProviderName()).isEqualTo("github");
        assertThat(event.getSourceIp()).isNull();
    }
}
