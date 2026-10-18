/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Combines multiple {@link AuthStrategy} instances; later strategies overwrite
 * earlier ones on header conflict. Useful when a service requires both an
 * API key and a tenant header for example.
 */
final class CompositeAuth implements AuthStrategy {

    private final List<AuthStrategy> strategies;

    CompositeAuth(AuthStrategy... strategies) {
        Objects.requireNonNull(strategies, "strategies");
        this.strategies = List.of(strategies);
    }

    @Override
    public Map<String, String> headers() {
        Map<String, String> merged = new LinkedHashMap<>();
        for (AuthStrategy strategy : strategies) {
            merged.putAll(strategy.headers());
        }
        return merged;
    }

    @Override
    public String name() {
        return "composite[" + strategies.stream().map(AuthStrategy::name).collect(Collectors.joining(",")) + "]";
    }
}
