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
import java.util.function.Supplier;

/**
 * Pluggable authentication contract that contributes outbound HTTP/WebSocket
 * headers for every request issued by the bridge.
 *
 * <p>Implementations must be cheap to invoke and thread-safe — they are
 * called on every request from the reactive Netty event loop.</p>
 *
 * <p>Use the static factory methods for the canonical strategies:</p>
 * <ul>
 *     <li>{@link #none()} — anonymous access.</li>
 *     <li>{@link #apiKey(String)} — fixed {@code X-API-Key}.</li>
 *     <li>{@link #apiKey(Supplier)} — dynamically resolved {@code X-API-Key}.</li>
 *     <li>{@link #bearer(String)} — fixed {@code Authorization: Bearer …}.</li>
 *     <li>{@link #bearer(Supplier)} — dynamically resolved bearer token.</li>
 *     <li>{@link #composite(AuthStrategy...)} — chain multiple strategies.</li>
 * </ul>
 */
@FunctionalInterface
public interface AuthStrategy {

    /**
     * Returns the headers to attach to a single outbound request.
     */
    Map<String, String> headers();

    /**
     * Returns the name of this strategy for logging/diagnostics.
     */
    default String name() {
        return getClass().getSimpleName();
    }

    static AuthStrategy none() {
        return NoAuth.INSTANCE;
    }

    static AuthStrategy apiKey(String value) {
        return new ApiKeyAuth(() -> value);
    }

    static AuthStrategy apiKey(Supplier<String> supplier) {
        return new ApiKeyAuth(supplier);
    }

    static AuthStrategy apiKey(String header, Supplier<String> supplier) {
        return new ApiKeyAuth(header, supplier);
    }

    static AuthStrategy bearer(String token) {
        return new BearerTokenAuth(() -> token);
    }

    static AuthStrategy bearer(Supplier<String> supplier) {
        return new BearerTokenAuth(supplier);
    }

    static AuthStrategy composite(AuthStrategy... strategies) {
        return new CompositeAuth(strategies);
    }
}
