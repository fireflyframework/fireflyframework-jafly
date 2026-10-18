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

import org.fireflyframework.agentic.bridge.AgenticBridge;
import org.fireflyframework.agentic.bridge.AgenticClient;
import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import org.fireflyframework.agentic.bridge.transport.queue.QueueAgenticPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Production implementation of {@link AgenticBridge}. Holds a single
 * {@link AgenticClient} and any number of named queue publishers.
 */
public final class DefaultAgenticBridge implements AgenticBridge {

    private final AgenticClient client;
    private final Map<String, QueueAgenticPublisher> publishers;

    public DefaultAgenticBridge(AgenticClient client, Map<String, QueueAgenticPublisher> publishers) {
        this.client = Objects.requireNonNull(client, "client");
        Map<String, QueueAgenticPublisher> copy = new LinkedHashMap<>(publishers == null ? Map.of() : publishers);
        this.publishers = Collections.unmodifiableMap(copy);
    }

    @Override
    public AgenticClient client() {
        return client;
    }

    @Override
    public Map<String, QueueAgenticPublisher> publishers() {
        return publishers;
    }

    @Override
    public Mono<AgenticHealth> health() {
        if (publishers.isEmpty()) {
            return client.health();
        }
        Mono<AgenticHealth> clientHealth = client.health();
        Flux<AgenticHealth> publisherHealth = Flux.fromIterable(publishers.values())
                .flatMap(QueueAgenticPublisher::health);
        return clientHealth.concatWith(publisherHealth)
                .reduce(this::merge);
    }

    private AgenticHealth merge(AgenticHealth left, AgenticHealth right) {
        AgenticHealth.Status status = worst(left.status(), right.status());
        Map<String, Object> details = new LinkedHashMap<>(left.details());
        right.details().forEach(details::putIfAbsent);
        String message = "Aggregated bridge health";
        return new AgenticHealth(status, message, java.time.Instant.now(),
                left.latency() != null ? left.latency() : right.latency(), details);
    }

    private AgenticHealth.Status worst(AgenticHealth.Status a, AgenticHealth.Status b) {
        if (a == AgenticHealth.Status.DOWN || b == AgenticHealth.Status.DOWN) return AgenticHealth.Status.DOWN;
        if (a == AgenticHealth.Status.DEGRADED || b == AgenticHealth.Status.DEGRADED) return AgenticHealth.Status.DEGRADED;
        if (a == AgenticHealth.Status.UNKNOWN || b == AgenticHealth.Status.UNKNOWN) return AgenticHealth.Status.UNKNOWN;
        return AgenticHealth.Status.UP;
    }

    @Override
    public void close() {
        try { client.close(); } catch (Exception ignored) {}
        publishers.values().forEach(p -> {
            try { p.close(); } catch (Exception ignored) {}
        });
    }
}
