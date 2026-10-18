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

package org.fireflyframework.callbacks.models.entity;

import org.fireflyframework.callbacks.interfaces.enums.CallbackStatus;
import org.fireflyframework.callbacks.interfaces.enums.HttpMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for callback configurations.
 * Represents the configuration for sending outbound webhooks to third-party systems.
 * This entity is mapped to the "callback_configurations" table in the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("callback_configurations")
public class CallbackConfiguration {

    /**
     * Unique identifier for the callback configuration.
     */
    @Id
    private UUID id;

    /**
     * Name of the callback configuration.
     */
    private String name;

    /**
     * Description of the callback configuration.
     */
    private String description;

    /**
     * Target URL for the callback endpoint.
     */
    private String url;

    /**
     * HTTP method to use for the callback (POST, PUT, PATCH).
     */
    @Column("http_method")
    private HttpMethod httpMethod;

    /**
     * Status of the callback configuration (ACTIVE, PAUSED, DISABLED, FAILED).
     */
    private CallbackStatus status;

    /**
     * Array of event types this callback is subscribed to.
     * Stored as TEXT[] in the database.
     */
    @Column("subscribed_event_types")
    private String[] subscribedEventTypes;

    /**
     * Custom headers to include in the callback request.
     * Stored as JSON TEXT in the database.
     */
    @Column("custom_headers")
    private String customHeaders;

    /**
     * Secret for HMAC signature generation (if enabled).
     */
    private String secret;

    /**
     * Whether to include HMAC signature in the callback request.
     */
    @Column("signature_enabled")
    private Boolean signatureEnabled;

    /**
     * Signature header name (e.g., "X-Firefly-Signature").
     */
    @Column("signature_header")
    private String signatureHeader;

    /**
     * Maximum number of retry attempts for failed callbacks.
     */
    @Column("max_retries")
    private Integer maxRetries;

    /**
     * Initial retry delay in milliseconds.
     */
    @Column("retry_delay_ms")
    private Integer retryDelayMs;

    /**
     * Retry backoff multiplier (exponential backoff).
     */
    @Column("retry_backoff_multiplier")
    private Double retryBackoffMultiplier;

    /**
     * Timeout for the callback request in milliseconds.
     */
    @Column("timeout_ms")
    private Integer timeoutMs;

    /**
     * Whether the callback is active.
     */
    private Boolean active;

    /**
     * Organization or tenant ID that owns this callback configuration.
     */
    @Column("tenant_id")
    private String tenantId;

    /**
     * Optional filter expression (JSONPath or similar) to filter events.
     */
    @Column("filter_expression")
    private String filterExpression;

    /**
     * Metadata for the callback configuration.
     * Stored as JSON TEXT in the database.
     */
    private String metadata;

    /**
     * Number of consecutive failures before auto-disabling.
     */
    @Column("failure_threshold")
    private Integer failureThreshold;

    /**
     * Current failure count.
     */
    @Column("failure_count")
    private Integer failureCount;

    /**
     * Last successful callback execution time.
     */
    @Column("last_success_at")
    private Instant lastSuccessAt;

    /**
     * Last failed callback execution time.
     */
    @Column("last_failure_at")
    private Instant lastFailureAt;

    /**
     * Callback configuration creation time.
     */
    @Column("created_at")
    private Instant createdAt;

    /**
     * Callback configuration last update time.
     */
    @Column("updated_at")
    private Instant updatedAt;

    /**
     * User who created the configuration.
     */
    @Column("created_by")
    private String createdBy;

    /**
     * User who last updated the configuration.
     */
    @Column("updated_by")
    private String updatedBy;
}
