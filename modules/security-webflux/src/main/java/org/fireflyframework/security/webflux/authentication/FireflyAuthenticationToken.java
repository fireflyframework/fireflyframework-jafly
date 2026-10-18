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

package org.fireflyframework.security.webflux.authentication;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Spring Security {@link org.springframework.security.core.Authentication} whose principal is the
 * framework's {@link SecurityPrincipal}. Authorities are derived from the principal so Spring's
 * {@code hasAuthority(...)} and {@code authorizeExchange().hasRole(...)} work natively, while
 * application code can still recover the rich principal.
 */
public class FireflyAuthenticationToken extends AbstractAuthenticationToken {

    private final transient SecurityPrincipal principal;

    public FireflyAuthenticationToken(SecurityPrincipal principal) {
        super(principal.authorities().stream().map(SimpleGrantedAuthority::new).toList());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public SecurityPrincipal securityPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.subject();
    }
}
