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
 * Adds an API-key header (defaults to {@code X-API-Key}) to every outbound
 * request. Reads the value from the supplied {@link Supplier} on every call
 * so token rotation works without rebuilding the client.
 */
final class ApiKeyAuth implements AuthStrategy {

    private static final String DEFAULT_HEADER = "X-API-Key";

    private final String header;
    private final Supplier<String> supplier;

    ApiKeyAuth(Supplier<String> supplier) {
        this(DEFAULT_HEADER, supplier);
    }

    ApiKeyAuth(String header, Supplier<String> supplier) {
        this.header = Objects.requireNonNull(header, "header");
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public Map<String, String> headers() {
        String value = supplier.get();
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return Map.of(header, value);
    }

    @Override
    public String name() {
        return "api-key(" + header + ")";
    }
}
