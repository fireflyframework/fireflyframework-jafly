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


package org.fireflyframework.security.idp.adapter.service;

import org.fireflyframework.security.idp.dtos.*;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IdpAdminService {

    Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request);

    Mono<Void> changePassword(ChangePasswordRequest request);

    Mono<Void> resetPassword(String username);

    Mono<String> assignTemporaryPassword(String userId);

    Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username);

    Mono<Void> mfaVerify(MfaVerifyRequest request);

    Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId);

    Mono<Void> revokeSession(String sessionId);

    Mono<ResponseEntity<List<String>>> getRoles(String userId);

    // New methods (stubs for now)
    Mono<Void> deleteUser(String userId);

    Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request);

    Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request);

    Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request);

    Mono<Void> assignRolesToUser(AssignRolesRequest request);

    Mono<Void> removeRolesFromUser(AssignRolesRequest request);

    Mono<ResponseEntity<List<String>>> listRoles();

    Mono<Void> deleteRole(String roleName);

    Mono<Void> updateRole(UpdateRoleRequest request);
}
