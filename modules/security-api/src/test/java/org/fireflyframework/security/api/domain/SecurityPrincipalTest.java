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

package org.fireflyframework.security.api.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPrincipalTest {

    @Test
    void normalizesNullCollectionsToEmptyImmutables() {
        SecurityPrincipal p = SecurityPrincipal.builder().subject("u1").build();

        assertThat(p.authorities()).isEmpty();
        assertThat(p.scopes()).isEmpty();
        assertThat(p.claims()).isEmpty();
        assertThat(p.amr()).isEmpty();
        assertThat(p.attributes()).isEmpty();
    }

    @Test
    void authorityHelpersReflectGrantedAuthorities() {
        SecurityPrincipal p = SecurityPrincipal.builder()
                .subject("u1")
                .authorities(Set.of("ROLE_admin", "ROLE_user"))
                .build();

        assertThat(p.hasAuthority("ROLE_admin")).isTrue();
        assertThat(p.hasAuthority("ROLE_missing")).isFalse();
        assertThat(p.hasAnyAuthority(List.of("ROLE_missing", "ROLE_user"))).isTrue();
        assertThat(p.hasAllAuthorities(List.of("ROLE_admin", "ROLE_user"))).isTrue();
        assertThat(p.hasAllAuthorities(List.of("ROLE_admin", "ROLE_missing"))).isFalse();
    }

    @Test
    void scopeAndClaimHelpersWork() {
        SecurityPrincipal p = SecurityPrincipal.builder()
                .subject("u1")
                .scopes(Set.of("read", "write"))
                .claims(Map.of("email", "a@b.com", "ver", 2))
                .build();

        assertThat(p.hasScope("read")).isTrue();
        assertThat(p.hasScope("delete")).isFalse();
        assertThat(p.claim("email", String.class)).isEqualTo("a@b.com");
        assertThat(p.claim("ver", Integer.class)).isEqualTo(2);
        assertThat(p.claim("email", Integer.class)).isNull();
    }

    @Test
    void isImmutable() {
        SecurityPrincipal p = SecurityPrincipal.builder()
                .subject("u1")
                .authorities(Set.of("ROLE_admin"))
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> p.authorities().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
