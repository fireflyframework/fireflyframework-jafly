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

package org.fireflyframework.security.test;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityTestFixturesTest {

    @Test
    void buildsPrincipalsAndAuthentications() {
        assertThat(TestPrincipals.user("u1", "admin").hasAuthority("admin")).isTrue();
        assertThat(TestPrincipals.authentication("u1", "admin").securityPrincipal().subject()).isEqualTo("u1");
        assertThat(TestPrincipals.authentication("u1", "admin").isAuthenticated()).isTrue();
    }

    @Test
    void fakePolicyDecisionPortReturnsConfiguredDecisionAndRecordsRequest() {
        FakePolicyDecisionPort permit = FakePolicyDecisionPort.permitAll();
        StepVerifier.create(permit.authorize(TestPrincipals.user("u1"), "read", "doc:1", Map.of()))
                .expectNextMatches(d -> d.granted())
                .verifyComplete();
        assertThat(permit.lastAction()).isEqualTo("read");
        assertThat(permit.lastResource()).isEqualTo("doc:1");

        FakePolicyDecisionPort deny = FakePolicyDecisionPort.denyAll("nope");
        StepVerifier.create(deny.authorize(TestPrincipals.user("u1"), "write", "doc:1", Map.of()))
                .expectNextMatches(d -> !d.granted())
                .verifyComplete();
    }
}
