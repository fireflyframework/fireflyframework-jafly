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

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.MsalException;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.RefreshTokenParameters;
import com.microsoft.aad.msal4j.UserNamePasswordParameters;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.fireflyframework.security.idp.azuread.client.GraphClientFactory;
import org.fireflyframework.security.idp.azuread.config.AzureAdProperties;
import org.fireflyframework.security.idp.azuread.util.AzureAdTokenParser;
import org.fireflyframework.security.idp.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Service for handling Azure AD authentication operations.
 *
 * <p>Implements authentication flows shared between Entra ID and B2C including:
 * <ul>
 *   <li>User login via MSAL ROPC flow</li>
 *   <li>Token refresh via MSAL refresh token flow</li>
 *   <li>Logout (revoke sign-in sessions via Graph)</li>
 *   <li>Token introspection (local JWT parsing)</li>
 *   <li>User info retrieval via Graph</li>
 *   <li>Refresh token revocation</li>
 * </ul>
 *
 * <p>All calls use {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}
 * since MSAL4J and Graph SDK are blocking.
 */
@RequiredArgsConstructor
@Slf4j
public class AzureAdAuthService {

    private final GraphClientFactory graphClientFactory;
    private final AzureAdProperties properties;

    private volatile PublicClientApplication publicClient;

    /**
     * Get or create a PublicClientApplication for ROPC flow.
     * ROPC (UserNamePasswordParameters) requires a public client, not a confidential client.
     */
    private PublicClientApplication getPublicClient() {
        if (publicClient == null) {
            synchronized (this) {
                if (publicClient == null) {
                    try {
                        publicClient = PublicClientApplication.builder(properties.getClientId())
                                .authority(properties.getAuthority())
                                .connectTimeoutForDefaultHttpClient(properties.getConnectionTimeout())
                                .readTimeoutForDefaultHttpClient(properties.getRequestTimeout())
                                .build();
                    } catch (MalformedURLException e) {
                        throw new IllegalStateException("Invalid Azure AD authority URL: " + properties.getAuthority(), e);
                    }
                }
            }
        }
        return publicClient;
    }

    /**
     * Authenticate user with username and password via MSAL ROPC flow.
     */
    public Mono<ResponseEntity<TokenResponse>> login(LoginRequest request) {
        return Mono.<ResponseEntity<TokenResponse>>fromCallable(() -> {
            log.info("Initiating Azure AD login for user: {}", request.getUsername());

            PublicClientApplication client = getPublicClient();

            Set<String> scopes = new HashSet<>();
            if (request.getScope() != null && !request.getScope().isEmpty()) {
                Collections.addAll(scopes, request.getScope().split("\\s+"));
            } else {
                scopes.add(properties.getClientId() + "/.default");
            }

            UserNamePasswordParameters parameters = UserNamePasswordParameters.builder(
                    scopes,
                    request.getUsername(),
                    request.getPassword().toCharArray())
                    .build();

            IAuthenticationResult result = client.acquireToken(parameters).join();

            TokenResponse tokenResponse = TokenResponse.builder()
                    .accessToken(result.accessToken())
                    .idToken(result.idToken())
                    .tokenType("Bearer")
                    .expiresIn(result.expiresOnDate() != null
                            ? (result.expiresOnDate().getTime() - System.currentTimeMillis()) / 1000
                            : 3600L)
                    .scope(String.join(" ", scopes))
                    .build();

            log.info("Successfully authenticated user: {}", request.getUsername());
            return ResponseEntity.ok(tokenResponse);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Azure AD login failed for user: {}", request.getUsername(), exception);

              if (exception instanceof MsalException) {
                  return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<TokenResponse>build());
              }
              return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<TokenResponse>build());
          });
    }

    /**
     * Refresh access token using the refresh token via MSAL4J.
     */
    public Mono<ResponseEntity<TokenResponse>> refresh(RefreshRequest request) {
        return Mono.<ResponseEntity<TokenResponse>>fromCallable(() -> {
            log.debug("Refreshing Azure AD token via refresh token");

            PublicClientApplication client = getPublicClient();

            Set<String> scopes = new HashSet<>();
            scopes.add(properties.getClientId() + "/.default");

            RefreshTokenParameters parameters = RefreshTokenParameters.builder(
                    scopes, request.getRefreshToken()).build();

            IAuthenticationResult result = client.acquireToken(parameters).join();

            TokenResponse tokenResponse = TokenResponse.builder()
                    .accessToken(result.accessToken())
                    .idToken(result.idToken())
                    .refreshToken(request.getRefreshToken())
                    .tokenType("Bearer")
                    .expiresIn(result.expiresOnDate() != null
                            ? (result.expiresOnDate().getTime() - System.currentTimeMillis()) / 1000
                            : 3600L)
                    .scope(String.join(" ", scopes))
                    .build();

            log.debug("Successfully refreshed token");
            return ResponseEntity.ok(tokenResponse);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Token refresh failed", exception);
              return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<TokenResponse>build());
          });
    }

    /**
     * Logout user by revoking all sign-in sessions via Microsoft Graph.
     */
    public Mono<Void> logout(LogoutRequest request) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Logging out user from Azure AD");

            String userId = AzureAdTokenParser.extractUserId(request.getAccessToken());
            GraphServiceClient graphClient = graphClientFactory.getClient();

            graphClient.users().byUserId(userId).revokeSignInSessions().post();

            log.info("Successfully logged out user: {}", userId);
            return null;

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Logout failed", exception);
              return Mono.error(new RuntimeException("Logout failed", exception));
          });
    }

    /**
     * Introspect access token by parsing JWT locally.
     */
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        return Mono.<ResponseEntity<IntrospectionResponse>>fromCallable(() -> {
            log.debug("Introspecting Azure AD token");

            boolean expired = AzureAdTokenParser.isExpired(accessToken);
            if (expired) {
                log.debug("Token is expired");
                IntrospectionResponse introspection = IntrospectionResponse.builder()
                        .active(false)
                        .build();
                return ResponseEntity.ok(introspection);
            }

            JsonNode payload = AzureAdTokenParser.parsePayload(accessToken);

            IntrospectionResponse introspection = IntrospectionResponse.builder()
                    .active(true)
                    .sub(payload.path("sub").asText(null))
                    .iss(payload.path("iss").asText(null))
                    .exp(payload.path("exp").asLong(0))
                    .iat(payload.path("iat").asLong(0))
                    .scope(payload.path("scp").asText(null))
                    .username(AzureAdTokenParser.extractUsername(accessToken))
                    .build();

            return ResponseEntity.ok(introspection);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Token introspection failed", exception);
              return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<IntrospectionResponse>build());
          });
    }

    /**
     * Get user information from access token via Microsoft Graph.
     */
    public Mono<ResponseEntity<UserInfoResponse>> getUserInfo(String accessToken) {
        return Mono.<ResponseEntity<UserInfoResponse>>fromCallable(() -> {
            log.debug("Fetching Azure AD user info");

            String userId = AzureAdTokenParser.extractUserId(accessToken);
            GraphServiceClient graphClient = graphClientFactory.getClient();

            User user = graphClient.users().byUserId(userId).get();

            UserInfoResponse userInfo = UserInfoResponse.builder()
                    .sub(user.getId())
                    .email(user.getMail())
                    .emailVerified(user.getMail() != null)
                    .preferredUsername(user.getUserPrincipalName())
                    .givenName(user.getGivenName())
                    .familyName(user.getSurname())
                    .name(user.getDisplayName())
                    .build();

            log.debug("Successfully fetched user info for: {}", userId);
            return ResponseEntity.ok(userInfo);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(exception -> {
              log.error("Failed to fetch user info", exception);
              return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<UserInfoResponse>build());
          });
    }

    /**
     * Revoke refresh token by revoking all sign-in sessions for the user.
     *
     * <p>Azure AD refresh tokens are opaque (not JWTs), so we cannot extract the user ID
     * directly from the refresh token. Instead, we attempt to parse it as a JWT first
     * (in case it's an access token being passed), and fall back to a warning if parsing fails.
     * The Cognito adapter follows the same pattern — this is a limitation of the token-only API.
     */
    public Mono<Void> revokeRefreshToken(String refreshToken) {
        return Mono.<Void>fromCallable(() -> {
            log.info("Revoking Azure AD refresh token");

            try {
                // Azure AD refresh tokens are opaque. If an access token (JWT) is passed,
                // we can extract the user ID and revoke sessions.
                String userId = AzureAdTokenParser.extractUserId(refreshToken);
                GraphServiceClient graphClient = graphClientFactory.getClient();
                graphClient.users().byUserId(userId).revokeSignInSessions().post();
                log.info("Successfully revoked sessions for user: {}", userId);
            } catch (Exception e) {
                // Refresh token is opaque — cannot extract user ID.
                // Azure AD does not support revoking a single refresh token via Graph API.
                log.warn("Cannot revoke opaque refresh token directly. " +
                         "Azure AD refresh tokens are opaque and cannot be individually revoked via Graph API. " +
                         "Use logout() with the access token to revoke all sessions.");
            }
            return null;

        }).subscribeOn(Schedulers.boundedElastic());
    }
}
