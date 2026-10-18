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

import java.util.Map;

/**
 * No-op authentication strategy used when the agentic service is reachable
 * over a private network or behind a service mesh.
 */
final class NoAuth implements AuthStrategy {

    static final NoAuth INSTANCE = new NoAuth();

    private NoAuth() {
    }

    @Override
    public Map<String, String> headers() {
        return Map.of();
    }

    @Override
    public String name() {
        return "none";
    }
}
