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

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for managing authorized callback domains.
 * Only callbacks to authorized domains are allowed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizedDomainDTO {

    /**
     * Unique identifier for the authorized domain.
     */
    private UUID id;

    /**
     * Domain name (e.g., "api.example.com", "*.example.com" for wildcard, "localhost:8080").
     * Must be between 1 and 255 characters.
     */
    @NotBlank(message = "Domain is required")
    @Size(min = 1, max = 255, message = "Domain must be between 1 and 255 characters")
    private String domain;

    /**
     * Organization or owner of this domain.
     * Maximum 255 characters.
     */
    @Size(max = 255, message = "Organization must not exceed 255 characters")
    private String organization;

    /**
     * Contact email for this domain.
     * Must be a valid email address with maximum 255 characters.
     */
    @Email(message = "Contact email must be a valid email address")
    @Size(max = 255, message = "Contact email must not exceed 255 characters")
    private String contactEmail;

    /**
     * Whether this domain is verified (e.g., via DNS TXT record or challenge).
     */
    @Builder.Default
    private Boolean verified = false;

    /**
     * Verification method used (DNS, HTTP, EMAIL).
     * Maximum 50 characters.
     */
    @Size(max = 50, message = "Verification method must not exceed 50 characters")
    private String verificationMethod;

    /**
     * Verification token for domain verification.
     * Maximum 500 characters.
     */
    @Size(max = 500, message = "Verification token must not exceed 500 characters")
    private String verificationToken;

    /**
     * When the domain was verified.
     */
    private Instant verifiedAt;

    /**
     * Whether this domain is active and can receive callbacks.
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Allowed URL paths (patterns). Null/empty means all paths allowed.
     * Example: ["/webhooks/*", "/api/v1/callbacks/*"]
     */
    private String[] allowedPaths;

    /**
     * Maximum callbacks per minute for this domain (rate limiting).
     * Must be between 1 and 10000.
     */
    @Builder.Default
    @Min(value = 1, message = "Max callbacks per minute must be at least 1")
    @Max(value = 10000, message = "Max callbacks per minute must not exceed 10000")
    private Integer maxCallbacksPerMinute = 100;

    /**
     * IP whitelist for this domain (optional additional security).
     */
    private String[] ipWhitelist;

    /**
     * Whether to require HTTPS for this domain.
     */
    @Builder.Default
    private Boolean requireHttps = true;

    /**
     * Tenant ID for multi-tenancy.
     * Maximum 100 characters.
     */
    @Size(max = 100, message = "Tenant ID must not exceed 100 characters")
    private String tenantId;

    /**
     * Additional notes about this domain.
     * Maximum 2000 characters.
     */
    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;

    /**
     * Metadata for the domain.
     */
    private Map<String, Object> metadata;

    /**
     * Domain expiration date (for temporary authorizations).
     */
    private Instant expiresAt;

    /**
     * Last time a callback was sent to this domain.
     */
    private Instant lastCallbackAt;

    /**
     * Total callbacks sent to this domain.
     * Must be non-negative.
     */
    @Builder.Default
    @Min(value = 0, message = "Total callbacks must be non-negative")
    private Long totalCallbacks = 0L;

    /**
     * Total failed callbacks to this domain.
     * Must be non-negative.
     */
    @Builder.Default
    @Min(value = 0, message = "Total failed must be non-negative")
    private Long totalFailed = 0L;

    /**
     * Creation timestamp.
     */
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    private Instant updatedAt;

    /**
     * User who authorized the domain.
     * Maximum 255 characters.
     */
    @Size(max = 255, message = "Authorized by must not exceed 255 characters")
    private String authorizedBy;
}
