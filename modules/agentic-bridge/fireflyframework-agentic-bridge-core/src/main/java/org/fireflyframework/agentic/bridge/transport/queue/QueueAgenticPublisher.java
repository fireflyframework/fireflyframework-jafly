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

import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import reactor.core.publisher.Mono;

/**
 * Reactive contract every queue publisher implementation must satisfy.
 *
 * <p>Implementations encode the {@link QueueInvocation} into the broker's
 * native frame, propagate W3C Trace Context headers, and acknowledge
 * delivery via the returned {@link Mono}.</p>
 */
public interface QueueAgenticPublisher extends AutoCloseable {

    /**
     * Friendly name of this publisher (used in logs, metrics, and in
     * {@code AgenticBridge.publisher(name)}).
     */
    String name();

    /**
     * Identifies the underlying broker — {@code "kafka"}, {@code "rabbitmq"},
     * or {@code "redis"}.
     */
    String transport();

    /**
     * Publishes a single invocation. The {@link Mono} completes when the
     * broker has acknowledged delivery (or rejected it with an error).
     */
    Mono<Void> publish(QueueInvocation invocation);

    /**
     * Reactive health probe.
     */
    Mono<AgenticHealth> health();

    @Override
    void close();
}
