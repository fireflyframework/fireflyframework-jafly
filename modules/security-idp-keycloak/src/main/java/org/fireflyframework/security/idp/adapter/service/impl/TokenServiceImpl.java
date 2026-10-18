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

import org.fireflyframework.security.idp.adapter.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessToken;

import java.util.*;
import java.util.stream.Stream;

/**
 * Service for token validation and information extraction.
 * Centralizes JWT processing logic.
 */
@Slf4j
public class TokenServiceImpl implements TokenService {

    /**
     * Parses and validates an access token.
     */
    @Override
    public Optional<AccessToken> parseAccessToken(String tokenString) {
        try {
            return Optional.of(
                    TokenVerifier.create(tokenString, AccessToken.class)
                            .getToken()
            );
        } catch (VerificationException e) {
            log.warn("Failed to parse access token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts user ID from token.
     */
    @Override
    public Optional<String> extractUserId(String tokenString) {
        return parseAccessToken(tokenString)
                .map(AccessToken::getSubject)
                .filter(subject -> !subject.isBlank());
    }

    /**
     * Extracts session ID from token.
     */
    @Override
    public Optional<String> extractSessionId(String tokenString) {
        return parseAccessToken(tokenString)
                .map(AccessToken::getSessionId)
                .filter(sessionId -> !sessionId.isBlank());
    }

    /**
     * Extracts all roles (realm and client) from JWT token.
     */
    @Override
    public List<String> extractRoles(String jwtAccessToken) {
        return parseAccessToken(jwtAccessToken)
                .map(this::extractRolesFromAccessToken)
                .orElse(List.of());
    }

    @Override
    public List<String> extractRolesFromAccessToken(AccessToken accessToken) {
        try {
            Stream<String> realmRoles = Optional.ofNullable(accessToken.getRealmAccess())
                    .map(AccessToken.Access::getRoles).stream().flatMap(Collection::stream);

            Stream<String> clientRoles = Optional.ofNullable(accessToken.getResourceAccess())
                    .map(Map::values).stream().flatMap(Collection::stream)
                    .map(AccessToken.Access::getRoles)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream);

            return Stream.concat(realmRoles, clientRoles)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.warn("Error extracting roles from token", e);
            return List.of();
        }
    }

    /**
     * Checks if token is expired.
     */
    @Override
    public boolean isTokenExpired(String tokenString) {
        return parseAccessToken(tokenString)
                .map(token -> token.getExp() < (System.currentTimeMillis() / 1000))
                .orElse(true);
    }
}