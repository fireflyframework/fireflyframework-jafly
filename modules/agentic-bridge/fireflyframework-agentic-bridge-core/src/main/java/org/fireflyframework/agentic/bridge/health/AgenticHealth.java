/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot of the bridge's view of the remote agentic service health.
 *
 * <p>Returned by {@code AgenticClient.health()}. The {@link Status} is
 * derived from the service's {@code GET /health} endpoint plus the bridge's
 * own internal state (open circuit breakers, repeated transport failures).</p>
 */
public final class AgenticHealth {

    public enum Status {
        UP,
        DEGRADED,
        DOWN,
        UNKNOWN
    }

    private final Status status;
    private final String message;
    private final Instant checkedAt;
    private final Duration latency;
    private final Map<String, Object> details;

    public AgenticHealth(Status status, String message, Instant checkedAt, Duration latency, Map<String, Object> details) {
        this.status = status == null ? Status.UNKNOWN : status;
        this.message = message;
        this.checkedAt = checkedAt == null ? Instant.now() : checkedAt;
        this.latency = latency;
        this.details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static AgenticHealth up(Duration latency, Map<String, Object> details) {
        return new AgenticHealth(Status.UP, "Service is healthy", Instant.now(), latency, details);
    }

    public static AgenticHealth down(String message, Throwable cause) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (cause != null) {
            details.put("cause", cause.getClass().getName());
            details.put("causeMessage", cause.getMessage());
        }
        return new AgenticHealth(Status.DOWN, message, Instant.now(), null, details);
    }

    public Status status() { return status; }
    public String message() { return message; }
    public Instant checkedAt() { return checkedAt; }
    public Duration latency() { return latency; }
    public Map<String, Object> details() { return details; }
}
