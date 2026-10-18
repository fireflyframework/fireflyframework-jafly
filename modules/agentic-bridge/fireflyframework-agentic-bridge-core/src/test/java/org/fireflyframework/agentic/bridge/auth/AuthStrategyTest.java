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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthStrategyTest {

    @Test
    void noneEmitsNoHeaders() {
        assertThat(AuthStrategy.none().headers()).isEmpty();
    }

    @Test
    void apiKeyEmitsHeader() {
        Map<String, String> headers = AuthStrategy.apiKey("secret").headers();
        assertThat(headers).containsEntry("X-API-Key", "secret");
    }

    @Test
    void apiKeyWithBlankValueEmitsNoHeader() {
        Map<String, String> headers = AuthStrategy.apiKey("").headers();
        assertThat(headers).isEmpty();
    }

    @Test
    void bearerEmitsAuthorizationHeader() {
        Map<String, String> headers = AuthStrategy.bearer("token-xyz").headers();
        assertThat(headers).containsEntry("Authorization", "Bearer token-xyz");
    }

    @Test
    void compositeMergesHeaders() {
        AuthStrategy composite = AuthStrategy.composite(AuthStrategy.apiKey("a"), AuthStrategy.bearer("b"));
        Map<String, String> headers = composite.headers();
        assertThat(headers).containsEntry("X-API-Key", "a").containsEntry("Authorization", "Bearer b");
    }
}
