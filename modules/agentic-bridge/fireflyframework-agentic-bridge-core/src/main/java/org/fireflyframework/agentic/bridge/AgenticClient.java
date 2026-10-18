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

import org.fireflyframework.agentic.bridge.catalog.AgentCatalog;
import org.fireflyframework.agentic.bridge.conversation.ConversationManager;
import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import org.fireflyframework.agentic.bridge.invocation.AgentRequest;
import org.fireflyframework.agentic.bridge.invocation.AgentResponse;
import org.fireflyframework.agentic.bridge.streaming.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive façade for invoking remote agents hosted by
 * {@code fireflyframework-agentic}.
 *
 * <p>The client is fully non-blocking: every method returns a Reactor
 * {@link Mono} or {@link Flux}. Build instances via
 * {@link AgenticClient#builder(String)} or rely on Spring Boot
 * auto-configuration to obtain a managed singleton.</p>
 *
 * <p>Implementations are thread-safe and intended to be cached for the
 * lifetime of the application.</p>
 */
public interface AgenticClient extends AutoCloseable {

    /**
     * Friendly name for this client (used in logs and metrics).
     */
    String name();

    /**
     * Issues a single non-streaming invocation against {@code agentName}.
     */
    Mono<AgentResponse> invoke(String agentName, AgentRequest request);

    /**
     * Issues a buffered SSE stream against {@code agentName}.
     */
    Flux<StreamEvent> stream(String agentName, AgentRequest request);

    /**
     * Issues an incremental (token-by-token) SSE stream against {@code agentName}.
     */
    Flux<StreamEvent> streamIncremental(String agentName, AgentRequest request);

    /**
     * Establishes a WebSocket session for multi-turn conversations.
     */
    Flux<StreamEvent> conversation(String agentName, AgentRequest request);

    /**
     * Refreshes and returns the catalog of agents currently registered on
     * the remote service.
     */
    Mono<AgentCatalog> catalog();

    /**
     * Returns the cached catalog, or empty if the catalog has never been
     * fetched.
     */
    Mono<AgentCatalog> cachedCatalog();

    /**
     * Reactive client for the {@code /agents/conversations} CRUD endpoints.
     */
    ConversationManager conversations();

    /**
     * Reactive health probe.
     */
    Mono<AgenticHealth> health();

    /**
     * Releases any resources held by the underlying transports. Idempotent.
     */
    @Override
    void close();

    /**
     * Returns a builder for a freshly named client.
     */
    static AgenticClientBuilder builder(String clientName) {
        return new AgenticClientBuilder(clientName);
    }
}
