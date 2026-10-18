/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.catalog;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot of every agent published by the connected agentic service at the
 * moment {@code GET /agents/} was last invoked.
 *
 * <p>Returned by {@code AgenticClient.catalog()}. Internally the bridge
 * caches the most recent snapshot so consumers can perform fast lookups by
 * name without an additional round trip.</p>
 */
public final class AgentCatalog {

    private final List<AgentDescriptor> agents;
    private final Map<String, AgentDescriptor> byName;
    private final Instant fetchedAt;

    public AgentCatalog(List<AgentDescriptor> agents, Instant fetchedAt) {
        Objects.requireNonNull(agents, "agents");
        this.agents = List.copyOf(agents);
        this.fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
        Map<String, AgentDescriptor> map = new LinkedHashMap<>();
        for (AgentDescriptor a : this.agents) {
            if (a.name() != null) {
                map.put(a.name(), a);
            }
        }
        this.byName = Collections.unmodifiableMap(map);
    }

    public List<AgentDescriptor> agents() {
        return agents;
    }

    public Optional<AgentDescriptor> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public boolean contains(String name) {
        return byName.containsKey(name);
    }

    public int size() {
        return agents.size();
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }
}
