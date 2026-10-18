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
package org.fireflyframework.security.idp.azuread.service;

import org.fireflyframework.security.idp.dtos.*;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Admin service interface for Azure AD user management operations.
 *
 * <p>Defines the administrative operations for managing users, roles, and sessions
 * in Azure AD (both Entra ID and B2C modes). Implementations translate these
 * operations into Microsoft Graph SDK calls.
 */
public interface AzureAdAdminService {

    /**
     * Create a new user in Azure AD.
     */
    Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request);

    /**
     * Change a user's password.
     */
    Mono<Void> changePassword(ChangePasswordRequest request);

    /**
     * Reset a user's password (admin-initiated).
     */
    Mono<Void> resetPassword(String username);

    /**
     * Initiate an MFA challenge for a user.
     */
    Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username);

    /**
     * Verify an MFA challenge code.
     */
    Mono<Void> mfaVerify(MfaVerifyRequest request);

    /**
     * List active sessions for a user.
     */
    Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId);

    /**
     * Revoke a specific session.
     */
    Mono<Void> revokeSession(String sessionId);

    /**
     * Get roles assigned to a user.
     */
    Mono<ResponseEntity<List<String>>> getRoles(String userId);

    /**
     * Delete a user from Azure AD.
     */
    Mono<Void> deleteUser(String userId);

    /**
     * Update an existing user's attributes.
     */
    Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request);

    /**
     * Create one or more roles (security groups) in Azure AD.
     */
    Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request);

    /**
     * Create a new scope (placeholder — scopes are configured in Azure AD app registration).
     */
    Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request);

    /**
     * Assign roles to a user by adding them to security groups.
     */
    Mono<Void> assignRolesToUser(AssignRolesRequest request);

    /**
     * Remove roles from a user by removing them from security groups.
     */
    Mono<Void> removeRolesFromUser(AssignRolesRequest request);
}
