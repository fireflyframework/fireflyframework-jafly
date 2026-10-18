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

package org.fireflyframework.security.core.context;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.authority.ClaimPaths;
import org.fireflyframework.security.spi.AuthorityMappingPort;

import java.time.Instant;
import java.util.Map;

/**
 * Projects a validated set of token claims into a {@link SecurityPrincipal}, normalizing authorities
 * and scopes through the configured {@link AuthorityMappingPort}. Stack-neutral: it accepts a plain
 * claims map so it works for both JWT and opaque-introspection paths.
 */
public class PrincipalFactory {

    private final AuthorityMappingPort authorityMapper;

    public PrincipalFactory(AuthorityMappingPort authorityMapper) {
        this.authorityMapper = authorityMapper;
    }

    public SecurityPrincipal fromClaims(String subject, String issuer, Map<String, Object> claims) {
        Map<String, Object> safe = claims == null ? Map.of() : claims;
        return SecurityPrincipal.builder()
                .subject(subject)
                .issuer(issuer)
                .authorities(authorityMapper.mapAuthorities(safe))
                .scopes(authorityMapper.mapScopes(safe))
                .claims(safe)
                .authTime(readInstant(safe.get("auth_time")))
                .acr(asString(safe.get("acr")))
                .amr(ClaimPaths.toStringSet(safe.get("amr")))
                .build();
    }

    private static Instant readInstant(Object value) {
        if (value instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue());
        }
        if (value instanceof Instant i) {
            return i;
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
