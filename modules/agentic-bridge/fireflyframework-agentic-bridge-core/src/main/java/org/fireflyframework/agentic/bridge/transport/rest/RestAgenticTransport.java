/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.transport.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.fireflyframework.agentic.bridge.auth.AuthStrategy;
import org.fireflyframework.agentic.bridge.exception.AgentInvocationException;
import org.fireflyframework.agentic.bridge.exception.AgentNotFoundException;
import org.fireflyframework.agentic.bridge.exception.AgenticAuthenticationException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException.AgenticErrorContext;
import org.fireflyframework.agentic.bridge.exception.AgenticRateLimitException;
import org.fireflyframework.agentic.bridge.exception.AgenticTimeoutException;
import org.fireflyframework.agentic.bridge.exception.AgenticTransportException;
import org.fireflyframework.agentic.bridge.invocation.AgentRequest;
import org.fireflyframework.agentic.bridge.invocation.AgentResponse;
import org.fireflyframework.agentic.bridge.invocation.StreamingMode;
import org.fireflyframework.agentic.bridge.observability.AgenticMetrics;
import org.fireflyframework.agentic.bridge.resilience.RetryPolicy;
import org.fireflyframework.agentic.bridge.streaming.DoneEvent;
import org.fireflyframework.agentic.bridge.streaming.StreamEvent;
import org.fireflyframework.agentic.bridge.transport.AgenticTransport;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * REST + Server-Sent-Events implementation of {@link AgenticTransport}.
 *
 * <p>Wraps a Spring {@link WebClient} and translates HTTP errors into the
 * bridge's strongly-typed {@link AgenticBridgeException} hierarchy.</p>
 */
public final class RestAgenticTransport implements AgenticTransport {

    private static final String NAME = "rest";
    private static final String AGENTS_PATH = "/agents";

    private final WebClient webClient;
    private final AuthStrategy auth;
    private final ObjectMapper mapper;
    private final SseEventDecoder decoder;
    private final RetryPolicy retryPolicy;
    private final Duration responseTimeout;
    private final AgenticMetrics metrics;

    public RestAgenticTransport(WebClient webClient,
                                AuthStrategy auth,
                                ObjectMapper mapper,
                                RetryPolicy retryPolicy,
                                Duration responseTimeout,
                                AgenticMetrics metrics) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.decoder = new SseEventDecoder(mapper);
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "responseTimeout");
        this.metrics = Objects.requireNonNullElse(metrics, AgenticMetrics.NOOP);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Mono<AgentResponse> invoke(String agentName, AgentRequest request) {
        Objects.requireNonNull(agentName, "agentName");
        Objects.requireNonNull(request, "request");
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();

        return webClient.post()
                .uri(AGENTS_PATH + "/{name}/run", agentName)
                .headers(headers -> applyHeaders(headers, requestId))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), response -> mapHttpError(response, agentName, requestId, "/agents/" + agentName + "/run"))
                .bodyToMono(AgentResponse.class)
                .timeout(responseTimeout, Mono.error(() -> timeoutException(agentName, "/agents/" + agentName + "/run", requestId)))
                .onErrorMap(this::translateError)
                .retryWhen(retryPolicy.toReactor())
                .flatMap(response -> {
                    if (!response.success()) {
                        return Mono.error(new AgentInvocationException(
                                response.error().orElse("Agent reported failure"),
                                AgenticErrorContext.builder()
                                        .agentName(agentName)
                                        .transport(NAME)
                                        .requestId(requestId)
                                        .endpoint("/agents/" + agentName + "/run")
                                        .build()));
                    }
                    return Mono.just(response);
                })
                .doOnEach(signal -> {
                    if (signal.isOnNext()) {
                        metrics.recordInvocation(agentName, NAME, "success", elapsed(start));
                    } else if (signal.isOnError()) {
                        metrics.recordInvocation(agentName, NAME, classifyOutcome(signal.getThrowable()), elapsed(start));
                    }
                });
    }

    @Override
    public Flux<StreamEvent> stream(String agentName, AgentRequest request, StreamingMode mode) {
        Objects.requireNonNull(agentName, "agentName");
        Objects.requireNonNull(request, "request");
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        AtomicLong tokens = new AtomicLong();
        String path = mode == StreamingMode.INCREMENTAL
                ? "/agents/" + agentName + "/stream/incremental"
                : "/agents/" + agentName + "/stream";

        metrics.recordStreamStart(agentName, mode.name().toLowerCase());

        Flux<StreamEvent> events = webClient.post()
                .uri(path)
                .headers(headers -> applyHeaders(headers, requestId))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), response -> mapHttpError(response, agentName, requestId, path))
                .bodyToFlux(new ParameterizedTypeReference<String>() {})
                .flatMap(line -> Flux.fromIterable(decoder.decode(line, mode).map(List::of).orElse(List.of())))
                .takeUntil(event -> event instanceof DoneEvent)
                .timeout(responseTimeout, Flux.error(() -> timeoutException(agentName, path, requestId)))
                .onErrorMap(this::translateError)
                .retryWhen(retryPolicy.toReactor());

        return events
                .doOnNext(e -> tokens.incrementAndGet())
                .doOnComplete(() -> metrics.recordStreamComplete(agentName, mode.name().toLowerCase(), "success", elapsed(start), tokens))
                .doOnError(err -> metrics.recordStreamComplete(agentName, mode.name().toLowerCase(), classifyOutcome(err), elapsed(start), tokens))
                .subscribeOn(Schedulers.parallel());
    }

    private void applyHeaders(HttpHeaders headers, String requestId) {
        headers.add("X-Request-ID", requestId);
        auth.headers().forEach(headers::add);
    }

    private Mono<? extends Throwable> mapHttpError(org.springframework.web.reactive.function.client.ClientResponse response,
                                                   String agentName,
                                                   String requestId,
                                                   String path) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    HttpStatus status = HttpStatus.resolve(response.statusCode().value());
                    int code = response.statusCode().value();
                    AgenticErrorContext ctx = AgenticErrorContext.builder()
                            .agentName(agentName)
                            .transport(NAME)
                            .requestId(requestId)
                            .endpoint(path)
                            .httpStatus(code)
                            .attribute("body", truncate(body))
                            .build();
                    String message = "Agentic service responded with " + code + (status == null ? "" : " " + status.getReasonPhrase());
                    if (code == 404) {
                        return new AgentNotFoundException(agentName, ctx);
                    }
                    if (code == 401 || code == 403) {
                        return new AgenticAuthenticationException(message, ctx);
                    }
                    if (code == 429) {
                        Duration retryAfter = response.headers().header("Retry-After").stream()
                                .findFirst()
                                .map(this::parseRetryAfter)
                                .orElse(null);
                        return new AgenticRateLimitException(message, ctx, retryAfter);
                    }
                    if (code >= 500) {
                        return new AgenticTransportException(message, ctx, null);
                    }
                    return new AgentInvocationException(message, ctx);
                });
    }

    private AgenticTimeoutException timeoutException(String agentName, String path, String requestId) {
        AgenticErrorContext ctx = AgenticErrorContext.builder()
                .agentName(agentName)
                .transport(NAME)
                .requestId(requestId)
                .endpoint(path)
                .build();
        return new AgenticTimeoutException(
                "Timed out waiting for agentic response after " + responseTimeout,
                responseTimeout,
                ctx,
                null);
    }

    private Throwable translateError(Throwable err) {
        if (err instanceof AgenticBridgeException) {
            return err;
        }
        if (err instanceof TimeoutException || err.getCause() instanceof TimeoutException) {
            return new AgenticTimeoutException("Agentic call timed out", responseTimeout,
                    AgenticErrorContext.builder().transport(NAME).build(), err);
        }
        if (err instanceof WebClientResponseException wre) {
            return new AgenticTransportException("HTTP " + wre.getStatusCode().value(),
                    AgenticErrorContext.builder().transport(NAME).httpStatus(wre.getStatusCode().value()).build(), wre);
        }
        if (err instanceof WebClientRequestException wre) {
            return new AgenticTransportException("Network failure: " + wre.getMessage(),
                    AgenticErrorContext.builder().transport(NAME).build(), wre);
        }
        return new AgenticTransportException("Unexpected transport failure: " + err.getMessage(),
                AgenticErrorContext.builder().transport(NAME).build(), err);
    }

    private String classifyOutcome(Throwable t) {
        if (t instanceof AgenticTimeoutException) return "timeout";
        if (t instanceof AgenticAuthenticationException) return "unauthorized";
        if (t instanceof AgenticRateLimitException) return "rate_limited";
        if (t instanceof AgentNotFoundException) return "not_found";
        if (t instanceof AgentInvocationException) return "invocation_error";
        return "transport_error";
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private Duration parseRetryAfter(String value) {
        try {
            long seconds = Long.parseLong(value.trim());
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String truncate(String body) {
        if (body == null) return "";
        return body.length() <= 1024 ? body : body.substring(0, 1024) + "...(truncated)";
    }
}
