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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, product-agnostic backoffice context container for internal portal/backoffice requests.
 *
 * <p>This context carries information about the backoffice operator performing the action and,
 * optionally, about a subject being impersonated. It deliberately carries <strong>no</strong>
 * product-domain concepts (no party, contract, or product). The impersonated principal is a generic
 * {@code String} subject so the backoffice platform stays neutral across products.</p>
 *
 * <ul>
 *   <li><strong>backofficeUserId</strong>: the authenticated backoffice/admin operator performing the action</li>
 *   <li><strong>impersonatedSubject</strong>: the generic subject being impersonated (may be {@code null})</li>
 *   <li><strong>tenantId</strong>: the generic tenant discriminator this context belongs to</li>
 *   <li><strong>backofficeRoles / backofficePermissions</strong>: authorization data for the operator</li>
 *   <li><strong>impersonatedSubjectRoles / impersonatedSubjectPermissions</strong>: informational authorization
 *       data for the impersonated subject</li>
 * </ul>
 *
 * <p>Validated identity is sourced from the {@code fireflyframework-security} platform
 * ({@code SecurityContextPort} / {@code SecurityPrincipal}). Impersonation, when present, is read from
 * an optional {@code X-Impersonate-Subject} request header.</p>
 *
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
@With
public class BackofficeContext {

    /**
     * Unique identifier of the backoffice operator (admin/support) performing the action.
     * Derived from the validated security principal's subject when it is a UUID; otherwise {@code null}
     * (the raw subject is then kept under the {@code "backofficeUserSubject"} attribute).
     */
    UUID backofficeUserId;

    /**
     * Generic subject being impersonated by the backoffice operator.
     * {@code null} when no impersonation is in effect. Read from the optional
     * {@code X-Impersonate-Subject} header.
     */
    String impersonatedSubject;

    /**
     * The generic tenant/organization this context belongs to.
     * Parsed from the security principal's tenant identifier when it is a UUID; otherwise {@code null}.
     */
    UUID tenantId;

    /**
     * Roles that the backoffice operator holds.
     * Used for authorization decisions. Sourced from the principal's authorities.
     */
    Set<String> backofficeRoles;

    /**
     * Permissions that the backoffice operator holds.
     * Used for fine-grained authorization. Sourced from the principal's scopes.
     */
    Set<String> backofficePermissions;

    /**
     * Roles that the impersonated subject holds.
     * Informational - the backoffice operator's permissions take precedence.
     */
    Set<String> impersonatedSubjectRoles;

    /**
     * Permissions that the impersonated subject holds.
     * Informational - the backoffice operator's permissions take precedence.
     */
    Set<String> impersonatedSubjectPermissions;

    /**
     * Reason for impersonation (optional, for audit purposes).
     * Example: "Support ticket #12345", "Administrative review".
     */
    String impersonationReason;

    /**
     * IP address of the backoffice operator (for audit trail).
     */
    String backofficeUserIpAddress;

    /**
     * Additional context-specific attributes.
     * Can be used to store generic context information (e.g. the raw backoffice user subject).
     */
    Map<String, Object> attributes;

    /**
     * Checks if the backoffice operator has a specific role.
     *
     * @param role the role to check
     * @return true if the role is present
     */
    public boolean hasBackofficeRole(String role) {
        return backofficeRoles != null && backofficeRoles.contains(role);
    }

    /**
     * Checks if the backoffice operator has any of the specified roles.
     *
     * @param roles the roles to check
     * @return true if any of the roles are present
     */
    public boolean hasBackofficeAnyRole(String... roles) {
        if (this.backofficeRoles == null || roles == null) {
            return false;
        }
        for (String role : roles) {
            if (this.backofficeRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the backoffice operator has a specific permission.
     *
     * @param permission the permission to check
     * @return true if the permission is present
     */
    public boolean hasBackofficePermission(String permission) {
        return backofficePermissions != null && backofficePermissions.contains(permission);
    }

    /**
     * Checks if the impersonated subject has a specific role (informational).
     *
     * @param role the role to check
     * @return true if the role is present for the impersonated subject
     */
    public boolean impersonatedSubjectHasRole(String role) {
        return impersonatedSubjectRoles != null && impersonatedSubjectRoles.contains(role);
    }

    /**
     * Indicates whether this context represents an active impersonation.
     *
     * @return true if an impersonated subject is set
     */
    public boolean isImpersonating() {
        return impersonatedSubject != null;
    }

    /**
     * Gets an attribute from the context.
     *
     * @param key the attribute key
     * @param <T> the expected type
     * @return the attribute value or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return attributes != null ? (T) attributes.get(key) : null;
    }
}
