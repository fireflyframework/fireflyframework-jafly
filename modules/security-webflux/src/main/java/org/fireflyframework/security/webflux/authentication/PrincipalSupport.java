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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Recovers a {@link SecurityPrincipal} from any Spring {@link Authentication}. Prefers the rich
 * principal carried by {@link FireflyAuthenticationToken}; otherwise builds a minimal principal
 * from the authentication's name and authorities (covers third-party {@code Authentication}s).
 */
public final class PrincipalSupport {

    private PrincipalSupport() {
    }

    public static SecurityPrincipal extract(Authentication authentication) {
        if (authentication instanceof FireflyAuthenticationToken token) {
            return token.securityPrincipal();
        }
        if (authentication != null && authentication.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal;
        }
        Set<String> authorities = new LinkedHashSet<>();
        if (authentication != null) {
            for (GrantedAuthority ga : authentication.getAuthorities()) {
                authorities.add(ga.getAuthority());
            }
        }
        return SecurityPrincipal.builder()
                .subject(authentication == null ? null : authentication.getName())
                .authorities(authorities)
                .build();
    }
}
