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

package org.fireflyframework.security.rs;

import org.fireflyframework.security.api.domain.BearerToken;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.TokenValidationPort;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/** Adds uncached current-state admission to a locally verified JWT without replacing its claims. */
public final class LiveActiveJwtDecoderImpl implements ReactiveJwtDecoder {
    private final ReactiveJwtDecoder local;
    private final TokenValidationPort live;
    private final Settings settings;
    private final Clock clock;
    private final LongSupplier nanos;

    public LiveActiveJwtDecoderImpl(ReactiveJwtDecoder local, TokenValidationPort live,
                                ResourceServerProperties properties) {
        this(local, live, properties, Clock.systemUTC(), System::nanoTime);
    }

    public LiveActiveJwtDecoderImpl(ReactiveJwtDecoder local, TokenValidationPort live,
                                ResourceServerProperties properties, Clock clock, LongSupplier nanos) {
        this.local = Objects.requireNonNull(local);
        this.live = Objects.requireNonNull(live);
        this.settings = Settings.from(properties);
        this.clock = Objects.requireNonNull(clock);
        this.nanos = Objects.requireNonNull(nanos);
    }

    @Override
    public Mono<Jwt> decode(String token) {
        return Mono.defer(() -> {
            long start = nanos.getAsLong();
            return Mono.defer(() -> local.decode(token))
                    .switchIfEmpty(Mono.error(refused()))
                    .flatMap(jwt -> {
                        Binding original = Binding.local(jwt, settings);
                        Duration remaining = remaining(start, original.expiration());
                        return Mono.defer(() -> live.validateLive(BearerToken.of(token)))
                                .switchIfEmpty(Mono.error(refused()))
                                .timeout(remaining)
                                .map(principal -> {
                                    Binding observed = Binding.remote(principal, settings);
                                    if (!original.matches(observed)) {
                                        throw refused();
                                    }
                                    if (!original.equals(Binding.local(jwt, settings))) {
                                        throw refused();
                                    }
                                    remaining(start, original.expiration());
                                    return jwt;
                                });
                    })
                    .timeout(settings.timeout())
                    .onErrorMap(error -> refused());
        }).onErrorMap(error -> refused());
    }

    private Duration remaining(long start, Instant expiration) {
        long elapsed = nanos.getAsLong() - start;
        if (elapsed < 0 || elapsed >= settings.timeout().toNanos()) {
            throw refused();
        }
        Duration untilExpiration = Duration.between(clock.instant(), expiration);
        Duration work = Duration.ofNanos(settings.timeout().toNanos() - elapsed);
        Duration remaining = work.compareTo(untilExpiration) < 0 ? work : untilExpiration;
        if (remaining.isNegative() || remaining.isZero()) {
            throw refused();
        }
        return remaining;
    }

    private static BadJwtException refused() {
        return new BadJwtException("Live token admission refused");
    }

    static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 2048) {
            throw new IllegalArgumentException("Invalid live token metadata");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) || Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Invalid live token metadata");
            }
            if (Character.isHighSurrogate(c)) {
                if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i))) {
                    throw new IllegalArgumentException("Invalid live token metadata");
                }
            }
        }
        return text;
    }

    static Set<String> audience(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 32) {
            throw new IllegalArgumentException("Invalid live token audience");
        }
        Set<String> result = new HashSet<>();
        for (Object item : list) {
            if (!result.add(text(item))) {
                throw new IllegalArgumentException("Invalid live token audience");
            }
        }
        return Set.copyOf(result);
    }

    record Settings(String issuer, Set<String> audiences, Set<String> clients, String clientClaim,
                    Duration timeout) {
        static Settings from(ResourceServerProperties properties) {
            if (properties == null || properties.getAdmissionMode() != ResourceServerProperties.JwtAdmissionMode.LIVE
                    || properties.getLiveState() == null) {
                throw new IllegalArgumentException("Live admission configuration is required");
            }
            var live = properties.getLiveState();
            Duration timeout = live.getTimeout();
            if (timeout == null || timeout.isZero() || timeout.isNegative()
                    || timeout.compareTo(Duration.ofSeconds(5)) > 0) {
                throw new IllegalArgumentException("Live admission timeout must be positive and at most five seconds");
            }
            return new Settings(text(properties.getIssuer()), audience(properties.getAudiences()),
                    audience(live.getAllowedClients()), text(live.getTokenClientClaim()), timeout);
        }
        @Override public String toString() { return "LiveSettings[REDACTED]"; }
    }

    record Binding(String issuer, String subject, Set<String> audiences, String client,
                   Instant expiration, String tokenId) {
        static Binding local(Jwt jwt, Settings settings) {
            return read(jwt.getClaims(), settings.clientClaim(), settings);
        }
        static Binding remote(SecurityPrincipal principal, Settings settings) {
            Binding binding = read(principal.claims(), "client_id", settings);
            if (!binding.issuer().equals(principal.issuer()) || !binding.subject().equals(principal.subject())) {
                throw refused();
            }
            return binding;
        }
        private static Binding read(Map<String, Object> claims, String clientClaim, Settings settings) {
            String issuer = text(claims.get("iss"));
            String subject = text(claims.get("sub"));
            Set<String> audiences = audience(claims.get("aud"));
            String client = text(claims.get(clientClaim));
            if (!(claims.get("exp") instanceof Instant expiration)
                    || !issuer.equals(settings.issuer()) || !settings.clients().contains(client)
                    || audiences.stream().noneMatch(settings.audiences()::contains)) {
                throw refused();
            }
            String tokenId = claims.containsKey("jti") ? text(claims.get("jti")) : null;
            return new Binding(issuer, subject, audiences, client, expiration, tokenId);
        }
        boolean matches(Binding observed) {
            return issuer.equals(observed.issuer) && subject.equals(observed.subject)
                    && audiences.equals(observed.audiences) && client.equals(observed.client)
                    && expiration.equals(observed.expiration)
                    && (tokenId == null || tokenId.equals(observed.tokenId));
        }
        @Override public String toString() { return "LiveBinding[REDACTED]"; }
    }
}
