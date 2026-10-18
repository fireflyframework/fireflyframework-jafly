/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.transport.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.agentic.bridge.invocation.AgentRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire envelope used by the bridge when serialising a {@link QueueInvocation}
 * to JSON for transport over Kafka, RabbitMQ, or Redis.
 *
 * <p>The shape is identical across brokers: a top-level object with
 * {@code agent}, {@code routing_key}, {@code reply_to}, {@code headers}, and
 * the embedded {@link AgentRequest} as {@code request}. The agentic
 * consumers pull {@code request.prompt} as the input.</p>
 */
public final class QueueMessageEnvelope {

    private QueueMessageEnvelope() {
    }

    public static byte[] encode(QueueInvocation invocation, ObjectMapper mapper) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("agent", invocation.agentName());
        envelope.put("request", invocation.request());
        if (invocation.routingKey() != null) envelope.put("routing_key", invocation.routingKey());
        if (invocation.replyTo() != null) envelope.put("reply_to", invocation.replyTo());
        if (!invocation.headers().isEmpty()) envelope.put("headers", invocation.headers());
        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode QueueInvocation envelope", e);
        }
    }
}
