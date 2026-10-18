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

package org.fireflyframework.common.backoffice.context;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, product-agnostic security context for backoffice requests.
 *
 * <p>Captures security-related information for a backoffice endpoint: the required roles/permissions,
 * the authorization outcome, and an optional impersonation audit trail. It carries <strong>no</strong>
 * product-domain concepts; the impersonated principal is a generic {@code String} subject.</p>
 *
 * <p>Security context can be configured in two ways:</p>
 * <ul>
 *   <li>Declarative: Using {@code @BackofficeSecure} annotation on endpoints/controllers</li>
 *   <li>Programmatic: Explicit endpoint-role mapping registration</li>
 * </ul>
 *
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
@With
public class BackofficeSecurityContext {

    /**
     * The endpoint being accessed (e.g., "/backoffice/api/v1/subjects/{subject}/accounts")
     */
    String endpoint;

    /**
     * The HTTP method being used (GET, POST, PUT, DELETE, etc.)
     */
    String httpMethod;

    /**
     * Backoffice roles required to access this endpoint.
     * Examples: "admin", "support", "analyst", "auditor"
     */
    Set<String> requiredBackofficeRoles;

    /**
     * Backoffice permissions required to access this endpoint.
     * Examples: "subjects:read", "accounts:write", "transactions:delete"
     */
    Set<String> requiredBackofficePermissions;

    /**
     * Whether impersonation is allowed for this endpoint.
     */
    @Builder.Default
    boolean impersonationAllowed = true;

    /**
     * Whether impersonation is required for this endpoint.
     * If true, the {@code X-Impersonate-Subject} header must be present.
     */
    @Builder.Default
    boolean impersonationRequired = false;

    /**
     * Whether authorization was successful.
     */
    boolean authorized;

    /**
     * Reason for authorization failure (if applicable).
     */
    String authorizationFailureReason;

    /**
     * The backoffice operator ID that was authenticated.
     */
    UUID backofficeUserId;

    /**
     * The generic subject being impersonated (may be {@code null}).
     */
    String impersonatedSubject;

    /**
     * Whether the backoffice operator is authorized to impersonate this subject.
     */
    boolean impersonationAuthorized;

    /**
     * Reason if impersonation was denied.
     */
    String impersonationDenialReason;

    /**
     * Timestamp when impersonation was authorized.
     */
    Instant impersonationAuthorizedAt;

    /**
     * Source of the security configuration.
     */
    SecurityConfigSource configSource;

    /**
     * Additional security attributes.
     */
    Map<String, Object> securityAttributes;

    /**
     * Whether this endpoint requires authentication.
     */
    @Builder.Default
    boolean requiresAuthentication = true;

    /**
     * Whether this endpoint allows anonymous access (typically false for backoffice).
     */
    @Builder.Default
    boolean allowAnonymous = false;

    /**
     * Audit trail information for impersonation.
     */
    ImpersonationAuditTrail auditTrail;

    /**
     * Checks if the security context requires any backoffice roles.
     *
     * @return true if roles are required
     */
    public boolean hasRequiredBackofficeRoles() {
        return requiredBackofficeRoles != null && !requiredBackofficeRoles.isEmpty();
    }

    /**
     * Checks if the security context requires any backoffice permissions.
     *
     * @return true if permissions are required
     */
    public boolean hasRequiredBackofficePermissions() {
        return requiredBackofficePermissions != null && !requiredBackofficePermissions.isEmpty();
    }

    /**
     * Checks if a specific backoffice role is required.
     *
     * @param role the role to check
     * @return true if the role is required
     */
    public boolean requiresBackofficeRole(String role) {
        return requiredBackofficeRoles != null && requiredBackofficeRoles.contains(role);
    }

    /**
     * Checks if a specific backoffice permission is required.
     *
     * @param permission the permission to check
     * @return true if the permission is required
     */
    public boolean requiresBackofficePermission(String permission) {
        return requiredBackofficePermissions != null && requiredBackofficePermissions.contains(permission);
    }

    /**
     * Gets a security attribute.
     *
     * @param key the attribute key
     * @param <T> the expected type
     * @return the attribute value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getSecurityAttribute(String key) {
        return securityAttributes != null ? (T) securityAttributes.get(key) : null;
    }

    /**
     * Checks if impersonation is both allowed and successfully authorized.
     *
     * @return true if impersonation is valid
     */
    public boolean isImpersonationValid() {
        return impersonationAllowed && impersonationAuthorized && impersonatedSubject != null;
    }

    /**
     * Source of security configuration.
     */
    public enum SecurityConfigSource {
        /**
         * Security configuration from {@code @BackofficeSecure} annotation.
         */
        ANNOTATION,

        /**
         * Security configuration from explicit endpoint-role mapping.
         */
        EXPLICIT_MAP,

        /**
         * Security configuration from default/fallback rules.
         */
        DEFAULT
    }

    /**
     * Audit trail for subject impersonation.
     */
    @Value
    @Builder(toBuilder = true)
    @With
    public static class ImpersonationAuditTrail {

        /**
         * Backoffice operator who initiated impersonation.
         */
        UUID backofficeUserId;

        /**
         * Generic subject being impersonated.
         */
        String impersonatedSubject;

        /**
         * Timestamp when impersonation started.
         */
        Instant startedAt;

        /**
         * IP address of backoffice operator.
         */
        String ipAddress;

        /**
         * User agent of backoffice operator.
         */
        String userAgent;

        /**
         * Reason for impersonation (e.g., support ticket number).
         */
        String reason;

        /**
         * Endpoint being accessed.
         */
        String endpoint;

        /**
         * HTTP method.
         */
        String httpMethod;

        /**
         * Request ID for tracing.
         */
        String requestId;

        /**
         * Additional audit metadata.
         */
        Map<String, Object> metadata;

        /**
         * Gets audit metadata.
         *
         * @param key the metadata key
         * @param <T> the expected type
         * @return the metadata value or null if not found
         */
        @SuppressWarnings("unchecked")
        public <T> T getMetadata(String key) {
            return metadata != null ? (T) metadata.get(key) : null;
        }
    }
}
