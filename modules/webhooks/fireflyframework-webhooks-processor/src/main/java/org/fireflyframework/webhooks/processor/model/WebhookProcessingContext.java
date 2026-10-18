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

package org.fireflyframework.webhooks.processor.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Context object containing all information needed to process a webhook event.
 * <p>
 * This is created from the Kafka message and passed to webhook processors.
 */
@Data
@Builder
public class WebhookProcessingContext {

    /**
     * Unique identifier for this webhook event.
     */
    private UUID eventId;

    /**
     * The provider that sent the webhook (e.g., "stripe", "shopify").
     */
    private String providerName;

    /**
     * The raw JSON payload from the webhook.
     */
    private JsonNode payload;

    /**
     * HTTP headers from the original webhook request.
     */
    private Map<String, String> headers;

    /**
     * Query parameters from the original webhook request.
     */
    private Map<String, String> queryParams;

    /**
     * Timestamp when the webhook was received by the platform.
     */
    private Instant receivedAt;

    /**
     * Kafka topic the message was consumed from.
     */
    private String topic;

    /**
     * Kafka partition the message came from.
     */
    private Integer partition;

    /**
     * Kafka offset of the message.
     */
    private Long offset;

    /**
     * Number of times this message has been processed (for retry tracking).
     */
    @Builder.Default
    private int attemptNumber = 1;

    /**
     * Additional metadata that can be set by processors.
     */
    private Map<String, Object> metadata;

    /**
     * Helper method to get a header value.
     *
     * @param headerName the header name
     * @return the header value or null if not present
     */
    public String getHeader(String headerName) {
        return headers != null ? headers.get(headerName) : null;
    }

    /**
     * Helper method to get a query parameter value.
     *
     * @param paramName the parameter name
     * @return the parameter value or null if not present
     */
    public String getQueryParam(String paramName) {
        return queryParams != null ? queryParams.get(paramName) : null;
    }

    /**
     * Helper method to check if this is a retry attempt.
     *
     * @return true if attempt number is greater than 1
     */
    public boolean isRetry() {
        return attemptNumber > 1;
    }
}
