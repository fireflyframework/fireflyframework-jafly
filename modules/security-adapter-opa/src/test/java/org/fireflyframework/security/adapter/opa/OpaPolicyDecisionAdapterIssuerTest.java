/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.adapter.opa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpaPolicyDecisionAdapterIssuerTest {
    private static final String ISSUER = "https://issuer.example.test/staff";
    private static final Set<String> LEGACY_FIELDS = Set.of("subject", "authorities", "scopes", "tenantId");
    private final AtomicReference<JsonNode> captured = new AtomicReference<>();

    private JsonNode authorize(String issuer, Map<String, Object> context) {
        var principal = SecurityPrincipal.builder().subject("ordinary-staff").issuer(issuer)
                .tenantId("source-tenant").authorities(Set.of("STAFF_READER")).scopes(Set.of("read"))
                .claims(Map.of("private-claim", "not-policy-input"))
                .attributes(Map.of("private-attribute", "not-policy-input")).build();
        var web = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> {
                    var output = new MockClientHttpRequest(HttpMethod.POST, URI.create("https://policy.example.test"));
                    return request.writeTo(output, ExchangeStrategies.withDefaults())
                            .then(Mono.defer(output::getBodyAsString)).flatMap(body -> Mono.fromCallable(() -> {
                                captured.set(new ObjectMapper().readTree(body).get("input"));
                                return ClientResponse.create(HttpStatus.OK)
                                        .header("Content-Type", "application/json").body("{\"result\":true}").build();
                            }));
                }).build();
        var decision = new OpaPolicyDecisionAdapter(web, "firefly/allow")
                .authorize(principal, "read", "resource", context).block(Duration.ofSeconds(3));
        assertNotNull(decision);
        assertTrue(decision.granted());
        assertNotNull(captured.get());
        return captured.get();
    }

    @Test
    void authenticatedIssuerIsProjectedWithoutClaimsOrAttributes() {
        var input = authorize(ISSUER, Map.of("purpose", "ordinary-read"));
        var subject = input.get("subject");
        assertEquals(ISSUER, subject.path("issuer").asText());
        var names = new HashSet<String>();
        subject.fieldNames().forEachRemaining(names::add);
        var expected = new HashSet<>(LEGACY_FIELDS);
        expected.add("issuer");
        assertEquals(expected, names);
        assertEquals("ordinary-staff", subject.path("subject").asText());
        assertEquals("source-tenant", subject.path("tenantId").asText());
        assertEquals("STAFF_READER", subject.path("authorities").get(0).asText());
        assertEquals("read", subject.path("scopes").get(0).asText());
        assertFalse(input.toString().contains("private-claim"));
        assertFalse(input.toString().contains("private-attribute"));
    }

    @Test
    void requestContextCannotReplaceAuthenticatedIssuer() {
        var input = authorize(ISSUER, Map.of("issuer", "https://different.example.test"));
        assertEquals(ISSUER, input.path("subject").path("issuer").asText());
        assertEquals("https://different.example.test", input.path("context").path("issuer").asText());
    }

    @Test
    void legacyPrincipalWithoutIssuerRetainsExactlyItsOriginalSubjectFields() {
        var input = authorize(null, Map.of());
        var subject = input.path("subject");
        var names = new HashSet<String>();
        subject.fieldNames().forEachRemaining(names::add);
        assertEquals(LEGACY_FIELDS, names);
        assertFalse(subject.has("issuer"));
    }
}
