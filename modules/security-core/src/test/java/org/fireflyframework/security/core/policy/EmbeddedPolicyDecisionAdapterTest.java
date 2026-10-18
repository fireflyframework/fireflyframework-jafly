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

package org.fireflyframework.security.core.policy;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

class EmbeddedPolicyDecisionAdapterTest {

    private final SecurityPrincipal p = SecurityPrincipal.builder().subject("u1").build();

    private void assertGranted(EmbeddedPolicyDecisionAdapter pdp, boolean expected) {
        StepVerifier.create(pdp.authorize(p, "read", "doc:1", Map.of()))
                .expectNextMatches(d -> d.granted() == expected)
                .verifyComplete();
    }

    @Test
    void noRulesPermitsToDeferToRbac() {
        assertGranted(new EmbeddedPolicyDecisionAdapter(List.of()), true);
    }

    @Test
    void denyOverridesPermit() {
        EmbeddedPolicyDecisionAdapter pdp = new EmbeddedPolicyDecisionAdapter(List.of(
                (pr, a, r, c) -> Mono.just(Decision.permit()),
                (pr, a, r, c) -> Mono.just(Decision.deny("blocked"))));
        assertGranted(pdp, false);
    }

    @Test
    void permitsWhenARulePermitsAndNoneDeny() {
        EmbeddedPolicyDecisionAdapter pdp = new EmbeddedPolicyDecisionAdapter(List.of(
                (pr, a, r, c) -> Mono.just(Decision.indeterminate("n/a")),
                (pr, a, r, c) -> Mono.just(Decision.permit())));
        assertGranted(pdp, true);
    }

    @Test
    void allAbstainDeniesFailClosed() {
        EmbeddedPolicyDecisionAdapter pdp = new EmbeddedPolicyDecisionAdapter(List.of(
                (pr, a, r, c) -> Mono.just(Decision.indeterminate("n/a"))));
        assertGranted(pdp, false);
    }

    @Test
    void ruleErrorIsTreatedAsDenyNotPermit() {
        EmbeddedPolicyDecisionAdapter pdp = new EmbeddedPolicyDecisionAdapter(List.of(
                (pr, a, r, c) -> Mono.error(new IllegalStateException("boom"))));
        assertGranted(pdp, false);
    }
}
