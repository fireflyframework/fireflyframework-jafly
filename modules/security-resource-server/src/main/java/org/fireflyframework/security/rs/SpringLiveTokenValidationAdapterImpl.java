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
import org.fireflyframework.security.api.exception.TokenValidationException;
import org.fireflyframework.security.spi.TokenValidationPort;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringReactiveOpaqueTokenIntrospector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** An uncached native Spring introspection client. Provider revocation behavior needs separate acceptance. */
public final class SpringLiveTokenValidationAdapterImpl implements TokenValidationPort, AutoCloseable {
    private final ReactiveOpaqueTokenIntrospector introspector;
    private final LiveActiveJwtDecoderImpl.Settings settings;
    private final Clock clock;
    private final HttpClient ownedClient;
    private final AtomicBoolean closed = new AtomicBoolean();

    /** The supplied introspector must perform an uncached current-state exchange for each call. */
    public SpringLiveTokenValidationAdapterImpl(ReactiveOpaqueTokenIntrospector introspector,
                                            ResourceServerProperties properties) {
        this(introspector, properties, Clock.systemUTC());
    }

    public SpringLiveTokenValidationAdapterImpl(ReactiveOpaqueTokenIntrospector introspector,
                                            ResourceServerProperties properties, Clock clock) {
        this(introspector, LiveActiveJwtDecoderImpl.Settings.from(properties), clock, null);
    }

    private SpringLiveTokenValidationAdapterImpl(ReactiveOpaqueTokenIntrospector introspector,
                                             LiveActiveJwtDecoderImpl.Settings settings, Clock clock, HttpClient ownedClient) {
        this.introspector = Objects.requireNonNull(introspector);
        this.settings = settings;
        this.clock = Objects.requireNonNull(clock);
        this.ownedClient = ownedClient;
    }

    public static SpringLiveTokenValidationAdapterImpl create(ResourceServerProperties properties) {
        var settings = LiveActiveJwtDecoderImpl.Settings.from(properties);
        var live = properties.getLiveState();
        URI endpoint;
        String clientId;
        String secret;
        try {
            endpoint = URI.create(LiveActiveJwtDecoderImpl.text(live.getIntrospectionUri()));
            clientId = LiveActiveJwtDecoderImpl.text(live.getIntrospectionClientId());
            secret = LiveActiveJwtDecoderImpl.text(live.getIntrospectionClientSecret());
            if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                    || endpoint.getRawFragment() != null || endpoint.getPort() == 0 || endpoint.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Valid explicit HTTPS introspection configuration is required");
        }
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(settings.timeout()).build();
        try {
            JdkClientHttpConnector connector = new JdkClientHttpConnector(client);
            connector.setReadTimeout(settings.timeout());
            WebClient webClient = ownedWebClientBuilder(connector, clientId, secret).build();
            return new SpringLiveTokenValidationAdapterImpl(new SpringReactiveOpaqueTokenIntrospector(endpoint.toASCIIString(), webClient),
                    settings, Clock.systemUTC(), client);
        } catch (RuntimeException error) {
            client.shutdownNow();
            throw new IllegalArgumentException("Live introspection client configuration refused");
        }
    }

    @Override
    public Mono<SecurityPrincipal> validate(BearerToken token) {
        return validateLive(token);
    }

    @Override
    public Mono<SecurityPrincipal> validateLive(BearerToken token) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            if (closed.get() || token == null || token.type() != BearerToken.TokenType.JWT
                    || token.value() == null || token.value().isBlank()) {
                return Mono.error(refused());
            }
            return Mono.defer(() -> introspector.introspect(token.value()))
                    .switchIfEmpty(Mono.error(refused()))
                    .timeout(settings.timeout())
                    .map(observed -> {
                        Map<String, Object> claims = new HashMap<>();
                        Map<String, Object> remote = observed.getAttributes();
                        if (!Boolean.TRUE.equals(remote.get("active"))) {
                            throw refused();
                        }
                        for (String name : new String[]{"iss", "sub", "aud", "exp", "client_id", "jti"}) {
                            if (remote.containsKey(name)) {
                                claims.put(name, remote.get(name));
                            }
                        }
                        // Copy and validate the allowed tuple; arbitrary provider attributes never become authentication claims.
                        var principal = SecurityPrincipal.builder()
                                .issuer(LiveActiveJwtDecoderImpl.text(claims.get("iss")))
                                .subject(LiveActiveJwtDecoderImpl.text(claims.get("sub")))
                                .claims(claims).build();
                        var binding = LiveActiveJwtDecoderImpl.Binding.remote(principal, settings);
                        if (!clock.instant().isBefore(binding.expiration()) || closed.get()) {
                            throw refused();
                        }
                        claims.put("aud", java.util.List.copyOf(binding.audiences()));
                        SecurityPrincipal result = principal.toBuilder().claims(claims).build();
                        long elapsed = System.nanoTime() - started;
                        if (elapsed < 0 || elapsed >= settings.timeout().toNanos()
                                || !clock.instant().isBefore(binding.expiration()) || closed.get()) {
                            throw refused();
                        }
                        return result;
                    });
        }).onErrorMap(error -> refused());
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && ownedClient != null) {
            ownedClient.shutdownNow();
        }
    }

    static WebClient.Builder ownedWebClientBuilder(ClientHttpConnector connector, String clientId, String secret) {
        return WebClient.builder().clientConnector(connector)
                .codecs(codecs -> {
                    codecs.defaultCodecs().maxInMemorySize(LiveIntrospectionResponseGuardImpl.MAX_RESPONSE_BYTES);
                    codecs.defaultCodecs().enableLoggingRequestDetails(false);
                    codecs.defaultCodecs().jacksonJsonDecoder(nativeJsonDecoder());
                })
                .defaultHeaders(headers -> headers.setBasicAuth(
                        URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                        URLEncoder.encode(secret, StandardCharsets.UTF_8)))
                .filter(new LiveIntrospectionResponseGuardImpl());
    }

    static JacksonJsonDecoder nativeJsonDecoder() {
        return new QuietJacksonJsonDecoderImpl();
    }

    /** The native codec honors this public hint before rendering decoded identity attributes. */
    private static final class QuietJacksonJsonDecoderImpl extends JacksonJsonDecoder {
        @Override
        public Flux<Object> decode(Publisher<DataBuffer> input, ResolvableType type, MimeType mimeType,
                                   Map<String, Object> hints) {
            return super.decode(input, type, mimeType, Hints.merge(hints, Hints.SUPPRESS_LOGGING_HINT, true))
                    .onErrorMap(error -> refused());
        }
        @Override
        public Mono<Object> decodeToMono(Publisher<DataBuffer> input, ResolvableType type, MimeType mimeType,
                                         Map<String, Object> hints) {
            return super.decodeToMono(input, type, mimeType, Hints.merge(hints, Hints.SUPPRESS_LOGGING_HINT, true))
                    .onErrorMap(error -> refused());
        }
        @Override
        public Object decode(DataBuffer input, ResolvableType type, MimeType mimeType, Map<String, Object> hints) {
            try {
                return super.decode(input, type, mimeType, Hints.merge(hints, Hints.SUPPRESS_LOGGING_HINT, true));
            } catch (RuntimeException error) {
                throw refused();
            }
        }
    }

    private static TokenValidationException refused() {
        return new TokenValidationException("Live token validation refused");
    }
}
