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

package org.fireflyframework.security.webflux.authz;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.policy.EmbeddedPolicyDecisionAdapterImpl;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

class PolicyAuthorizationManagerTest {

    private AuthorizationContext getRequest(String path) {
        return new AuthorizationContext(MockServerWebExchange.from(MockServerHttpRequest.get(path)));
    }

    private FireflyAuthenticationToken authenticated() {
        return new FireflyAuthenticationToken(SecurityPrincipal.builder().subject("u1").build());
    }

    @Test
    void deniesUnauthenticatedRequests() {
        PolicyAuthorizationManagerImpl manager = new PolicyAuthorizationManagerImpl(new EmbeddedPolicyDecisionAdapterImpl(List.of()));
        StepVerifier.create(manager.authorize(Mono.empty(), getRequest("/api/x")))
                .expectNextMatches(d -> !d.isGranted())
                .verifyComplete();
    }

    @Test
    void permitsAuthenticatedWhenPolicyAbstains() {
        PolicyAuthorizationManagerImpl manager = new PolicyAuthorizationManagerImpl(new EmbeddedPolicyDecisionAdapterImpl(List.of()));
        StepVerifier.create(manager.authorize(Mono.just(authenticated()), getRequest("/api/x")))
                .expectNextMatches(AuthorizationResult::isGranted)
                .verifyComplete();
    }

    @Test
    void deniesAuthenticatedWhenPolicyDenies() {
        PolicyDecisionPort denyAll = (p, a, r, c) -> Mono.just(Decision.deny("blocked"));
        PolicyAuthorizationManagerImpl manager = new PolicyAuthorizationManagerImpl(denyAll);
        StepVerifier.create(manager.authorize(Mono.just(authenticated()), getRequest("/api/x")))
                .expectNextMatches(d -> !d.isGranted())
                .verifyComplete();
    }

    @Test
    void failsClosedOnPolicyError() {
        PolicyDecisionPort boom = (p, a, r, c) -> Mono.error(new IllegalStateException("pdp down"));
        PolicyAuthorizationManagerImpl manager = new PolicyAuthorizationManagerImpl(boom);
        StepVerifier.create(manager.authorize(Mono.just(authenticated()), getRequest("/api/x")))
                .expectNextMatches(d -> !d.isGranted())
                .verifyComplete();
    }
}
