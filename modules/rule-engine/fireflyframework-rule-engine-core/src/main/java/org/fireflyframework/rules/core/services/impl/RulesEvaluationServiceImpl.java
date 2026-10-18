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

import org.fireflyframework.rules.core.audit.AuditHelper;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.services.RuleDefinitionService;
import org.fireflyframework.rules.core.services.RulesEvaluationService;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditEventType;
import org.fireflyframework.rules.interfaces.dtos.evaluation.PlainYamlEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RuleEvaluationByCodeRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RulesEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RulesEvaluationResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of RulesEvaluationService.
 * Provides business logic for evaluating YAML DSL rules with comprehensive audit trail recording.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesEvaluationServiceImpl implements RulesEvaluationService {

    private final ASTRulesEvaluationEngine rulesEvaluationEngine;
    private final RuleDefinitionService ruleDefinitionService;
    private final AuditHelper auditHelper;

    @Override
    public Mono<RulesEvaluationResponseDTO> evaluateRulesDirectWithAudit(
            RulesEvaluationRequestDTO request, ServerWebExchange exchange) {
        
        log.info("Evaluating base64-encoded YAML rules definition with input data: {}", request.getInputData());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Decode base64 YAML content
            String yamlContent = new String(Base64.getDecoder().decode(request.getRulesDefinitionBase64()), StandardCharsets.UTF_8);
            
            return rulesEvaluationEngine.evaluateRulesReactive(yamlContent, request.getInputData())
                    .map(this::buildResponse)
                    .flatMap(result -> {
                        long executionTime = System.currentTimeMillis() - startTime;
                        log.info("Rules evaluation completed successfully");

                        // Create metadata with evaluation details
                        Map<String, Object> metadata = createEvaluationMetadata("DIRECT_BASE64", request.getInputData(), result);

                        // Record audit event and return the result
                        return auditHelper.recordAuditEventWithMetadata(
                                AuditEventType.RULE_EVALUATION_DIRECT,
                                null, // entityId
                                null, // ruleCode
                                request,
                                result,
                                200,
                                true,
                                executionTime,
                                metadata,
                                exchange
                        ).thenReturn(result);
                    })
                    .onErrorResume(error -> {
                        long executionTime = System.currentTimeMillis() - startTime;
                        log.error("Rules evaluation failed", error);

                        // Record audit event for error and return error response
                        return auditHelper.recordAuditEventWithError(
                                AuditEventType.RULE_EVALUATION_DIRECT,
                                null, // entityId
                                null, // ruleCode
                                request,
                                error.getMessage(),
                                500,
                                executionTime,
                                exchange
                        ).then(Mono.just(RulesEvaluationResponseDTO.builder()
                                .success(false)
                                .error(error.getMessage())
                                .executionTimeMs(executionTime)
                                .build()));
                    });
        } catch (IllegalArgumentException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Invalid base64 encoding in rules definition", e);

            // Record audit event for encoding error and return error response
            return auditHelper.recordAuditEventWithError(
                    AuditEventType.RULE_EVALUATION_DIRECT,
                    null, // entityId
                    null, // ruleCode
                    request,
                    "Invalid base64 encoding in rules definition: " + e.getMessage(),
                    400,
                    executionTime,
                    exchange
            ).then(Mono.just(RulesEvaluationResponseDTO.builder()
                    .success(false)
                    .error("Invalid base64 encoding in rules definition: " + e.getMessage())
                    .build()));
        }
    }

    @Override
    public Mono<RulesEvaluationResponseDTO> evaluateRulesPlainWithAudit(
            PlainYamlEvaluationRequestDTO request, ServerWebExchange exchange) {
        

        log.info("Evaluating plain YAML rules definition with input data: {}", request.getInputData());

        long startTime = System.currentTimeMillis();
        
        return rulesEvaluationEngine.evaluateRulesReactive(request.getYamlContent(), request.getInputData())
                .map(this::buildResponse)
                .flatMap(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.info("Plain YAML rules evaluation completed successfully");

                    // Create metadata with evaluation details
                    Map<String, Object> metadata = createEvaluationMetadata("DIRECT_PLAIN", request.getInputData(), result);

                    // Record audit event and return the result
                    return auditHelper.recordAuditEventWithMetadata(
                            AuditEventType.RULE_EVALUATION_PLAIN,
                            null, // entityId
                            null, // ruleCode
                            request,
                            result,
                            200,
                            true,
                            executionTime,
                            metadata,
                            exchange
                    ).thenReturn(result);
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.error("Plain YAML rules evaluation failed", error);

                    // Record audit event for error and return error response
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_EVALUATION_PLAIN,
                            null, // entityId
                            null, // ruleCode
                            request,
                            error.getMessage(),
                            500,
                            executionTime,
                            exchange
                    ).then(Mono.just(RulesEvaluationResponseDTO.builder()
                            .success(false)
                            .error(error.getMessage())
                            .executionTimeMs(executionTime)
                            .build()));
                });
    }

    @Override
    public Mono<RulesEvaluationResponseDTO> evaluateRuleByCodeWithAudit(
            RuleEvaluationByCodeRequestDTO request, ServerWebExchange exchange) {
        
        log.info("Evaluating rule by code: {} with input data: {}", request.getRuleDefinitionCode(), request.getInputData());
        
        long startTime = System.currentTimeMillis();
        
        return ruleDefinitionService.evaluateRuleByCode(request.getRuleDefinitionCode(), request.getInputData())
                .map(this::buildResponse)
                .flatMap(result -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.info("Rule evaluation by code completed successfully");

                    // Create metadata with evaluation details
                    Map<String, Object> metadata = createEvaluationMetadata("BY_CODE", request.getInputData(), result);

                    // Record audit event and return the result
                    return auditHelper.recordAuditEventWithMetadata(
                            AuditEventType.RULE_EVALUATION_BY_CODE,
                            null, // entityId
                            request.getRuleDefinitionCode(),
                            request,
                            result,
                            200,
                            true,
                            executionTime,
                            metadata,
                            exchange
                    ).thenReturn(result);
                })
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.error("Rule evaluation by code failed", error);

                    // Record audit event for error and return error response
                    return auditHelper.recordAuditEventWithError(
                            AuditEventType.RULE_EVALUATION_BY_CODE,
                            null, // entityId
                            request.getRuleDefinitionCode(),
                            request,
                            error.getMessage(),
                            500,
                            executionTime,
                            exchange
                    ).then(Mono.just(RulesEvaluationResponseDTO.builder()
                            .success(false)
                            .error(error.getMessage())
                            .executionTimeMs(executionTime)
                            .build()));
                });
    }

    /**
     * Build response DTO from evaluation result
     */
    private RulesEvaluationResponseDTO buildResponse(ASTRulesEvaluationResult result) {
        return RulesEvaluationResponseDTO.builder()
                .success(result.isSuccess())
                .conditionResult(result.isConditionResult())
                .outputData(result.getOutputData())
                .circuitBreakerTriggered(result.isCircuitBreakerTriggered())
                .circuitBreakerMessage(result.getCircuitBreakerMessage())
                .error(result.getError())
                .executionTimeMs(result.getExecutionTimeMs())
                .build();
    }

    /**
     * Create metadata for evaluation audit events
     */
    private Map<String, Object> createEvaluationMetadata(String evaluationType, Map<String, Object> inputData, 
                                                         RulesEvaluationResponseDTO result) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("evaluationType", evaluationType);
        metadata.put("inputDataSize", inputData != null ? inputData.size() : 0);
        
        if (result != null) {
            metadata.put("conditionResult", result.getConditionResult());
            metadata.put("circuitBreakerTriggered", result.getCircuitBreakerTriggered());
            metadata.put("outputDataSize", result.getOutputData() != null ? result.getOutputData().size() : 0);
        }
        
        return metadata;
    }
}
