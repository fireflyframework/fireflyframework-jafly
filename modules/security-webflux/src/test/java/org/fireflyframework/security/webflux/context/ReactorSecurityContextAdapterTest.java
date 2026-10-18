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

package org.fireflyframework.security.webflux.context;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.test.StepVerifier;

import java.util.Set;

class ReactorSecurityContextAdapterTest {

    private final ReactorSecurityContextAdapter adapter = new ReactorSecurityContextAdapter();

    @Test
    void returnsPrincipalFromReactiveSecurityContext() {
        SecurityPrincipal principal = SecurityPrincipal.builder()
                .subject("u1").authorities(Set.of("admin")).build();
        FireflyAuthenticationToken token = new FireflyAuthenticationToken(principal);

        StepVerifier.create(adapter.currentPrincipal()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token)))
                .expectNextMatches(p -> p.subject().equals("u1") && p.hasAuthority("admin"))
                .verifyComplete();
    }

    @Test
    void emptyWhenNoSecurityContext() {
        StepVerifier.create(adapter.currentPrincipal()).verifyComplete();
    }
}
