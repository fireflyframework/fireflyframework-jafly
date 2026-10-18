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

package org.fireflyframework.webhooks.core.domain.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.fireflyframework.webhooks.core.domain.WebhookMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event representing a webhook received from a provider.
 * <p>
 * This event captures the complete webhook data for event sourcing
 * and message queue publishing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookReceivedEvent {

    /**
     * Unique identifier for this webhook event
     */
    private UUID eventId;

    /**
     * Name of the webhook provider (e.g., "stripe", "paypal")
     */
    private String providerName;

    /**
     * Raw webhook payload as JSON
     */
    private JsonNode payload;

    /**
     * HTTP headers from the webhook request
     */
    private Map<String, String> headers;

    /**
     * Query parameters from the webhook request
     */
    private Map<String, String> queryParams;

    /**
     * Timestamp when the webhook was received
     */
    private Instant receivedAt;

    /**
     * Client IP address that sent the webhook
     */
    private String sourceIp;

    /**
     * HTTP method used for the webhook request
     */
    private String httpMethod;

    /**
     * Compressed payload (if compression is enabled)
     */
    private byte[] compressedPayload;

    /**
     * Indicates if the payload is compressed
     */
    @Builder.Default
    private boolean compressed = false;

    /**
     * Enriched metadata (geolocation, User-Agent, etc.)
     */
    private WebhookMetadata enrichedMetadata;

    /**
     * Event type identifier for messaging
     */
    @JsonIgnore
    public String getEventType() {
        return "webhook." + providerName + ".received";
    }
}
