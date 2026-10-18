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
package org.fireflyframework.security.idp.web;

import jakarta.validation.Valid;
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/idp")
@RequiredArgsConstructor
public class IdpController {

    private final IdpAdapter idpAdapter;

    @PostMapping("/login")
    public Mono<ResponseEntity<TokenResponse>> login(@RequestBody LoginRequest request) {
        return idpAdapter.login(request);
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refresh(@RequestBody RefreshRequest request) {
        return idpAdapter.refresh(request);
    }

    @PostMapping("/logout")
    public Mono<Void> logout(@RequestBody LogoutRequest request) {
        return idpAdapter.logout(request);
    }

    @GetMapping("/introspect")
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(@RequestHeader("Authorization") String accessToken) {
        return idpAdapter.introspect(accessToken);
    }

    @GetMapping("/userinfo")
    public Mono<ResponseEntity<UserInfoResponse>> getUserInfo(@RequestHeader("Authorization") String accessToken) {
        return idpAdapter.getUserInfo(accessToken);
    }

    @PostMapping("/revoke-refresh-token")
    public Mono<Void> revokeRefreshToken(@RequestParam("refreshToken") String refreshToken) {
        return idpAdapter.revokeRefreshToken(refreshToken);
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<CreateUserResponse>> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        return idpAdapter.registerUser(request);
    }

    @PostMapping("/admin/users")
    public Mono<ResponseEntity<CreateUserResponse>> createUser(@RequestBody CreateUserRequest request) {
        return idpAdapter.createUser(request);
    }

    @PostMapping("/admin/password")
    public Mono<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        return idpAdapter.changePassword(request);
    }

    @PostMapping("/admin/password/reset")
    public Mono<Void> resetPassword(@RequestParam("username") String username) {
        return idpAdapter.resetPassword(username);
    }

    @PostMapping("/admin/mfa/challenge")
    public Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(@RequestParam("username") String username) {
        return idpAdapter.mfaChallenge(username);
    }

    @PostMapping("/admin/mfa/verify")
    public Mono<Void> mfaVerify(@RequestBody MfaVerifyRequest request) {
        return idpAdapter.mfaVerify(request);
    }

    @GetMapping("/admin/users/{userId}/sessions")
    public Mono<ResponseEntity<List<SessionInfo>>> listSessions(@PathVariable("userId") String userId) {
        return idpAdapter.listSessions(userId);
    }

    @DeleteMapping("/admin/sessions/{sessionId}")
    public Mono<Void> revokeSession(@PathVariable("sessionId") String sessionId) {
        return idpAdapter.revokeSession(sessionId);
    }

    @GetMapping("/admin/users/{userId}/roles")
    public Mono<ResponseEntity<List<String>>> getRoles(@PathVariable("userId") String userId) {
        return idpAdapter.getRoles(userId);
    }

    @DeleteMapping("/admin/users/{userId}")
    public Mono<Void> deleteUser(@PathVariable("userId") String userId) {
        return idpAdapter.deleteUser(userId);
    }

    @PutMapping("/admin/users")
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(@RequestBody UpdateUserRequest request) {
        return idpAdapter.updateUser(request);
    }

    @PostMapping("/admin/roles")
    public Mono<ResponseEntity<CreateRolesResponse>> createRoles(@RequestBody CreateRolesRequest request) {
        return idpAdapter.createRoles(request);
    }

    @PostMapping("/admin/scopes")
    public Mono<ResponseEntity<CreateScopeResponse>> createScope(@RequestBody CreateScopeRequest request) {
        return idpAdapter.createScope(request);
    }

    @PostMapping("/admin/users/roles/assign")
    public Mono<Void> assignRolesToUser(@RequestBody AssignRolesRequest request) {
        return idpAdapter.assignRolesToUser(request);
    }

    @PostMapping("/admin/users/roles/remove")
    public Mono<Void> removeRolesFromUser(@RequestBody AssignRolesRequest request) {
        return idpAdapter.removeRolesFromUser(request);
    }
}
