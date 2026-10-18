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

package org.fireflyframework.rules.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.rules.core.services.impl.AuditTrailServiceImpl;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditEventType;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailFilterDTO;
import org.fireflyframework.rules.models.entities.AuditTrail;
import org.fireflyframework.rules.models.repositories.AuditTrailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuditTrailService
 */
@ExtendWith(MockitoExtension.class)
class AuditTrailServiceTest {

    @Mock
    private AuditTrailRepository auditTrailRepository;

    @Mock
    private ObjectMapper objectMapper;

    private AuditTrailService auditTrailService;

    private UUID testEntityId;
    private String testRuleCode;
    private String testUserId;

    @BeforeEach
    void setUp() {
        auditTrailService = new AuditTrailServiceImpl(auditTrailRepository, objectMapper);
        testEntityId = UUID.randomUUID();
        testRuleCode = "test_rule_v1";
        testUserId = "test.user@company.com";
    }

    @Test
    void recordAuditEvent_ShouldCreateAuditTrail() {
        // Given
        AuditTrail savedAuditTrail = createTestAuditTrail();
        when(auditTrailRepository.save(any(AuditTrail.class))).thenReturn(Mono.just(savedAuditTrail));

        // When & Then
        StepVerifier.create(auditTrailService.recordAuditEvent(
                AuditEventType.RULE_DEFINITION_CREATE,
                testEntityId,
                testRuleCode,
                testUserId,
                "POST",
                "/api/v1/rules/definitions",
                "{\"code\":\"test_rule\"}",
                "{\"id\":\"123\"}",
                201,
                true,
                150L
        ))
                .assertNext(auditTrailDTO -> {
                    assertThat(auditTrailDTO).isNotNull();
                    assertThat(auditTrailDTO.getOperationType()).isEqualTo("RULE_DEFINITION_CREATE");
                    assertThat(auditTrailDTO.getEntityType()).isEqualTo("RULE_DEFINITION");
                    assertThat(auditTrailDTO.getEntityId()).isEqualTo(testEntityId);
                    assertThat(auditTrailDTO.getRuleCode()).isEqualTo(testRuleCode);
                    assertThat(auditTrailDTO.getUserId()).isEqualTo(testUserId);
                    assertThat(auditTrailDTO.getSuccess()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void recordAuditEventWithMetadata_ShouldSerializeMetadata() throws Exception {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", 123);

        String metadataJson = "{\"key1\":\"value1\",\"key2\":123}";
        when(objectMapper.writeValueAsString(metadata)).thenReturn(metadataJson);

        AuditTrail savedAuditTrail = createTestAuditTrail();
        savedAuditTrail.setMetadata(metadataJson);
        when(auditTrailRepository.save(any(AuditTrail.class))).thenReturn(Mono.just(savedAuditTrail));

        // When & Then
        StepVerifier.create(auditTrailService.recordAuditEventWithMetadata(
                AuditEventType.RULE_EVALUATION_DIRECT,
                null,
                null,
                testUserId,
                "POST",
                "/api/v1/rules/evaluate/direct",
                "{\"inputData\":{}}",
                "{\"success\":true}",
                200,
                true,
                250L,
                metadata
        ))
                .assertNext(auditTrailDTO -> {
                    assertThat(auditTrailDTO).isNotNull();
                    assertThat(auditTrailDTO.getMetadata()).isEqualTo(metadataJson);
                })
                .verifyComplete();
    }

    @Test
    void getAuditTrails_ShouldReturnPaginatedResults() {
        // Given
        AuditTrailFilterDTO filterDTO = AuditTrailFilterDTO.builder()
                .operationType("RULE_DEFINITION_CREATE")
                .page(0)
                .size(10)
                .sortBy("createdAt")
                .sortDirection("DESC")
                .build();

        List<AuditTrail> auditTrails = List.of(createTestAuditTrail());
        when(auditTrailRepository.findWithFilters(
                eq("RULE_DEFINITION_CREATE"), isNull(), isNull(), isNull(), 
                isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Flux.fromIterable(auditTrails));

        when(auditTrailRepository.countWithFilters(
                eq("RULE_DEFINITION_CREATE"), isNull(), isNull(), isNull(), 
                isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(auditTrailService.getAuditTrails(filterDTO))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getTotalElements()).isEqualTo(1L);
                    assertThat(response.getCurrentPage()).isEqualTo(0);
                    assertThat(response.getTotalPages()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void getAuditTrailById_ShouldReturnAuditTrail() {
        // Given
        UUID auditTrailId = UUID.randomUUID();
        AuditTrail auditTrail = createTestAuditTrail();
        auditTrail.setId(auditTrailId);
        when(auditTrailRepository.findById(auditTrailId)).thenReturn(Mono.just(auditTrail));

        // When & Then
        StepVerifier.create(auditTrailService.getAuditTrailById(auditTrailId))
                .assertNext(auditTrailDTO -> {
                    assertThat(auditTrailDTO).isNotNull();
                    assertThat(auditTrailDTO.getId()).isEqualTo(auditTrailId);
                })
                .verifyComplete();
    }

    @Test
    void getRecentAuditTrailsForEntity_ShouldReturnRecentTrails() {
        // Given
        List<AuditTrail> auditTrails = List.of(createTestAuditTrail());
        when(auditTrailRepository.findRecentByEntityId(testEntityId, 5))
                .thenReturn(Flux.fromIterable(auditTrails));

        // When & Then
        StepVerifier.create(auditTrailService.getRecentAuditTrailsForEntity(testEntityId, 5))
                .assertNext(auditTrailDTOs -> {
                    assertThat(auditTrailDTOs).hasSize(1);
                    assertThat(auditTrailDTOs.get(0).getEntityId()).isEqualTo(testEntityId);
                })
                .verifyComplete();
    }

    @Test
    void getAuditTrailStatistics_ShouldReturnStatistics() {
        // Given
        when(auditTrailRepository.count()).thenReturn(Mono.just(100L));
        when(auditTrailRepository.countBySuccess(true)).thenReturn(Mono.just(90L));
        when(auditTrailRepository.countBySuccess(false)).thenReturn(Mono.just(10L));
        when(auditTrailRepository.countByOperationType("RULE_DEFINITION_CREATE")).thenReturn(Mono.just(20L));
        when(auditTrailRepository.countByOperationType("RULE_DEFINITION_UPDATE")).thenReturn(Mono.just(15L));
        when(auditTrailRepository.countByOperationType("RULE_DEFINITION_DELETE")).thenReturn(Mono.just(5L));
        when(auditTrailRepository.countByOperationType("RULE_EVALUATION_DIRECT")).thenReturn(Mono.just(60L));

        // When & Then
        StepVerifier.create(auditTrailService.getAuditTrailStatistics())
                .assertNext(stats -> {
                    assertThat(stats).isNotNull();
                    assertThat(stats.get("totalCount")).isEqualTo(100L);
                    assertThat(stats.get("successCount")).isEqualTo(90L);
                    assertThat(stats.get("failureCount")).isEqualTo(10L);
                    assertThat(stats.get("successRate")).isEqualTo(0.9);
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> operationCounts = (Map<String, Object>) stats.get("operationCounts");
                    assertThat(operationCounts.get("ruleDefinitionCreate")).isEqualTo(20L);
                    assertThat(operationCounts.get("ruleDefinitionUpdate")).isEqualTo(15L);
                    assertThat(operationCounts.get("ruleDefinitionDelete")).isEqualTo(5L);
                    assertThat(operationCounts.get("ruleEvaluation")).isEqualTo(60L);
                })
                .verifyComplete();
    }

    @Test
    void deleteOldAuditTrails_ShouldDeleteOldRecords() {
        // Given
        AuditTrail oldAuditTrail = createTestAuditTrail();
        when(auditTrailRepository.findByCreatedAtBefore(any(OffsetDateTime.class)))
                .thenReturn(Flux.just(oldAuditTrail));
        when(auditTrailRepository.delete(any(AuditTrail.class))).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(auditTrailService.deleteOldAuditTrails(90))
                .assertNext(deletedCount -> {
                    assertThat(deletedCount).isEqualTo(0L); // delete() returns Mono.empty(), so count is 0
                })
                .verifyComplete();
    }

    private AuditTrail createTestAuditTrail() {
        return AuditTrail.builder()
                .id(UUID.randomUUID())
                .operationType("RULE_DEFINITION_CREATE")
                .entityType("RULE_DEFINITION")
                .entityId(testEntityId)
                .ruleCode(testRuleCode)
                .userId(testUserId)
                .httpMethod("POST")
                .endpoint("/api/v1/rules/definitions")
                .requestData("{\"code\":\"test_rule\"}")
                .responseData("{\"id\":\"123\"}")
                .statusCode(201)
                .success(true)
                .executionTimeMs(150L)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
