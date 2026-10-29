/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.adapter.opa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.PolicyEvaluation;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpaPolicyEvaluationAdapterTest {
    private static final Duration WAIT = Duration.ofSeconds(3);
    private static final String PERMIT = "{\"result\":{\"allow\":true,\"policy_revision\":\"bundle-revision-17\"},\"decision_id\":\"decision-23\"}";
    private final SecurityPrincipal principal = SecurityPrincipal.builder().subject("ordinary-subject")
            .issuer("https://issuer.example.test").tenantId("opaque/platform/tenant")
            .authorities(Set.of("READER")).scopes(Set.of("read"))
            .claims(Map.of("private-claim", "excluded")).attributes(Map.of("private-attribute", "excluded")).build();

    private OpaPolicyEvaluationAdapter adapter(String body) {
        return new OpaPolicyEvaluationAdapter(WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json").body(body).build())).build(), "/firefly/versioned");
    }

    private PolicyEvaluation evaluate(String body) {
        var result = adapter(body).evaluate(principal, "read", "resource", Map.of()).block(WAIT);
        assertNotNull(result);
        return result;
    }

    private void assertRefused(PolicyEvaluation evaluation) {
        assertEquals(Decision.Effect.INDETERMINATE, evaluation.decision().effect());
        assertTrue(evaluation.provenance().isEmpty());
        assertEquals("OPA versioned evaluation unavailable", evaluation.decision().reason());
    }

    @Test
    void structuredPermitRetainsExactSameResponseServerMetadata() {
        var result = evaluate(PERMIT);
        assertTrue(result.decision().granted());
        assertEquals("bundle-revision-17", result.provenance().orElseThrow().policyRevision());
        assertEquals("decision-23", result.provenance().orElseThrow().decisionId());
    }

    @Test
    void structuredDenyRetainsMetadataWithoutGranting() {
        var result = evaluate(PERMIT.replace("true", "false"));
        assertEquals(Decision.Effect.DENY, result.decision().effect());
        assertEquals("decision-23", result.provenance().orElseThrow().decisionId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"result\":true}", "{}", "null", "[]", "{invalid",
            "{\"result\":{\"allow\":true,\"policy_revision\":\"revision\"}}",
            "{\"result\":{\"allow\":true},\"decision_id\":\"decision\"}",
            "{\"result\":{\"allow\":\"true\",\"policy_revision\":\"revision\"},\"decision_id\":\"decision\"}",
            "{\"result\":{\"allow\":1,\"policy_revision\":\"revision\"},\"decision_id\":\"decision\"}",
            "{\"result\":{\"allow\":true,\"policy_revision\":null},\"decision_id\":\"decision\"}",
            "{\"result\":{\"allow\":true,\"policy_revision\":17},\"decision_id\":\"decision\"}",
            "{\"result\":{\"allow\":true,\"policy_revision\":\" \"},\"decision_id\":\"decision\"}",
            "{\"result\":{\"allow\":true,\"policy_revision\":\"revision\"},\"decision_id\":\"\"}",
            "{\"result\":{\"allow\":true,\"policy_revision\":\"revision\"},\"decision_id\":[]}",
            "{\"result\":{\"allow\":true,\"policy_revision\":\"revision\"},\"decision_id\":\"decision\",\"decision_id\":\"second\"}",
            "{\"result\":{\"allow\":true,\"allow\":false,\"policy_revision\":\"revision\"},\"decision_id\":\"decision\"}"})
    void missingMalformedBooleanOrDuplicateMetadataIsIndeterminate(String body) {
        assertRefused(evaluate(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"obligations\":[{\"type\":\"MFA_REQUIRED\"}]", "\"obligations\":[]",
            "\"constraints\":{\"max_amount\":100}", "\"constraints\":null", "\"unknown\":false",
            "\"allow_if\":true", "\"policyRevision\":\"shadow\"", "\"decision_id\":\"nested\""})
    void unsupportedResultSemanticsRefuseRatherThanDroppingConditions(String extraField) {
        assertRefused(evaluate("{\"result\":{\"allow\":true,\"policy_revision\":\"revision\"," + extraField
                + "},\"decision_id\":\"decision\"}"));
    }

    @Test
    void unsupportedFieldsOnFalseDecisionAlsoRefuseProvenance() {
        assertRefused(evaluate("{\"result\":{\"allow\":false,\"policy_revision\":\"revision\","
                + "\"obligations\":[{\"type\":\"MFA_REQUIRED\"}]},\"decision_id\":\"decision\"}"));
    }

    @Test
    void legitimateTopLevelOpaMetadataDoesNotReplaceRequiredResultRevision() {
        var result = evaluate("{\"result\":{\"allow\":true,\"policy_revision\":\"evaluated-revision\"},"
                + "\"decision_id\":\"decision\",\"metrics\":{\"timer_rego_query_eval_ns\":12},"
                + "\"explanation\":[],\"provenance\":{\"bundles\":{\"bundle\":{\"revision\":\"bundle-metadata\"}}}}");
        assertTrue(result.decision().granted());
        assertEquals("evaluated-revision", result.provenance().orElseThrow().policyRevision());
        assertEquals("decision", result.provenance().orElseThrow().decisionId());
    }

    @Test
    void trailingJsonIsRefused() {
        assertRefused(evaluate(PERMIT + " {}"));
    }

    @Test
    void controlCharactersInServerMetadataAreRefused() {
        assertRefused(evaluate(PERMIT.replace("bundle-revision-17", "revision\\nchanged")));
        assertRefused(evaluate(PERMIT.replace("decision-23", "decision\\tchanged")));
    }

    @Test
    void oversizedMetadataIsRefused() {
        assertRefused(evaluate(PERMIT.replace("bundle-revision-17", "r".repeat(1025))));
        assertRefused(evaluate(PERMIT.replace("decision-23", "d".repeat(257))));
    }

    @Test
    void exactSixteenKiBResponsePermitsAndOneAdditionalByteRefuses() {
        var exact = PERMIT + " ".repeat(16384 - PERMIT.length());
        assertTrue(evaluate(exact).decision().granted());
        assertRefused(evaluate(exact + " "));
    }

    @Test
    void chunkedResponseCannotExceedBound() {
        var factory = new DefaultDataBufferFactory();
        var response = ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                .body(Flux.just(factory.wrap(PERMIT.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        factory.wrap(new byte[16384]))).build();
        var client = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.just(response)).build();
        assertRefused(new OpaPolicyEvaluationAdapter(client, "firefly/versioned")
                .evaluate(principal, "read", "resource", Map.of()).block(WAIT));
    }

    @Test
    void emptyResponseIsIndeterminate() {
        assertRefused(evaluate(""));
    }

    @Test
    void transportAndHttpFailuresNeverExposeRawDetailsOrMetadata() {
        var bad = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.error(new IOException("private token/body/URL"))).build();
        assertRefused(new OpaPolicyEvaluationAdapter(bad, "firefly/versioned")
                .evaluate(principal, "read", "resource", Map.of()).block(WAIT));
        var failed = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Content-Type", "application/json").body(PERMIT).build())).build();
        assertRefused(new OpaPolicyEvaluationAdapter(failed, "firefly/versioned")
                .evaluate(principal, "read", "resource", Map.of()).block(WAIT));
    }

    @Test
    void requestUsesAuthenticatedIssuerAndOpaqueTenantWithoutClaimsOrAttributes() {
        var capture = new AtomicReference<JsonNode>();
        var count = new AtomicInteger();
        var web = WebClient.builder().baseUrl("https://policy.example.test").exchangeFunction(request -> {
            count.incrementAndGet();
            assertEquals("/v1/data/firefly/versioned", request.url().getPath());
            var output = new MockClientHttpRequest(HttpMethod.POST, URI.create("https://policy.example.test"));
            return request.writeTo(output, ExchangeStrategies.withDefaults()).then(Mono.defer(output::getBodyAsString))
                    .flatMap(body -> Mono.fromCallable(() -> {
                        capture.set(new ObjectMapper().readTree(body).path("input"));
                        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(PERMIT).build();
                    }));
        }).build();
        var result = new OpaPolicyEvaluationAdapter(web, "firefly/versioned")
                .evaluate(principal, "read", "resource", Map.of("issuer", "context-cannot-replace-principal")).block(WAIT);
        assertNotNull(result);
        assertTrue(result.decision().granted());
        assertEquals(1, count.get());
        assertEquals(principal.issuer(), capture.get().path("subject").path("issuer").asText());
        assertEquals(principal.tenantId(), capture.get().path("subject").path("tenantId").asText());
        assertFalse(capture.get().toString().contains("private-claim"));
        assertFalse(capture.get().toString().contains("private-attribute"));
    }

    @Test
    void concurrentResponsesKeepTheirOwnProvenance() {
        var first = Sinks.<ClientResponse>one();
        var second = Sinks.<ClientResponse>one();
        var count = new AtomicInteger();
        var client = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> count.getAndIncrement() == 0 ? first.asMono() : second.asMono()).build();
        var adapter = new OpaPolicyEvaluationAdapter(client, "firefly/versioned");
        var one = new AtomicReference<PolicyEvaluation>();
        var two = new AtomicReference<PolicyEvaluation>();
        adapter.evaluate(principal, "one", "resource", Map.of()).subscribe(one::set);
        adapter.evaluate(principal, "two", "resource", Map.of()).subscribe(two::set);
        second.tryEmitValue(ClientResponse.create(HttpStatus.OK).body(PERMIT.replace("decision-23", "decision-two")).build());
        first.tryEmitValue(ClientResponse.create(HttpStatus.OK).body(PERMIT.replace("decision-23", "decision-one")).build());
        assertEquals("decision-one", one.get().provenance().orElseThrow().decisionId());
        assertEquals("decision-two", two.get().provenance().orElseThrow().decisionId());
    }

    @Test
    void partialBodyErrorReleasesPooledBufferAndRefusesMetadata() {
        var factory = new org.springframework.core.io.buffer.NettyDataBufferFactory(
                io.netty.buffer.PooledByteBufAllocator.DEFAULT);
        var buffer = factory.wrap(io.netty.buffer.Unpooled.wrappedBuffer(
                PERMIT.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var response = ClientResponse.create(HttpStatus.OK).body(Flux.concat(Mono.just(buffer),
                Mono.error(new IOException("private partial response")))).build();
        var client = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.just(response)).build();
        assertRefused(new OpaPolicyEvaluationAdapter(client, "firefly/versioned")
                .evaluate(principal, "read", "resource", Map.of()).block(WAIT));
        assertEquals(0, buffer.getNativeBuffer().refCnt());
    }

    @Test
    void cancellationDuringPartialBodyReleasesPooledBufferAndEmitsNothing() {
        var factory = new org.springframework.core.io.buffer.NettyDataBufferFactory(
                io.netty.buffer.PooledByteBufAllocator.DEFAULT);
        var buffer = factory.wrap(io.netty.buffer.Unpooled.wrappedBuffer(
                PERMIT.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var seen = new AtomicInteger();
        var emitted = new AtomicInteger();
        var response = ClientResponse.create(HttpStatus.OK).body(Flux.concat(
                Mono.just(buffer).doOnNext(value -> seen.incrementAndGet()), Mono.never())).build();
        var client = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.just(response)).build();
        StepVerifier.create(new OpaPolicyEvaluationAdapter(client, "firefly/versioned")
                .evaluate(principal, "read", "resource", Map.of()).doOnNext(value -> emitted.incrementAndGet()))
                .then(() -> assertEquals(1, seen.get())).thenCancel().verify(WAIT);
        assertEquals(0, emitted.get());
        assertEquals(0, buffer.getNativeBuffer().refCnt());
    }

    @Test
    void cancellationDoesNotEmitAnEvaluationOrProvenance() {
        var cancelled = new AtomicInteger();
        var emitted = new AtomicInteger();
        var client = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.<ClientResponse>never().doOnCancel(cancelled::incrementAndGet)).build();
        StepVerifier.create(new OpaPolicyEvaluationAdapter(client, "firefly/versioned")
                .evaluate(principal, "read", "resource", Map.of()).doOnNext(value -> emitted.incrementAndGet()))
                .thenCancel().verify(WAIT);
        assertEquals(0, emitted.get());
        assertEquals(1, cancelled.get());
    }
}
