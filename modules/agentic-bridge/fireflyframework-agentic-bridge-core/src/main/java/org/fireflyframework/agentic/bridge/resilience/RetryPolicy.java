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

import org.fireflyframework.agentic.bridge.exception.AgenticAuthenticationException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticTransportException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Retry configuration applied to every reactive HTTP/WebSocket invocation.
 *
 * <p>Backed by {@link Retry#backoff(long, Duration)} with optional jitter and
 * a customisable predicate. The default predicate retries
 * {@link AgenticTransportException} and any other {@link AgenticBridgeException}
 * whose {@code httpStatus} is 5xx, while never retrying authentication
 * failures.</p>
 */
public final class RetryPolicy {

    private static final Predicate<Throwable> DEFAULT_FILTER = throwable -> {
        if (throwable instanceof AgenticAuthenticationException) {
            return false;
        }
        if (throwable instanceof AgenticTransportException) {
            return true;
        }
        if (throwable instanceof AgenticBridgeException ex) {
            return ex.getErrorContext().httpStatus()
                    .map(status -> status >= 500 && status < 600)
                    .orElse(false);
        }
        return false;
    };

    public static final RetryPolicy DISABLED = new RetryPolicy(false, 0, Duration.ZERO, Duration.ZERO, false, t -> false);

    private final boolean enabled;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final boolean jitter;
    private final Predicate<Throwable> filter;

    private RetryPolicy(boolean enabled, int maxAttempts, Duration initialBackoff, Duration maxBackoff,
                        boolean jitter, Predicate<Throwable> filter) {
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.jitter = jitter;
        this.filter = filter;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RetryPolicy defaults() {
        return builder().build();
    }

    public boolean enabled() { return enabled; }
    public int maxAttempts() { return maxAttempts; }
    public Duration initialBackoff() { return initialBackoff; }
    public Duration maxBackoff() { return maxBackoff; }
    public boolean jitter() { return jitter; }
    public Predicate<Throwable> filter() { return filter; }

    /**
     * Materialises this policy as a Reactor {@link Retry} signal usable from
     * any {@code Mono#retryWhen} or {@code Flux#retryWhen} call.
     */
    public Retry toReactor() {
        if (!enabled || maxAttempts <= 0) {
            return Retry.max(0);
        }
        RetryBackoffSpec spec = Retry.backoff(maxAttempts, initialBackoff)
                .maxBackoff(maxBackoff)
                .filter(filter)
                .transientErrors(true);
        return jitter ? spec.jitter(0.5d) : spec.jitter(0d);
    }

    public static final class Builder {
        private boolean enabled = true;
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(250);
        private Duration maxBackoff = Duration.ofSeconds(5);
        private boolean jitter = true;
        private Predicate<Throwable> filter = DEFAULT_FILTER;

        public Builder enabled(boolean value) { this.enabled = value; return this; }
        public Builder maxAttempts(int value) {
            if (value < 0) throw new IllegalArgumentException("maxAttempts must be >= 0");
            this.maxAttempts = value;
            return this;
        }
        public Builder initialBackoff(Duration value) {
            if (value == null || value.isNegative()) throw new IllegalArgumentException("initialBackoff must be non-negative");
            this.initialBackoff = value;
            return this;
        }
        public Builder maxBackoff(Duration value) {
            if (value == null || value.isNegative()) throw new IllegalArgumentException("maxBackoff must be non-negative");
            this.maxBackoff = value;
            return this;
        }
        public Builder jitter(boolean value) { this.jitter = value; return this; }
        public Builder filter(Predicate<Throwable> value) {
            if (value != null) this.filter = value;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(enabled, maxAttempts, initialBackoff, maxBackoff, jitter, filter);
        }
    }
}
