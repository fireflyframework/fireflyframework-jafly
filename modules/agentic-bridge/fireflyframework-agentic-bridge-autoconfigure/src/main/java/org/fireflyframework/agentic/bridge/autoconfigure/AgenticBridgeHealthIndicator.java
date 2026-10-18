/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.autoconfigure;

import org.fireflyframework.agentic.bridge.AgenticBridge;
import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Reactive Spring Boot Actuator health indicator backed by
 * {@link AgenticBridge#health()}.
 */
public final class AgenticBridgeHealthIndicator implements ReactiveHealthIndicator {

    private final AgenticBridge bridge;

    public AgenticBridgeHealthIndicator(AgenticBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public Mono<Health> health() {
        return bridge.health()
                .map(this::translate)
                .onErrorResume(err -> Mono.just(Health.down(err).build()));
    }

    private Health translate(AgenticHealth health) {
        Health.Builder builder = switch (health.status()) {
            case UP -> Health.up();
            case DEGRADED -> Health.status("DEGRADED");
            case DOWN -> Health.down();
            case UNKNOWN -> Health.unknown();
        };
        builder.withDetail("status", health.status().name());
        if (health.message() != null) builder.withDetail("message", health.message());
        if (health.latency() != null) builder.withDetail("latencyMs", health.latency().toMillis());
        builder.withDetail("checkedAt", health.checkedAt());
        if (!health.details().isEmpty()) {
            builder.withDetail("details", health.details());
        }
        return builder.build();
    }
}
