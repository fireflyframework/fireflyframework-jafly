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
import org.fireflyframework.security.idp.adapter.keycloak.KeycloakAPIFactory;
import org.fireflyframework.security.idp.adapter.service.IdpUserService;
import org.fireflyframework.security.idp.dtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
public class IdpUserServiceImpl implements IdpUserService {

    private final KeycloakAPIFactory keycloakAPIFactory;

    public IdpUserServiceImpl(KeycloakAPIFactory keycloakAPIFactory) {
        this.keycloakAPIFactory = keycloakAPIFactory;
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> login(LoginRequest request) {
        return keycloakAPIFactory.tokenWebClient()
                .post()
                .uri("/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(keycloakAPIFactory.passwordGrantBody(request)))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> Mono.just(KeycloakExceptionHandler.handleException(throwable)));
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> refresh(RefreshRequest request) {
        return keycloakAPIFactory.tokenWebClient()
                .post()
                .uri("/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(keycloakAPIFactory.refreshTokenBody(request.getRefreshToken())))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> Mono.just(KeycloakExceptionHandler.handleException(throwable)));
    }

    @Override
    public Mono<Void> logout(LogoutRequest request) {
        // Ensure proper Bearer token format
        String authHeader = request.getAccessToken();
        if (authHeader != null && !authHeader.toLowerCase().startsWith("bearer ")) {
            authHeader = "Bearer " + authHeader;
        }
        
        return keycloakAPIFactory.tokenWebClient()
                .post()
                .uri("/logout")
                .header("Authorization", authHeader)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(keycloakAPIFactory.logoutBody(request.getRefreshToken())))
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(throwable -> {
                    log.warn("Error calling logout endpoint", throwable);
                    return Mono.empty();
                });

    }

    @Override
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        // Extract raw token in case the header value includes the "Bearer " prefix
        String rawToken = accessToken;
        if (rawToken != null && rawToken.toLowerCase().startsWith("bearer ")) {
            rawToken = rawToken.substring(7).trim();
        }

        return keycloakAPIFactory.tokenWebClient()
                .post()
                .uri("/token/introspect")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(keycloakAPIFactory.introspectionBody(rawToken)))
                .retrieve()
                .bodyToMono(IntrospectionResponse.class)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> Mono.just(KeycloakExceptionHandler.handleException(throwable)));
    }

    @Override
    public Mono<ResponseEntity<UserInfoResponse>> getUserInfo(String accessToken) {
        // Ensure proper Bearer token format
        String authHeader = accessToken;
        if (authHeader != null && !authHeader.toLowerCase().startsWith("bearer ")) {
            authHeader = "Bearer " + authHeader;
        }
        
        return keycloakAPIFactory.tokenWebClient()
                .get()
                .uri("/userinfo")
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(UserInfoResponse.class)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> Mono.just(KeycloakExceptionHandler.handleException(throwable)));
    }

    @Override
    public Mono<Void> revokeRefreshToken(String refreshToken) {
        return keycloakAPIFactory.tokenWebClient()
                .post()
                // Keycloak's RFC 7009 revocation endpoint is /protocol/openid-connect/revoke
                // (the tokenWebClient base URL already ends in /protocol/openid-connect).
                .uri("/revoke")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(keycloakAPIFactory.revocationBody(refreshToken)))
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(throwable -> {
                    log.warn("Error calling token revocation endpoint", throwable);
                    return Mono.empty();
                });

    }

}
