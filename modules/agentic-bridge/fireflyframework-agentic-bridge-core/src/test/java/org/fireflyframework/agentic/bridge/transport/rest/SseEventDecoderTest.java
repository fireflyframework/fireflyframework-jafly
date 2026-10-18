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
import org.fireflyframework.agentic.bridge.invocation.StreamingMode;
import org.fireflyframework.agentic.bridge.streaming.ChunkEvent;
import org.fireflyframework.agentic.bridge.streaming.DoneEvent;
import org.fireflyframework.agentic.bridge.streaming.ErrorEvent;
import org.fireflyframework.agentic.bridge.streaming.StreamEvent;
import org.fireflyframework.agentic.bridge.streaming.TokenEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventDecoderTest {

    private final SseEventDecoder decoder = new SseEventDecoder(new ObjectMapper());

    @Test
    void doneSentinelMapsToDoneEvent() {
        Optional<StreamEvent> event = decoder.decode("[DONE]", StreamingMode.BUFFERED);
        assertThat(event).contains(DoneEvent.INSTANCE);
    }

    @Test
    void textPayloadMapsToChunkInBufferedMode() {
        StreamEvent event = decoder.decode("{\"text\":\"hello\"}", StreamingMode.BUFFERED).orElseThrow();
        assertThat(event).isInstanceOfSatisfying(ChunkEvent.class, e -> assertThat(e.text()).isEqualTo("hello"));
    }

    @Test
    void textPayloadMapsToTokenInIncrementalMode() {
        StreamEvent event = decoder.decode("{\"text\":\"hi\"}", StreamingMode.INCREMENTAL).orElseThrow();
        assertThat(event).isInstanceOfSatisfying(TokenEvent.class, e -> assertThat(e.text()).isEqualTo("hi"));
    }

    @Test
    void tokenPayloadMapsToTokenEvent() {
        StreamEvent event = decoder.decode("{\"token\":\"foo\"}", StreamingMode.INCREMENTAL).orElseThrow();
        assertThat(event).isInstanceOfSatisfying(TokenEvent.class, e -> assertThat(e.text()).isEqualTo("foo"));
    }

    @Test
    void errorPayloadMapsToErrorEvent() {
        StreamEvent event = decoder.decode("{\"error\":\"boom\"}", StreamingMode.BUFFERED).orElseThrow();
        assertThat(event).isInstanceOfSatisfying(ErrorEvent.class, e -> assertThat(e.message()).isEqualTo("boom"));
    }
}
