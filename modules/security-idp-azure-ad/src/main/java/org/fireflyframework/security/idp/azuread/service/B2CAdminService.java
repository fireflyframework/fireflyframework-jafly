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

import com.microsoft.graph.models.ObjectIdentity;
import com.microsoft.graph.models.PasswordProfile;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.fireflyframework.security.idp.azuread.client.GraphClientFactory;
import org.fireflyframework.security.idp.azuread.config.AzureAdProperties;
import org.fireflyframework.security.idp.dtos.CreateUserRequest;
import org.fireflyframework.security.idp.dtos.CreateUserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Azure AD B2C implementation of the admin service.
 *
 * <p>Extends {@link EntraIdAdminService} with B2C-specific overrides:
 * <ul>
 *   <li>{@code createUser} uses {@code identities} array with {@code ObjectIdentity}
 *       for local account (signInType: "emailAddress" and "userName")</li>
 *   <li>{@code resetPassword} uses temporary password with forceChangePasswordNextSignIn=true
 *       (B2C uses user flows for self-service)</li>
 * </ul>
 */
@Slf4j
public class B2CAdminService extends EntraIdAdminService {

    public B2CAdminService(GraphClientFactory graphClientFactory, AzureAdProperties properties) {
        super(graphClientFactory, properties);
    }

    @Override
    public Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request) {
        return Mono.<ResponseEntity<CreateUserResponse>>fromCallable(() -> {
            log.info("Creating B2C user: {}", request.getUsername());

            GraphServiceClient graphClient = graphClientFactory.getClient();
            String b2cDomain = properties.getB2c().getDomain();
            String issuer = b2cDomain != null ? b2cDomain : properties.getTenantId();

            User user = new User();
            user.setDisplayName(buildDisplayName(request));
            user.setGivenName(request.getGivenName());
            user.setSurname(request.getFamilyName());
            user.setAccountEnabled(true);

            // Build B2C identities array
            List<ObjectIdentity> identities = new ArrayList<>();

            // Email sign-in identity
            if (request.getEmail() != null) {
                ObjectIdentity emailIdentity = new ObjectIdentity();
                emailIdentity.setSignInType("emailAddress");
                emailIdentity.setIssuer(issuer);
                emailIdentity.setIssuerAssignedId(request.getEmail());
                identities.add(emailIdentity);
            }

            // Username sign-in identity
            ObjectIdentity usernameIdentity = new ObjectIdentity();
            usernameIdentity.setSignInType("userName");
            usernameIdentity.setIssuer(issuer);
            usernameIdentity.setIssuerAssignedId(request.getUsername());
            identities.add(usernameIdentity);

            user.setIdentities(identities);

            if (request.getPassword() != null) {
                PasswordProfile passwordProfile = new PasswordProfile();
                passwordProfile.setPassword(request.getPassword());
                passwordProfile.setForceChangePasswordNextSignIn(false);
                user.setPasswordProfile(passwordProfile);
            }

            User createdUser = graphClient.users().post(user);

            CreateUserResponse response = CreateUserResponse.builder()
                    .id(createdUser.getId())
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .createdAt(Instant.now())
                    .build();

            log.info("Successfully created B2C user: {}", request.getUsername());
            return ResponseEntity.ok(response);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to create B2C user: {}", request.getUsername(), exception);
              return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<CreateUserResponse>build());
          });
    }

    @Override
    public Mono<Void> resetPassword(String username) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Resetting password for B2C user: {}", username);

            GraphServiceClient graphClient = graphClientFactory.getClient();

            User user = new User();
            PasswordProfile passwordProfile = new PasswordProfile();
            passwordProfile.setPassword(UUID.randomUUID().toString().substring(0, 16) + "!Aa1");
            passwordProfile.setForceChangePasswordNextSignIn(true);
            user.setPasswordProfile(passwordProfile);

            graphClient.users().byUserId(username).patch(user);

            log.info("Successfully initiated password reset for B2C user: {} — user should use self-service flow", username);
            return null;

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to reset B2C password", exception);
              return Mono.error(new RuntimeException("B2C password reset failed", exception));
          });
    }
}
