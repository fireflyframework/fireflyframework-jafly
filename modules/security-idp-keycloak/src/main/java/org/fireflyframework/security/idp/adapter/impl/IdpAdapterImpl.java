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


package org.fireflyframework.security.idp.adapter.impl;

import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.adapter.service.IdpAdminService;
import org.fireflyframework.security.idp.adapter.service.IdpUserService;
import org.fireflyframework.security.idp.dtos.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

public class IdpAdapterImpl implements IdpAdapter {

    private final IdpUserService userService;
    private final IdpAdminService adminService;

    @Autowired
    public IdpAdapterImpl(IdpUserService userService, IdpAdminService adminService) {
        this.userService = userService;
        this.adminService = adminService;
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> login(LoginRequest request) {
        return userService.login(request);
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> refresh(RefreshRequest request) {
        return userService.refresh(request);
    }

    @Override
    public Mono<Void> logout(LogoutRequest request) {
        return userService.logout(request);
    }

    @Override
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        return userService.introspect(accessToken);
    }

    @Override
    public Mono<ResponseEntity<UserInfoResponse>> getUserInfo(String accessToken) {
        return userService.getUserInfo(accessToken);
    }

    @Override
    public Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request) {
        return adminService.createUser(request);
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request) {
        return adminService.changePassword(request);
    }

    @Override
    public Mono<Void> resetPassword(String username) {
        return adminService.resetPassword(username);
    }

    @Override
    public Mono<String> assignTemporaryPassword(String userId) {
        return adminService.assignTemporaryPassword(userId);
    }

    @Override
    public Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username) {
        return adminService.mfaChallenge(username);
    }

    @Override
    public Mono<Void> mfaVerify(MfaVerifyRequest request) {
        return adminService.mfaVerify(request);
    }

    @Override
    public Mono<Void> revokeRefreshToken(String refreshToken) {
        return userService.revokeRefreshToken(refreshToken);
    }

    @Override
    public Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId) {
        return adminService.listSessions(userId);
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        return adminService.revokeSession(sessionId);
    }

    @Override
    public Mono<ResponseEntity<List<String>>> getRoles(String userId) {
        return adminService.getRoles(userId);
    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        return adminService.deleteUser(userId);
    }

    @Override
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request) {
        return adminService.updateUser(request);
    }

    @Override
    public Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request) {
        return adminService.createRoles(request);
    }

    @Override
    public Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request) {
        return adminService.createScope(request);
    }

    @Override
    public Mono<Void> assignRolesToUser(AssignRolesRequest request) {
        return adminService.assignRolesToUser(request);
    }

    @Override
    public Mono<Void> removeRolesFromUser(AssignRolesRequest request) {
        return adminService.removeRolesFromUser(request);
    }

    @Override
    public Mono<ResponseEntity<List<String>>> listRoles() {
        return adminService.listRoles();
    }

    @Override
    public Mono<Void> deleteRole(String roleName) {
        return adminService.deleteRole(roleName);
    }

    @Override
    public Mono<Void> updateRole(UpdateRoleRequest request) {
        return adminService.updateRole(request);
    }
}
