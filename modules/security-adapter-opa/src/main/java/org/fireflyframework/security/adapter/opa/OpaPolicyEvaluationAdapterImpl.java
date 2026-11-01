/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.adapter.opa;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.PolicyEvaluation;
import org.fireflyframework.security.api.domain.PolicyEvaluationProvenance;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit structured OPA capability; no boolean fallback or automatic activation.
 * Requires {"result":{"allow":boolean,"policy_revision":string},"decision_id":string}
 * in one response. The rule must report the actual policy revision, and OPA decision
 * logging must provide the decision identifier. No client-generated replacement is used.
 */
public final class OpaPolicyEvaluationAdapterImpl implements PolicyEvaluationPort {
    private static final int MAXIMUM_RESPONSE_BYTES = 16 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String UNAVAILABLE = "OPA versioned evaluation unavailable";
    private final WebClient webClient;
    private final String decisionPath;

    public OpaPolicyEvaluationAdapterImpl(WebClient webClient, String decisionPath) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.decisionPath = Objects.requireNonNull(decisionPath, "decisionPath").startsWith("/")
                ? decisionPath.substring(1) : decisionPath;
        if (this.decisionPath.isBlank()) {
            throw new IllegalArgumentException("OPA structured decision path required");
        }
    }

    @Override
    public Mono<PolicyEvaluation> evaluate(SecurityPrincipal principal, String action, String resource,
                                            Map<String, Object> context) {
        return Mono.defer(() -> webClient.post().uri("/v1/data/" + decisionPath)
                .bodyValue(input(principal, action, resource, context))
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().thenReturn(unavailable());
                    }
                    return DataBufferUtils.join(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class),
                                    MAXIMUM_RESPONSE_BYTES)
                            .map(buffer -> {
                                try {
                                    byte[] bytes = new byte[buffer.readableByteCount()];
                                    buffer.read(bytes);
                                    return parse(bytes);
                                } finally {
                                    DataBufferUtils.release(buffer);
                                }
                            });
                }))
                .switchIfEmpty(Mono.fromSupplier(OpaPolicyEvaluationAdapterImpl::unavailable))
                .onErrorResume(error -> Mono.just(unavailable()));
    }

    private static Map<String, Object> input(SecurityPrincipal principal, String action, String resource,
                                              Map<String, Object> context) {
        Map<String, Object> subject = new HashMap<>();
        subject.put("subject", principal.subject());
        if (principal.issuer() != null) {
            subject.put("issuer", principal.issuer());
        }
        subject.put("authorities", principal.authorities());
        subject.put("scopes", principal.scopes());
        subject.put("tenantId", principal.tenantId());
        Map<String, Object> input = new HashMap<>();
        input.put("subject", subject);
        input.put("action", action);
        input.put("resource", resource);
        input.put("context", context == null ? Map.of() : context);
        return Map.of("input", input);
    }

    private static PolicyEvaluation parse(byte[] bytes) {
        try {
            JsonNode root = MAPPER.readTree(bytes);
            if (root == null || !root.isObject()) {
                return unavailable();
            }
            JsonNode result = root.path("result");
            JsonNode allow = result.path("allow");
            JsonNode revision = result.path("policy_revision");
            JsonNode decisionId = root.path("decision_id");
            if (!result.isObject() || result.size() != 2 || !allow.isBoolean()
                    || !revision.isTextual() || !decisionId.isTextual()) {
                return unavailable();
            }
            var provenance = new PolicyEvaluationProvenance(revision.textValue(), decisionId.textValue());
            return new PolicyEvaluation(allow.booleanValue() ? Decision.permit() : Decision.deny("denied by OPA policy"),
                    Optional.of(provenance));
        } catch (IOException | IllegalArgumentException error) {
            return unavailable();
        }
    }

    private static PolicyEvaluation unavailable() {
        return new PolicyEvaluation(Decision.indeterminate(UNAVAILABLE), Optional.empty());
    }
}
