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

import org.fireflyframework.security.spi.AuthorityMappingPort;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * IdP-agnostic {@link AuthorityMappingPort}: extracts roles/groups and scopes from configured claim
 * paths and normalizes them into framework authorities. Replaces the duplicated, dead
 * {@code JwtClaimsRoleExtractor} traversal that previously lived in the starter.
 */
public class ConfigurableAuthorityMapperImpl implements AuthorityMappingPort {

    private final AuthorityMappingProperties properties;

    public ConfigurableAuthorityMapperImpl(AuthorityMappingProperties properties) {
        this.properties = properties == null ? AuthorityMappingProperties.defaults() : properties;
    }

    @Override
    public Set<String> mapAuthorities(Map<String, Object> claims) {
        Set<String> out = new LinkedHashSet<>();
        for (String path : properties.roleClaimPaths()) {
            for (String role : ClaimPaths.readStringSet(claims, path)) {
                out.add(applyPrefix(role));
            }
        }
        return out;
    }

    @Override
    public Set<String> mapScopes(Map<String, Object> claims) {
        Set<String> out = new LinkedHashSet<>();
        for (String path : properties.scopeClaimPaths()) {
            out.addAll(scopeValues(ClaimPaths.read(claims, path)));
        }
        return out;
    }

    private static Set<String> scopeValues(Object value) {
        if (!(value instanceof String scope)) {
            return ClaimPaths.toStringSet(value);
        }
        // RFC 6749 scope-token permits commas; only ASCII SP separates scopes.
        for (int i = 0; i < scope.length(); i++) {
            char character = scope.charAt(i);
            if (character < 0x20 || character > 0x7e || character == '"' || character == '\\') {
                return Set.of();
            }
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String token : scope.split(" ")) {
            if (!token.isEmpty()) {
                scopes.add(token);
            }
        }
        return scopes;
    }

    private String applyPrefix(String authority) {
        String prefix = properties.authorityPrefix();
        if (prefix.isEmpty() || authority.startsWith(prefix)) {
            return authority;
        }
        return prefix + authority;
    }
}
