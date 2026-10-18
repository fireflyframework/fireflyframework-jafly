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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event representing a webhook that was rejected or failed processing.
 * <p>
 * This event is published to the Dead Letter Queue (DLQ) for webhooks that:
 * - Failed validation (signature, IP whitelist, payload size, etc.)
 * - Failed processing after max retries
 * - Encountered unrecoverable errors
 * <p>
 * The DLQ allows for:
 * - Manual inspection and debugging of failed webhooks
 * - Replay of webhooks after fixing issues
 * - Monitoring and alerting on webhook failures
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRejectedEvent {

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
     * Timestamp when the webhook was originally received
     */
    private Instant receivedAt;

    /**
     * Timestamp when the webhook was rejected
     */
    private Instant rejectedAt;

    /**
     * Client IP address that sent the webhook
     */
    private String sourceIp;

    /**
     * HTTP method used for the webhook request
     */
    private String httpMethod;

    /**
     * Reason for rejection (e.g., "Invalid signature", "IP not whitelisted", "Max retries exceeded")
     */
    private String rejectionReason;

    /**
     * Category of rejection for easier filtering and monitoring
     */
    private RejectionCategory rejectionCategory;

    /**
     * Error message or stack trace (if applicable)
     */
    private String errorDetails;

    /**
     * Number of retry attempts (if applicable)
     */
    private Integer retryCount;

    /**
     * Original exception class name (if applicable)
     */
    private String exceptionType;

    /**
     * Event type identifier for messaging
     */
    public String getEventType() {
        return "webhook." + providerName + ".rejected";
    }

    /**
     * Categories of webhook rejection for monitoring and alerting
     */
    public enum RejectionCategory {
        /**
         * Validation failures (signature, IP whitelist, payload size, etc.)
         */
        VALIDATION_FAILURE,

        /**
         * Processing failures after max retries
         */
        PROCESSING_FAILURE,

        /**
         * Timeout or circuit breaker open
         */
        TIMEOUT_FAILURE,

        /**
         * Unrecoverable errors (e.g., malformed payload, missing required fields)
         */
        UNRECOVERABLE_ERROR,

        /**
         * Rate limit exceeded
         */
        RATE_LIMIT_EXCEEDED,

        /**
         * Other/unknown failures
         */
        OTHER
    }
}

