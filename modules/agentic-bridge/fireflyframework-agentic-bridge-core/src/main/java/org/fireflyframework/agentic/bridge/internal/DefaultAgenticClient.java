/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.agentic.bridge.AgenticClient;
import org.fireflyframework.agentic.bridge.auth.AuthStrategy;
import org.fireflyframework.agentic.bridge.catalog.AgentCatalog;
import org.fireflyframework.agentic.bridge.catalog.AgentDescriptor;
import org.fireflyframework.agentic.bridge.conversation.ConversationManager;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException.AgenticErrorContext;
import org.fireflyframework.agentic.bridge.exception.AgenticTransportException;
import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import org.fireflyframework.agentic.bridge.invocation.AgentRequest;
import org.fireflyframework.agentic.bridge.invocation.AgentResponse;
import org.fireflyframework.agentic.bridge.invocation.StreamingMode;
import org.fireflyframework.agentic.bridge.observability.AgenticMetrics;
import org.fireflyframework.agentic.bridge.resilience.CircuitBreakerSettings;
import org.fireflyframework.agentic.bridge.resilience.RetryPolicy;
import org.fireflyframework.agentic.bridge.streaming.StreamEvent;
import org.fireflyframework.agentic.bridge.transport.AgenticTransport;
import org.fireflyframework.agentic.bridge.transport.rest.RestAgenticTransport;
import org.fireflyframework.agentic.bridge.transport.websocket.WebSocketAgenticTransport;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production implementation of {@link AgenticClient}. Composes the REST and
 * WebSocket transports plus the conversation manager into a single coherent
 * façade.
 *
 * <p>Construction is intentionally package-private; callers obtain instances
 * through {@link AgenticClient#builder(String)}.</p>
 */
public final class DefaultAgenticClient implements AgenticClient {

    private final String name;
    private final WebClient webClient;
    private final AuthStrategy auth;
    private final ObjectMapper mapper;
    private final Duration responseTimeout;
    private final AgenticMetrics metrics;
    private final AgenticTransport restTransport;
    private final AgenticTransport websocketTransport;
    private final ConversationManager conversationManager;
    private final AtomicReference<AgentCatalog> cachedCatalog = new AtomicReference<>();

    public DefaultAgenticClient(String name,
                                String baseUrl,
                                WebClient webClient,
                                WebSocketClient webSocketClient,
                                AuthStrategy auth,
                                Map<String, String> defaultHeaders,
                                Duration responseTimeout,
                                RetryPolicy retryPolicy,
                                CircuitBreakerSettings circuitBreakerSettings,
                                ObjectMapper mapper,
                                AgenticMetrics metrics) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(defaultHeaders, "defaultHeaders");
        Objects.requireNonNull(circuitBreakerSettings, "circuitBreakerSettings");
        this.name = Objects.requireNonNullElse(name, "default");
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "responseTimeout");
        this.metrics = Objects.requireNonNullElse(metrics, AgenticMetrics.NOOP);
        this.restTransport = new RestAgenticTransport(webClient, auth, mapper, retryPolicy, responseTimeout, this.metrics);
        this.websocketTransport = new WebSocketAgenticTransport(webSocketClient, baseUrl, auth, mapper, responseTimeout, this.metrics);
        this.conversationManager = new RestConversationManager(webClient, auth, mapper);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Mono<AgentResponse> invoke(String agentName, AgentRequest request) {
        return restTransport.invoke(agentName, request);
    }

    @Override
    public Flux<StreamEvent> stream(String agentName, AgentRequest request) {
        return restTransport.stream(agentName, request, StreamingMode.BUFFERED);
    }

    @Override
    public Flux<StreamEvent> streamIncremental(String agentName, AgentRequest request) {
        return restTransport.stream(agentName, request, StreamingMode.INCREMENTAL);
    }

    @Override
    public Flux<StreamEvent> conversation(String agentName, AgentRequest request) {
        return websocketTransport.stream(agentName, request, StreamingMode.BUFFERED);
    }

    @Override
    public Mono<AgentCatalog> catalog() {
        return webClient.get()
                .uri("/agents/")
                .headers(h -> auth.headers().forEach(h::add))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .map(items -> {
                    List<AgentDescriptor> descriptors = items.stream()
                            .map(item -> mapper.convertValue(item, new TypeReference<AgentDescriptor>() {}))
                            .toList();
                    AgentCatalog snapshot = new AgentCatalog(descriptors, Instant.now());
                    cachedCatalog.set(snapshot);
                    return snapshot;
                })
                .onErrorMap(err -> err instanceof AgenticBridgeException
                        ? err
                        : new AgenticTransportException("Catalog fetch failed",
                            AgenticErrorContext.builder().transport("rest").endpoint("/agents/").build(), err));
    }

    @Override
    public Mono<AgentCatalog> cachedCatalog() {
        AgentCatalog cached = cachedCatalog.get();
        return cached != null ? Mono.just(cached) : Mono.empty();
    }

    @Override
    public ConversationManager conversations() {
        return conversationManager;
    }

    @Override
    public Mono<AgenticHealth> health() {
        long start = System.nanoTime();
        return webClient.get()
                .uri("/health")
                .headers(h -> auth.headers().forEach(h::add))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .map(payload -> AgenticHealth.up(Duration.ofNanos(System.nanoTime() - start), payload))
                .onErrorResume(err -> Mono.just(AgenticHealth.down(
                        "Failed to reach agentic /health: " + err.getMessage(), err)));
    }

    @Override
    public void close() {
        try { restTransport.close(); } catch (Exception ignored) {}
        try { websocketTransport.close(); } catch (Exception ignored) {}
    }
}
