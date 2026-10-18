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

package org.fireflyframework.webhooks.interfaces.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO representing the response after receiving a webhook.
 * <p>
 * This is returned to the webhook provider to acknowledge receipt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response after webhook event is received")
public class WebhookResponseDTO {

    @Schema(description = "Unique identifier for the processed webhook event", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID eventId;

    @Schema(description = "Status of the webhook processing", example = "ACCEPTED", allowableValues = {"ACCEPTED", "ERROR", "REJECTED"})
    private String status;

    @Schema(description = "Message describing the result", example = "Webhook received and queued for processing")
    private String message;

    @Schema(description = "Timestamp when the webhook was received by the platform")
    private Instant receivedAt;

    @Schema(description = "Timestamp when the webhook was processed and acknowledged")
    private Instant processedAt;

    @Schema(description = "Provider name for reference", example = "stripe")
    private String providerName;

    @Schema(description = "Echo of the received payload for verification purposes")
    private JsonNode receivedPayload;

    @Schema(description = "Metadata about the webhook processing")
    private Map<String, Object> metadata;

    /**
     * Creates a success response.
     *
     * @param eventId the event ID
     * @param providerName the provider name
     * @return a success response
     */
    public static WebhookResponseDTO success(UUID eventId, String providerName) {
        Instant now = Instant.now();
        return WebhookResponseDTO.builder()
                .eventId(eventId)
                .status("ACCEPTED")
                .message("Webhook received and queued for processing")
                .receivedAt(now)
                .processedAt(now)
                .providerName(providerName)
                .build();
    }

    /**
     * Creates a success response with full details.
     *
     * @param eventId the event ID
     * @param providerName the provider name
     * @param receivedAt when the webhook was received
     * @param payload the received payload
     * @param metadata additional metadata
     * @return a success response with full details
     */
    public static WebhookResponseDTO success(
            UUID eventId,
            String providerName,
            Instant receivedAt,
            JsonNode payload,
            Map<String, Object> metadata) {
        return WebhookResponseDTO.builder()
                .eventId(eventId)
                .status("ACCEPTED")
                .message("Webhook received and queued for processing")
                .receivedAt(receivedAt)
                .processedAt(Instant.now())
                .providerName(providerName)
                .receivedPayload(payload)
                .metadata(metadata)
                .build();
    }

    /**
     * Creates an error response.
     *
     * @param eventId the event ID (may be null)
     * @param providerName the provider name
     * @param errorMessage the error message
     * @return an error response
     */
    public static WebhookResponseDTO error(UUID eventId, String providerName, String errorMessage) {
        Instant now = Instant.now();
        return WebhookResponseDTO.builder()
                .eventId(eventId)
                .status("ERROR")
                .message(errorMessage)
                .receivedAt(now)
                .processedAt(now)
                .providerName(providerName)
                .build();
    }

    /**
     * Creates an error response with full details.
     *
     * @param eventId the event ID (may be null)
     * @param providerName the provider name
     * @param errorMessage the error message
     * @param receivedAt when the webhook was received
     * @param payload the received payload (if available)
     * @return an error response with full details
     */
    public static WebhookResponseDTO error(
            UUID eventId,
            String providerName,
            String errorMessage,
            Instant receivedAt,
            JsonNode payload) {
        return WebhookResponseDTO.builder()
                .eventId(eventId)
                .status("ERROR")
                .message(errorMessage)
                .receivedAt(receivedAt)
                .processedAt(Instant.now())
                .providerName(providerName)
                .receivedPayload(payload)
                .build();
    }
}
