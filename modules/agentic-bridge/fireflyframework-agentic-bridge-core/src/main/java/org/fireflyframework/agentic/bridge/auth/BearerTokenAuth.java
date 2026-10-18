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
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Adds an {@code Authorization: Bearer <token>} header to every outbound
 * request. Reads the token from the supplied {@link Supplier} on every call
 * so it integrates naturally with OAuth2 helpers that refresh tokens in the
 * background.
 */
final class BearerTokenAuth implements AuthStrategy {

    private final Supplier<String> supplier;

    BearerTokenAuth(Supplier<String> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public Map<String, String> headers() {
        String token = supplier.get();
        if (token == null || token.isBlank()) {
            return Map.of();
        }
        return Map.of("Authorization", "Bearer " + token);
    }

    @Override
    public String name() {
        return "bearer";
    }
}
