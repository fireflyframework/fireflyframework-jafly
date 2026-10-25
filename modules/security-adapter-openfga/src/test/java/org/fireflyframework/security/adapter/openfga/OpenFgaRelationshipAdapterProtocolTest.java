/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.adapter.openfga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class OpenFgaRelationshipAdapterProtocolTest {
    private static final Duration WAIT = Duration.ofSeconds(3);

    private OpenFgaRelationshipAdapter adapter(String body) {
        return new OpenFgaRelationshipAdapter(WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json").body(body).build())).build(), "protocol-store");
    }

    @Test
    void literalBooleanTruePermits() {
        var allowed = adapter("{\"allowed\":true}").check("subject", "member", "resource").block(WAIT);
        assertNotNull(allowed);
        assertTrue(allowed);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"allowed\":false}", "{\"allowed\":\"true\"}", "{\"allowed\":1}",
            "{\"allowed\":{}}", "{\"allowed\":[]}", "{\"allowed\":null}", "{}", "{invalid"})
    void nonBooleanTrueAndMalformedResponsesDeny(String response) {
        var allowed = adapter(response).check("subject", "member", "resource").block(WAIT);
        assertNotNull(allowed);
        assertFalse(allowed);
    }

    @Test
    void unavailableTransportDenies() {
        var client = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.error(new IOException("Owned protocol transport unavailable"))).build();
        var allowed = new OpenFgaRelationshipAdapter(client, "protocol-store")
                .check("subject", "member", "resource").block(WAIT);
        assertNotNull(allowed);
        assertFalse(allowed);
    }
}
