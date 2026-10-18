/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records Micrometer instruments for every bridge invocation, stream, and
 * publish operation.
 *
 * <p>Designed to degrade gracefully: when the {@link MeterRegistry} is
 * {@code null}, every method becomes a no-op so that consumers without
 * Micrometer on the classpath can still use the bridge.</p>
 */
public final class AgenticMetrics {

    public static final AgenticMetrics NOOP = new AgenticMetrics(null, "default");

    private final MeterRegistry registry;
    private final String clientName;

    public AgenticMetrics(MeterRegistry registry, String clientName) {
        this.registry = registry;
        this.clientName = Objects.requireNonNullElse(clientName, "default");
    }

    public boolean enabled() {
        return registry != null;
    }

    public void recordInvocation(String agentName, String transport, String outcome, Duration duration) {
        if (registry == null) {
            return;
        }
        Tags tags = baseTags(agentName, transport).and("outcome", outcome);
        registry.counter("firefly.agentic.bridge.invocations", tags).increment();
        Timer.builder("firefly.agentic.bridge.invocation.duration")
                .tags(tags)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    public void recordStreamStart(String agentName, String mode) {
        if (registry == null) {
            return;
        }
        Tags tags = baseTags(agentName, "stream").and("mode", mode);
        registry.counter("firefly.agentic.bridge.streams", tags).increment();
    }

    public void recordStreamComplete(String agentName, String mode, String outcome, Duration duration, AtomicLong tokens) {
        if (registry == null) {
            return;
        }
        Tags tags = Tags.of(Tag.of("client", clientName), Tag.of("agent", agentName), Tag.of("mode", mode), Tag.of("outcome", outcome));
        Timer.builder("firefly.agentic.bridge.stream.duration")
                .tags(tags)
                .register(registry)
                .record(duration);
        if (tokens != null) {
            registry.counter("firefly.agentic.bridge.stream.tokens",
                    Tags.of(Tag.of("client", clientName), Tag.of("agent", agentName), Tag.of("mode", mode)))
                    .increment(tokens.get());
        }
    }

    public void recordPublish(String publisher, String transport, String outcome, Duration duration) {
        if (registry == null) {
            return;
        }
        Tags tags = Tags.of(Tag.of("publisher", publisher), Tag.of("transport", transport), Tag.of("outcome", outcome));
        registry.counter("firefly.agentic.bridge.publish", tags).increment();
        Timer.builder("firefly.agentic.bridge.publish.duration")
                .tags(tags)
                .register(registry)
                .record(duration);
    }

    private Tags baseTags(String agentName, String transport) {
        return Tags.of(
                Tag.of("client", clientName),
                Tag.of("agent", agentName == null ? "unknown" : agentName),
                Tag.of("transport", transport == null ? "unknown" : transport));
    }
}
