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

package org.fireflyframework.rules.web.controllers;

import org.fireflyframework.rules.core.services.BatchRulesEvaluationService;
import org.fireflyframework.rules.interfaces.dtos.evaluation.BatchRulesEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.BatchRulesEvaluationResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * REST controller for batch rule evaluation operations.
 * Provides high-performance endpoints for evaluating multiple rules
 * in a single request with comprehensive monitoring and statistics.
 */
@RestController
@RequestMapping("/api/v1/rules/batch")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Batch Rules Evaluation", description = "High-performance batch processing operations for evaluating multiple rules")
public class BatchRulesEvaluationController {

    private final BatchRulesEvaluationService batchRulesEvaluationService;

    /**
     * Evaluate multiple rules in a single batch operation
     *
     * @param request the batch evaluation request containing multiple rule evaluation requests
     * @param exchange the server web exchange for audit context
     * @return the batch evaluation response with results and statistics
     */
    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate multiple rules in batch",
               description = "Process multiple rule evaluations concurrently in a single request. " +
                           "Provides comprehensive error handling, performance metrics, and configurable concurrency limits. " +
                           "Input variables must use camelCase naming convention.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Batch evaluation completed (may include partial failures)"),
            @ApiResponse(responseCode = "400", description = "Invalid batch request structure or parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error during batch processing")
    })
    public Mono<ResponseEntity<BatchRulesEvaluationResponseDTO>> evaluateBatch(
            @Parameter(description = "Batch evaluation request with multiple rule evaluation requests", required = true)
            @Valid @RequestBody BatchRulesEvaluationRequestDTO request,
            ServerWebExchange exchange) {
        
        log.info("Received batch evaluation request with {} rules", 
                request.getEvaluationRequests().size());
        
        return batchRulesEvaluationService.evaluateBatch(request, exchange)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("Batch evaluation completed successfully"))
                .doOnError(error -> log.error("Batch evaluation failed", error));
    }

    /**
     * Validate a batch evaluation request without executing it
     *
     * @param request the batch evaluation request to validate
     * @return validation results and any detected issues
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate batch evaluation request",
               description = "Validate the structure and content of a batch evaluation request without executing it. " +
                           "Checks request format, rule codes, input data structure, and batch configuration options.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validation completed"),
            @ApiResponse(responseCode = "400", description = "Invalid request structure"),
            @ApiResponse(responseCode = "500", description = "Internal server error during validation")
    })
    public Mono<ResponseEntity<BatchRulesEvaluationResponseDTO>> validateBatchRequest(
            @Parameter(description = "Batch evaluation request to validate", required = true)
            @Valid @RequestBody BatchRulesEvaluationRequestDTO request) {
        
        return batchRulesEvaluationService.validateBatchRequest(request)
                .map(ResponseEntity::ok);
    }

    /**
     * Get current batch processing statistics
     *
     * @return current batch processing performance metrics and statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get batch processing statistics",
               description = "Retrieve real-time statistics about batch processing performance, " +
                           "including throughput metrics, cache hit rates, and system resource utilization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<BatchRulesEvaluationResponseDTO>> getBatchStatistics() {
        return batchRulesEvaluationService.getBatchStatistics()
                .map(ResponseEntity::ok);
    }

    /**
     * Get health status of the batch processing system
     *
     * @return health status information including system capacity and availability
     */
    @GetMapping("/health")
    @Operation(summary = "Get batch processing health status",
               description = "Check the health and availability of the batch processing system, " +
                           "including system resources, cache availability, and processing capacity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health status retrieved successfully"),
            @ApiResponse(responseCode = "503", description = "Service unavailable or degraded"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<BatchRulesEvaluationResponseDTO>> getHealthStatus() {
        return batchRulesEvaluationService.getHealthStatus()
                .map(response -> {
                    if (response.getBatchStatus() == BatchRulesEvaluationResponseDTO.BatchStatus.SUCCESS) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.status(503).body(response);
                    }
                });
    }
}
