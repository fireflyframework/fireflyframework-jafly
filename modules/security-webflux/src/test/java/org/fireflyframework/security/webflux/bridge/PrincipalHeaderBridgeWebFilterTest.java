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
import org.fireflyframework.security.core.tenant.ConfigurableTenantResolver;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.security.spi.TenantResolverPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalHeaderBridgeWebFilterTest {

    private final PrincipalHeaderBridgeProperties properties = new PrincipalHeaderBridgeProperties();
    private final TenantResolverPort tenantResolver = new ConfigurableTenantResolver(List.of("tenant_ids"));

    /** Records how many times it ran and the exchange it saw — to assert single execution + mutation. */
    private static final class CapturingChain implements WebFilterChain {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            calls.incrementAndGet();
            captured.set(exchange);
            return Mono.empty();
        }
    }

    private static SecurityContextPort principalPort(SecurityPrincipal principal) {
        return () -> principal == null ? Mono.empty() : Mono.just(principal);
    }

    private PrincipalHeaderBridgeWebFilter filter(SecurityContextPort port) {
        return new PrincipalHeaderBridgeWebFilter(port, tenantResolver, properties);
    }

    @Test
    void overwritesTrustedHeadersFromToken() {
        SecurityPrincipal principal = SecurityPrincipal.builder()
                .subject("user-1")
                .authorities(Set.of("idp-admin"))
                .claims(Map.of("tenant_ids", List.of("tenant-a")))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").header("X-Tenant-Id", "evil")); // client spoof
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter(principalPort(principal)).filter(exchange, chain)).verifyComplete();

        assertThat(chain.calls).hasValue(1);
        HttpHeaders headers = chain.captured.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("tenant-a"); // spoof overwritten
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(headers.getFirst("X-User-Roles")).isEqualTo("idp-admin");
    }

    @Test
    void failsClosedWhenTenantUnresolved() {
        SecurityPrincipal principal = SecurityPrincipal.builder()
                .subject("user-1")
                .claims(Map.of("tenant_ids", List.of("a", "b"))) // >1 -> empty -> 403
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/x"));
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter(principalPort(principal)).filter(exchange, chain))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403))
                .verify();

        assertThat(chain.calls).hasValue(0); // fail-closed: chain never runs
    }

    @Test
    void passesThroughUntouchedWhenUnauthenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").header("X-Tenant-Id", "client"));
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter(principalPort(null)).filter(exchange, chain)).verifyComplete();

        assertThat(chain.calls).hasValue(1);
        assertThat(chain.captured.get().getRequest().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("client");
    }
}
