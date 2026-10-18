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
 * DTO representing a webhook event received from any provider.
 * <p>
 * This DTO captures the raw webhook payload along with metadata about
 * the provider and request context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Webhook event data received from a provider")
public class WebhookEventDTO {

    @Schema(description = "Unique identifier for this webhook event", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID eventId;

    @Schema(description = "Name of the webhook provider", example = "stripe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String providerName;

    @Schema(description = "Raw webhook payload as JSON", requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode payload;

    @Schema(description = "HTTP headers from the webhook request")
    private Map<String, String> headers;

    @Schema(description = "Query parameters from the webhook request")
    private Map<String, String> queryParams;

    @Schema(description = "Timestamp when the webhook was received")
    private Instant receivedAt;

    @Schema(description = "Client IP address that sent the webhook")
    private String sourceIp;

    @Schema(description = "HTTP method used for the webhook request", example = "POST")
    private String httpMethod;

    @Schema(description = "Enriched metadata (geolocation, User-Agent, etc.)")
    private Map<String, Object> enrichedMetadata;
}
