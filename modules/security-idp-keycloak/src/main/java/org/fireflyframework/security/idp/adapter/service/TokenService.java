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

import org.keycloak.representations.AccessToken;

import java.util.List;
import java.util.Optional;

public interface TokenService {
    Optional<AccessToken> parseAccessToken(String tokenString);

    Optional<String> extractUserId(String tokenString);

    Optional<String> extractSessionId(String tokenString);

    List<String> extractRoles(String jwtAccessToken);

    List<String> extractRolesFromAccessToken(AccessToken accessToken);

    boolean isTokenExpired(String tokenString);
}
