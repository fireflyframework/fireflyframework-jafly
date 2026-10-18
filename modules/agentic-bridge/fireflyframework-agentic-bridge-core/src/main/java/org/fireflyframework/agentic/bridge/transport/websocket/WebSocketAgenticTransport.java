/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.transport.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.agentic.bridge.auth.AuthStrategy;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException.AgenticErrorContext;
import org.fireflyframework.agentic.bridge.exception.AgenticStreamException;
import org.fireflyframework.agentic.bridge.exception.AgenticTransportException;
import org.fireflyframework.agentic.bridge.invocation.AgentRequest;
import org.fireflyframework.agentic.bridge.invocation.AgentResponse;
import org.fireflyframework.agentic.bridge.invocation.StreamingMode;
import org.fireflyframework.agentic.bridge.observability.AgenticMetrics;
import org.fireflyframework.agentic.bridge.streaming.ConversationIdEvent;
import org.fireflyframework.agentic.bridge.streaming.ErrorEvent;
import org.fireflyframework.agentic.bridge.streaming.ResultEvent;
import org.fireflyframework.agentic.bridge.streaming.StreamEvent;
import org.fireflyframework.agentic.bridge.streaming.TokenEvent;
import org.fireflyframework.agentic.bridge.transport.AgenticTransport;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket implementation of {@link AgenticTransport} that targets the
 * {@code /ws/agents/{name}} endpoint exposed by the agentic REST layer.
 *
 * <p>Multi-turn conversations are first-class: callers obtain a
 * {@link Flux} of {@link StreamEvent}s and can interleave further
 * {@link AgentRequest}s through subsequent invocations sharing a
 * {@code conversation_id}.</p>
 */
public final class WebSocketAgenticTransport implements AgenticTransport {

    private static final String NAME = "websocket";

    private final WebSocketClient client;
    private final URI baseUri;
    private final AuthStrategy auth;
    private final ObjectMapper mapper;
    private final Duration responseTimeout;
    private final AgenticMetrics metrics;

    public WebSocketAgenticTransport(WebSocketClient client,
                                     String baseUrl,
                                     AuthStrategy auth,
                                     ObjectMapper mapper,
                                     Duration responseTimeout,
                                     AgenticMetrics metrics) {
        this.client = Objects.requireNonNull(client, "client");
        this.baseUri = URI.create(toWsScheme(baseUrl));
        this.auth = Objects.requireNonNull(auth, "auth");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "responseTimeout");
        this.metrics = Objects.requireNonNullElse(metrics, AgenticMetrics.NOOP);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Mono<AgentResponse> invoke(String agentName, AgentRequest request) {
        // Use websocket transport to obtain a single result frame.
        return stream(agentName, request, StreamingMode.BUFFERED)
                .filter(event -> event instanceof ResultEvent || event instanceof ErrorEvent)
                .next()
                .map(event -> {
                    if (event instanceof ResultEvent r) {
                        return new AgentResponse(agentName, r.output(), r.success(), null, Map.of());
                    }
                    ErrorEvent err = (ErrorEvent) event;
                    return new AgentResponse(agentName, null, false, err.message(), Map.of());
                });
    }

    @Override
    public Flux<StreamEvent> stream(String agentName, AgentRequest request, StreamingMode mode) {
        Objects.requireNonNull(agentName, "agentName");
        Objects.requireNonNull(request, "request");
        URI uri = baseUri.resolve("/ws/agents/" + agentName);
        long start = System.nanoTime();
        AtomicLong tokens = new AtomicLong();

        Sinks.Many<StreamEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        Mono<Void> session = client.execute(uri, buildHeaders(), wsSession -> {
            String payload;
            try {
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("prompt", request.prompt());
                if (request.deps() != null) envelope.put("deps", request.deps());
                if (request.modelSettings() != null) envelope.put("model_settings", request.modelSettings());
                if (request.conversationId() != null) envelope.put("conversation_id", request.conversationId());
                payload = mapper.writeValueAsString(envelope);
            } catch (Exception e) {
                sink.tryEmitError(new AgenticStreamException("Failed to encode WebSocket payload",
                        AgenticErrorContext.builder().agentName(agentName).transport(NAME).build(), e));
                return Mono.empty();
            }

            Mono<Void> sendInitial = wsSession.send(Mono.just(wsSession.textMessage(payload)));
            Mono<Void> receive = wsSession.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(raw -> handleFrame(raw, sink, tokens))
                    .then();
            return sendInitial.then(receive)
                    .doOnError(err -> sink.tryEmitError(translate(err, agentName)))
                    .doFinally(signal -> sink.tryEmitComplete());
        }).timeout(responseTimeout, Mono.error(() ->
                new AgenticTransportException("WebSocket session timed out",
                        AgenticErrorContext.builder().agentName(agentName).transport(NAME).build(), null)));

        // Drive the session and ensure errors propagate.
        session.subscribe(
                ignored -> {},
                err -> sink.tryEmitError(translate(err, agentName)),
                sink::tryEmitComplete);

        String mode0 = mode == null ? "buffered" : mode.name().toLowerCase();
        metrics.recordStreamStart(agentName, mode0);
        return sink.asFlux()
                .doOnComplete(() -> metrics.recordStreamComplete(agentName, mode0, "success", elapsed(start), tokens))
                .doOnError(err -> metrics.recordStreamComplete(agentName, mode0, "transport_error", elapsed(start), tokens));
    }

    private void handleFrame(String raw, Sinks.Many<StreamEvent> sink, AtomicLong tokens) {
        try {
            Map<String, Object> envelope = mapper.readValue(raw, Map.class);
            String type = String.valueOf(envelope.get("type"));
            Object data = envelope.get("data");
            switch (type) {
                case "token" -> {
                    sink.tryEmitNext(new TokenEvent(data == null ? "" : data.toString()));
                    tokens.incrementAndGet();
                }
                case "result" -> {
                    boolean success = Boolean.TRUE.equals(envelope.getOrDefault("success", Boolean.TRUE));
                    sink.tryEmitNext(new ResultEvent(data, success));
                }
                case "error" -> sink.tryEmitNext(new ErrorEvent(data == null ? "" : data.toString()));
                case "conversation_id" -> sink.tryEmitNext(new ConversationIdEvent(data == null ? "" : data.toString()));
                default -> {
                    // Forward unknown frames as token events so consumers can still observe them.
                    sink.tryEmitNext(new TokenEvent(raw));
                }
            }
        } catch (Exception ex) {
            sink.tryEmitError(new AgenticStreamException("Invalid WebSocket frame: " + raw,
                    AgenticErrorContext.builder().transport(NAME).attribute("payload", raw).build(), ex));
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        auth.headers().forEach(headers::add);
        headers.add("X-Request-ID", UUID.randomUUID().toString());
        return headers;
    }

    private static String toWsScheme(String baseUrl) {
        if (baseUrl.startsWith("https://")) {
            return "wss://" + baseUrl.substring("https://".length());
        }
        if (baseUrl.startsWith("http://")) {
            return "ws://" + baseUrl.substring("http://".length());
        }
        return baseUrl;
    }

    private Throwable translate(Throwable err, String agentName) {
        if (err instanceof AgenticBridgeException) return err;
        return new AgenticTransportException("WebSocket failure",
                AgenticErrorContext.builder().agentName(agentName).transport(NAME).build(), err);
    }

    private static Duration elapsed(long start) {
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
