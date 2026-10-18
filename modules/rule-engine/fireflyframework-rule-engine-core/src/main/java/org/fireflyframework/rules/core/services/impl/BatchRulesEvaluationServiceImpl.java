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

import org.fireflyframework.rules.core.services.BatchRulesEvaluationService;
import org.fireflyframework.rules.core.services.RulesEvaluationService;
import org.fireflyframework.rules.interfaces.dtos.evaluation.BatchRulesEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.BatchRulesEvaluationResponseDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RuleEvaluationByCodeRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of batch rule evaluation service.
 * Provides high-performance batch processing with concurrent evaluation,
 * comprehensive error handling, and detailed performance metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchRulesEvaluationServiceImpl implements BatchRulesEvaluationService {

    private final RulesEvaluationService rulesEvaluationService;

    // Performance tracking
    private final AtomicLong totalBatchesProcessed = new AtomicLong(0);
    private final AtomicLong totalEvaluationsProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

    @Override
    public Mono<BatchRulesEvaluationResponseDTO> evaluateBatch(
            BatchRulesEvaluationRequestDTO request, 
            ServerWebExchange exchange) {
        
        log.info("Starting batch evaluation with {} requests", 
                request.getEvaluationRequests().size());
        
        long startTime = System.currentTimeMillis();
        
        return processBatchRequests(request, exchange)
                .doOnSuccess(response -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    totalBatchesProcessed.incrementAndGet();
                    totalEvaluationsProcessed.addAndGet(request.getEvaluationRequests().size());
                    totalProcessingTimeMs.addAndGet(processingTime);
                    
                    log.info("Completed batch evaluation in {}ms with {} successful, {} failed", 
                            processingTime,
                            response.getBatchSummary().getSuccessfulEvaluations(),
                            response.getBatchSummary().getFailedEvaluations());
                })
                .doOnError(error -> log.error("Batch evaluation failed", error));
    }

    @Override
    public Mono<BatchRulesEvaluationResponseDTO> evaluateBatch(
            BatchRulesEvaluationRequestDTO request) {
        return evaluateBatch(request, null);
    }

    @Override
    public Mono<BatchRulesEvaluationResponseDTO> getBatchStatistics() {
        Map<String, Object> performanceMetrics = new HashMap<>();
        performanceMetrics.put("totalBatchesProcessed", totalBatchesProcessed.get());
        performanceMetrics.put("totalEvaluationsProcessed", totalEvaluationsProcessed.get());
        performanceMetrics.put("averageProcessingTimeMs", 
                totalBatchesProcessed.get() > 0 ? 
                        (double) totalProcessingTimeMs.get() / totalBatchesProcessed.get() : 0.0);
        
        BatchRulesEvaluationResponseDTO.BatchSummary summary = 
                BatchRulesEvaluationResponseDTO.BatchSummary.builder()
                        .totalRequests(0)
                        .successfulEvaluations(0)
                        .failedEvaluations(0)
                        .skippedEvaluations(0)
                        .successRate(100.0)
                        .averageProcessingTimeMs(0.0)
                        .minProcessingTimeMs(0L)
                        .maxProcessingTimeMs(0L)
                        .cacheHits(0)
                        .cacheMisses(0)
                        .cacheHitRate(0.0)
                        .performanceMetrics(performanceMetrics)
                        .build();
        
        return Mono.just(BatchRulesEvaluationResponseDTO.builder()
                .batchStatus(BatchRulesEvaluationResponseDTO.BatchStatus.SUCCESS)
                .evaluationResults(new ArrayList<>())
                .batchSummary(summary)
                .build());
    }

    @Override
    public Mono<BatchRulesEvaluationResponseDTO> getHealthStatus() {
        // Simple health check implementation
        return Mono.just(BatchRulesEvaluationResponseDTO.builder()
                .batchStatus(BatchRulesEvaluationResponseDTO.BatchStatus.SUCCESS)
                .evaluationResults(new ArrayList<>())
                .batchSummary(BatchRulesEvaluationResponseDTO.BatchSummary.builder()
                        .totalRequests(0)
                        .successfulEvaluations(0)
                        .failedEvaluations(0)
                        .build())
                .build());
    }

    @Override
    public Mono<BatchRulesEvaluationResponseDTO> validateBatchRequest(
            BatchRulesEvaluationRequestDTO request) {
        
        // Basic validation
        if (request.getEvaluationRequests() == null || request.getEvaluationRequests().isEmpty()) {
            return Mono.just(BatchRulesEvaluationResponseDTO.builder()
                    .batchStatus(BatchRulesEvaluationResponseDTO.BatchStatus.FAILED)
                    .evaluationResults(new ArrayList<>())
                    .batchSummary(BatchRulesEvaluationResponseDTO.BatchSummary.builder()
                            .totalRequests(0)
                            .successfulEvaluations(0)
                            .failedEvaluations(1)
                            .build())
                    .build());
        }
        
        return Mono.just(BatchRulesEvaluationResponseDTO.builder()
                .batchStatus(BatchRulesEvaluationResponseDTO.BatchStatus.SUCCESS)
                .evaluationResults(new ArrayList<>())
                .batchSummary(BatchRulesEvaluationResponseDTO.BatchSummary.builder()
                        .totalRequests(request.getEvaluationRequests().size())
                        .successfulEvaluations(request.getEvaluationRequests().size())
                        .failedEvaluations(0)
                        .build())
                .build());
    }

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_CONCURRENCY = 20;

    private Mono<BatchRulesEvaluationResponseDTO> processBatchRequests(
            BatchRulesEvaluationRequestDTO request,
            ServerWebExchange exchange) {

        // Enforce server-side limits
        if (request.getEvaluationRequests().size() > MAX_BATCH_SIZE) {
            return Mono.error(new IllegalArgumentException(
                "Batch size " + request.getEvaluationRequests().size() + " exceeds maximum of " + MAX_BATCH_SIZE));
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Enforce server-side concurrency cap
        int requestedConcurrency = request.getBatchOptions() != null ?
                request.getBatchOptions().getMaxConcurrency() : 10;
        int maxConcurrency = Math.min(requestedConcurrency, MAX_CONCURRENCY);

        return Flux.fromIterable(request.getEvaluationRequests())
                .flatMap(singleRequest -> processIndividualRequest(singleRequest, request, exchange)
                        .doOnNext(result -> {
                            if (result.getStatus() == BatchRulesEvaluationResponseDTO.EvaluationStatus.SUCCESS) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        })
                        .onErrorResume(error -> {
                            failureCount.incrementAndGet();
                            return Mono.just(
                                    BatchRulesEvaluationResponseDTO.SingleRuleEvaluationResult.builder()
                                            .requestId(singleRequest.getRequestId())
                                            .ruleDefinitionCode(singleRequest.getRuleDefinitionCode())
                                            .status(BatchRulesEvaluationResponseDTO.EvaluationStatus.FAILED)
                                            .errorMessage(error.getMessage())
                                            .errorCode("EVALUATION_ERROR")
                                            .processingTimeMs(0L)
                                            .startTime(OffsetDateTime.now())
                                            .build());
                        }), maxConcurrency)
                .collectList()
                .map(evaluationResults -> {
                    BatchRulesEvaluationResponseDTO.BatchStatus batchStatus =
                            failureCount.get() == 0 ? BatchRulesEvaluationResponseDTO.BatchStatus.SUCCESS :
                                    successCount.get() > 0 ? BatchRulesEvaluationResponseDTO.BatchStatus.PARTIAL_SUCCESS :
                                            BatchRulesEvaluationResponseDTO.BatchStatus.FAILED;

                    BatchRulesEvaluationResponseDTO.BatchSummary summary =
                            BatchRulesEvaluationResponseDTO.BatchSummary.builder()
                                    .totalRequests(request.getEvaluationRequests().size())
                                    .successfulEvaluations(successCount.get())
                                    .failedEvaluations(failureCount.get())
                                    .skippedEvaluations(0)
                                    .successRate(successCount.get() * 100.0 / request.getEvaluationRequests().size())
                                    .averageProcessingTimeMs(0.0)
                                    .minProcessingTimeMs(0L)
                                    .maxProcessingTimeMs(0L)
                                    .cacheHits(0)
                                    .cacheMisses(0)
                                    .cacheHitRate(0.0)
                                    .performanceMetrics(new HashMap<>())
                                    .build();

                    return BatchRulesEvaluationResponseDTO.builder()
                            .batchStatus(batchStatus)
                            .evaluationResults(evaluationResults)
                            .batchSummary(summary)
                            .build();
                });
    }

    private Mono<BatchRulesEvaluationResponseDTO.SingleRuleEvaluationResult> processIndividualRequest(
            BatchRulesEvaluationRequestDTO.SingleRuleEvaluationRequest singleRequest,
            BatchRulesEvaluationRequestDTO batchRequest,
            ServerWebExchange exchange) {
        
        long startTime = System.currentTimeMillis();
        OffsetDateTime startDateTime = OffsetDateTime.now();
        
        // Merge global and individual input data
        Map<String, Object> mergedInputData = new HashMap<>();
        if (batchRequest.getGlobalInputData() != null) {
            mergedInputData.putAll(batchRequest.getGlobalInputData());
        }
        if (singleRequest.getInputData() != null) {
            mergedInputData.putAll(singleRequest.getInputData());
        }
        
        RuleEvaluationByCodeRequestDTO evaluationRequest = RuleEvaluationByCodeRequestDTO.builder()
                .ruleDefinitionCode(singleRequest.getRuleDefinitionCode())
                .inputData(mergedInputData)
                .build();
        
        return rulesEvaluationService.evaluateRuleByCodeWithAudit(evaluationRequest, exchange)
                .map(result -> BatchRulesEvaluationResponseDTO.SingleRuleEvaluationResult.builder()
                        .requestId(singleRequest.getRequestId())
                        .ruleDefinitionCode(singleRequest.getRuleDefinitionCode())
                        .status(BatchRulesEvaluationResponseDTO.EvaluationStatus.SUCCESS)
                        .result(result)
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .startTime(startDateTime)
                        .build())
                .onErrorResume(error -> Mono.just(
                        BatchRulesEvaluationResponseDTO.SingleRuleEvaluationResult.builder()
                                .requestId(singleRequest.getRequestId())
                                .ruleDefinitionCode(singleRequest.getRuleDefinitionCode())
                                .status(BatchRulesEvaluationResponseDTO.EvaluationStatus.FAILED)
                                .errorMessage(error.getMessage())
                                .errorCode("RULE_EVALUATION_ERROR")
                                .processingTimeMs(System.currentTimeMillis() - startTime)
                                .startTime(startDateTime)
                                .build()));
    }
}
