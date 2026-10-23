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


package org.fireflyframework.web.idempotency.filter;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.web.idempotency.cache.TestIdempotencyCache;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedIdempotencyWebFilterTest {
    private final TestIdempotencyCache cache = new TestIdempotencyCache();
    private final IdempotencyWebFilter filter = new IdempotencyWebFilter(properties("service-a"), cache);
    private final AtomicInteger effects = new AtomicInteger();
    private final WebFilterChain writer = exchange -> {
        effects.incrementAndGet();
        return respond(exchange, HttpStatus.CREATED, exchange.getRequest().getMethod() + " " + exchange.getRequest().getURI().getRawPath());
    };

    @Test void sameKeyOnAnotherRouteExecutesThatRoute() {
        run(request("POST", "/api/headers", "{}"), writer);
        var second = request("POST", "/api/legs", "{}");
        run(second, writer);
        assertThat(second.getResponse().getBodyAsString().block()).isEqualTo("POST /api/legs");
        assertThat(effects.get()).isEqualTo(2);
    }

    @Test void safeReadDoesNotReplayOrPopulateMutationCache() {
        run(request("POST", "/api/headers", "{}"), writer);
        var get = request("GET", "/api/headers", "");
        run(get, writer);
        run(request("GET", "/api/headers", ""), writer);
        assertThat(get.getResponse().getBodyAsString().block()).isEqualTo("GET /api/headers");
        assertThat(effects.get()).isEqualTo(3);
    }

    @Test void mutationMethodsAndQueryStringsHaveSeparateScopes() {
        run(request("POST", "/api/headers?a=1", "{}"), writer);
        run(request("PATCH", "/api/headers?a=1", "{}"), writer);
        run(request("POST", "/api/headers?a=2", "{}"), writer);
        assertThat(effects.get()).isEqualTo(3);
    }

    @Test void changedPayloadReturnsConflictBeforeAnotherEffect() {
        run(request("POST", "/api/headers", "{\"amount\":1}"), writer);
        var changed = request("POST", "/api/headers", "{\"amount\":2}");
        run(changed, writer);
        assertThat(changed.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void identicalPayloadReplaysResponseAndLocation() {
        WebFilterChain create = exchange -> {
            effects.incrementAndGet();
            exchange.getResponse().getHeaders().setLocation(java.net.URI.create("/api/headers/created"));
            return respond(exchange, HttpStatus.CREATED, "created");
        };
        run(request("POST", "/api/headers", "{}"), create);
        var duplicate = request("POST", "/api/headers", "{}");
        run(duplicate, create);
        assertThat(duplicate.getResponse().getBodyAsString().block()).isEqualTo("created");
        assertThat(duplicate.getResponse().getHeaders().getLocation()).isEqualTo(java.net.URI.create("/api/headers/created"));
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void authenticatedTenantAndSubjectAndIssuerAreIsolated() {
        run(request("POST", "/api/headers", "{}", "alice", "tenant-a", "issuer-a"), writer);
        run(request("POST", "/api/headers", "{}", "alice", "tenant-b", "issuer-a"), writer);
        run(request("POST", "/api/headers", "{}", "bob", "tenant-a", "issuer-a"), writer);
        run(request("POST", "/api/headers", "{}", "alice", "tenant-a", "issuer-b"), writer);
        assertThat(effects.get()).isEqualTo(4);
    }

    @Test void anonymousRequestsDoNotShareResponseCache() {
        var first = MockServerWebExchange.from(MockServerHttpRequest.post("/api/headers").header("X-Idempotency-Key", "shared").build());
        var second = MockServerWebExchange.from(MockServerHttpRequest.post("/api/headers").header("X-Idempotency-Key", "shared").build());
        run(first, writer); run(second, writer);
        assertThat(effects.get()).isEqualTo(2);
    }

    @Test void requestBodyRemainsReadableByController() {
        WebFilterChain echo = exchange -> org.springframework.core.io.buffer.DataBufferUtils.join(exchange.getRequest().getBody())
            .flatMap(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()]; buffer.read(bytes);
                org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
                return respond(exchange, HttpStatus.OK, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            });
        var exchange = request("POST", "/api/headers", "{\"amount\":1}");
        run(exchange, echo);
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("{\"amount\":1}");
    }

    @Test void emptySuccessIsReplayedAndConcurrentWaitersComplete() {
        Sinks.One<Void> gate = Sinks.one();
        WebFilterChain empty = exchange -> {
            effects.incrementAndGet();
            return gate.asMono().then(Mono.defer(() -> { exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT); return exchange.getResponse().setComplete(); }));
        };
        var first = request("DELETE", "/api/headers/1", "");
        var duplicate = request("DELETE", "/api/headers/1", "");
        var a = filter.filter(first, empty).toFuture();
        var b = filter.filter(duplicate, empty).toFuture();
        gate.tryEmitEmpty();
        Mono.when(Mono.fromFuture(a), Mono.fromFuture(b)).block(Duration.ofSeconds(2));
        assertThat(duplicate.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var replay = request("DELETE", "/api/headers/1", ""); run(replay, empty);
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void concurrentChangedPayloadIsRejectedBeforeFirstResponse() {
        Sinks.One<Void> gate = Sinks.one();
        var first = filter.filter(request("POST", "/api/headers", "one"), exchange -> {
            effects.incrementAndGet(); return gate.asMono().then(Mono.defer(() -> respond(exchange, HttpStatus.CREATED, "created")));
        }).toFuture();
        var conflicting = request("POST", "/api/headers", "two");
        run(conflicting, writer);
        assertThat(conflicting.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        gate.tryEmitEmpty(); Mono.fromFuture(first).block(Duration.ofSeconds(2));
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void serverErrorIsNotPersistedForLaterRetry() {
        run(request("POST", "/api/headers", "{}"), exchange -> respond(exchange, HttpStatus.SERVICE_UNAVAILABLE, "unavailable"));
        var retry = request("POST", "/api/headers", "{}"); run(retry, writer);
        assertThat(retry.getResponse().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void separateApplicationsCannotReplayFromTheSameBackend() {
        run(request("POST", "/api/headers", "{}"), writer);
        var other = new IdempotencyWebFilter(properties("service-b"), cache);
        other.filter(request("POST", "/api/headers", "{}"), writer).block(Duration.ofSeconds(2));
        assertThat(effects.get()).isEqualTo(2);
    }

    @Test void missingApplicationNamespaceBypassesReuse() {
        var unresolved = new IdempotencyWebFilter(new IdempotencyProperties(), cache);
        unresolved.filter(request("POST", "/api/headers", "{}"), writer).block(Duration.ofSeconds(2));
        unresolved.filter(request("POST", "/api/headers", "{}"), writer).block(Duration.ofSeconds(2));
        assertThat(effects.get()).isEqualTo(2);
    }

    @Test void suppliedTenantHeadersDoNotChangeTheTrustedIdentity() {
        run(request("POST", "/api/headers", "{}"), writer);
        var forged = request("POST", "/api/headers", "{}").mutate()
                .request(builder -> builder.header("X-Tenant-Id", "another-tenant")).build();
        run(forged, writer);
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void concurrentIdenticalRequestsExecuteOnce() {
        Sinks.One<Void> gate = Sinks.one();
        WebFilterChain delayed = exchange -> {
            effects.incrementAndGet();
            return gate.asMono().then(Mono.defer(() -> respond(exchange, HttpStatus.CREATED, "created")));
        };
        var requests = java.util.stream.IntStream.range(0, 8).mapToObj(i -> request("POST", "/api/headers", "{}")).toList();
        var futures = requests.stream().map(exchange -> filter.filter(exchange, delayed).toFuture()).toList();
        gate.tryEmitEmpty();
        Mono.when(futures.stream().map(Mono::fromFuture).toList()).block(Duration.ofSeconds(2));
        assertThat(effects.get()).isEqualTo(1);
        assertThat(requests).allSatisfy(exchange -> assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("created"));
    }

    @Test void failureReleasesWaitersAndAllowsLaterRetry() {
        Sinks.One<Void> gate = Sinks.one();
        var first = filter.filter(request("POST", "/api/headers", "{}"), exchange -> gate.asMono().then(Mono.error(new IllegalStateException("synthetic failure")))).toFuture();
        var second = filter.filter(request("POST", "/api/headers", "{}"), writer).toFuture();
        gate.tryEmitEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Mono.fromFuture(first).block(Duration.ofSeconds(2))).hasMessage("synthetic failure");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Mono.fromFuture(second).block(Duration.ofSeconds(2))).hasMessage("synthetic failure");
        run(request("POST", "/api/headers", "{}"), writer);
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void cancellationReleasesExistingWaiters() {
        var first = filter.filter(request("POST", "/api/headers", "{}"), exchange -> Mono.never()).toFuture();
        var second = filter.filter(request("POST", "/api/headers", "{}"), writer).toFuture();
        first.cancel(true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Mono.fromFuture(second).block(Duration.ofSeconds(2))).isInstanceOf(java.util.concurrent.CancellationException.class);
        run(request("POST", "/api/headers", "{}"), writer);
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void oversizedRequestCannotReachTheHandler() {
        var small = properties("small"); small.setMaxRequestBodyBytes(3);
        var bounded = new IdempotencyWebFilter(small, cache);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bounded.filter(request("POST", "/api/headers", "four"), writer).block(Duration.ofSeconds(2)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(error -> assertThat(((org.springframework.web.server.ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        assertThat(effects.get()).isZero();
    }

    @Test void representationAndPreconditionChangesConflict() {
        run(request("POST", "/api/headers", "{}"), writer);
        var changed = request("POST", "/api/headers", "{}").mutate().request(builder -> builder.header("If-Match", "version-2")).build();
        run(changed, writer);
        assertThat(changed.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void differentAuthoritySetsCannotCollideThroughListFormatting() {
        var actor = SecurityPrincipal.builder().issuer("issuer-a").subject("alice").tenantId("tenant-a")
                .authorities(java.util.Set.of("read, write")).build();
        var different = actor.toBuilder().authorities(java.util.Set.of("read", "write")).build();
        var first = request("POST", "/api/headers", "{}").mutate().principal(Mono.just(new UsernamePasswordAuthenticationToken(actor, "", List.of()))).build();
        var second = request("POST", "/api/headers", "{}").mutate().principal(Mono.just(new UsernamePasswordAuthenticationToken(different, "", List.of()))).build();
        run(first, writer); run(second, writer);
        assertThat(effects.get()).isEqualTo(2);
    }

    @Test void trustedTenantResolverSupportsPrincipalsWhoseTenantLivesInValidatedClaims() {
        var scope = new org.fireflyframework.web.idempotency.scope.AuthenticatedIdempotencyScopeResolver(principal ->
                Mono.justOrEmpty(principal.claim("validated-tenant", String.class)));
        var resolved = new IdempotencyWebFilter(properties("service-a"), cache, scope);
        var actor = SecurityPrincipal.builder().issuer("issuer-a").subject("alice")
                .claims(java.util.Map.of("validated-tenant", "tenant-a")).build();
        var auth = new UsernamePasswordAuthenticationToken(actor, "", List.of());
        var first = request("POST", "/api/headers", "{}").mutate().principal(Mono.just(auth)).build();
        var second = request("POST", "/api/headers", "{}").mutate().principal(Mono.just(auth)).build();
        resolved.filter(first, writer).block(); resolved.filter(second, writer).block();
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void legacyRawKeyEntriesAreNeverReplayed() {
        cache.putSync("shared", new org.fireflyframework.web.idempotency.model.CachedResponse(201, "legacy".getBytes(), "text/plain"));
        var request = request("POST", "/api/headers", "{}"); run(request, writer);
        assertThat(request.getResponse().getBodyAsString().block()).isEqualTo("POST /api/headers");
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void anotherFilterInstanceReadsTheSameBackendEntry() {
        run(request("POST", "/api/headers", "{}"), writer);
        var restarted = new IdempotencyWebFilter(properties("service-a"), cache);
        var duplicate = request("POST", "/api/headers", "{}");
        restarted.filter(duplicate, writer).block(Duration.ofSeconds(2));
        assertThat(duplicate.getResponse().getBodyAsString().block()).isEqualTo("POST /api/headers");
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void awaitingCacheWriteKeepsConcurrentRequestsOnTheOriginalEffect() {
        Sinks.One<Void> persist = Sinks.one();
        var delayedCache = new org.fireflyframework.web.idempotency.cache.IdempotencyCache() {
            public Mono<org.fireflyframework.web.idempotency.model.CachedResponse> get(String key) { return cache.get(key); }
            public Mono<Void> put(String key, org.fireflyframework.web.idempotency.model.CachedResponse response) {
                return persist.asMono().then(Mono.defer(() -> cache.put(key, response)));
            }
        };
        var waiting = new IdempotencyWebFilter(properties("service-a"), delayedCache);
        var first = waiting.filter(request("POST", "/api/headers", "{}"), writer).toFuture();
        var duplicate = waiting.filter(request("POST", "/api/headers", "{}"), writer).toFuture();
        assertThat(first).isNotDone(); assertThat(duplicate).isNotDone();
        assertThat(effects.get()).isEqualTo(1);
        persist.tryEmitEmpty();
        Mono.when(Mono.fromFuture(first), Mono.fromFuture(duplicate)).block(Duration.ofSeconds(2));
        run(request("POST", "/api/headers", "{}"), writer);
        assertThat(effects.get()).isEqualTo(1);
    }

    @Test void oversizedResponseDoesNotLeaveAnInFlightClaim() {
        var small = properties("small-response"); small.setMaxResponseBodyBytes(3);
        var bounded = new IdempotencyWebFilter(small, cache);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bounded.filter(request("POST", "/api/headers", "{}"), writer).block(Duration.ofSeconds(2)))
                .isInstanceOf(org.springframework.core.io.buffer.DataBufferLimitException.class);
        assertThat(effects.get()).isEqualTo(1);
        bounded.filter(request("POST", "/api/headers", "{}"), exchange -> respond(exchange, HttpStatus.OK, "ok")).block(Duration.ofSeconds(2));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"X-Synthetic-Flavour", "*"})
    void concurrentWaiterCannotReplayAnUnsupportedVaryRepresentation(String vary) {
        Sinks.One<Void> gate = Sinks.one();
        WebFilterChain varying = exchange -> {
            effects.incrementAndGet();
            exchange.getResponse().getHeaders().set("Vary", vary);
            return gate.asMono().then(Mono.defer(() -> respond(exchange, HttpStatus.CREATED, "first variant")));
        };
        var original = request("POST", "/api/headers", "{}");
        var duplicate = request("POST", "/api/headers", "{}");
        var first = filter.filter(original, varying).toFuture();
        var second = filter.filter(duplicate, varying).toFuture();
        gate.tryEmitEmpty();
        Mono.when(Mono.fromFuture(first), Mono.fromFuture(second)).block(Duration.ofSeconds(2));
        assertThat(original.getResponse().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicate.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(effects.get()).isEqualTo(1);
        run(request("POST", "/api/headers", "{}"), writer);
        assertThat(effects.get()).isEqualTo(2);
    }

    private IdempotencyProperties properties(String namespace) {
        var properties = new IdempotencyProperties(); properties.setNamespace(namespace); return properties;
    }

    private void run(ServerWebExchange exchange, WebFilterChain chain) { filter.filter(exchange, chain).block(Duration.ofSeconds(2)); }
    private MockServerWebExchange request(String method, String uri, String body) { return request(method, uri, body, "alice", "tenant-a", "issuer-a"); }
    private MockServerWebExchange request(String method, String uri, String body, String subject, String tenant, String issuer) {
        var principal = SecurityPrincipal.builder().subject(subject).tenantId(tenant).issuer(issuer).build();
        return MockServerWebExchange.builder(MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), uri)
            .header("X-Idempotency-Key", "shared").contentType(MediaType.APPLICATION_JSON).body(body))
            .principal(new UsernamePasswordAuthenticationToken(principal, "", List.of())).build();
    }
    private Mono<Void> respond(ServerWebExchange exchange, HttpStatus status, String body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
