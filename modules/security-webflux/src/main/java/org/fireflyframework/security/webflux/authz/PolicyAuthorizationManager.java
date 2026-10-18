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

import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.webflux.authentication.PrincipalSupport;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * URL policy enforcement point. Denies unauthenticated requests (secure-by-default) and, for
 * authenticated requests, delegates the ABAC decision to the {@link PolicyDecisionPort} using the
 * HTTP method as the action and the request path as the resource. Fail-closed: any error denies.
 */
public class PolicyAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final PolicyDecisionPort policyDecisionPort;

    public PolicyAuthorizationManager(PolicyDecisionPort policyDecisionPort) {
        this.policyDecisionPort = policyDecisionPort;
    }

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        ServerHttpRequest request = context.getExchange().getRequest();
        String action = request.getMethod().name();
        String resource = request.getPath().value();
        Map<String, Object> ctx = Map.of("method", action, "path", resource);

        return authentication
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> policyDecisionPort
                        .authorize(PrincipalSupport.extract(auth), action, resource, ctx)
                        .map(decision -> new AuthorizationDecision(decision.granted())))
                .onErrorReturn(new AuthorizationDecision(false))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
