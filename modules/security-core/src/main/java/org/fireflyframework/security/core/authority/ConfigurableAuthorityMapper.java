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
public class ConfigurableAuthorityMapper implements AuthorityMappingPort {

    private final AuthorityMappingProperties properties;

    public ConfigurableAuthorityMapper(AuthorityMappingProperties properties) {
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
            out.addAll(ClaimPaths.readStringSet(claims, path));
        }
        return out;
    }

    private String applyPrefix(String authority) {
        String prefix = properties.authorityPrefix();
        if (prefix.isEmpty() || authority.startsWith(prefix)) {
            return authority;
        }
        return prefix + authority;
    }
}
