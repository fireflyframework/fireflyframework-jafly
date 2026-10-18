/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.fireflyframework.agentic.bridge.auth.AuthStrategy;
import org.fireflyframework.agentic.bridge.internal.DefaultAgenticClient;
import org.fireflyframework.agentic.bridge.observability.AgenticMetrics;
import org.fireflyframework.agentic.bridge.resilience.CircuitBreakerSettings;
import org.fireflyframework.agentic.bridge.resilience.RetryPolicy;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link AgenticClient} instances.
 *
 * <p>Mirrors the look-and-feel of {@code RestClientBuilder} from
 * {@code fireflyframework-client} so existing Firefly developers feel at
 * home. Every option has sensible defaults; the only mandatory call is
 * {@link #baseUrl(String)}.</p>
 */
public final class AgenticClientBuilder {

    private final String clientName;
    private String baseUrl;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration responseTimeout = Duration.ofSeconds(60);
    private Duration writeTimeout = Duration.ofSeconds(30);
    private int maxInMemorySize = 16 * 1024 * 1024;
    private boolean compressionEnabled = true;
    private boolean followRedirects = false;
    private String userAgent;
    private AuthStrategy authStrategy = AuthStrategy.none();
    private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private RetryPolicy retryPolicy = RetryPolicy.defaults();
    private CircuitBreakerSettings circuitBreakerSettings = CircuitBreakerSettings.builder().enabled(false).build();
    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private WebClient webClient;
    private WebSocketClient webSocketClient;
    private boolean requireTls = false;

    AgenticClientBuilder(String clientName) {
        if (clientName == null || clientName.isBlank()) {
            throw new IllegalArgumentException("clientName must not be blank");
        }
        this.clientName = clientName.trim();
    }

    public AgenticClientBuilder baseUrl(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        URI uri = URI.create(baseUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("baseUrl must be absolute: " + baseUrl);
        }
        if (requireTls && !"https".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("baseUrl must use https when requireTls=true: " + baseUrl);
        }
        this.baseUrl = stripTrailingSlash(uri.toString());
        return this;
    }

    public AgenticClientBuilder requireTls(boolean value) {
        this.requireTls = value;
        return this;
    }

    public AgenticClientBuilder connectTimeout(Duration timeout) {
        this.connectTimeout = requirePositive(timeout, "connectTimeout");
        return this;
    }

    public AgenticClientBuilder responseTimeout(Duration timeout) {
        this.responseTimeout = requirePositive(timeout, "responseTimeout");
        return this;
    }

    public AgenticClientBuilder writeTimeout(Duration timeout) {
        this.writeTimeout = requirePositive(timeout, "writeTimeout");
        return this;
    }

    public AgenticClientBuilder timeout(Duration timeout) {
        Duration t = requirePositive(timeout, "timeout");
        this.connectTimeout = t;
        this.responseTimeout = t;
        this.writeTimeout = t;
        return this;
    }

    public AgenticClientBuilder maxInMemorySize(int bytes) {
        if (bytes <= 0) throw new IllegalArgumentException("maxInMemorySize must be > 0");
        this.maxInMemorySize = bytes;
        return this;
    }

    public AgenticClientBuilder compression(boolean enabled) {
        this.compressionEnabled = enabled;
        return this;
    }

    public AgenticClientBuilder followRedirects(boolean enabled) {
        this.followRedirects = enabled;
        return this;
    }

    public AgenticClientBuilder userAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public AgenticClientBuilder auth(AuthStrategy strategy) {
        this.authStrategy = Objects.requireNonNullElse(strategy, AuthStrategy.none());
        return this;
    }

    public AgenticClientBuilder defaultHeader(String name, String value) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("header name must not be blank");
        if (value != null) defaultHeaders.put(name, value);
        return this;
    }

    public AgenticClientBuilder retry(Consumer<RetryPolicy.Builder> customizer) {
        RetryPolicy.Builder b = RetryPolicy.builder();
        customizer.accept(b);
        this.retryPolicy = b.build();
        return this;
    }

    public AgenticClientBuilder retry(RetryPolicy policy) {
        this.retryPolicy = Objects.requireNonNullElse(policy, RetryPolicy.defaults());
        return this;
    }

    public AgenticClientBuilder circuitBreaker(Consumer<CircuitBreakerSettings.Builder> customizer) {
        CircuitBreakerSettings.Builder b = CircuitBreakerSettings.builder();
        customizer.accept(b);
        this.circuitBreakerSettings = b.build();
        return this;
    }

    public AgenticClientBuilder meterRegistry(MeterRegistry registry) {
        this.meterRegistry = registry;
        return this;
    }

    public AgenticClientBuilder objectMapper(ObjectMapper mapper) {
        this.objectMapper = mapper;
        return this;
    }

    public AgenticClientBuilder webClient(WebClient webClient) {
        this.webClient = webClient;
        return this;
    }

    public AgenticClientBuilder webSocketClient(WebSocketClient client) {
        this.webSocketClient = client;
        return this;
    }

    public AgenticClient build() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("baseUrl must be configured before building AgenticClient");
        }
        ObjectMapper mapper = objectMapper != null ? objectMapper : defaultObjectMapper();
        WebClient effectiveWebClient = webClient != null ? customiseWebClient(webClient, mapper) : buildWebClient(mapper);
        WebSocketClient effectiveWsClient = webSocketClient != null ? webSocketClient : buildWebSocketClient();
        AgenticMetrics metrics = new AgenticMetrics(meterRegistry, clientName);

        return new DefaultAgenticClient(
                clientName,
                baseUrl,
                effectiveWebClient,
                effectiveWsClient,
                authStrategy,
                defaultHeaders,
                responseTimeout,
                retryPolicy,
                circuitBreakerSettings,
                mapper,
                metrics);
    }

    private WebClient buildWebClient(ObjectMapper mapper) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(responseTimeout)
                .followRedirect(followRedirects)
                .compress(compressionEnabled)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeout.toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toMillis(), TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> {
                    codecs.defaultCodecs().maxInMemorySize(maxInMemorySize);
                    codecs.defaultCodecs().jackson2JsonDecoder(new org.springframework.http.codec.json.Jackson2JsonDecoder(mapper));
                    codecs.defaultCodecs().jackson2JsonEncoder(new org.springframework.http.codec.json.Jackson2JsonEncoder(mapper));
                });
        if (userAgent != null) {
            builder.defaultHeader("User-Agent", userAgent);
        }
        defaultHeaders.forEach(builder::defaultHeader);
        return builder.build();
    }

    private WebClient customiseWebClient(WebClient existing, ObjectMapper mapper) {
        WebClient.Builder builder = existing.mutate().baseUrl(baseUrl);
        if (userAgent != null) {
            builder.defaultHeader("User-Agent", userAgent);
        }
        defaultHeaders.forEach(builder::defaultHeader);
        return builder.build();
    }

    private WebSocketClient buildWebSocketClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .compress(compressionEnabled);
        return new ReactorNettyWebSocketClient(httpClient);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
