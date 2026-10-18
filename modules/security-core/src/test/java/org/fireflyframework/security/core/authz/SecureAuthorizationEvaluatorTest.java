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

package org.fireflyframework.security.core.authz;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecureAuthorizationEvaluatorTest {

    private final SecureAuthorizationEvaluator evaluator = new SecureAuthorizationEvaluator();

    private SecurityPrincipal principal(Set<String> authorities, Set<String> scopes) {
        return SecurityPrincipal.builder().subject("u1").authorities(authorities).scopes(scopes).build();
    }

    @Test
    void deniesNullPrincipal() {
        SecureRequirement req = new SecureRequirement(List.of("admin"), List.of(), List.of(), false, false, false, "");
        assertThat(evaluator.evaluate(null, req).granted()).isFalse();
    }

    @Test
    void anyRoleMatchesByDefault() {
        SecureRequirement req = new SecureRequirement(List.of("admin", "ops"), List.of(), List.of(), false, false, false, "");
        assertThat(evaluator.evaluate(principal(Set.of("ops"), Set.of()), req).granted()).isTrue();
    }

    @Test
    void requireAllRolesEnforcesAndSemantics() {
        SecureRequirement req = new SecureRequirement(List.of("admin", "ops"), List.of(), List.of(), true, false, false, "");
        assertThat(evaluator.evaluate(principal(Set.of("ops"), Set.of()), req).granted()).isFalse();
        assertThat(evaluator.evaluate(principal(Set.of("ops", "admin"), Set.of()), req).granted()).isTrue();
    }

    @Test
    void dimensionsAreAndedTogether() {
        SecureRequirement req = new SecureRequirement(List.of("admin"), List.of("write"), List.of(), false, false, false, "");
        assertThat(evaluator.evaluate(principal(Set.of("admin"), Set.of("read")), req).granted()).isFalse();
        assertThat(evaluator.evaluate(principal(Set.of("admin"), Set.of("write")), req).granted()).isTrue();
    }

    @Test
    void emptyRequirementPermitsAuthenticatedPrincipal() {
        SecureRequirement req = new SecureRequirement(List.of(), List.of(), List.of(), false, false, false, "");
        assertThat(evaluator.evaluate(principal(Set.of(), Set.of()), req).granted()).isTrue();
    }

    @Test
    void spelExpressionIsEvaluatedAgainstPrincipal() {
        SecureRequirement ok = new SecureRequirement(List.of(), List.of(), List.of(), false, false, false,
                "hasAuthority('admin')");
        SecureRequirement no = new SecureRequirement(List.of(), List.of(), List.of(), false, false, false,
                "hasAuthority('missing')");
        assertThat(evaluator.evaluate(principal(Set.of("admin"), Set.of()), ok).granted()).isTrue();
        assertThat(evaluator.evaluate(principal(Set.of("admin"), Set.of()), no).granted()).isFalse();
    }

    @Test
    void malformedExpressionFailsClosed() {
        SecureRequirement bad = new SecureRequirement(List.of(), List.of(), List.of(), false, false, false,
                "this is not valid spel (((");
        assertThat(evaluator.evaluate(principal(Set.of("admin"), Set.of()), bad).granted()).isFalse();
    }
}
