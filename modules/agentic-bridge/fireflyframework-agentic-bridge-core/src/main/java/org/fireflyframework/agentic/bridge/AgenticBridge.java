/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge;

import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import org.fireflyframework.agentic.bridge.transport.queue.QueueAgenticPublisher;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregated façade combining synchronous {@link AgenticClient} access with
 * any number of named queue publishers, exposed under a single bean.
 *
 * <p>Callers that only need a single client per service can inject
 * {@link AgenticClient} directly; the {@code AgenticBridge} is the natural
 * choice when an application needs to mix synchronous invocations with
 * asynchronous queue dispatch and wants a single dependency to inject.</p>
 */
public interface AgenticBridge extends AutoCloseable {

    /**
     * Primary synchronous client.
     */
    AgenticClient client();

    /**
     * Returns every named queue publisher that has been registered.
     */
    Map<String, QueueAgenticPublisher> publishers();

    /**
     * Returns the set of publisher names known to this bridge.
     */
    default Set<String> publisherNames() {
        return publishers().keySet();
    }

    /**
     * Looks up a publisher by name.
     */
    default Optional<QueueAgenticPublisher> publisher(String name) {
        return Optional.ofNullable(publishers().get(name));
    }

    /**
     * Aggregated reactive health probe — combines the client's health with
     * each publisher's reported health.
     */
    Mono<AgenticHealth> health();

    @Override
    void close();
}
