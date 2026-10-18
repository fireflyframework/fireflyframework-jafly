/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.resilience;

import java.time.Duration;
import java.util.Optional;

/**
 * Resilience4j circuit-breaker tuning knobs surfaced through the bridge
 * builder. Resilience4j is an optional dependency; the bridge only attempts
 * to materialise a circuit breaker if the library is on the classpath
 * <em>and</em> the user explicitly enables this configuration.
 */
public final class CircuitBreakerSettings {

    public static final CircuitBreakerSettings DISABLED = new CircuitBreakerSettings(false, null, 50f, 100, 10, Duration.ofSeconds(30), Duration.ofSeconds(2));

    private final boolean enabled;
    private final String name;
    private final float failureRateThreshold;
    private final int slidingWindowSize;
    private final int minimumNumberOfCalls;
    private final Duration waitDurationInOpenState;
    private final Duration slowCallDurationThreshold;

    private CircuitBreakerSettings(boolean enabled, String name, float failureRateThreshold,
                                   int slidingWindowSize, int minimumNumberOfCalls,
                                   Duration waitDurationInOpenState, Duration slowCallDurationThreshold) {
        this.enabled = enabled;
        this.name = name;
        this.failureRateThreshold = failureRateThreshold;
        this.slidingWindowSize = slidingWindowSize;
        this.minimumNumberOfCalls = minimumNumberOfCalls;
        this.waitDurationInOpenState = waitDurationInOpenState;
        this.slowCallDurationThreshold = slowCallDurationThreshold;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean enabled() { return enabled; }
    public Optional<String> name() { return Optional.ofNullable(name); }
    public float failureRateThreshold() { return failureRateThreshold; }
    public int slidingWindowSize() { return slidingWindowSize; }
    public int minimumNumberOfCalls() { return minimumNumberOfCalls; }
    public Duration waitDurationInOpenState() { return waitDurationInOpenState; }
    public Duration slowCallDurationThreshold() { return slowCallDurationThreshold; }

    public static final class Builder {
        private boolean enabled = true;
        private String name;
        private float failureRateThreshold = 50f;
        private int slidingWindowSize = 100;
        private int minimumNumberOfCalls = 10;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private Duration slowCallDurationThreshold = Duration.ofSeconds(2);

        public Builder enabled(boolean value) { this.enabled = value; return this; }
        public Builder name(String value) { this.name = value; return this; }
        public Builder failureRateThreshold(float value) { this.failureRateThreshold = value; return this; }
        public Builder slidingWindowSize(int value) { this.slidingWindowSize = value; return this; }
        public Builder minimumNumberOfCalls(int value) { this.minimumNumberOfCalls = value; return this; }
        public Builder waitDurationInOpenState(Duration value) { this.waitDurationInOpenState = value; return this; }
        public Builder slowCallDurationThreshold(Duration value) { this.slowCallDurationThreshold = value; return this; }

        public CircuitBreakerSettings build() {
            return new CircuitBreakerSettings(enabled, name, failureRateThreshold, slidingWindowSize,
                    minimumNumberOfCalls, waitDurationInOpenState, slowCallDurationThreshold);
        }
    }
}
