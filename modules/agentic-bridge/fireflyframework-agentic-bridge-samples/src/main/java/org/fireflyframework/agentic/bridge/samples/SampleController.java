/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.samples;

import org.fireflyframework.agentic.bridge.AgenticBridge;
import org.fireflyframework.agentic.bridge.AgenticClient;
import org.fireflyframework.agentic.bridge.catalog.AgentCatalog;
import org.fireflyframework.agentic.bridge.invocation.AgentRequest;
import org.fireflyframework.agentic.bridge.invocation.AgentResponse;
import org.fireflyframework.agentic.bridge.streaming.StreamEvent;
import org.fireflyframework.agentic.bridge.transport.queue.QueueAgenticPublisher;
import org.fireflyframework.agentic.bridge.transport.queue.QueueInvocation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Demonstrates every façade exposed by the bridge.
 */
@RestController
@RequestMapping("/sample")
public class SampleController {

    private final AgenticClient client;
    private final AgenticBridge bridge;

    public SampleController(AgenticClient client, AgenticBridge bridge) {
        this.client = client;
        this.bridge = bridge;
    }

    @GetMapping("/agents")
    public Mono<AgentCatalog> agents() {
        return client.catalog();
    }

    @PostMapping("/{agent}/invoke")
    public Mono<AgentResponse> invoke(@PathVariable String agent, @RequestBody Map<String, Object> body) {
        AgentRequest request = AgentRequest.of(String.valueOf(body.getOrDefault("prompt", "")));
        return client.invoke(agent, request);
    }

    @PostMapping(value = "/{agent}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEvent> stream(@PathVariable String agent, @RequestBody Map<String, Object> body) {
        return client.streamIncremental(agent, AgentRequest.of(String.valueOf(body.getOrDefault("prompt", ""))));
    }

    @PostMapping(value = "/{agent}/conversation", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEvent> conversation(@PathVariable String agent,
                                          @RequestBody Map<String, Object> body) {
        AgentRequest request = AgentRequest.of(String.valueOf(body.getOrDefault("prompt", "")));
        Object conversationId = body.get("conversation_id");
        if (conversationId != null) {
            request = request.withConversationId(conversationId.toString());
        }
        return client.conversation(agent, request);
    }

    @PostMapping("/{publisher}/{agent}/publish")
    public Mono<Void> publish(@PathVariable String publisher,
                              @PathVariable String agent,
                              @RequestBody Map<String, Object> body) {
        QueueAgenticPublisher target = bridge.publisher(publisher)
                .orElseThrow(() -> new IllegalArgumentException("No publisher named " + publisher));
        return target.publish(QueueInvocation.of(agent,
                AgentRequest.of(String.valueOf(body.getOrDefault("prompt", "")))));
    }
}
