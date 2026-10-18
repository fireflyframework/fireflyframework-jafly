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

package org.fireflyframework.callbacks.interfaces.dto;

import org.fireflyframework.callbacks.interfaces.enums.CallbackStatus;
import org.fireflyframework.callbacks.interfaces.enums.HttpMethod;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for callback configuration.
 * Represents the configuration for sending outbound webhooks to third parties.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackConfigurationDTO {

    /**
     * Unique identifier for the callback configuration.
     */
    private UUID id;

    /**
     * Name of the callback configuration.
     * Must be between 1 and 255 characters.
     */
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;

    /**
     * Description of the callback configuration.
     * Maximum 2000 characters.
     */
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    /**
     * Target URL for the callback endpoint.
     * Must be a valid HTTP or HTTPS URL with maximum length of 2048 characters.
     */
    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
    private String url;

    /**
     * HTTP method to use for the callback.
     */
    @Builder.Default
    @NotNull(message = "HTTP method is required")
    private HttpMethod httpMethod = HttpMethod.POST;

    /**
     * Status of the callback configuration.
     */
    @Builder.Default
    @NotNull(message = "Status is required")
    private CallbackStatus status = CallbackStatus.ACTIVE;

    /**
     * Array of event types this callback is subscribed to.
     * Examples: "customer.created", "loan.approved", "payment.received"
     * Must contain at least one event type.
     */
    @NotNull(message = "Event types are required")
    @Size(min = 1, message = "At least one event type must be specified")
    private String[] subscribedEventTypes;

    /**
     * Custom headers to include in the callback request.
     */
    private Map<String, String> customHeaders;

    /**
     * Secret for HMAC signature generation (if enabled).
     * Maximum 500 characters. Required if signatureEnabled is true.
     */
    @Size(max = 500, message = "Secret must not exceed 500 characters")
    private String secret;

    /**
     * Whether to include HMAC signature in the callback request.
     */
    @Builder.Default
    private Boolean signatureEnabled = false;

    /**
     * Signature header name (e.g., "X-Firefly-Signature").
     * Maximum 100 characters. Defaults to "X-Firefly-Signature" if not specified.
     */
    @Size(max = 100, message = "Signature header must not exceed 100 characters")
    private String signatureHeader;

    /**
     * Maximum number of retry attempts for failed callbacks.
     * Must be between 0 and 10.
     */
    @Builder.Default
    @Min(value = 0, message = "Max retries must be at least 0")
    @Max(value = 10, message = "Max retries must not exceed 10")
    private Integer maxRetries = 3;

    /**
     * Initial retry delay in milliseconds.
     * Must be between 100ms and 300000ms (5 minutes).
     */
    @Builder.Default
    @Min(value = 100, message = "Retry delay must be at least 100ms")
    @Max(value = 300000, message = "Retry delay must not exceed 300000ms (5 minutes)")
    private Integer retryDelayMs = 1000;

    /**
     * Retry backoff multiplier (exponential backoff).
     * Must be between 1.0 and 10.0.
     */
    @Builder.Default
    @DecimalMin(value = "1.0", message = "Retry backoff multiplier must be at least 1.0")
    @DecimalMax(value = "10.0", message = "Retry backoff multiplier must not exceed 10.0")
    private Double retryBackoffMultiplier = 2.0;

    /**
     * Timeout for the callback request in milliseconds.
     * Must be between 1000ms (1 second) and 300000ms (5 minutes).
     */
    @Builder.Default
    @Min(value = 1000, message = "Timeout must be at least 1000ms (1 second)")
    @Max(value = 300000, message = "Timeout must not exceed 300000ms (5 minutes)")
    private Integer timeoutMs = 30000;

    /**
     * Whether the callback is active.
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Organization or tenant ID that owns this callback configuration.
     * Maximum 100 characters.
     */
    @Size(max = 100, message = "Tenant ID must not exceed 100 characters")
    private String tenantId;

    /**
     * Optional filter expression (JSONPath or similar) to filter events.
     * Maximum 1000 characters.
     */
    @Size(max = 1000, message = "Filter expression must not exceed 1000 characters")
    private String filterExpression;

    /**
     * Metadata for the callback configuration.
     */
    private Map<String, Object> metadata;

    /**
     * Number of consecutive failures before auto-disabling.
     * Must be between 1 and 100.
     */
    @Builder.Default
    @Min(value = 1, message = "Failure threshold must be at least 1")
    @Max(value = 100, message = "Failure threshold must not exceed 100")
    private Integer failureThreshold = 10;

    /**
     * Current failure count.
     * Must be non-negative.
     */
    @Builder.Default
    @Min(value = 0, message = "Failure count must be non-negative")
    private Integer failureCount = 0;

    /**
     * Last successful callback execution time.
     */
    private Instant lastSuccessAt;

    /**
     * Last failed callback execution time.
     */
    private Instant lastFailureAt;

    /**
     * Callback configuration creation time.
     */
    private Instant createdAt;

    /**
     * Callback configuration last update time.
     */
    private Instant updatedAt;

    /**
     * User who created the configuration.
     * Maximum 255 characters.
     */
    @Size(max = 255, message = "Created by must not exceed 255 characters")
    private String createdBy;

    /**
     * User who last updated the configuration.
     * Maximum 255 characters.
     */
    @Size(max = 255, message = "Updated by must not exceed 255 characters")
    private String updatedBy;
}
