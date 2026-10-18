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
package org.fireflyframework.security.idp.azuread.adapter;

import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.azuread.service.AzureAdAdminService;
import org.fireflyframework.security.idp.azuread.service.AzureAdAuthService;
import org.fireflyframework.security.idp.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Azure AD implementation of the Firefly IDP Adapter.
 *
 * <p>This adapter provides a unified interface to Azure AD (Entra ID and B2C),
 * implementing all authentication and user management operations defined in the
 * IdpAdapter interface.
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>User authentication (ROPC via MSAL4J)</li>
 *   <li>Token refresh and revocation</li>
 *   <li>User management (CRUD operations via Graph SDK)</li>
 *   <li>Role/Group management</li>
 *   <li>Session management</li>
 *   <li>Password operations</li>
 * </ul>
 *
 * @see IdpAdapter
 * @see AzureAdAuthService
 * @see AzureAdAdminService
 */
@RequiredArgsConstructor
@Slf4j
public class AzureAdIdpAdapter implements IdpAdapter {

    private final AzureAdAuthService authService;
    private final AzureAdAdminService adminService;

    @Override
    public Mono<ResponseEntity<TokenResponse>> login(LoginRequest request) {
        log.debug("Delegating login to AzureAdAuthService");
        return authService.login(request);
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> refresh(RefreshRequest request) {
        log.debug("Delegating token refresh to AzureAdAuthService");
        return authService.refresh(request);
    }

    @Override
    public Mono<Void> logout(LogoutRequest request) {
        log.debug("Delegating logout to AzureAdAuthService");
        return authService.logout(request);
    }

    @Override
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        log.debug("Delegating token introspection to AzureAdAuthService");
        return authService.introspect(accessToken);
    }

    @Override
    public Mono<ResponseEntity<UserInfoResponse>> getUserInfo(String accessToken) {
        log.debug("Delegating getUserInfo to AzureAdAuthService");
        return authService.getUserInfo(accessToken);
    }

    @Override
    public Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request) {
        log.debug("Delegating createUser to AzureAdAdminService");
        return adminService.createUser(request);
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request) {
        log.debug("Delegating changePassword to AzureAdAdminService");
        return adminService.changePassword(request);
    }

    @Override
    public Mono<Void> resetPassword(String username) {
        log.debug("Delegating resetPassword to AzureAdAdminService");
        return adminService.resetPassword(username);
    }

    @Override
    public Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username) {
        log.debug("Delegating mfaChallenge to AzureAdAdminService");
        return adminService.mfaChallenge(username);
    }

    @Override
    public Mono<Void> mfaVerify(MfaVerifyRequest request) {
        log.debug("Delegating mfaVerify to AzureAdAdminService");
        return adminService.mfaVerify(request);
    }

    @Override
    public Mono<Void> revokeRefreshToken(String refreshToken) {
        log.debug("Delegating revokeRefreshToken to AzureAdAuthService");
        return authService.revokeRefreshToken(refreshToken);
    }

    @Override
    public Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId) {
        log.debug("Delegating listSessions to AzureAdAdminService");
        return adminService.listSessions(userId);
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        log.debug("Delegating revokeSession to AzureAdAdminService");
        return adminService.revokeSession(sessionId);
    }

    @Override
    public Mono<ResponseEntity<List<String>>> getRoles(String userId) {
        log.debug("Delegating getRoles to AzureAdAdminService");
        return adminService.getRoles(userId);
    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        log.debug("Delegating deleteUser to AzureAdAdminService");
        return adminService.deleteUser(userId);
    }

    @Override
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request) {
        log.debug("Delegating updateUser to AzureAdAdminService");
        return adminService.updateUser(request);
    }

    @Override
    public Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request) {
        log.debug("Delegating createRoles to AzureAdAdminService");
        return adminService.createRoles(request);
    }

    @Override
    public Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request) {
        log.debug("Delegating createScope to AzureAdAdminService");
        return adminService.createScope(request);
    }

    @Override
    public Mono<Void> assignRolesToUser(AssignRolesRequest request) {
        log.debug("Delegating assignRolesToUser to AzureAdAdminService");
        return adminService.assignRolesToUser(request);
    }

    @Override
    public Mono<Void> removeRolesFromUser(AssignRolesRequest request) {
        log.debug("Delegating removeRolesFromUser to AzureAdAdminService");
        return adminService.removeRolesFromUser(request);
    }
}
