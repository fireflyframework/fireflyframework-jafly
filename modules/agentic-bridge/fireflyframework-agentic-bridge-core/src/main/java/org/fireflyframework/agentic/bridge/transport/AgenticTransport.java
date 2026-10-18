/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.transport;

import org.fireflyframework.agentic.bridge.invocation.AgentRequest;
import org.fireflyframework.agentic.bridge.invocation.AgentResponse;
import org.fireflyframework.agentic.bridge.invocation.StreamingMode;
import org.fireflyframework.agentic.bridge.streaming.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Common contract every synchronous-style transport must satisfy.
 *
 * <p>The bridge currently ships REST/SSE and WebSocket implementations.
 * Both implement this same interface so that consumers can swap transports
 * by re-binding a single bean.</p>
 */
public interface AgenticTransport extends AutoCloseable {

    /**
     * Friendly name identifying this transport in logs and metrics
     * ({@code "rest"}, {@code "websocket"}, …).
     */
    String name();

    /**
     * Issues a single non-streaming invocation.
     */
    Mono<AgentResponse> invoke(String agentName, AgentRequest request);

    /**
     * Issues a streaming invocation. The supplied {@link StreamingMode}
     * selects between buffered chunks and incremental tokens.
     */
    Flux<StreamEvent> stream(String agentName, AgentRequest request, StreamingMode mode);

    @Override
    default void close() {
        // default is no-op; transports holding resources override.
    }
}
