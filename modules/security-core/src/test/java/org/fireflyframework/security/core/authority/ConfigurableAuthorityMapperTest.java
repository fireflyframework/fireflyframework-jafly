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

    private final ConfigurableAuthorityMapperImpl mapper =
            new ConfigurableAuthorityMapperImpl(AuthorityMappingProperties.defaults());

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
        ConfigurableAuthorityMapperImpl prefixed = new ConfigurableAuthorityMapperImpl(
                new AuthorityMappingProperties(List.of("roles"), List.of("scp"), "ROLE_"));
        Map<String, Object> claims = Map.of("roles", List.of("admin", "ROLE_existing"));
        assertThat(prefixed.mapAuthorities(claims)).containsExactlyInAnyOrder("ROLE_admin", "ROLE_existing");
    }

    @Test
    void commaInsideScopeTokenDoesNotGrantSeparateScopes() {
        assertThat(mapper.mapScopes(Map.of("scope", "funds:reserve,funds:read")))
                .containsExactly("funds:reserve,funds:read")
                .doesNotContain("funds:reserve", "funds:read");
    }

    @Test
    void configuredScpStringAlsoKeepsCommaTokensOpaque() {
        assertThat(mapper.mapScopes(Map.of("scp", "accounts:read,accounts:write")))
                .containsExactly("accounts:read,accounts:write");
    }

    @Test
    void malformedScalarScopeClaimCannotGrantPrefixOrSuffixScopes() {
        for (String scope : List.of("read\twrite", "read\nwrite", "read\rwrite", "read\fwrite",
                "read" + (char) 0x0b + "write", "read\u00a0write", "read\"write", "read\\write",
                "read" + (char) 0x7f + "write", "read write\nadmin")) {
            assertThat(mapper.mapScopes(Map.of("scope", scope))).isEmpty();
        }
    }

    @Test
    void existingScopeArraysStayOpaqueAndScpArraysRemainSupported() {
        Map<String, Object> claims = Map.of(
                "scope", List.of("read,write", "opaque member", "read\twrite"),
                "scp", List.of("profile", "email"));
        assertThat(mapper.mapScopes(claims)).containsExactlyInAnyOrder(
                "read,write", "opaque member", "read\twrite", "profile", "email");
    }

    @Test
    void asciiSpacesPreserveExistingPaddingDeduplicationAndCaseSensitivity() {
        assertThat(mapper.mapScopes(Map.of("scope", "  read   write read READ  ")))
                .containsExactlyInAnyOrder("read", "write", "READ");
    }

    @Test
    void genericRoleStringDelimitersRemainUnchanged() {
        assertThat(mapper.mapAuthorities(Map.of("roles", "admin,auditor support\tviewer")))
                .containsExactlyInAnyOrder("admin", "auditor", "support", "viewer");
    }

}
