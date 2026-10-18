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

package org.fireflyframework.security.core.authority;

import java.util.List;

/**
 * Configuration for {@link ConfigurableAuthorityMapper}: which claim paths carry roles/groups and
 * scopes, and an optional authority prefix. Defaults cover Keycloak, Cognito, and Entra conventions.
 *
 * @param roleClaimPaths  dot-paths inspected for roles/groups, in order
 * @param scopeClaimPaths dot-paths inspected for OAuth2 scopes, in order
 * @param authorityPrefix prefix applied to mapped authorities (e.g. {@code "ROLE_"}); may be empty
 */
public record AuthorityMappingProperties(
        List<String> roleClaimPaths,
        List<String> scopeClaimPaths,
        String authorityPrefix
) {

    public AuthorityMappingProperties {
        roleClaimPaths = roleClaimPaths == null || roleClaimPaths.isEmpty()
                ? List.of("roles", "realm_access.roles", "cognito:groups", "groups")
                : List.copyOf(roleClaimPaths);
        scopeClaimPaths = scopeClaimPaths == null || scopeClaimPaths.isEmpty()
                ? List.of("scope", "scp")
                : List.copyOf(scopeClaimPaths);
        authorityPrefix = authorityPrefix == null ? "" : authorityPrefix;
    }

    public static AuthorityMappingProperties defaults() {
        return new AuthorityMappingProperties(null, null, "");
    }
}
