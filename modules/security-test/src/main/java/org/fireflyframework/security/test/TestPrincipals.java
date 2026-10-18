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

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;

import java.util.Set;

/**
 * Factory helpers for building {@link SecurityPrincipal}s and {@link FireflyAuthenticationToken}s in
 * tests, so application test code does not hand-roll principals or wrestle with the security context.
 */
public final class TestPrincipals {

    private TestPrincipals() {
    }

    /** A principal with the given subject and authorities. */
    public static SecurityPrincipal user(String subject, String... authorities) {
        return SecurityPrincipal.builder().subject(subject).authorities(Set.of(authorities)).build();
    }

    /** A principal carrying both authorities and OAuth2 scopes. */
    public static SecurityPrincipal user(String subject, Set<String> authorities, Set<String> scopes) {
        return SecurityPrincipal.builder().subject(subject).authorities(authorities).scopes(scopes).build();
    }

    /** An authenticated {@link FireflyAuthenticationToken} for use with ReactiveSecurityContextHolder. */
    public static FireflyAuthenticationToken authentication(String subject, String... authorities) {
        return new FireflyAuthenticationToken(user(subject, authorities));
    }
}
