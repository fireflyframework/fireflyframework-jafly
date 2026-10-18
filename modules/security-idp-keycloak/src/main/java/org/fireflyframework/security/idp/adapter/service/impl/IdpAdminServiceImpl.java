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


package org.fireflyframework.security.idp.adapter.service.impl;

import org.fireflyframework.security.idp.adapter.exception.KeycloakExceptionHandler;
import org.fireflyframework.security.idp.adapter.keycloak.KeycloakClientFactory;
import org.fireflyframework.security.idp.adapter.service.IdpAdminService;
import org.fireflyframework.security.idp.adapter.service.TokenService;
import org.fireflyframework.security.idp.dtos.*;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.*;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class IdpAdminServiceImpl implements IdpAdminService {

    private final KeycloakClientFactory keycloakFactory;
    private final TokenService tokenService;

    public IdpAdminServiceImpl(KeycloakClientFactory keycloakFactory, TokenService tokenService) {
        this.keycloakFactory = keycloakFactory;
        this.tokenService = tokenService;
    }

    @Override
    public Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request) {
        return Mono.fromCallable(() -> performCreateUser(request))
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error creating user: {}", request.getUsername(), throwable);
                    return Mono.just(KeycloakExceptionHandler.handleException(throwable));
                });
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request) {
        return Mono.defer(() -> {
            try {
                performChangePassword(request);
            } catch (Throwable t) {
                log.error("Error changing password for user: {}", request.getUserId(), t);
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> resetPassword(String username) {
        return Mono.defer(() -> {
            try {
                performResetPassword(username);
            } catch (Throwable t) {
                log.error("Error resetting password for user: {}", username, t);
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<String> assignTemporaryPassword(String userId) {
        // Unlike resetPassword above, errors here are propagated (not swallowed): the caller relays the
        // returned secret out-of-band, so a silent failure would leave an unusable account.
        return Mono.fromCallable(() -> performAssignTemporaryPassword(userId));
    }

    private String performAssignTemporaryPassword(String userId) {
        log.debug("Assigning temporary password for userId: {}", userId);
        if (userId == null || userId.isBlank()) {
            throw new WebApplicationException("userId cannot be null or empty");
        }
        // Mixed-case + digit + symbol so it satisfies any realm password policy; single use.
        String temporaryPassword = "Aa1!" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(temporaryPassword);
            credential.setTemporary(true);
            keycloak.realm(keycloakFactory.getRealm()).users().get(userId).resetPassword(credential);
        }
        return temporaryPassword;
    }

    @Override
    public Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username) {
        return Mono.error(new UnsupportedOperationException("mfaChallenge not implemented"));
    }

    @Override
    public Mono<Void> mfaVerify(MfaVerifyRequest request) {
        return Mono.error(new UnsupportedOperationException("mfaVerify not implemented"));
    }

    @Override
    public Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId) {
        return Mono.fromCallable(() -> performListSessions(userId))
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error listing sessions for user: {}", userId, throwable);
                    return Mono.just(KeycloakExceptionHandler.handleException(throwable));
                });
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        return Mono.defer(() -> {
            try {
                performRevokeSession(sessionId);
            } catch (Throwable t) {
                log.error("Error revoking session: {}", sessionId, t);
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<ResponseEntity<List<String>>> getRoles(String userId) {
        return Mono.fromCallable(() -> performGetRoles(userId))
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error getting roles for user: {}", userId, throwable);
                    return Mono.just(KeycloakExceptionHandler.handleException(throwable));
                });
    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        return Mono.defer(() -> {
            try {
                performDeleteUser(userId);
            } catch (Throwable t) {
                log.error("Error deleting user: {}", userId, t);
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request) {
        return Mono.fromCallable(() -> performUpdateUser(request))
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error updating user: {}", request.getUserId(), throwable);
                    return Mono.just(KeycloakExceptionHandler.handleException(throwable));
                });
    }

    @Override
    public Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request) {
        return Mono.fromCallable(() -> performCreateRoles(request))
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error creating roles: {}", request.getRoleNames(), throwable);
                    return Mono.just(KeycloakExceptionHandler.handleException(throwable));
                });
    }

    @Override
    public Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request) {
        return Mono.fromCallable(() -> performCreateScope(request))
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error creating scope: {}", request.getName(), throwable);
                    return Mono.just(KeycloakExceptionHandler.handleException(throwable));
                });
    }

    @Override
    public Mono<Void> assignRolesToUser(AssignRolesRequest request) {
        return Mono.defer(() -> {
            try {
                performAssignRolesToUser(request);
            } catch (Throwable t) {
                log.error("Error assigning roles to user: {}", request.getUserId(), t);
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> removeRolesFromUser(AssignRolesRequest request) {
        return Mono.defer(() -> {
            try {
                performRemoveRolesFromUser(request);
            } catch (Throwable t) {
                log.error("Error removing roles from user: {}", request.getUserId(), t);
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<ResponseEntity<List<String>>> listRoles() {
        return Mono.fromCallable(this::performListRoles)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error listing realm roles", throwable);
                    return Mono.just(KeycloakExceptionHandler.handleException(throwable));
                });
    }

    @Override
    public Mono<Void> deleteRole(String roleName) {
        return Mono.<Void>fromRunnable(() -> performDeleteRole(roleName))
                .doOnError(t -> log.error("Error deleting realm role: {}", roleName, t));
    }

    @Override
    public Mono<Void> updateRole(UpdateRoleRequest request) {
        return Mono.<Void>fromRunnable(() -> performUpdateRole(request))
                .doOnError(t -> log.error("Error updating realm role: {}",
                        request == null ? null : request.getName(), t));
    }

    // Private methods for business logic

    private CreateUserResponse performCreateUser(CreateUserRequest request) {
        log.debug("Creating user: {}", request.getUsername());

        String userId;
        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            UserRepresentation user = new UserRepresentation();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setEnabled(true);
            if (request.getGivenName() != null) {
                user.setFirstName(request.getGivenName());
            }
            if (request.getFamilyName() != null) {
                user.setLastName(request.getFamilyName());
            }

            Map<String, List<String>> userAttributes = new HashMap<>();
            if (request.getAttributes() != null) {
                // A value may be a single scalar or already a collection (e.g. a multivalued attribute
                // like tenant_ids). Preserve list elements instead of String.valueOf(list) -> "[a, b]".
                request.getAttributes().forEach((k, v) -> {
                    if (v instanceof Collection<?> values) {
                        userAttributes.put(k, values.stream().map(String::valueOf).toList());
                    } else {
                        userAttributes.put(k, List.of(String.valueOf(v)));
                    }
                });
            }
            user.setAttributes(userAttributes);

            Response response = keycloak.realm(keycloakFactory.getRealm())
                    .users()
                    .create(user);

            if (response.getStatus() != 201) {
                throw new WebApplicationException("Failed to create user", response.getStatus());
            }

            userId = CreatedResponseUtil.getCreatedId(response);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(request.getPassword());
            credential.setTemporary(false);

            keycloak.realm(keycloakFactory.getRealm())
                    .users()
                    .get(userId)
                    .resetPassword(credential);
        }

        return new CreateUserResponse(
                userId,
                request.getUsername(),
                request.getEmail(),
                Instant.now()
        );
    }

    private void performChangePassword(ChangePasswordRequest request) {
        log.debug("Changing password for user: {}", request.getUserId());

        String username = getUsernameById(request.getUserId());

        try (Keycloak passwordKeycloak = keycloakFactory.createPasswordClient(username, request.getOldPassword())) {
            // Verify old password by getting token
            AccessTokenResponse tokenResponse = passwordKeycloak.tokenManager().getAccessToken();
            if (tokenResponse == null || tokenResponse.getToken() == null) {
                throw new IllegalArgumentException("Old password is incorrect");
            }

            // Additional validation using TokenService
            if (tokenService.isTokenExpired(tokenResponse.getToken())) {
                throw new IllegalArgumentException("Authentication failed - token expired");
            }
        }

        // Use admin client to change password
        try (Keycloak adminKeycloak = keycloakFactory.createClientCredentialsClient()) {
            CredentialRepresentation newCred = new CredentialRepresentation();
            newCred.setType(CredentialRepresentation.PASSWORD);
            newCred.setValue(request.getNewPassword());
            newCred.setTemporary(false);

            adminKeycloak.realm(keycloakFactory.getRealm())
                    .users()
                    .get(request.getUserId())
                    .resetPassword(newCred);
        }
    }

    private void performResetPassword(String username) {
        log.debug("Resetting password for username: {}", username);

        if (username == null || username.isBlank()) {
            throw new WebApplicationException("username cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());

            List<UserRepresentation> users;
            try {
                users = realmResource.users().searchByUsername(username, true);
            } catch (Throwable t) {
                users = realmResource.users().search(username, null, null);
            }

            if (users == null || users.isEmpty()) {
                throw new WebApplicationException("User not found", 404);
            }

            UserRepresentation user = users.stream()
                    .filter(Objects::nonNull)
                    .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                    .findFirst()
                    .orElse(users.get(0));

            UserResource userResource = realmResource.users().get(user.getId());

            try {
                userResource.executeActionsEmail(List.of("UPDATE_PASSWORD"));
            } catch (Throwable t) {
                CredentialRepresentation temp = new CredentialRepresentation();
                temp.setType(CredentialRepresentation.PASSWORD);
                temp.setValue(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                temp.setTemporary(true);
                userResource.resetPassword(temp);
            }
        }
    }

    private String getUsernameById(String userId) {
        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            return keycloak.realm(keycloakFactory.getRealm())
                    .users()
                    .get(userId)
                    .toRepresentation()
                    .getUsername();
        }
    }

    private List<SessionInfo> performListSessions(String userId) {
        log.debug("Listing sessions for user: {}", userId);

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());

            List<UserSessionRepresentation> userSessions =
                    realmResource.users().get(userId).getUserSessions();

            return userSessions.stream()
                    .map(session -> {
                        SessionInfo info = new SessionInfo();
                        info.setSessionId(session.getId());
                        info.setUserId(userId);
                        info.setCreatedAt(Instant.ofEpochMilli(session.getStart()));
                        info.setLastAccessAt(Instant.ofEpochMilli(session.getLastAccess()));
                        info.setIpAddress(session.getIpAddress());
                        return info;
                    })
                    .collect(Collectors.toList());
        }
    }

    private void performRevokeSession(String sessionId) {
        log.debug("Revoking session: {}", sessionId);

        if (sessionId == null || sessionId.isBlank()) {
            throw new WebApplicationException("SessionId cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());
            realmResource.deleteSession(sessionId, false);
        }
    }

    private List<String> performGetRoles(String userId) {
        log.debug("Getting roles for user: {}", userId);

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            UserResource userResource = keycloak.realm(keycloakFactory.getRealm()).users().get(userId);
            MappingsRepresentation mappings = userResource.roles().getAll();

            Stream<String> realmRoleNames = Stream.ofNullable(mappings.getRealmMappings())
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .map(RoleRepresentation::getName)
                    .filter(Objects::nonNull);

            Stream<String> clientRoleNames = Stream.ofNullable(mappings.getClientMappings())
                    .map(Map::values)
                    .flatMap(Collection::stream)
                    .filter(Objects::nonNull)
                    .map(ClientMappingsRepresentation::getMappings)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .map(RoleRepresentation::getName)
                    .filter(Objects::nonNull);

            return Stream.concat(realmRoleNames, clientRoleNames)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(LinkedHashSet::new),
                            List::copyOf));
        }
    }

    private void performDeleteUser(String userId) {
        log.debug("Deleting user: {}", userId);

        if (userId == null || userId.isBlank()) {
            throw new WebApplicationException("userId cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());
            realmResource.users().get(userId).remove();
        }
    }

    private UpdateUserResponse performUpdateUser(UpdateUserRequest request) {
        log.debug("Updating user: {}", request.getUserId());

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new WebApplicationException("userId cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());

            // Retrieve existing user
            UserRepresentation user = realmResource.users().get(request.getUserId()).toRepresentation();
            if (user == null) {
                throw new WebApplicationException("User not found", 404);
            }

            // Update fields if they are not null
            if (request.getEmail() != null) user.setEmail(request.getEmail());
            if (request.getGivenName() != null) user.setFirstName(request.getGivenName());
            if (request.getFamilyName() != null) user.setLastName(request.getFamilyName());
            if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
            if (request.getAttributes() != null) {
                Map<String, List<String>> existingAttributes = user.getAttributes();
                if (existingAttributes != null) {
                    existingAttributes.putAll(request.getAttributes());
                } else {
                    user.setAttributes(request.getAttributes());
                }
            }

            // Perform the update
            realmResource.users().get(request.getUserId()).update(user);

            // Build response
            UpdateUserResponse response = new UpdateUserResponse();
            response.setId(user.getId());
            response.setUsername(user.getUsername());
            response.setEmail(user.getEmail());
            response.setUpdatedAt(Instant.now());

            return response;
        }
    }

    private CreateRolesResponse performCreateRoles(CreateRolesRequest request) {
        log.debug("Creating roles: {}", request.getRoleNames());

        if (request.getRoleNames() == null || request.getRoleNames().isEmpty()) {
            throw new WebApplicationException("roleNames cannot be null or empty");
        }

        List<String> createdRoles = new ArrayList<>();

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());

            if (request.getContext() != null && !request.getContext().isBlank()) {
                // Create roles in a specific client context
                ClientResource clientResource = realmResource.clients()
                        .findByClientId(request.getContext())
                        .stream()
                        .findFirst()
                        .map(clientRep -> realmResource.clients().get(clientRep.getId()))
                        .orElseThrow(() -> new WebApplicationException("Client not found: " + request.getContext()));

                for (String roleName : request.getRoleNames()) {
                    RoleRepresentation role = new RoleRepresentation();
                    role.setName(roleName);
                    role.setDescription(request.getDescription());
                    clientResource.roles().create(role);
                    createdRoles.add(roleName);
                }
            } else {
                // Create roles in the realm
                for (String roleName : request.getRoleNames()) {
                    RoleRepresentation role = new RoleRepresentation();
                    role.setName(roleName);
                    role.setDescription(request.getDescription());
                    realmResource.roles().create(role);
                    createdRoles.add(roleName);
                }
            }
        }

        CreateRolesResponse response = new CreateRolesResponse();
        response.setCreatedRoleNames(createdRoles);
        return response;
    }

    private List<String> performListRoles() {
        log.debug("Listing realm roles");

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());
            return realmResource.roles().list().stream()
                    .map(RoleRepresentation::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(LinkedHashSet::new),
                            List::copyOf));
        }
    }

    private void performDeleteRole(String roleName) {
        log.debug("Deleting realm role: {}", roleName);

        if (roleName == null || roleName.isBlank()) {
            throw new WebApplicationException("roleName cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());
            realmResource.roles().deleteRole(roleName);
        }
    }

    private void performUpdateRole(UpdateRoleRequest request) {
        log.debug("Updating realm role: {}", request == null ? null : request.getName());

        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new WebApplicationException("role name cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());
            RoleRepresentation role = realmResource.roles().get(request.getName()).toRepresentation();
            if (role == null) {
                throw new WebApplicationException("Role not found: " + request.getName(), 404);
            }
            if (request.getNewName() != null) role.setName(request.getNewName());
            if (request.getDescription() != null) role.setDescription(request.getDescription());
            realmResource.roles().get(request.getName()).update(role);
        }
    }

    private CreateScopeResponse performCreateScope(CreateScopeRequest request) {
        log.debug("Creating scope: {}", request.getName());

        if (request.getName() == null || request.getName().isBlank()) {
            throw new WebApplicationException("Scope name cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());

            RoleRepresentation scopeRole = new RoleRepresentation();
            scopeRole.setName(request.getName());
            scopeRole.setDescription(request.getDescription());

            String scopeId;

            if (request.getContext() != null && !request.getContext().isBlank()) {
                // Create scope as a client role
                ClientResource clientResource = realmResource.clients()
                        .findByClientId(request.getContext())
                        .stream()
                        .findFirst()
                        .map(clientRep -> realmResource.clients().get(clientRep.getId()))
                        .orElseThrow(() -> new WebApplicationException("Client not found: " + request.getContext()));

                clientResource.roles().create(scopeRole);
                scopeId = clientResource.roles().get(request.getName()).toRepresentation().getId();
            } else {
                // Create scope as a realm role
                realmResource.roles().create(scopeRole);
                scopeId = realmResource.roles().get(request.getName()).toRepresentation().getId();
            }

            CreateScopeResponse response = new CreateScopeResponse();
            response.setId(scopeId);
            response.setName(request.getName());
            response.setCreatedAt(Instant.now());

            return response;
        }
    }

    private void performAssignRolesToUser(AssignRolesRequest request) {
        log.debug("Assigning roles {} to user: {}", request.getRoleNames(), request.getUserId());

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new WebApplicationException("userId cannot be null or empty");
        }

        if (request.getRoleNames() == null || request.getRoleNames().isEmpty()) {
            throw new WebApplicationException("roleNames cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());
            UserResource userResource = realmResource.users().get(request.getUserId());

            List<RoleRepresentation> rolesToAssign = new ArrayList<>();
            for (String roleName : request.getRoleNames()) {
                RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
                if (role != null) {
                    rolesToAssign.add(role);
                } else {
                    throw new WebApplicationException("Role not found: " + roleName);
                }
            }

            userResource.roles().realmLevel().add(rolesToAssign);
        }
    }

    private void performRemoveRolesFromUser(AssignRolesRequest request) {
        log.debug("Removing roles {} from user: {}", request.getRoleNames(), request.getUserId());

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new WebApplicationException("userId cannot be null or empty");
        }

        if (request.getRoleNames() == null || request.getRoleNames().isEmpty()) {
            throw new WebApplicationException("roleNames cannot be null or empty");
        }

        try (Keycloak keycloak = keycloakFactory.createClientCredentialsClient()) {
            RealmResource realmResource = keycloak.realm(keycloakFactory.getRealm());
            UserResource userResource = realmResource.users().get(request.getUserId());

            List<RoleRepresentation> rolesToRemove = new ArrayList<>();
            for (String roleName : request.getRoleNames()) {
                RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
                if (role != null) {
                    rolesToRemove.add(role);
                } else {
                    throw new WebApplicationException("Role not found: " + roleName);
                }
            }

            userResource.roles().realmLevel().remove(rolesToRemove);
        }
    }

}
