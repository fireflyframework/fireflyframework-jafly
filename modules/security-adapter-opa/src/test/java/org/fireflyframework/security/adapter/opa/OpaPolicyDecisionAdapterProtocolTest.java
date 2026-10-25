/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.adapter.opa;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpaPolicyDecisionAdapterProtocolTest {
    private static final Duration WAIT = Duration.ofSeconds(3);
    private final SecurityPrincipal principal = SecurityPrincipal.builder().subject("protocol-subject").build();

    private OpaPolicyDecisionAdapter adapter(String body) {
        return new OpaPolicyDecisionAdapter(WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json").body(body).build())).build(), "firefly/allow");
    }

    @Test
    void literalBooleanTruePermits() {
        var decision = adapter("{\"result\":true}").authorize(principal, "read", "resource", Map.of()).block(WAIT);
        assertNotNull(decision);
        assertTrue(decision.granted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"result\":false}", "{\"result\":\"true\"}", "{\"result\":1}",
            "{\"result\":{}}", "{\"result\":[]}", "{\"result\":null}", "{}", "{invalid"})
    void nonBooleanTrueAndMalformedResponsesDeny(String response) {
        var decision = adapter(response).authorize(principal, "read", "resource", Map.of()).block(WAIT);
        assertNotNull(decision);
        assertFalse(decision.granted());
    }

    @Test
    void unavailableTransportDenies() {
        var client = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.error(new IOException("Owned protocol transport unavailable"))).build();
        var decision = new OpaPolicyDecisionAdapter(client, "firefly/allow")
                .authorize(principal, "read", "resource", Map.of()).block(WAIT);
        assertNotNull(decision);
        assertFalse(decision.granted());
    }
}
