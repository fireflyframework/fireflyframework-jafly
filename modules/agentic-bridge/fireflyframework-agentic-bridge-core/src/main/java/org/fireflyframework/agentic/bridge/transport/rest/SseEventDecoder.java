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
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticStreamException;
import org.fireflyframework.agentic.bridge.invocation.StreamingMode;
import org.fireflyframework.agentic.bridge.streaming.ChunkEvent;
import org.fireflyframework.agentic.bridge.streaming.DoneEvent;
import org.fireflyframework.agentic.bridge.streaming.ErrorEvent;
import org.fireflyframework.agentic.bridge.streaming.StreamEvent;
import org.fireflyframework.agentic.bridge.streaming.TokenEvent;

import java.util.Map;
import java.util.Optional;

/**
 * Decodes the SSE payloads emitted by the agentic REST exposure layer into
 * the bridge's strongly-typed {@link StreamEvent} hierarchy.
 *
 * <p>The agentic side emits two payload shapes:</p>
 * <ul>
 *     <li>{@code {"text": "..."}} from buffered streaming
 *         ({@code POST /agents/{name}/stream}).</li>
 *     <li>{@code {"token": "..."}} from incremental streaming
 *         ({@code POST /agents/{name}/stream/incremental}).</li>
 * </ul>
 * <p>Plus the literal sentinel {@code [DONE]} when streaming completes.</p>
 */
public final class SseEventDecoder {

    private final ObjectMapper mapper;

    public SseEventDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<StreamEvent> decode(String data, StreamingMode mode) {
        if (data == null) {
            return Optional.empty();
        }
        String trimmed = data.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if ("[DONE]".equals(trimmed)) {
            return Optional.of(DoneEvent.INSTANCE);
        }
        try {
            Map<String, Object> payload = mapper.readValue(trimmed, Map.class);
            if (payload.containsKey("error")) {
                return Optional.of(new ErrorEvent(String.valueOf(payload.get("error"))));
            }
            if (payload.containsKey("token")) {
                Object tok = payload.get("token");
                return Optional.of(new TokenEvent(tok == null ? "" : tok.toString()));
            }
            if (payload.containsKey("text")) {
                Object text = payload.get("text");
                String value = text == null ? "" : text.toString();
                if (mode == StreamingMode.INCREMENTAL) {
                    return Optional.of(new TokenEvent(value));
                }
                return Optional.of(new ChunkEvent(value));
            }
            // Unknown shape — preserve it as a chunk so consumers can still observe it.
            return Optional.of(new ChunkEvent(trimmed));
        } catch (Exception ex) {
            throw new AgenticStreamException("Failed to decode SSE payload",
                    AgenticBridgeException.AgenticErrorContext.builder()
                            .transport("rest-sse")
                            .attribute("payload", trimmed)
                            .build(),
                    ex);
        }
    }
}
