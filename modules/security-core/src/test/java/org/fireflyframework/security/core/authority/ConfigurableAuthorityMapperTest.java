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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurableAuthorityMapperTest {

    private final ConfigurableAuthorityMapper mapper =
            new ConfigurableAuthorityMapper(AuthorityMappingProperties.defaults());

    @Test
    void mapsTopLevelRolesArray() {
        Map<String, Object> claims = Map.of("roles", List.of("admin", "user"));
        assertThat(mapper.mapAuthorities(claims)).containsExactlyInAnyOrder("admin", "user");
    }

    @Test
    void mapsKeycloakRealmAccessRoles() {
        Map<String, Object> claims = Map.of("realm_access", Map.of("roles", List.of("teller", "manager")));
        assertThat(mapper.mapAuthorities(claims)).contains("teller", "manager");
    }

    @Test
    void mapsCognitoGroups() {
        Map<String, Object> claims = Map.of("cognito:groups", List.of("Admins"));
        assertThat(mapper.mapAuthorities(claims)).contains("Admins");
    }

    @Test
    void mapsSpaceDelimitedScopeString() {
        Map<String, Object> claims = Map.of("scope", "read write admin");
        assertThat(mapper.mapScopes(claims)).containsExactlyInAnyOrder("read", "write", "admin");
    }

    @Test
    void appliesAuthorityPrefixWithoutDoublePrefixing() {
        ConfigurableAuthorityMapper prefixed = new ConfigurableAuthorityMapper(
                new AuthorityMappingProperties(List.of("roles"), List.of("scp"), "ROLE_"));
        Map<String, Object> claims = Map.of("roles", List.of("admin", "ROLE_existing"));
        assertThat(prefixed.mapAuthorities(claims)).containsExactlyInAnyOrder("ROLE_admin", "ROLE_existing");
    }
}
