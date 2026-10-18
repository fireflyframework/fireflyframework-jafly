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

package org.fireflyframework.security.idp.internaldb.adapter;

import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.dtos.*;
import org.fireflyframework.security.idp.internaldb.repository.SessionRepository;
import org.fireflyframework.security.idp.internaldb.service.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Internal Database implementation of the Firefly IDP Adapter.
 * 
 * <p>This adapter provides a self-contained identity provider using:
 * <ul>
 *   <li>Internal PostgreSQL database for user storage</li>
 *   <li>R2DBC for reactive database access</li>
 *   <li>JWT tokens for authentication</li>
 *   <li>BCrypt for password hashing</li>
 * </ul>
 * 
 * <p>This implementation is ideal for:
 * <ul>
 *   <li>Development and testing environments</li>
 *   <li>Simple authentication requirements</li>
 *   <li>Environments without external IDP dependencies</li>
 *   <li>Microservices needing standalone authentication</li>
 * </ul>
 * 
 * @see IdpAdapter
 * @see AuthenticationService
 * @see UserManagementService
 * @see RoleService
 */
@RequiredArgsConstructor
@Slf4j
public class InternalDbIdpAdapter implements IdpAdapter {

    private final AuthenticationService authenticationService;
    private final UserManagementService userManagementService;
    private final RoleService roleService;
    private final SessionRepository sessionRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;

    @Override
    public Mono<ResponseEntity<TokenResponse>> login(LoginRequest request) {
        log.debug("Login request for user: {}", request.getUsername());

        return authenticationService.authenticate(request.getUsername(), request.getPassword())
                .map(tokenResponse -> ResponseEntity.ok(tokenResponse))
                .onErrorResume(e -> {
                    log.error("Login failed for user {}: {}", request.getUsername(), e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(TokenResponse.builder().build()));
                });
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> refresh(RefreshRequest request) {
        log.debug("Refresh token request");

        return authenticationService.refreshToken(request.getRefreshToken())
                .map(tokenResponse -> ResponseEntity.ok(tokenResponse))
                .onErrorResume(e -> {
                    log.error("Token refresh failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(TokenResponse.builder().build()));
                });
    }

    @Override
    public Mono<Void> logout(LogoutRequest request) {
        log.debug("Logout request");

        return authenticationService.logout(request.getAccessToken(), request.getRefreshToken())
                .onErrorResume(e -> {
                    log.error("Logout failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        log.debug("Token introspection request");

        return authenticationService.validateAccessToken(accessToken)
                .flatMap(isValid -> {
                    if (!isValid) {
                        return Mono.just(ResponseEntity.ok(IntrospectionResponse.builder()
                                .active(false)
                                .build()));
                    }

                    try {
                        Claims claims = jwtTokenService.parseToken(accessToken);
                        String userId = claims.getSubject();
                        String username = claims.get("username", String.class);
                        
                        @SuppressWarnings("unchecked")
                        List<String> roles = claims.get("roles", List.class);

                        IntrospectionResponse response = IntrospectionResponse.builder()
                                .active(true)
                                .sub(userId)
                                .username(username)
                                .scope(roles != null ? String.join(" ", roles) : "")
                                .exp(claims.getExpiration().getTime() / 1000)
                                .iat(claims.getIssuedAt().getTime() / 1000)
                                .build();

                        return Mono.just(ResponseEntity.ok(response));

                    } catch (Exception e) {
                        log.error("Token introspection failed: {}", e.getMessage());
                        return Mono.just(ResponseEntity.ok(IntrospectionResponse.builder()
                                .active(false)
                                .build()));
                    }
                });
    }

    @Override
    public Mono<ResponseEntity<UserInfoResponse>> getUserInfo(String accessToken) {
        log.debug("Get user info request");

        return authenticationService.validateAccessToken(accessToken)
                .flatMap(isValid -> {
                    if (!isValid) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(UserInfoResponse.builder().build()));
                    }

                    try {
                        Claims claims = jwtTokenService.parseToken(accessToken);
                        UUID userId = UUID.fromString(claims.getSubject());

                        return userManagementService.getUserById(userId)
                                .flatMap(user -> roleService.getUserRoles(userId)
                                        .collectList()
                                        .map(roles -> {
                                            // Build full name from first and last name
                                            String fullName = (user.getFirstName() != null && user.getLastName() != null)
                                                    ? user.getFirstName() + " " + user.getLastName()
                                                    : user.getUsername();
                                            
                                            UserInfoResponse response = UserInfoResponse.builder()
                                                    .sub(user.getId().toString())
                                                    .preferredUsername(user.getUsername())  // DTO uses preferredUsername
                                                    .email(user.getEmail())
                                                    .givenName(user.getFirstName())  // DTO uses givenName
                                                    .familyName(user.getLastName())  // DTO uses familyName
                                                    .name(fullName)  // Full name for display
                                                    .build();
                                            return ResponseEntity.ok(response);
                                        }));

                    } catch (Exception e) {
                        log.error("Get user info failed: {}", e.getMessage());
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(UserInfoResponse.builder().build()));
                    }
                });
    }

    @Override
    public Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request) {
        log.debug("Create user request: {}", request.getUsername());

        return userManagementService.createUser(request)
                .map(user -> {
                    CreateUserResponse response = CreateUserResponse.builder()
                            .id(user.getId().toString())  // DTO uses 'id' not 'userId'
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .createdAt(user.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                            .build();
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .onErrorResume(e -> {
                    log.error("Create user failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(CreateUserResponse.builder().build()));
                });
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request) {
        log.debug("Change password request for user: {}", request.getUserId());

        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            return Mono.error(new IllegalArgumentException("User ID is required"));
        }

        UUID userId = UUID.fromString(request.getUserId());
        return userManagementService.changePassword(userId, request.getOldPassword(), request.getNewPassword())
                .onErrorResume(e -> {
                    log.error("Change password failed: {}", e.getMessage());
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<Void> resetPassword(String username) {
        log.debug("Reset password request for user: {}", username);

        return passwordResetService.initiateReset(username)
                .doOnSuccess(v -> log.info("Password reset initiated for user: {}", username))
                .onErrorResume(e -> {
                    log.error("Reset password failed for user {}: {}", username, e.getMessage());
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username) {
        log.debug("MFA challenge request for user: {}", username);

        return mfaService.isMfaEnabled(username)
                .flatMap(enabled -> {
                    if (!enabled) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(MfaChallengeResponse.builder()
                                        .deliveryMethod("NONE")
                                        .build()));
                    }

                    MfaChallengeResponse response = MfaChallengeResponse.builder()
                            .challengeId(UUID.randomUUID().toString())
                            .deliveryMethod("TOTP")
                            .destination("Authenticator App")
                            .expiresAt(java.time.Instant.now().plusSeconds(300))
                            .build();

                    return Mono.just(ResponseEntity.ok(response));
                });
    }

    @Override
    public Mono<Void> mfaVerify(MfaVerifyRequest request) {
        log.debug("MFA verify request for user: {}", request.getUserId());

        if (request.getUserId() == null || request.getCode() == null) {
            return Mono.error(new IllegalArgumentException("User ID and code are required"));
        }

        UUID userId = UUID.fromString(request.getUserId());
        return mfaService.verifyTotp(userId, request.getCode())
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new RuntimeException("Invalid MFA code"));
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> revokeRefreshToken(String refreshToken) {
        log.debug("Revoke refresh token request");

        return authenticationService.revokeRefreshToken(refreshToken)
                .onErrorResume(e -> {
                    log.error("Revoke refresh token failed: {}", e.getMessage());
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId) {
        log.debug("List sessions request for user: {}", userId);

        if (userId == null || userId.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(List.of()));
        }

        UUID userUuid = UUID.fromString(userId);
        
        return sessionRepository.findByUserIdAndRevoked(userUuid, false)
                .map(session -> SessionInfo.builder()
                        .sessionId(session.getId().toString())
                        .userId(userUuid.toString())
                        .createdAt(session.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())  // Convert to Instant
                        .expiresAt(session.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                        .ipAddress(session.getIpAddress())
                        .userAgent(session.getUserAgent())
                        .build())
                .collectList()
                .map(sessions -> ResponseEntity.ok(sessions))
                .onErrorResume(e -> {
                    log.error("List sessions failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(List.of()));
                });
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        log.debug("Revoke session request: {}", sessionId);

        if (sessionId == null || sessionId.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Session ID is required"));
        }

        UUID sessionUuid = UUID.fromString(sessionId);
        
        return sessionRepository.findById(sessionUuid)
                .flatMap(session -> {
                    session.setRevoked(true);
                    return sessionRepository.save(session).then();
                })
                .onErrorResume(e -> {
                    log.error("Revoke session failed: {}", e.getMessage());
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<ResponseEntity<List<String>>> getRoles(String userId) {
        log.debug("Get roles request for user: {}", userId);

        if (userId == null || userId.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(List.of()));
        }

        UUID userUuid = UUID.fromString(userId);
        
        return roleService.getUserRoles(userUuid)
                .collectList()
                .map(roles -> ResponseEntity.ok(roles))
                .onErrorResume(e -> {
                    log.error("Get roles failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(List.of()));
                });
    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        log.debug("Delete user request: {}", userId);

        if (userId == null || userId.isEmpty()) {
            return Mono.error(new IllegalArgumentException("User ID is required"));
        }

        UUID userUuid = UUID.fromString(userId);
        
        return userManagementService.deleteUser(userUuid)
                .onErrorResume(e -> {
                    log.error("Delete user failed: {}", e.getMessage());
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request) {
        log.debug("Update user request: {}", request.getUserId());

        return userManagementService.updateUser(request)
                .map(user -> {
                    UpdateUserResponse response = UpdateUserResponse.builder()
                            .id(user.getId().toString())  // DTO uses 'id' not 'userId'
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .updatedAt(user.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                            .build();
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Update user failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(UpdateUserResponse.builder().build()));
                });
    }

    @Override
    public Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request) {
        log.debug("Create roles request: {}", request.getRoleNames());

        return roleService.createRoles(request.getRoleNames())
                .map(role -> role.getName())
                .collectList()
                .map(createdRoles -> {
                    CreateRolesResponse response = CreateRolesResponse.builder()
                            .createdRoleNames(createdRoles)  // DTO uses 'createdRoleNames'
                            .build();
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .onErrorResume(e -> {
                    log.error("Create roles failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(CreateRolesResponse.builder().build()));
                });
    }

    @Override
    public Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request) {
        log.warn("InternalDbIdpAdapter does not support scopes natively; scope '{}' will be stored as a role. " +
                "Consider using KeycloakIdpAdapter for full OAuth2 scope support.", request.getName());

        return roleService.createRole(request.getName(), request.getDescription())
                .map(role -> {
                    CreateScopeResponse response = CreateScopeResponse.builder()
                            .id(role.getId().toString())  // DTO has id field
                            .name(role.getName())  // DTO uses 'name' not 'scopeName'
                            .createdAt(role.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                            .build();
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .onErrorResume(e -> {
                    log.error("Create scope failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(CreateScopeResponse.builder().build()));
                });
    }

    @Override
    public Mono<Void> assignRolesToUser(AssignRolesRequest request) {
        log.debug("Assign roles to user request: {}", request.getUserId());

        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            return Mono.error(new IllegalArgumentException("User ID is required"));
        }

        UUID userId = UUID.fromString(request.getUserId());
        
        return roleService.assignRolesToUser(userId, request.getRoleNames())
                .onErrorResume(e -> {
                    log.error("Assign roles failed: {}", e.getMessage());
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<Void> removeRolesFromUser(AssignRolesRequest request) {
        log.debug("Remove roles from user request: {}", request.getUserId());

        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            return Mono.error(new IllegalArgumentException("User ID is required"));
        }

        UUID userId = UUID.fromString(request.getUserId());
        
        return roleService.removeRolesFromUser(userId, request.getRoleNames())
                .onErrorResume(e -> {
                    log.error("Remove roles failed: {}", e.getMessage());
                    return Mono.error(e);
                });
    }
}
