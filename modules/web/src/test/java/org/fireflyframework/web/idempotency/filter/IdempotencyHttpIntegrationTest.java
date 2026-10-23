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
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.fireflyframework.web.idempotency.cache.TestIdempotencyCache;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyHttpIntegrationTest {
    @Test void actualHttpControllerReceivesOwnBodyAndReadCannotReplayMutation() {
        withServer((client, controller) -> {
            assertThat(write(client, "/api/headers", "one").block()).isEqualTo("/api/headers:one");
            assertThat(write(client, "/api/headers", "one").block()).isEqualTo("/api/headers:one");
            assertThat(write(client, "/api/legs", "two").block()).isEqualTo("/api/legs:two");
            assertThat(client.post().uri("/api/headers").header("X-Idempotency-Key", "shared").bodyValue("different")
                    .exchangeToMono(response -> Mono.just(response.statusCode())).block()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(client.get().uri("/api/headers").header("X-Idempotency-Key", "shared").retrieve().bodyToMono(String.class).block()).isEqualTo("read-current");
            assertThat(controller.effects.get()).isEqualTo(2);
        });
    }

    @Test void disabledControllerMethodActuallyBypassesResponseReuse() {
        withServer((client, controller) -> {
            assertThat(write(client, "/api/heartbeat", "one").block()).isEqualTo("heartbeat:1");
            assertThat(write(client, "/api/heartbeat", "one").block()).isEqualTo("heartbeat:2");
            assertThat(controller.effects.get()).isEqualTo(2);
        });
    }

    @Test void disabledControllerIgnoresMalformedRetryHeaders() {
        withServer((client, controller) -> {
            var response = client.post().uri("/api/heartbeat").header("X-Idempotency-Key", "")
                    .exchangeToMono(result -> result.toEntity(String.class)).block();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("heartbeat:1");
            assertThat(controller.effects.get()).isEqualTo(1);
        });
    }

    @Test void applicationEncodedResponseAndSafeMetadataSurviveReplay() {
        withServer((client, controller) -> {
            var original = client.post().uri("/api/encoded").header("X-Idempotency-Key", "encoded-key")
                    .header("Accept-Encoding", "gzip").bodyValue("report")
                    .exchangeToMono(response -> response.toEntity(byte[].class)).block();
            var replay = client.post().uri("/api/encoded").header("X-Idempotency-Key", "encoded-key")
                    .header("Accept-Encoding", "gzip").bodyValue("report")
                    .exchangeToMono(response -> response.toEntity(byte[].class)).block();
            assertThat(original.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(original.getHeaders().getFirst("Content-Encoding")).isEqualTo("gzip");
            assertThat(replay.getHeaders().getFirst("Content-Encoding")).isEqualTo("gzip");
            assertThat(decodedGzip(original.getBody())).isEqualTo("synthetic report");
            assertThat(decodedGzip(replay.getBody())).isEqualTo("synthetic report");
            assertThat(replay.getBody()).containsExactly(original.getBody());
            for (String header : List.of("Vary", "Location", "ETag", "Content-Language", "Content-Location", "Content-Disposition", "Last-Modified")) {
                assertThat(replay.getHeaders().get(header)).as(header).isEqualTo(original.getHeaders().get(header));
            }
            assertThat(original.getHeaders().getFirst("Set-Cookie")).contains("synthetic-session");
            assertThat(original.getHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-response-secret");
            assertThat(replay.getHeaders().containsHeader("Set-Cookie")).isFalse();
            assertThat(replay.getHeaders().containsHeader("Authorization")).isFalse();
            var changedNegotiation = client.post().uri("/api/encoded").header("X-Idempotency-Key", "encoded-key")
                    .header("Accept-Encoding", "identity").bodyValue("report")
                    .exchangeToMono(response -> response.toEntity(byte[].class)).block();
            assertThat(changedNegotiation.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(controller.effects.get()).isEqualTo(1);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("changedHeaderValues")
    void changingOnlyTheSecondSelectedHeaderValueConflicts(String name, String first, String second, String changed) {
        withServer((client, controller) -> {
            var original = client.post().uri("/api/representations").header("X-Idempotency-Key", "header-key")
                    .header(name, first, second).bodyValue("same-body")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            var retry = client.post().uri("/api/representations").header("X-Idempotency-Key", "header-key")
                    .header(name, first, changed).bodyValue("same-body")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            assertThat(original.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(controller.effects.get()).isEqualTo(1);
        });
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> changedHeaderValues() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("Accept", "text/plain", "application/xml", "application/json"),
                org.junit.jupiter.params.provider.Arguments.of("If-Match", "\"v1\"", "\"v2\"", "\"v3\""),
                org.junit.jupiter.params.provider.Arguments.of("If-None-Match", "\"v1\"", "\"v2\"", "\"v3\""),
                org.junit.jupiter.params.provider.Arguments.of("Accept-Encoding", "gzip", "identity;q=0.5", "identity;q=0.7"),
                org.junit.jupiter.params.provider.Arguments.of("Accept-Language", "en-US", "en;q=0.5", "fr;q=0.5"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"X-Synthetic-Flavour", "*"})
    void unsupportedVaryFieldsDoNotCacheAnUnfingerprintedRepresentation(String vary) {
        withServer((client, controller) -> {
            var original = client.post().uri("/api/custom-vary").header("X-Idempotency-Key", "vary-key")
                    .header("X-Synthetic-Vary", vary).header("X-Synthetic-Flavour", "first").bodyValue("same-body")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            var retry = client.post().uri("/api/custom-vary").header("X-Idempotency-Key", "vary-key")
                    .header("X-Synthetic-Vary", vary).header("X-Synthetic-Flavour", "second").bodyValue("same-body")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            assertThat(original.getBody()).isEqualTo("first");
            assertThat(retry.getBody()).isEqualTo("second");
            assertThat(controller.effects.get()).isEqualTo(2);
        });
    }

    @Test void selectedHeaderFieldBoundariesAreNotCollapsedIntoCommaJoinedText() {
        withServer((client, controller) -> {
            var original = client.post().uri("/api/representations").header("X-Idempotency-Key", "framing-key")
                    .header("If-Match", "\"v1\"", "\"v2\"").bodyValue("same-body")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            var retry = client.post().uri("/api/representations").header("X-Idempotency-Key", "framing-key")
                    .header("If-Match", "\"v1\", \"v2\"").bodyValue("same-body")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            assertThat(original.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(controller.effects.get()).isEqualTo(1);
        });
    }

    @Test void responseMetadataFinalizedBeforeCommitSurvivesEncodedReplay() {
        withServer((client, controller) -> {
            var original = client.post().uri("/api/late-encoded").header("X-Idempotency-Key", "late-gzip")
                    .header("Accept-Encoding", "gzip").bodyValue("same")
                    .exchangeToMono(response -> response.toEntity(byte[].class)).block();
            var replay = client.post().uri("/api/late-encoded").header("X-Idempotency-Key", "late-gzip")
                    .header("Accept-Encoding", "gzip").bodyValue("same")
                    .exchangeToMono(response -> response.toEntity(byte[].class)).block();
            assertThat(original.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(original.getHeaders().getFirst("Content-Encoding")).isEqualTo("gzip");
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(replay.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.TEXT_PLAIN);
            assertThat(replay.getHeaders().getFirst("Content-Encoding")).isEqualTo("gzip");
            assertThat(decodedGzip(original.getBody())).isEqualTo("synthetic report");
            assertThat(decodedGzip(replay.getBody())).isEqualTo("synthetic report");
            assertThat(replay.getHeaders().getFirst("Vary")).isEqualTo("Accept-Encoding");
            assertThat(controller.effects.get()).isEqualTo(1);
        });
    }

    @Test void lateUnsupportedVaryCannotRetainTheOriginalVariant() {
        withServer((client, controller) -> {
            var original = client.post().uri("/api/late-vary").header("X-Idempotency-Key", "late-vary")
                    .header("X-Synthetic-Flavour", "first").bodyValue("same")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            var retry = client.post().uri("/api/late-vary").header("X-Idempotency-Key", "late-vary")
                    .header("X-Synthetic-Flavour", "second").bodyValue("same")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            assertThat(original.getHeaders().getFirst("Vary")).isEqualTo("X-Synthetic-Flavour");
            assertThat(retry.getBody()).isEqualTo("second");
            assertThat(controller.effects.get()).isEqualTo(2);
        });
    }

    @Test void finalFailureStatusIsNotMistakenForThePrecommitSuccess() {
        withServer((client, controller) -> {
            var original = client.post().uri("/api/late-status").header("X-Idempotency-Key", "late-status").bodyValue("same")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            var retry = client.post().uri("/api/late-status").header("X-Idempotency-Key", "late-status").bodyValue("same")
                    .exchangeToMono(response -> response.toEntity(String.class)).block();
            assertThat(original.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(controller.effects.get()).isEqualTo(2);
        });
    }

    @Test void emptyResponseUsesFinalStatusAndMetadata() {
        withServer((client, controller) -> {
            var original = client.post().uri("/api/late-empty").header("X-Idempotency-Key", "late-empty")
                    .exchangeToMono(response -> response.toEntity(byte[].class)).block();
            var replay = client.post().uri("/api/late-empty").header("X-Idempotency-Key", "late-empty")
                    .exchangeToMono(response -> response.toEntity(byte[].class)).block();
            assertThat(original.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(replay.getHeaders().getFirst("ETag")).isEqualTo("\"late-empty\"");
            assertThat(replay.getBody()).isNullOrEmpty();
            assertThat(controller.effects.get()).isEqualTo(1);
        });
    }

    @Test void pendingHttpDuplicateCannotConsumeVariantAddedAtCommit() {
        withServer((client, controller) -> {
            var original = pendingRequest(client, "vary", "first").toFuture();
            controller.commitEntered.asMono().block(Duration.ofSeconds(5));
            var duplicateSubscribed = reactor.core.publisher.Sinks.<Void>one();
            controller.cache.afterLookup = () -> duplicateSubscribed.tryEmitEmpty();
            var duplicate = pendingRequest(client, "vary", "second").toFuture();
            duplicateSubscribed.asMono().block(Duration.ofSeconds(5));
            controller.commitRelease.tryEmitEmpty();
            var first = Mono.fromFuture(original).block(Duration.ofSeconds(5));
            var second = Mono.fromFuture(duplicate).block(Duration.ofSeconds(5));
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(second.getBody()).doesNotContain("first variant");
            assertThat(controller.effects.get()).isEqualTo(1);
        });
    }

    @Test void commitFailureCannotCompletePendingHttpDuplicateWithSuccess() {
        withServer((client, controller) -> {
            controller.failCommit.set(true);
            var original = pendingRequest(client, "failure", "first").toFuture();
            controller.commitEntered.asMono().block(Duration.ofSeconds(5));
            var duplicateSubscribed = reactor.core.publisher.Sinks.<Void>one();
            controller.cache.afterLookup = () -> duplicateSubscribed.tryEmitEmpty();
            var duplicate = pendingRequest(client, "failure", "first").toFuture();
            duplicateSubscribed.asMono().block(Duration.ofSeconds(5));
            controller.commitRelease.tryEmitEmpty();
            assertThat(Mono.fromFuture(original).block(Duration.ofSeconds(5)).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(Mono.fromFuture(duplicate).block(Duration.ofSeconds(5)).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(controller.effects.get()).isEqualTo(1);
            assertThat(pendingRequest(client, "failure", "first").block(Duration.ofSeconds(5)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(controller.effects.get()).isEqualTo(2);
        });
    }

    private Mono<org.springframework.http.ResponseEntity<String>> pendingRequest(WebClient client, String mode, String flavour) {
        return client.post().uri("/api/late-pending").header("X-Idempotency-Key", "late-pending")
                .header("X-Synthetic-Mode", mode).header("X-Synthetic-Flavour", flavour).bodyValue("same")
                .exchangeToMono(response -> response.toEntity(String.class));
    }

    private String decodedGzip(byte[] bytes) {
        try (var input = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new AssertionError("The HTTP representation must decode as gzip", error);
        }
    }

    private Mono<String> write(WebClient client, String uri, String body) {
        return client.post().uri(uri).header("X-Idempotency-Key", "shared").bodyValue(body).retrieve().bodyToMono(String.class);
    }

    private void withServer(java.util.function.BiConsumer<WebClient, SyntheticController> assertion) {
        try (var context = new AnnotationConfigApplicationContext(HttpFixture.class)) {
            var handler = WebHttpHandlerBuilder.applicationContext(context).build();
            var server = HttpServer.create().host("127.0.0.1").port(0).handle(new ReactorHttpHandlerAdapter(handler)).bindNow(Duration.ofSeconds(10));
            try {
                var client = WebClient.builder().clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create().compress(false))).baseUrl("http://127.0.0.1:" + server.port()).build();
                assertion.accept(client, context.getBean(SyntheticController.class));
            } finally { server.disposeNow(Duration.ofSeconds(10)); }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class HttpFixture {
        @Bean ObservedIdempotencyCache observedIdempotencyCache() { return new ObservedIdempotencyCache(); }
        @Bean SyntheticController syntheticController(ObservedIdempotencyCache cache) { return new SyntheticController(cache); }
        @Bean @org.springframework.core.annotation.Order(-90) WebFilter trustedSyntheticIdentity() {
            var principal = SecurityPrincipal.builder().issuer("synthetic-issuer").subject("synthetic-actor").tenantId("synthetic-tenant").build();
            var auth = new UsernamePasswordAuthenticationToken(principal, "", List.of());
            return (exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(auth)).build());
        }
        @Bean IdempotencyWebFilter idempotencyWebFilter(org.springframework.beans.factory.ObjectProvider<org.springframework.web.reactive.HandlerMapping> mappings,
                                                        ObservedIdempotencyCache cache) {
            var properties = new IdempotencyProperties(); properties.setNamespace("synthetic-http-service");
            return new IdempotencyWebFilter(properties, cache,
                    new org.fireflyframework.web.idempotency.scope.AuthenticatedIdempotencyScopeResolver(), () -> mappings.orderedStream().toList());
        }
    }

    static class ObservedIdempotencyCache extends TestIdempotencyCache {
        volatile Runnable afterLookup = () -> { };
        @Override public Mono<org.fireflyframework.web.idempotency.model.CachedResponse> get(String key) {
            Runnable observer = afterLookup;
            return super.get(key).doAfterTerminate(observer);
        }
    }

    @RestController
    static class SyntheticController {
        final AtomicInteger effects = new AtomicInteger();
        final ObservedIdempotencyCache cache;
        final reactor.core.publisher.Sinks.One<Void> commitEntered = reactor.core.publisher.Sinks.one();
        final reactor.core.publisher.Sinks.One<Void> commitRelease = reactor.core.publisher.Sinks.one();
        final java.util.concurrent.atomic.AtomicBoolean failCommit = new java.util.concurrent.atomic.AtomicBoolean();
        SyntheticController(ObservedIdempotencyCache cache) { this.cache = cache; }
        @PostMapping({"/api/headers", "/api/legs"})
        Mono<String> create(@RequestBody String body, org.springframework.http.server.reactive.ServerHttpRequest request) {
            effects.incrementAndGet(); return Mono.just(request.getPath().value() + ":" + body);
        }
        @PostMapping("/api/encoded")
        Mono<org.springframework.http.ResponseEntity<byte[]>> encoded() {
            effects.incrementAndGet();
            try {
                var output = new java.io.ByteArrayOutputStream();
                try (var gzip = new java.util.zip.GZIPOutputStream(output)) {
                    gzip.write("synthetic report".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return Mono.just(org.springframework.http.ResponseEntity.status(HttpStatus.CREATED)
                        .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                        .header("Content-Encoding", "gzip").header("Vary", "Accept-Encoding", "Accept-Language")
                        .header("Content-Language", "en-US").header("Location", "/reports/synthetic")
                        .header("Content-Location", "/reports/synthetic/report.txt")
                        .header("Content-Disposition", "attachment; filename=report.txt")
                        .header("Last-Modified", "Wed, 21 Oct 2026 07:28:00 GMT").header("ETag", "\"v1\"")
                        .header("Set-Cookie", "synthetic-session=secret; HttpOnly")
                        .header("Authorization", "Bearer synthetic-response-secret").body(output.toByteArray()));
            } catch (java.io.IOException error) {
                return Mono.error(error);
            }
        }
        @PostMapping("/api/representations")
        Mono<Void> representation(@RequestBody String body, org.springframework.web.server.ServerWebExchange exchange) {
            effects.incrementAndGet();
            exchange.getResponse().setStatusCode(HttpStatus.CREATED);
            exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(
                    "selected representation".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        @PostMapping("/api/custom-vary")
        Mono<org.springframework.http.ResponseEntity<String>> customVary(
                @RequestHeader("X-Synthetic-Vary") String vary,
                @RequestHeader("X-Synthetic-Flavour") String flavour) {
            effects.incrementAndGet();
            return Mono.just(org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).header("Vary", vary).body(flavour));
        }
        @PostMapping("/api/late-encoded")
        Mono<Void> lateEncoded(org.springframework.web.server.ServerWebExchange exchange) {
            effects.incrementAndGet();
            exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
            exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().setStatusCode(HttpStatus.CREATED);
                exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
                exchange.getResponse().getHeaders().set("Content-Encoding", "gzip");
                exchange.getResponse().getHeaders().set("Vary", "Accept-Encoding");
                return Mono.empty();
            });
            try {
                var output = new java.io.ByteArrayOutputStream();
                try (var gzip = new java.util.zip.GZIPOutputStream(output)) {
                    gzip.write("synthetic report".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(output.toByteArray())));
            } catch (java.io.IOException error) { return Mono.error(error); }
        }
        @PostMapping("/api/late-vary")
        Mono<Void> lateVary(@RequestHeader("X-Synthetic-Flavour") String flavour, org.springframework.web.server.ServerWebExchange exchange) {
            effects.incrementAndGet();
            exchange.getResponse().setStatusCode(HttpStatus.CREATED);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set("Vary", "X-Synthetic-Flavour");
                return Mono.empty();
            });
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(flavour.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        @PostMapping("/api/late-status")
        Mono<Void> lateStatus(org.springframework.web.server.ServerWebExchange exchange) {
            effects.incrementAndGet();
            exchange.getResponse().setStatusCode(HttpStatus.CREATED);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return Mono.empty();
            });
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap("unavailable".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        @PostMapping("/api/late-empty")
        Mono<Void> lateEmpty(org.springframework.web.server.ServerWebExchange exchange) {
            effects.incrementAndGet();
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                exchange.getResponse().getHeaders().set("ETag", "\"late-empty\"");
                return Mono.empty();
            });
            return exchange.getResponse().setComplete();
        }
        @PostMapping("/api/late-pending")
        Mono<Void> latePending(@RequestHeader("X-Synthetic-Mode") String mode,
                               @RequestHeader("X-Synthetic-Flavour") String flavour,
                               org.springframework.web.server.ServerWebExchange exchange) {
            effects.incrementAndGet();
            exchange.getResponse().setStatusCode(HttpStatus.CREATED);
            exchange.getResponse().beforeCommit(() -> {
                commitEntered.tryEmitEmpty();
                return commitRelease.asMono().then(Mono.defer(() -> {
                    if (failCommit.getAndSet(false)) return Mono.error(new IllegalStateException("synthetic commit failure"));
                    if (mode.equals("vary")) exchange.getResponse().getHeaders().set("Vary", "X-Synthetic-Flavour");
                    return Mono.empty();
                }));
            });
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap((flavour + " variant").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        @GetMapping("/api/headers") Mono<String> read() { return Mono.just("read-current"); }
        @PostMapping("/api/heartbeat") @DisableIdempotency
        Mono<String> heartbeat() { return Mono.just("heartbeat:" + effects.incrementAndGet()); }
    }
}
