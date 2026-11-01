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


package org.fireflyframework.web.idempotency.scope;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.TenantResolverPort;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/** Uses validated Jafly authentication and tenant resolution, never caller-supplied identity headers. */
public final class AuthenticatedIdempotencyScopeResolverImpl implements IdempotencyScopeResolver {

    private final TenantResolverPort tenantResolver;

    public AuthenticatedIdempotencyScopeResolverImpl() {
        this(null);
    }

    public AuthenticatedIdempotencyScopeResolverImpl(TenantResolverPort tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal().ofType(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .ofType(SecurityPrincipal.class)
                .filter(principal -> present(principal.issuer()) && present(principal.subject()))
                .flatMap(principal -> resolveTenant(principal).filter(this::present)
                        .map(tenant -> namespace(principal, tenant)));
    }

    private Mono<String> resolveTenant(SecurityPrincipal principal) {
        return tenantResolver == null ? Mono.justOrEmpty(principal.tenantId()) : tenantResolver.resolveTenant(principal);
    }

    private String namespace(SecurityPrincipal principal, String tenant) {
        return List.of(principal.issuer(), principal.subject(), tenant,
                        encodeSet(principal.authorities()), encodeSet(principal.scopes())).stream()
                .map(value -> value.length() + ":" + value).collect(Collectors.joining());
    }

    private String encodeSet(java.util.Set<String> values) {
        return values.stream().sorted().map(value -> value.length() + ":" + value).collect(Collectors.joining());
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
