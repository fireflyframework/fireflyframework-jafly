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

import org.fireflyframework.agentic.bridge.invocation.AgentRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a fire-and-forget invocation dispatched to a message broker for
 * asynchronous consumption by the agentic side's queue consumers.
 */
public final class QueueInvocation {

    private final String agentName;
    private final AgentRequest request;
    private final String routingKey;
    private final String replyTo;
    private final Map<String, String> headers;

    private QueueInvocation(String agentName, AgentRequest request, String routingKey, String replyTo, Map<String, String> headers) {
        this.agentName = Objects.requireNonNull(agentName, "agentName");
        this.request = Objects.requireNonNull(request, "request");
        this.routingKey = routingKey;
        this.replyTo = replyTo;
        this.headers = headers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    public static QueueInvocation of(String agentName, AgentRequest request) {
        return new QueueInvocation(agentName, request, null, null, null);
    }

    public String agentName() { return agentName; }
    public AgentRequest request() { return request; }
    public String routingKey() { return routingKey; }
    public String replyTo() { return replyTo; }
    public Map<String, String> headers() { return headers; }

    public QueueInvocation withRoutingKey(String key) {
        return new QueueInvocation(agentName, request, key, replyTo, headers);
    }

    public QueueInvocation withReplyTo(String replyTo) {
        return new QueueInvocation(agentName, request, routingKey, replyTo, headers);
    }

    public QueueInvocation withHeader(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(headers);
        copy.put(key, value);
        return new QueueInvocation(agentName, request, routingKey, replyTo, copy);
    }

    public QueueInvocation withHeaders(Map<String, String> headers) {
        return new QueueInvocation(agentName, request, routingKey, replyTo, headers);
    }
}
