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

package org.fireflyframework.security.webflux.bridge;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.security.spi.TenantResolverPort;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Derives trusted internal request headers from the <strong>validated token</strong>, never from
 * the client.
 *
 * <p>Runs right after Spring Security has authenticated the request (order {@value #ORDER}): it
 * reads the principal, resolves the tenant via {@link TenantResolverPort} and
 * <strong>overwrites</strong> the configured tenant/user/roles headers on the downstream request
 * before it reaches a controller, so any client-supplied value is discarded. Unauthenticated
 * (permit-all) requests pass through untouched. When a tenant is required but unresolved, the
 * request fails closed with 403.</p>
 */
public class PrincipalHeaderBridgeWebFilter implements WebFilter, Ordered {

    /** After Spring Security's {@code WebFilterChainProxy} (-100), before the controller. */
    public static final int ORDER = -90;

    private final SecurityContextPort securityContext;
    private final TenantResolverPort tenantResolver;
    private final PrincipalHeaderBridgeProperties properties;

    public PrincipalHeaderBridgeWebFilter(SecurityContextPort securityContext,
                                          TenantResolverPort tenantResolver,
                                          PrincipalHeaderBridgeProperties properties) {
        this.securityContext = securityContext;
        this.tenantResolver = tenantResolver;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Map the principal to a mutated exchange, default to the original when there is no principal
        // (permit-all paths), then run the chain exactly once. Flat-mapping onto chain.filter (a
        // Mono<Void> that completes empty) would re-trigger defaultIfEmpty and run the chain twice.
        // The inner Mono fails closed (403) on an unresolvable required tenant.
        return securityContext.currentPrincipal()
                .flatMap(principal -> withTrustedHeaders(exchange, principal))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private Mono<ServerWebExchange> withTrustedHeaders(ServerWebExchange exchange, SecurityPrincipal principal) {
        return tenantResolver.resolveTenant(principal)
                .map(tenant -> mutate(exchange, principal, tenant))
                .switchIfEmpty(Mono.defer(() -> properties.isTenantRequired()
                        ? Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "tenant_resolution_failed: token does not carry exactly one tenant"))
                        : Mono.just(mutate(exchange, principal, null))));
    }

    private ServerWebExchange mutate(ServerWebExchange exchange, SecurityPrincipal principal, String tenant) {
        return exchange.mutate()
                .request(r -> r.headers(headers -> {
                    if (tenant != null) {
                        headers.set(properties.getTenantHeader(), tenant);
                    } else {
                        headers.remove(properties.getTenantHeader());
                    }
                    headers.set(properties.getUserHeader(), principal.subject());
                    headers.set(properties.getRolesHeader(), String.join(",", principal.authorities()));
                }))
                .build();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
