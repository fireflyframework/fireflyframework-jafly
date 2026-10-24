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

import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.fireflyframework.web.idempotency.cache.IdempotencyCache;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.fireflyframework.web.idempotency.model.CachedResponse;
import org.fireflyframework.web.idempotency.scope.AuthenticatedIdempotencyScopeResolver;
import org.fireflyframework.web.idempotency.scope.IdempotencyScopeResolver;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded HTTP response reuse for authenticated, scoped mutations. Business transactions must
 * still enforce their own durable idempotency: cache retention and in-flight coordination are
 * not a cross-process transaction boundary.
 */
public class IdempotencyWebFilter implements WebFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyWebFilter.class);
    private static final List<HttpMethod> MUTATIONS = List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private static final List<String> FINGERPRINT_HEADERS = List.of("Content-Type", "Accept", "Content-Encoding",
            "Accept-Encoding", "Accept-Language", "If-Match", "If-None-Match");
    private static final List<String> REPLAY_HEADERS = List.of("Location", "ETag", "Content-Language",
            "Content-Encoding", "Content-Location", "Content-Disposition", "Last-Modified", "Vary");
    private final IdempotencyCache cache;
    private final IdempotencyProperties properties;
    private final IdempotencyScopeResolver scopeResolver;
    private final java.util.function.Supplier<List<HandlerMapping>> handlerMappings;
    private final Map<String, PendingResponse> inFlight = new ConcurrentHashMap<>();

    public IdempotencyWebFilter(IdempotencyProperties properties, IdempotencyCache cache) {
        this(properties, cache, new AuthenticatedIdempotencyScopeResolver());
    }

    public IdempotencyWebFilter(IdempotencyProperties properties, IdempotencyCache cache, IdempotencyScopeResolver scopeResolver) {
        this(properties, cache, scopeResolver, List::of);
    }

    public IdempotencyWebFilter(IdempotencyProperties properties, IdempotencyCache cache,
                                IdempotencyScopeResolver scopeResolver,
                                java.util.function.Supplier<List<HandlerMapping>> handlerMappings) {
        this.properties = properties;
        this.cache = cache;
        this.scopeResolver = scopeResolver;
        this.handlerMappings = handlerMappings;
    }

    /** Runs after Spring Security and the trusted principal bridge. */
    @Override public int getOrder() { return -80; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled() || properties.getNamespace() == null || properties.getNamespace().isBlank() || !MUTATIONS.contains(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        List<String> keys = exchange.getRequest().getHeaders().get(properties.getHeaderName());
        if (keys == null) return chain.filter(exchange);
        return disabled(exchange).flatMap(disabled -> {
            if (disabled) {
                return chain.filter(exchange);
            }
            if (keys.size() != 1 || keys.getFirst() == null || keys.getFirst().isBlank() || keys.getFirst().length() > 256) {
                return reject(exchange, HttpStatus.BAD_REQUEST, "Invalid " + properties.getHeaderName() + " header");
            }
            // Resolve to a value before flatMap: a completed Mono<Void> must not fall through and execute twice.
            return scopeResolver.resolve(exchange).filter(scope -> !scope.isBlank())
                    .map(Optional::of).defaultIfEmpty(Optional.empty())
                    .flatMap(scope -> scope.isEmpty() ? chain.filter(exchange) : readBody(exchange)
                            .flatMap(body -> process(exchange, chain, scope.get(), keys.getFirst(), body)));
        });
    }

    private Mono<byte[]> readBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), properties.getMaxRequestBodyBytes())
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally { DataBufferUtils.release(buffer); }
                }).defaultIfEmpty(new byte[0])
                .onErrorMap(DataBufferLimitException.class, error ->
                        new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Idempotency request body limit exceeded", error));
    }

    private Mono<Void> process(ServerWebExchange exchange, WebFilterChain chain, String scope, String clientKey, byte[] body) {
        var request = exchange.getRequest();
        String key = "v2:" + digest(parts(properties.getNamespace(), scope, request.getMethod().name(), request.getURI().getRawPath(),
                Optional.ofNullable(request.getURI().getRawQuery()).orElse(""), clientKey));
        List<String> fields = new java.util.ArrayList<>();
        for (String name : FINGERPRINT_HEADERS) {
            List<String> values = request.getHeaders().get(name);
            fields.add(name);
            fields.add(values == null ? null : Integer.toString(values.size()));
            if (values != null) fields.addAll(values);
        }
        String fingerprint = digest(parts(fields.toArray(String[]::new)), body);
        return cache.get(key).map(Optional::of).defaultIfEmpty(Optional.empty()).flatMap(cached -> {
            if (cached.isPresent()) return replay(exchange, cached.get(), fingerprint);
            return Mono.defer(() -> {
                PendingResponse pending = new PendingResponse(fingerprint, Sinks.one());
                PendingResponse existing = inFlight.putIfAbsent(key, pending);
                if (existing != null) {
                    return existing.fingerprint().equals(fingerprint)
                            ? existing.response().asMono().flatMap(response -> replay(exchange, response, fingerprint))
                            : reject(exchange, HttpStatus.CONFLICT, "Idempotency key was already used with a different request");
                }
                // A previous owner may have finished between the first lookup and this claim.
                return cache.get(key).map(Optional::of).defaultIfEmpty(Optional.empty()).flatMap(second -> {
                    if (second.isPresent()) {
                        pending.response().tryEmitValue(second.get());
                        return replay(exchange, second.get(), fingerprint);
                    }
                    return execute(exchange, chain, key, body, pending);
                }).doOnError(pending.response()::tryEmitError)
                  .doFinally(signal -> {
                      pending.response().tryEmitError(new CancellationException("Idempotent response unavailable"));
                      inFlight.remove(key, pending);
                  });
            });
        });
    }

    private Mono<Void> execute(ServerWebExchange exchange, WebFilterChain chain, String key, byte[] body, PendingResponse pending) {
        AtomicReference<byte[]> captured = new AtomicReference<>(new byte[0]);
        var response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override public Mono<Void> writeWith(Publisher<? extends DataBuffer> publisher) {
                return DataBufferUtils.join(Flux.from(publisher), properties.getMaxResponseBodyBytes())
                        .map(buffer -> {
                            try {
                                byte[] bytes = new byte[buffer.readableByteCount()]; buffer.read(bytes); return bytes;
                            } finally { DataBufferUtils.release(buffer); }
                        }).defaultIfEmpty(new byte[0]).flatMap(bytes -> {
                            captured.set(bytes);
                            return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
                        });
            }
            @Override public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> publisher) {
                return writeWith(Flux.from(publisher).concatMap(Flux::from));
            }
        };
        var replayableRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override public Flux<DataBuffer> getBody() {
                return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(body)));
            }
        };
        return chain.filter(exchange.mutate().request(replayableRequest).response(response).build())
                .then(Mono.defer(() -> exchange.getResponse().setComplete()))
                .then(Mono.defer(() -> {
                    // Commit callbacks finalize status and representation headers during delegate completion.
                    CachedResponse result = snapshot(exchange, pending.fingerprint(), captured.get());
                    CachedResponse completed = result;
                    Mono<Void> save = HttpStatusCode.valueOf(result.getStatus()).is2xxSuccessful() && supportedVary(result)
                            ? cache.put(key, result).onErrorResume(error -> {
                                log.warn("HTTP idempotency cache write failed; durable business idempotency is required", error);
                                return Mono.empty();
                            }) : Mono.empty();
                    return save.doOnSuccess(ignored -> pending.response().tryEmitValue(completed));
                }));
    }

    private CachedResponse snapshot(ServerWebExchange exchange, String fingerprint, byte[] body) {
        var response = exchange.getResponse();
        CachedResponse result = new CachedResponse(response.getStatusCode() == null ? 200 : response.getStatusCode().value(),
                body, response.getHeaders().getFirst("Content-Type"));
        result.setRequestFingerprint(fingerprint);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : REPLAY_HEADERS) {
            List<String> values = response.getHeaders().get(name);
            if (values != null) headers.put(name, List.copyOf(values));
        }
        result.setHeaders(headers);
        return result;
    }

    private Mono<Void> replay(ServerWebExchange exchange, CachedResponse cached, String fingerprint) {
        if (!supportedVary(cached)) {
            return reject(exchange, HttpStatus.CONFLICT, "Response varies on unsupported request headers; use durable business recovery");
        }
        if (!fingerprint.equals(cached.getRequestFingerprint())) {
            return reject(exchange, HttpStatus.CONFLICT, "Idempotency key was already used with a different request");
        }
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(cached.getStatus()));
        if (cached.getContentType() != null) response.getHeaders().setContentType(MediaType.parseMediaType(cached.getContentType()));
        if (cached.getHeaders() != null) cached.getHeaders().forEach((name, values) -> {
            if (REPLAY_HEADERS.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(name))) response.getHeaders().put(name, values);
        });
        return response.writeWith(Mono.just(response.bufferFactory().wrap(cached.getBody())));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(message.getBytes(StandardCharsets.UTF_8))));
    }

    private Mono<Boolean> disabled(ServerWebExchange exchange) {
        Object handler = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod method) return Mono.just(method.hasMethodAnnotation(DisableIdempotency.class));
        // Filters run before DispatcherHandler selects the controller. Resolve metadata lazily,
        // without invoking the handler; path-name heuristics cannot honor method annotations.
        return Flux.fromIterable(handlerMappings.get()).concatMap(mapping -> mapping.getHandler(exchange)).next()
                .map(selected -> selected instanceof HandlerMethod method && method.hasMethodAnnotation(DisableIdempotency.class))
                .defaultIfEmpty(false);
    }

    private boolean supportedVary(CachedResponse response) {
        if (response.getHeaders() == null) return true;
        return response.getHeaders().entrySet().stream()
                .filter(entry -> "Vary".equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .flatMap(value -> java.util.Arrays.stream(value.split(",", -1)))
                .map(String::trim)
                .allMatch(name -> FINGERPRINT_HEADERS.stream().anyMatch(selected -> selected.equalsIgnoreCase(name)));
    }

    private byte[] parts(String... values) {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.DataOutputStream(bytes)) {
            output.writeInt(values.length);
            for (String value : values) {
                byte[] encoded = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
                output.writeInt(encoded == null ? -1 : encoded.length);
                if (encoded != null) output.write(encoded);
            }
            return bytes.toByteArray();
        } catch (java.io.IOException impossible) {
            throw new java.io.UncheckedIOException(impossible);
        }
    }

    private String digest(byte[]... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] value : values) digest.update(value);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record PendingResponse(String fingerprint, Sinks.One<CachedResponse> response) { }
}
