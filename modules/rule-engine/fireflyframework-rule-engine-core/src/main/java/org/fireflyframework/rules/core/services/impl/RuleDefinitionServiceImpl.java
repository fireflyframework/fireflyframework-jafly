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

package org.fireflyframework.rules.core.services.impl;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.core.audit.AuditHelper;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.mappers.RuleDefinitionMapper;
import org.fireflyframework.rules.core.services.CacheService;
import org.fireflyframework.rules.core.services.RuleDefinitionService;
import org.fireflyframework.rules.core.validation.YamlDslValidator;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditEventType;
import org.fireflyframework.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import org.fireflyframework.rules.models.entities.RuleDefinition;
import org.fireflyframework.rules.models.repositories.RuleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Implementation of RuleDefinitionService.
 * Provides CRUD operations for YAML DSL rule definitions with validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleDefinitionServiceImpl implements RuleDefinitionService {

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final ASTRulesEvaluationEngine rulesEvaluationEngine;
    private final YamlDslValidator yamlDslValidator;
    private final AuditHelper auditHelper;
    private final CacheService cacheService;

    @Override
    public Mono<PaginationResponse<RuleDefinitionDTO>> filterRuleDefinitions(
            FilterRequest<RuleDefinitionDTO> filterRequest) {
        log.info("Filtering rule definitions with criteria: {}", filterRequest);

        return FilterUtils
                .createFilter(
                        RuleDefinition.class,
                        ruleDefinitionMapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<RuleDefinitionDTO> createRuleDefinition(RuleDefinitionDTO ruleDefinitionDTO) {
        log.info("Creating rule definition with code: {}", ruleDefinitionDTO.getCode());
        
        return validateRuleDefinition(ruleDefinitionDTO.getYamlContent())
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        return Mono.error(new IllegalArgumentException(
                                "Rule definition validation failed: " + validationResult.getErrorSummary()));
                    }
                    
                    // Check if code already exists
                    return ruleDefinitionRepository.existsByCode(ruleDefinitionDTO.getCode())
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.error(new IllegalArgumentException(
                                            "Rule definition with code '" + ruleDefinitionDTO.getCode() + "' already exists"));
                                }
                                
                                RuleDefinition entity = ruleDefinitionMapper.toEntity(ruleDefinitionDTO);
                                entity.setId(UUID.randomUUID());
                                entity.setCreatedAt(OffsetDateTime.now());
                                entity.setUpdatedAt(OffsetDateTime.now());
                                
                                return ruleDefinitionRepository.save(entity)
                                        .map(ruleDefinitionMapper::toDTO)
                                        .doOnSuccess(dto -> cacheService.invalidateRuleDefinition(dto.getCode()));
                            });
                });
    }

    @Override
    public Mono<RuleDefinitionDTO> updateRuleDefinition(UUID ruleDefinitionId, RuleDefinitionDTO ruleDefinitionDTO) {
        log.info("Updating rule definition with ID: {}", ruleDefinitionId);
        
        return validateRuleDefinition(ruleDefinitionDTO.getYamlContent())
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        return Mono.error(new IllegalArgumentException(
                                "Rule definition validation failed: " + validationResult.getErrorSummary()));
                    }
                    
                    return ruleDefinitionRepository.findById(ruleDefinitionId)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "Rule definition not found with ID: " + ruleDefinitionId)))
                            .flatMap(existingEntity -> {
                                // Update entity with new data
                                RuleDefinition updatedEntity = ruleDefinitionMapper.toEntity(ruleDefinitionDTO);
                                updatedEntity.setId(existingEntity.getId());
                                updatedEntity.setCreatedAt(existingEntity.getCreatedAt());
                                updatedEntity.setCreatedBy(existingEntity.getCreatedBy());
                                updatedEntity.setUpdatedAt(OffsetDateTime.now());
                                
                                return ruleDefinitionRepository.save(updatedEntity)
                                        .map(ruleDefinitionMapper::toDTO)
                                        .doOnSuccess(dto -> cacheService.invalidateRuleDefinition(dto.getCode()));
                            });
                });
    }

    @Override
    public Mono<Void> deleteRuleDefinition(UUID ruleDefinitionId) {
        log.info("Deleting rule definition with ID: {}", ruleDefinitionId);
        
        return ruleDefinitionRepository.findById(ruleDefinitionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Rule definition not found with ID: " + ruleDefinitionId)))
                .flatMap(entity -> ruleDefinitionRepository.delete(entity)
                        .doOnSuccess(v -> cacheService.invalidateRuleDefinition(entity.getCode())));
    }

    @Override
    public Mono<RuleDefinitionDTO> getRuleDefinitionById(UUID ruleDefinitionId) {
        log.info("Getting rule definition by ID: {}", ruleDefinitionId);
        
        return ruleDefinitionRepository.findById(ruleDefinitionId)
                .map(ruleDefinitionMapper::toDTO);
    }

    @Override
    public Mono<RuleDefinitionDTO> getRuleDefinitionByCode(String code) {
        log.info("Getting rule definition by code: {}", code);
        
        return ruleDefinitionRepository.findByCode(code)
                .map(ruleDefinitionMapper::toDTO);
    }

    @Override
    public Mono<ValidationResult> validateRuleDefinition(String yamlContent) {
        log.info("Validating YAML DSL rule definition using comprehensive validator");

        return Mono.fromCallable(() -> yamlDslValidator.validate(yamlContent))
                .doOnSuccess(result -> log.info("Validation completed: valid={}, issues={}",
                        result.isValid(), result.getSummary() != null ? result.getSummary().getTotalIssues() : 0))
                .doOnError(error -> log.error("Error during rule definition validation", error))
                .onErrorReturn(ValidationResult.builder()
                        .status(ValidationResult.ValidationStatus.CRITICAL_ERROR)
                        .summary(ValidationResult.ValidationSummary.builder()
                                .totalIssues(1)
                                .criticalErrors(1)
                                .qualityScore(0.0)
                                .build())
                        .build());
    }



    @Override
    public Mono<ASTRulesEvaluationResult> evaluateRuleByCode(String code, java.util.Map<String, Object> inputData) {
        log.info("Evaluating rule definition by code: {} with input data: {}", code, inputData);

        return getRuleDefinitionByCode(code)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Rule definition not found with code: " + code)))
                .flatMap(ruleDefinition -> {
                    if (!ruleDefinition.getIsActive()) {
                        return Mono.error(new IllegalArgumentException(
                                "Rule definition is not active: " + code));
                    }

                    return rulesEvaluationEngine.evaluateRulesReactive(
                            ruleDefinition.getYamlContent(), inputData);
                });
    }

    // Audit-aware methods implementation

    @Override
    public Mono<PaginationResponse<RuleDefinitionDTO>> filterRuleDefinitionsWithAudit(
            FilterRequest<RuleDefinitionDTO> filterRequest, ServerWebExchange exchange) {

        long startTime = System.currentTimeMillis();

        return filterRuleDefinitions(filterRequest)
                .flatMap(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event and return the result
                    return auditHelper.recordAuditEvent(
                            AuditEventType.RULE_DEFINITION_FILTER,
                            null, // entityId
                            null, // ruleCode
                            filterRequest,
                            result,
                            200,
                            true,
                            executionTime,
                            exchange
                    ).thenReturn(result);
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event for error and re-throw
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_DEFINITION_FILTER,
                            null, // entityId
                            null, // ruleCode
                            filterRequest,
                            error.getMessage(),
                            500,
                            executionTime,
                            exchange
                    ).then(Mono.error(error));
                });
    }

    @Override
    public Mono<RuleDefinitionDTO> createRuleDefinitionWithAudit(RuleDefinitionDTO ruleDefinitionDTO, ServerWebExchange exchange) {

        long startTime = System.currentTimeMillis();

        return createRuleDefinition(ruleDefinitionDTO)
                .flatMap(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event and return the result
                    return auditHelper.recordAuditEvent(
                            AuditEventType.RULE_DEFINITION_CREATE,
                            result.getId(),
                            result.getCode(),
                            ruleDefinitionDTO,
                            result,
                            201,
                            true,
                            executionTime,
                            exchange
                    ).thenReturn(result);
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event for error and re-throw
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_DEFINITION_CREATE,
                            null, // entityId
                            ruleDefinitionDTO.getCode(),
                            ruleDefinitionDTO,
                            error.getMessage(),
                            400,
                            executionTime,
                            exchange
                    ).then(Mono.error(error));
                });
    }

    @Override
    public Mono<RuleDefinitionDTO> updateRuleDefinitionWithAudit(UUID ruleDefinitionId, RuleDefinitionDTO ruleDefinitionDTO, ServerWebExchange exchange) {

        long startTime = System.currentTimeMillis();

        return updateRuleDefinition(ruleDefinitionId, ruleDefinitionDTO)
                .flatMap(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event and return the result
                    return auditHelper.recordAuditEvent(
                            AuditEventType.RULE_DEFINITION_UPDATE,
                            ruleDefinitionId,
                            result.getCode(),
                            ruleDefinitionDTO,
                            result,
                            200,
                            true,
                            executionTime,
                            exchange
                    ).thenReturn(result);
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event for error and re-throw
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_DEFINITION_UPDATE,
                            ruleDefinitionId,
                            ruleDefinitionDTO.getCode(),
                            ruleDefinitionDTO,
                            error.getMessage(),
                            400,
                            executionTime,
                            exchange
                    ).then(Mono.error(error));
                });
    }

    @Override
    public Mono<Void> deleteRuleDefinitionWithAudit(UUID ruleDefinitionId, ServerWebExchange exchange) {

        long startTime = System.currentTimeMillis();

        // Get rule definition first for audit purposes
        return getRuleDefinitionById(ruleDefinitionId)
                .flatMap(ruleDefinition -> {
                    return deleteRuleDefinition(ruleDefinitionId)
                            .then(Mono.defer(() -> {
                                long executionTime = System.currentTimeMillis() - startTime;

                                // Record audit event and return empty
                                return auditHelper.recordAuditEvent(
                                        AuditEventType.RULE_DEFINITION_DELETE,
                                        ruleDefinitionId,
                                        ruleDefinition.getCode(),
                                        null, // requestData
                                        null, // responseData
                                        204,
                                        true,
                                        executionTime,
                                        exchange
                                ).then();
                            }));
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event for error and re-throw
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_DEFINITION_DELETE,
                            ruleDefinitionId,
                            null, // ruleCode
                            null, // requestData
                            error.getMessage(),
                            400,
                            executionTime,
                            exchange
                    ).then(Mono.error(error));
                });
    }

    @Override
    public Mono<RuleDefinitionDTO> getRuleDefinitionByIdWithAudit(UUID ruleDefinitionId, ServerWebExchange exchange) {

        long startTime = System.currentTimeMillis();

        return getRuleDefinitionById(ruleDefinitionId)
                .flatMap(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event and return the result
                    String ruleCode = result != null ? result.getCode() : null;
                    return auditHelper.recordAuditEvent(
                            AuditEventType.RULE_DEFINITION_GET,
                            ruleDefinitionId,
                            ruleCode,
                            null, // requestData
                            result,
                            200,
                            true,
                            executionTime,
                            exchange
                    ).thenReturn(result);
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event for error and re-throw
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_DEFINITION_GET,
                            ruleDefinitionId,
                            null, // ruleCode
                            null, // requestData
                            error.getMessage(),
                            500,
                            executionTime,
                            exchange
                    ).then(Mono.error(error));
                });
    }

    @Override
    public Mono<RuleDefinitionDTO> getRuleDefinitionByCodeWithAudit(String code, ServerWebExchange exchange) {

        long startTime = System.currentTimeMillis();

        return getRuleDefinitionByCode(code)
                .flatMap(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event and return the result
                    UUID entityId = result != null ? result.getId() : null;
                    return auditHelper.recordAuditEvent(
                            AuditEventType.RULE_DEFINITION_GET,
                            entityId,
                            code,
                            null, // requestData
                            result,
                            200,
                            true,
                            executionTime,
                            exchange
                    ).thenReturn(result);
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event for error and re-throw
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_DEFINITION_GET,
                            null, // entityId
                            code,
                            null, // requestData
                            error.getMessage(),
                            500,
                            executionTime,
                            exchange
                    ).then(Mono.error(error));
                });
    }

    @Override
    public Mono<ValidationResult> validateRuleDefinitionWithAudit(String yamlContent, ServerWebExchange exchange) {

        long startTime = System.currentTimeMillis();

        return validateRuleDefinition(yamlContent)
                .flatMap(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event and return the result
                    return auditHelper.recordAuditEvent(
                            AuditEventType.RULE_DEFINITION_VALIDATE,
                            null, // entityId
                            null, // ruleCode
                            yamlContent,
                            result,
                            200,
                            true,
                            executionTime,
                            exchange
                    ).thenReturn(result);
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Record audit event for error and re-throw
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_DEFINITION_VALIDATE,
                            null, // entityId
                            null, // ruleCode
                            yamlContent,
                            error.getMessage(),
                            400,
                            executionTime,
                            exchange
                    ).then(Mono.error(error));
                });
    }
}
