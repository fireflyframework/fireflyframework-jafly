/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.rules.core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.rules.core.services.AuditTrailService;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditEventType;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuditHelper
 */
@ExtendWith(MockitoExtension.class)
class AuditHelperTest {

    @Mock
    private AuditTrailService auditTrailService;

    @Mock
    private ObjectMapper objectMapper;

    private AuditHelper auditHelper;

    @BeforeEach
    void setUp() {
        auditHelper = new AuditHelper(auditTrailService, objectMapper);
    }

    @Test
    void recordAuditEvent_ShouldExtractWebContextAndRecordEvent() throws Exception {
        // Given
        UUID entityId = UUID.randomUUID();
        String ruleCode = "test_rule_v1";
        Map<String, Object> requestData = Map.of("code", "test_rule");
        Map<String, Object> responseData = Map.of("id", entityId.toString());

        ServerWebExchange exchange = createMockExchange();
        
        AuditTrailDTO expectedAuditTrail = AuditTrailDTO.builder()
                .id(UUID.randomUUID())
                .operationType("RULE_DEFINITION_CREATE")
                .entityType("RULE_DEFINITION")
                .build();

        when(objectMapper.writeValueAsString(requestData)).thenReturn("{\"code\":\"test_rule\"}");
        when(objectMapper.writeValueAsString(responseData)).thenReturn("{\"id\":\"" + entityId + "\"}");
        when(auditTrailService.recordAuditEvent(
                eq(AuditEventType.RULE_DEFINITION_CREATE),
                eq(entityId),
                eq(ruleCode),
                eq("test-user"),
                eq("POST"),
                eq("/api/v1/rules/definitions"),
                eq("{\"code\":\"test_rule\"}"),
                eq("{\"id\":\"" + entityId + "\"}"),
                eq(201),
                eq(true),
                isNull(),
                eq(150L),
                isNull(),
                eq("192.168.1.100"),
                eq("Mozilla/5.0"),
                eq("session-123"),
                eq("correlation-456")
        )).thenReturn(Mono.just(expectedAuditTrail));

        // When & Then
        StepVerifier.create(auditHelper.recordAuditEvent(
                AuditEventType.RULE_DEFINITION_CREATE,
                entityId,
                ruleCode,
                requestData,
                responseData,
                201,
                true,
                150L,
                exchange
        ))
                .verifyComplete();
    }

    @Test
    void recordAuditEventWithError_ShouldRecordErrorEvent() throws Exception {
        // Given
        UUID entityId = UUID.randomUUID();
        String ruleCode = "test_rule_v1";
        Map<String, Object> requestData = Map.of("code", "test_rule");
        String errorMessage = "Validation failed";

        ServerWebExchange exchange = createMockExchange();
        
        AuditTrailDTO expectedAuditTrail = AuditTrailDTO.builder()
                .id(UUID.randomUUID())
                .operationType("RULE_DEFINITION_CREATE")
                .entityType("RULE_DEFINITION")
                .success(false)
                .errorMessage(errorMessage)
                .build();

        when(objectMapper.writeValueAsString(requestData)).thenReturn("{\"code\":\"test_rule\"}");
        when(auditTrailService.recordAuditEvent(
                eq(AuditEventType.RULE_DEFINITION_CREATE),
                eq(entityId),
                eq(ruleCode),
                eq("test-user"),
                eq("POST"),
                eq("/api/v1/rules/definitions"),
                eq("{\"code\":\"test_rule\"}"),
                isNull(),
                eq(400),
                eq(false),
                eq(errorMessage),
                eq(100L),
                isNull(),
                eq("192.168.1.100"),
                eq("Mozilla/5.0"),
                eq("session-123"),
                eq("correlation-456")
        )).thenReturn(Mono.just(expectedAuditTrail));

        // When & Then
        StepVerifier.create(auditHelper.recordAuditEventWithError(
                AuditEventType.RULE_DEFINITION_CREATE,
                entityId,
                ruleCode,
                requestData,
                errorMessage,
                400,
                100L,
                exchange
        ))
                .verifyComplete();
    }

    @Test
    void recordAuditEventWithMetadata_ShouldRecordEventWithMetadata() throws Exception {
        // Given
        UUID entityId = UUID.randomUUID();
        String ruleCode = "test_rule_v1";
        Map<String, Object> requestData = Map.of("inputData", Map.of("creditScore", 750));
        Map<String, Object> responseData = Map.of("success", true, "conditionResult", true);
        Map<String, Object> metadata = Map.of("evaluationType", "DIRECT", "inputDataSize", 1);

        ServerWebExchange exchange = createMockExchange();
        
        AuditTrailDTO expectedAuditTrail = AuditTrailDTO.builder()
                .id(UUID.randomUUID())
                .operationType("RULE_EVALUATION_DIRECT")
                .entityType("RULE_EVALUATION")
                .build();

        when(objectMapper.writeValueAsString(requestData)).thenReturn("{\"inputData\":{\"creditScore\":750}}");
        when(objectMapper.writeValueAsString(responseData)).thenReturn("{\"success\":true,\"conditionResult\":true}");
        when(auditTrailService.recordAuditEventWithMetadata(
                eq(AuditEventType.RULE_EVALUATION_DIRECT),
                eq(entityId),
                eq(ruleCode),
                eq("test-user"),
                eq("POST"),
                eq("/api/v1/rules/definitions"),
                eq("{\"inputData\":{\"creditScore\":750}}"),
                eq("{\"success\":true,\"conditionResult\":true}"),
                eq(200),
                eq(true),
                eq(200L),
                eq(metadata)
        )).thenReturn(Mono.just(expectedAuditTrail));

        // When & Then
        StepVerifier.create(auditHelper.recordAuditEventWithMetadata(
                AuditEventType.RULE_EVALUATION_DIRECT,
                entityId,
                ruleCode,
                requestData,
                responseData,
                200,
                true,
                200L,
                metadata,
                exchange
        ))
                .verifyComplete();
    }

    @Test
    void recordAuditEvent_WithAnonymousUser_ShouldUseAnonymousUserId() throws Exception {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/rules/definitions")
                        .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
        );

        AuditTrailDTO expectedAuditTrail = AuditTrailDTO.builder()
                .id(UUID.randomUUID())
                .operationType("RULE_DEFINITION_CREATE")
                .entityType("RULE_DEFINITION")
                .userId("anonymous")
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(auditTrailService.recordAuditEvent(
                eq(AuditEventType.RULE_DEFINITION_CREATE),
                isNull(),
                isNull(),
                eq("anonymous"),
                eq("POST"),
                eq("/api/v1/rules/definitions"),
                eq("{}"),
                eq("{}"),
                eq(200),
                eq(true),
                isNull(),
                eq(100L),
                isNull(),
                eq("192.168.1.100"),
                eq("Mozilla/5.0"),
                isNull(),
                isNull()
        )).thenReturn(Mono.just(expectedAuditTrail));

        // When & Then
        StepVerifier.create(auditHelper.recordAuditEvent(
                AuditEventType.RULE_DEFINITION_CREATE,
                null,
                null,
                new HashMap<>(),
                new HashMap<>(),
                200,
                true,
                100L,
                exchange
        ))
                .verifyComplete();
    }

    private ServerWebExchange createMockExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/rules/definitions")
                        .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                        .header("X-User-Id", "test-user")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Session-Id", "session-123")
                        .header("X-Correlation-Id", "correlation-456")
                        .build()
        );
    }
}
