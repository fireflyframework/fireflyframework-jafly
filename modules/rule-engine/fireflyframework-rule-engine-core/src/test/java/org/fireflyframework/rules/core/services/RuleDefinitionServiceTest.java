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

import org.fireflyframework.rules.core.audit.AuditHelper;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.services.CacheService;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.mappers.RuleDefinitionMapper;
import org.fireflyframework.rules.core.services.impl.RuleDefinitionServiceImpl;
import org.fireflyframework.rules.core.validation.YamlDslValidator;
import org.fireflyframework.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import org.fireflyframework.rules.models.entities.RuleDefinition;
import org.fireflyframework.rules.models.repositories.RuleDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for RuleDefinitionService CRUD operations.
 * Tests all service layer functionality including validation, error handling, and business logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleDefinitionService Tests")
class RuleDefinitionServiceTest {

    @Mock
    private RuleDefinitionRepository ruleDefinitionRepository;

    @Mock
    private RuleDefinitionMapper ruleDefinitionMapper;

    @Mock
    private ASTRulesEvaluationEngine rulesEvaluationEngine;

    @Mock
    private YamlDslValidator yamlDslValidator;

    @Mock
    private AuditHelper auditHelper;

    @Mock
    private CacheService cacheService;

    private RuleDefinitionService ruleDefinitionService;

    private static final String VALID_YAML_CONTENT = """
            name: "Test Rule"
            description: "A test rule for unit testing"
            
            inputs:
              - creditScore
              - annualIncome
            
            when:
              - creditScore greater_than 700
              - annualIncome greater_than 50000
            
            then:
              - set approval_status to "APPROVED"
            
            else:
              - set approval_status to "DECLINED"
            
            output:
              approval_status: text
            """;

    @BeforeEach
    void setUp() {
        ruleDefinitionService = new RuleDefinitionServiceImpl(
                ruleDefinitionRepository,
                ruleDefinitionMapper,
                rulesEvaluationEngine,
                yamlDslValidator,
                auditHelper,
                cacheService
        );
    }

    @Nested
    @DisplayName("Create Rule Definition Tests")
    class CreateRuleDefinitionTests {

        @Test
        @DisplayName("Should create rule definition successfully with valid YAML")
        void shouldCreateRuleDefinitionSuccessfully() {
            // Given
            RuleDefinitionDTO inputDTO = createTestRuleDefinitionDTO();
            RuleDefinition entity = createTestRuleDefinitionEntity();
            RuleDefinitionDTO outputDTO = createTestRuleDefinitionDTO();
            outputDTO.setId(entity.getId());

            ValidationResult validationResult = ValidationResult.builder()
                    .status(ValidationResult.ValidationStatus.VALID)
                    .build();

            when(yamlDslValidator.validate(VALID_YAML_CONTENT)).thenReturn(validationResult);
            when(ruleDefinitionRepository.existsByCode("TEST_RULE_001")).thenReturn(Mono.just(false));
            when(ruleDefinitionMapper.toEntity(inputDTO)).thenReturn(entity);
            when(ruleDefinitionRepository.save(any(RuleDefinition.class))).thenReturn(Mono.just(entity));
            when(ruleDefinitionMapper.toDTO(entity)).thenReturn(outputDTO);

            // When & Then
            StepVerifier.create(ruleDefinitionService.createRuleDefinition(inputDTO))
                    .assertNext(result -> {
                        assertThat(result).isNotNull();
                        assertThat(result.getCode()).isEqualTo("TEST_RULE_001");
                        assertThat(result.getName()).isEqualTo("Test Rule");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when YAML validation fails")
        void shouldFailWhenYamlValidationFails() {
            // Given
            RuleDefinitionDTO inputDTO = createTestRuleDefinitionDTO();
            inputDTO.setYamlContent("invalid yaml content");

            ValidationResult validationResult = ValidationResult.builder()
                    .status(ValidationResult.ValidationStatus.ERROR)
                    .build();

            when(yamlDslValidator.validate("invalid yaml content")).thenReturn(validationResult);

            // When & Then
            StepVerifier.create(ruleDefinitionService.createRuleDefinition(inputDTO))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains("Rule definition validation failed"))
                    .verify();
        }

        @Test
        @DisplayName("Should fail when rule code already exists")
        void shouldFailWhenRuleCodeAlreadyExists() {
            // Given
            RuleDefinitionDTO inputDTO = createTestRuleDefinitionDTO();

            ValidationResult validationResult = ValidationResult.builder()
                    .status(ValidationResult.ValidationStatus.VALID)
                    .build();

            when(yamlDslValidator.validate(VALID_YAML_CONTENT)).thenReturn(validationResult);
            when(ruleDefinitionRepository.existsByCode("TEST_RULE_001")).thenReturn(Mono.just(true));

            // When & Then
            StepVerifier.create(ruleDefinitionService.createRuleDefinition(inputDTO))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains("already exists"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Update Rule Definition Tests")
    class UpdateRuleDefinitionTests {

        @Test
        @DisplayName("Should update rule definition successfully")
        void shouldUpdateRuleDefinitionSuccessfully() {
            // Given
            UUID ruleId = UUID.randomUUID();
            RuleDefinitionDTO inputDTO = createTestRuleDefinitionDTO();
            RuleDefinition existingEntity = createTestRuleDefinitionEntity();
            existingEntity.setId(ruleId);
            RuleDefinition updatedEntity = createTestRuleDefinitionEntity();
            updatedEntity.setId(ruleId);
            RuleDefinitionDTO outputDTO = createTestRuleDefinitionDTO();
            outputDTO.setId(ruleId);

            ValidationResult validationResult = ValidationResult.builder()
                    .status(ValidationResult.ValidationStatus.VALID)
                    .build();

            when(yamlDslValidator.validate(VALID_YAML_CONTENT)).thenReturn(validationResult);
            when(ruleDefinitionRepository.findById(ruleId)).thenReturn(Mono.just(existingEntity));
            when(ruleDefinitionMapper.toEntity(inputDTO)).thenReturn(updatedEntity);
            when(ruleDefinitionRepository.save(any(RuleDefinition.class))).thenReturn(Mono.just(updatedEntity));
            when(ruleDefinitionMapper.toDTO(updatedEntity)).thenReturn(outputDTO);

            // When & Then
            StepVerifier.create(ruleDefinitionService.updateRuleDefinition(ruleId, inputDTO))
                    .assertNext(result -> {
                        assertThat(result).isNotNull();
                        assertThat(result.getId()).isEqualTo(ruleId);
                        assertThat(result.getCode()).isEqualTo("TEST_RULE_001");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when rule definition not found")
        void shouldFailWhenRuleDefinitionNotFound() {
            // Given
            UUID ruleId = UUID.randomUUID();
            RuleDefinitionDTO inputDTO = createTestRuleDefinitionDTO();

            ValidationResult validationResult = ValidationResult.builder()
                    .status(ValidationResult.ValidationStatus.VALID)
                    .build();

            when(yamlDslValidator.validate(VALID_YAML_CONTENT)).thenReturn(validationResult);
            when(ruleDefinitionRepository.findById(ruleId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(ruleDefinitionService.updateRuleDefinition(ruleId, inputDTO))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains("not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Delete Rule Definition Tests")
    class DeleteRuleDefinitionTests {

        @Test
        @DisplayName("Should delete rule definition successfully")
        void shouldDeleteRuleDefinitionSuccessfully() {
            // Given
            UUID ruleId = UUID.randomUUID();
            RuleDefinition entity = createTestRuleDefinitionEntity();
            entity.setId(ruleId);

            when(ruleDefinitionRepository.findById(ruleId)).thenReturn(Mono.just(entity));
            when(ruleDefinitionRepository.delete(entity)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(ruleDefinitionService.deleteRuleDefinition(ruleId))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when rule definition not found for deletion")
        void shouldFailWhenRuleDefinitionNotFoundForDeletion() {
            // Given
            UUID ruleId = UUID.randomUUID();

            when(ruleDefinitionRepository.findById(ruleId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(ruleDefinitionService.deleteRuleDefinition(ruleId))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains("not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Get Rule Definition Tests")
    class GetRuleDefinitionTests {

        @Test
        @DisplayName("Should get rule definition by ID successfully")
        void shouldGetRuleDefinitionByIdSuccessfully() {
            // Given
            UUID ruleId = UUID.randomUUID();
            RuleDefinition entity = createTestRuleDefinitionEntity();
            entity.setId(ruleId);
            RuleDefinitionDTO dto = createTestRuleDefinitionDTO();
            dto.setId(ruleId);

            when(ruleDefinitionRepository.findById(ruleId)).thenReturn(Mono.just(entity));
            when(ruleDefinitionMapper.toDTO(entity)).thenReturn(dto);

            // When & Then
            StepVerifier.create(ruleDefinitionService.getRuleDefinitionById(ruleId))
                    .assertNext(result -> {
                        assertThat(result).isNotNull();
                        assertThat(result.getId()).isEqualTo(ruleId);
                        assertThat(result.getCode()).isEqualTo("TEST_RULE_001");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when rule definition not found by ID")
        void shouldReturnEmptyWhenRuleDefinitionNotFoundById() {
            // Given
            UUID ruleId = UUID.randomUUID();

            when(ruleDefinitionRepository.findById(ruleId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(ruleDefinitionService.getRuleDefinitionById(ruleId))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Rule Evaluation Tests")
    class RuleEvaluationTests {

        @Test
        @DisplayName("Should evaluate rule by code successfully")
        void shouldEvaluateRuleByCodeSuccessfully() {
            // Given
            String ruleCode = "TEST_RULE_001";
            Map<String, Object> inputData = new HashMap<>();
            inputData.put("creditScore", 750);
            inputData.put("annualIncome", 60000);

            RuleDefinitionDTO dto = createTestRuleDefinitionDTO();
            when(ruleDefinitionRepository.findByCode(ruleCode)).thenReturn(Mono.just(createTestRuleDefinitionEntity()));
            when(ruleDefinitionMapper.toDTO(any(RuleDefinition.class))).thenReturn(dto);
            when(rulesEvaluationEngine.evaluateRulesReactive(VALID_YAML_CONTENT, inputData))
                    .thenReturn(Mono.just(ASTRulesEvaluationResult.builder()
                            .success(true)
                            .conditionResult(true)
                            .build()));

            // When & Then
            StepVerifier.create(ruleDefinitionService.evaluateRuleByCode(ruleCode, inputData))
                    .assertNext(result -> {
                        assertThat(result).isNotNull();
                        assertThat(result.isSuccess()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when rule not found for evaluation")
        void shouldFailWhenRuleNotFoundForEvaluation() {
            // Given
            String ruleCode = "NONEXISTENT_RULE";
            Map<String, Object> inputData = new HashMap<>();

            when(ruleDefinitionRepository.findByCode(ruleCode)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(ruleDefinitionService.evaluateRuleByCode(ruleCode, inputData))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains("not found"))
                    .verify();
        }
    }

    // Helper methods
    private RuleDefinitionDTO createTestRuleDefinitionDTO() {
        return RuleDefinitionDTO.builder()
                .code("TEST_RULE_001")
                .name("Test Rule")
                .description("A test rule for unit testing")
                .yamlContent(VALID_YAML_CONTENT)
                .isActive(true)
                .build();
    }

    private RuleDefinition createTestRuleDefinitionEntity() {
        RuleDefinition entity = new RuleDefinition();
        entity.setId(UUID.randomUUID());
        entity.setCode("TEST_RULE_001");
        entity.setName("Test Rule");
        entity.setDescription("A test rule for unit testing");
        entity.setYamlContent(VALID_YAML_CONTENT);
        entity.setIsActive(true);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }
}
