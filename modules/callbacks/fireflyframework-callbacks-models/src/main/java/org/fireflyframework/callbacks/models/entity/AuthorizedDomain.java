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
 * Entity for authorized callback domains.
 * Only callbacks to authorized domains are allowed.
 * This entity is mapped to the "authorized_domains" table in the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("authorized_domains")
public class AuthorizedDomain {

    /**
     * Unique identifier for the authorized domain.
     */
    @Id
    private UUID id;

    /**
     * Domain name (e.g., "api.example.com", "*.example.com" for wildcard, "localhost:8080").
     */
    private String domain;

    /**
     * Organization or owner of this domain.
     */
    private String organization;

    /**
     * Contact email for this domain.
     */
    @Column("contact_email")
    private String contactEmail;

    /**
     * Whether this domain is verified (e.g., via DNS TXT record or challenge).
     */
    private Boolean verified;

    /**
     * Verification method used (DNS, HTTP, EMAIL).
     */
    @Column("verification_method")
    private String verificationMethod;

    /**
     * Verification token for domain verification.
     */
    @Column("verification_token")
    private String verificationToken;

    /**
     * When the domain was verified.
     */
    @Column("verified_at")
    private Instant verifiedAt;

    /**
     * Whether this domain is active and can receive callbacks.
     */
    private Boolean active;

    /**
     * Allowed URL paths (patterns). Null/empty means all paths allowed.
     * Stored as TEXT[] in the database.
     */
    @Column("allowed_paths")
    private String[] allowedPaths;

    /**
     * Maximum callbacks per minute for this domain (rate limiting).
     */
    @Column("max_callbacks_per_minute")
    private Integer maxCallbacksPerMinute;

    /**
     * IP whitelist for this domain (optional additional security).
     * Stored as TEXT[] in the database.
     */
    @Column("ip_whitelist")
    private String[] ipWhitelist;

    /**
     * Whether to require HTTPS for this domain.
     */
    @Column("require_https")
    private Boolean requireHttps;

    /**
     * Tenant ID for multi-tenancy.
     */
    @Column("tenant_id")
    private String tenantId;

    /**
     * Additional notes about this domain.
     */
    private String notes;

    /**
     * Metadata for the domain.
     * Stored as JSON TEXT in the database.
     */
    private String metadata;

    /**
     * Domain expiration date (for temporary authorizations).
     */
    @Column("expires_at")
    private Instant expiresAt;

    /**
     * Last time a callback was sent to this domain.
     */
    @Column("last_callback_at")
    private Instant lastCallbackAt;

    /**
     * Total callbacks sent to this domain.
     */
    @Column("total_callbacks")
    private Long totalCallbacks;

    /**
     * Total failed callbacks to this domain.
     */
    @Column("total_failed")
    private Long totalFailed;

    /**
     * Creation timestamp.
     */
    @Column("created_at")
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    @Column("updated_at")
    private Instant updatedAt;

    /**
     * User who authorized the domain.
     */
    @Column("authorized_by")
    private String authorizedBy;
}
