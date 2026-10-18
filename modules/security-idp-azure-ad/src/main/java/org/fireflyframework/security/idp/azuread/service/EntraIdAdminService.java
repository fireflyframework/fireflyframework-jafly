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

import com.microsoft.graph.models.AppRole;
import com.microsoft.graph.models.AppRoleAssignment;
import com.microsoft.graph.models.AppRoleAssignmentCollectionResponse;
import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.DirectoryObjectCollectionResponse;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.GroupCollectionResponse;
import com.microsoft.graph.models.PasswordProfile;
import com.microsoft.graph.models.ReferenceCreate;
import com.microsoft.graph.models.ServicePrincipal;
import com.microsoft.graph.models.SignInActivity;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.fireflyframework.security.idp.azuread.client.GraphClientFactory;
import org.fireflyframework.security.idp.azuread.config.AzureAdProperties;
import org.fireflyframework.security.idp.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Entra ID (Azure AD) implementation of the admin service using Microsoft Graph SDK.
 *
 * <p>Implements administrative operations including:
 * <ul>
 *   <li>User creation, update, and deletion</li>
 *   <li>Password management</li>
 *   <li>Role/Group management (app-roles, groups, or both strategies)</li>
 *   <li>Session management via sign-in activity</li>
 * </ul>
 *
 * <p>All calls use {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}
 * since the Graph SDK is blocking.
 */
@RequiredArgsConstructor
@Slf4j
public class EntraIdAdminService implements AzureAdAdminService {

    protected final GraphClientFactory graphClientFactory;
    protected final AzureAdProperties properties;

    @Override
    public Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request) {
        return Mono.<ResponseEntity<CreateUserResponse>>fromCallable(() -> {
            log.info("Creating Entra ID user: {}", request.getUsername());

            GraphServiceClient graphClient = graphClientFactory.getClient();

            User user = new User();
            user.setDisplayName(buildDisplayName(request));
            user.setMailNickname(request.getUsername());
            // Use email as UPN if available; otherwise fall back to username.
            // Azure AD requires UPN format user@domain, not user@tenant-guid.
            user.setUserPrincipalName(request.getEmail() != null ? request.getEmail() : request.getUsername());
            user.setGivenName(request.getGivenName());
            user.setSurname(request.getFamilyName());
            user.setMail(request.getEmail());
            user.setAccountEnabled(true);

            if (request.getPassword() != null) {
                PasswordProfile passwordProfile = new PasswordProfile();
                passwordProfile.setPassword(request.getPassword());
                passwordProfile.setForceChangePasswordNextSignIn(false);
                user.setPasswordProfile(passwordProfile);
            }

            User createdUser = graphClient.users().post(user);

            CreateUserResponse response = CreateUserResponse.builder()
                    .id(createdUser.getId())
                    .username(createdUser.getUserPrincipalName())
                    .email(request.getEmail())
                    .createdAt(Instant.now())
                    .build();

            log.info("Successfully created user: {}", request.getUsername());
            return ResponseEntity.ok(response);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to create user: {}", request.getUsername(), exception);
              return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<CreateUserResponse>build());
          });
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Changing password for user: {}", request.getUserId());

            GraphServiceClient graphClient = graphClientFactory.getClient();

            User user = new User();
            PasswordProfile passwordProfile = new PasswordProfile();
            passwordProfile.setPassword(request.getNewPassword());
            passwordProfile.setForceChangePasswordNextSignIn(false);
            user.setPasswordProfile(passwordProfile);

            graphClient.users().byUserId(request.getUserId()).patch(user);

            log.info("Successfully changed password for user: {}", request.getUserId());
            return null;

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to change password", exception);
              return Mono.error(new RuntimeException("Password change failed", exception));
          });
    }

    @Override
    public Mono<Void> resetPassword(String username) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Resetting password for user: {}", username);

            GraphServiceClient graphClient = graphClientFactory.getClient();

            User user = new User();
            PasswordProfile passwordProfile = new PasswordProfile();
            passwordProfile.setPassword(UUID.randomUUID().toString().substring(0, 16) + "!Aa1");
            passwordProfile.setForceChangePasswordNextSignIn(true);
            user.setPasswordProfile(passwordProfile);

            graphClient.users().byUserId(username).patch(user);

            log.info("Successfully initiated password reset for user: {}", username);
            return null;

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to reset password", exception);
              return Mono.error(new RuntimeException("Password reset failed", exception));
          });
    }

    @Override
    public Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username) {
        return Mono.<ResponseEntity<MfaChallengeResponse>>fromCallable(() -> {
            log.info("MFA challenge requested for user: {} — Azure MFA is managed by Conditional Access policies", username);

            // Azure AD MFA is managed by Conditional Access policies, not programmatically
            MfaChallengeResponse response = MfaChallengeResponse.builder()
                    .challengeId(UUID.randomUUID().toString())
                    .deliveryMethod("AZURE_MFA_POLICY")
                    .build();

            return ResponseEntity.ok(response);

        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> mfaVerify(MfaVerifyRequest request) {
        return Mono.<Void>fromCallable(() -> {
            log.info("MFA verification requested — Azure MFA is managed by Conditional Access policies");
            // Azure AD MFA verification is handled by Conditional Access policies
            return null;

        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId) {
        return Mono.<ResponseEntity<List<SessionInfo>>>fromCallable(() -> {
            log.info("Listing sessions for user: {}", userId);

            GraphServiceClient graphClient = graphClientFactory.getClient();

            User user = graphClient.users().byUserId(userId).get(requestConfig ->
                    requestConfig.queryParameters.select = new String[]{"id", "displayName", "signInActivity"});

            List<SessionInfo> sessions = new ArrayList<>();
            if (user != null && user.getSignInActivity() != null) {
                SignInActivity activity = user.getSignInActivity();
                OffsetDateTime lastSignIn = activity.getLastSignInDateTime();
                SessionInfo session = SessionInfo.builder()
                        .sessionId(userId)
                        .userId(userId)
                        .lastAccessAt(lastSignIn != null ? lastSignIn.toInstant() : null)
                        .build();
                sessions.add(session);
            }

            return ResponseEntity.ok(sessions);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to list sessions", exception);
              return Mono.just(ResponseEntity.ok(Collections.emptyList()));
          });
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Revoking session for user: {}", sessionId);

            GraphServiceClient graphClient = graphClientFactory.getClient();
            graphClient.users().byUserId(sessionId).revokeSignInSessions().post();

            log.info("Successfully revoked session: {}", sessionId);
            return null;

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to revoke session", exception);
              return Mono.error(new RuntimeException("Session revocation failed", exception));
          });
    }

    @Override
    public Mono<ResponseEntity<List<String>>> getRoles(String userId) {
        return Mono.<ResponseEntity<List<String>>>fromCallable(() -> {
            log.info("Getting roles for user: {}", userId);

            String strategy = properties.getRoleMapping().getStrategy();
            List<String> roles = new ArrayList<>();

            if ("app-roles".equals(strategy) || "both".equals(strategy)) {
                roles.addAll(getAppRoles(userId));
            }
            if ("groups".equals(strategy) || "both".equals(strategy)) {
                roles.addAll(getGroupRoles(userId));
            }

            return ResponseEntity.ok(roles);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to get user roles", exception);
              return Mono.just(ResponseEntity.ok(Collections.emptyList()));
          });
    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Deleting user: {}", userId);

            GraphServiceClient graphClient = graphClientFactory.getClient();
            graphClient.users().byUserId(userId).delete();

            log.info("Successfully deleted user: {}", userId);
            return null;

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to delete user", exception);
              return Mono.error(new RuntimeException("User deletion failed", exception));
          });
    }

    @Override
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request) {
        return Mono.<ResponseEntity<UpdateUserResponse>>fromCallable(() -> {
            log.info("Updating user: {}", request.getUserId());

            GraphServiceClient graphClient = graphClientFactory.getClient();

            User user = new User();
            if (request.getEmail() != null) {
                user.setMail(request.getEmail());
            }
            if (request.getGivenName() != null) {
                user.setGivenName(request.getGivenName());
            }
            if (request.getFamilyName() != null) {
                user.setSurname(request.getFamilyName());
            }
            if (request.getEnabled() != null) {
                user.setAccountEnabled(request.getEnabled());
            }

            graphClient.users().byUserId(request.getUserId()).patch(user);

            UpdateUserResponse response = UpdateUserResponse.builder()
                    .id(request.getUserId())
                    .username(request.getUserId())
                    .email(request.getEmail())
                    .updatedAt(Instant.now())
                    .build();

            log.info("Successfully updated user: {}", request.getUserId());
            return ResponseEntity.ok(response);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to update user", exception);
              return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<UpdateUserResponse>build());
          });
    }

    @Override
    public Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request) {
        return Mono.<ResponseEntity<CreateRolesResponse>>fromCallable(() -> {
            log.info("Creating roles: {}", request.getRoleNames());

            GraphServiceClient graphClient = graphClientFactory.getClient();
            List<String> createdRoles = new ArrayList<>();

            for (String roleName : request.getRoleNames()) {
                try {
                    Group group = new Group();
                    group.setDisplayName(roleName);
                    group.setMailEnabled(false);
                    group.setMailNickname(roleName.replaceAll("[^a-zA-Z0-9]", ""));
                    group.setSecurityEnabled(true);
                    group.setDescription(request.getDescription() != null
                            ? request.getDescription()
                            : "Role: " + roleName);

                    graphClient.groups().post(group);
                    createdRoles.add(roleName);

                } catch (Exception e) {
                    log.warn("Failed to create role: {}", roleName, e);
                }
            }

            CreateRolesResponse response = CreateRolesResponse.builder()
                    .createdRoleNames(createdRoles)
                    .build();

            return ResponseEntity.ok(response);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to create roles", exception);
              return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<CreateRolesResponse>build());
          });
    }

    @Override
    public Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request) {
        return Mono.<ResponseEntity<CreateScopeResponse>>fromCallable(() -> {
            log.info("Creating scope: {} — Scopes are configured in Azure AD app registration", request.getName());

            // Scopes in Azure AD are configured in the app registration, not programmatically
            CreateScopeResponse response = CreateScopeResponse.builder()
                    .name(request.getName())
                    .createdAt(Instant.now())
                    .build();

            return ResponseEntity.ok(response);

        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> assignRolesToUser(AssignRolesRequest request) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Assigning roles to user: {}", request.getUserId());

            GraphServiceClient graphClient = graphClientFactory.getClient();

            for (String roleName : request.getRoleNames()) {
                try {
                    String groupId = findGroupIdByDisplayName(graphClient, roleName);
                    if (groupId != null) {
                        ReferenceCreate referenceCreate = new ReferenceCreate();
                        referenceCreate.setOdataId("https://graph.microsoft.com/v1.0/directoryObjects/" + request.getUserId());
                        graphClient.groups().byGroupId(groupId).members().ref().post(referenceCreate);
                    } else {
                        log.warn("Group not found for role: {}", roleName);
                    }
                } catch (Exception e) {
                    log.warn("Failed to assign role {} to user {}", roleName, request.getUserId(), e);
                }
            }

            log.info("Successfully assigned roles to user: {}", request.getUserId());
            return null;

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to assign roles", exception);
              return Mono.error(new RuntimeException("Role assignment failed", exception));
          });
    }

    @Override
    public Mono<Void> removeRolesFromUser(AssignRolesRequest request) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Removing roles from user: {}", request.getUserId());

            GraphServiceClient graphClient = graphClientFactory.getClient();

            for (String roleName : request.getRoleNames()) {
                try {
                    String groupId = findGroupIdByDisplayName(graphClient, roleName);
                    if (groupId != null) {
                        graphClient.groups().byGroupId(groupId)
                                .members().byDirectoryObjectId(request.getUserId()).ref().delete();
                    } else {
                        log.warn("Group not found for role: {}", roleName);
                    }
                } catch (Exception e) {
                    log.warn("Failed to remove role {} from user {}", roleName, request.getUserId(), e);
                }
            }

            log.info("Successfully removed roles from user: {}", request.getUserId());
            return null;

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to remove roles", exception);
              return Mono.error(new RuntimeException("Role removal failed", exception));
          });
    }

    // ---- Helper methods ----

    /**
     * Build display name from given name and family name.
     */
    protected String buildDisplayName(CreateUserRequest request) {
        String givenName = request.getGivenName() != null ? request.getGivenName() : "";
        String familyName = request.getFamilyName() != null ? request.getFamilyName() : "";
        String displayName = (givenName + " " + familyName).trim();
        return displayName.isEmpty() ? request.getUsername() : displayName;
    }

    /**
     * Get app role assignments for a user by resolving actual role names
     * from the service principal's appRoles collection.
     */
    private List<String> getAppRoles(String userId) {
        GraphServiceClient graphClient = graphClientFactory.getClient();

        AppRoleAssignmentCollectionResponse assignmentsResponse =
                graphClient.users().byUserId(userId).appRoleAssignments().get();

        if (assignmentsResponse == null || assignmentsResponse.getValue() == null) {
            return Collections.emptyList();
        }

        // Group assignments by resourceId to minimize API calls
        Map<String, List<AppRoleAssignment>> byResource = assignmentsResponse.getValue().stream()
                .filter(a -> a.getAppRoleId() != null && a.getResourceId() != null)
                .collect(Collectors.groupingBy(a -> a.getResourceId().toString()));

        List<String> roleNames = new ArrayList<>();
        for (Map.Entry<String, List<AppRoleAssignment>> entry : byResource.entrySet()) {
            try {
                ServicePrincipal sp = graphClient.servicePrincipals()
                        .byServicePrincipalId(entry.getKey()).get();
                if (sp != null && sp.getAppRoles() != null) {
                    Map<UUID, String> roleMap = sp.getAppRoles().stream()
                            .filter(r -> r.getId() != null && r.getValue() != null)
                            .collect(Collectors.toMap(AppRole::getId, AppRole::getValue));
                    for (AppRoleAssignment assignment : entry.getValue()) {
                        String roleName = roleMap.get(assignment.getAppRoleId());
                        if (roleName != null) {
                            roleNames.add(roleName);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve app roles for resource: {}", entry.getKey(), e);
            }
        }
        return roleNames;
    }

    /**
     * Get group-based roles for a user, filtered by prefix if configured.
     */
    private List<String> getGroupRoles(String userId) {
        GraphServiceClient graphClient = graphClientFactory.getClient();
        String prefix = properties.getRoleMapping().getGroupPrefix();

        DirectoryObjectCollectionResponse memberOfResponse =
                graphClient.users().byUserId(userId).memberOf().get();

        if (memberOfResponse == null || memberOfResponse.getValue() == null) {
            return Collections.emptyList();
        }

        return memberOfResponse.getValue().stream()
                .filter(obj -> obj instanceof Group)
                .map(obj -> ((Group) obj).getDisplayName())
                .filter(Objects::nonNull)
                .filter(name -> prefix == null || prefix.isEmpty() || name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    /**
     * Find a group ID by its display name.
     */
    protected String findGroupIdByDisplayName(GraphServiceClient graphClient, String displayName) {
        // Sanitize for OData filter injection — escape single quotes
        String sanitized = displayName.replace("'", "''");
        GroupCollectionResponse groupsResponse = graphClient.groups().get(requestConfig ->
                requestConfig.queryParameters.filter = "displayName eq '" + sanitized + "'");

        if (groupsResponse != null && groupsResponse.getValue() != null && !groupsResponse.getValue().isEmpty()) {
            return groupsResponse.getValue().get(0).getId();
        }
        return null;
    }
}
