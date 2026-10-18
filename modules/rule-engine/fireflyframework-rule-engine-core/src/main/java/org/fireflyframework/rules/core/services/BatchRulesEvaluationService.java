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

import org.fireflyframework.rules.interfaces.dtos.evaluation.BatchRulesEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.BatchRulesEvaluationResponseDTO;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Service interface for batch rule evaluation operations.
 * Provides high-performance batch processing capabilities for evaluating multiple rules
 * or rule sets in a single operation with optimized concurrency and caching.
 */
public interface BatchRulesEvaluationService {

    /**
     * Evaluates multiple rules in batch with comprehensive audit trail.
     * Processes all evaluation requests concurrently with configurable limits
     * and provides detailed statistics and performance metrics.
     *
     * @param request the batch evaluation request containing multiple rule evaluation requests
     * @param exchange the server web exchange for audit context
     * @return a Mono emitting the batch evaluation response with results and statistics
     */
    Mono<BatchRulesEvaluationResponseDTO> evaluateBatch(
            BatchRulesEvaluationRequestDTO request, 
            ServerWebExchange exchange);

    /**
     * Evaluates multiple rules in batch without audit trail.
     * Optimized for high-performance scenarios where audit logging is not required.
     *
     * @param request the batch evaluation request containing multiple rule evaluation requests
     * @return a Mono emitting the batch evaluation response with results and statistics
     */
    Mono<BatchRulesEvaluationResponseDTO> evaluateBatch(
            BatchRulesEvaluationRequestDTO request);

    /**
     * Gets current batch processing statistics.
     * Provides real-time metrics about batch processing performance,
     * cache hit rates, and system resource utilization.
     *
     * @return a Mono emitting the current batch processing statistics
     */
    Mono<BatchRulesEvaluationResponseDTO> getBatchStatistics();

    /**
     * Gets health status of the batch processing system.
     * Checks system resources, cache availability, and processing capacity.
     *
     * @return a Mono emitting the health status information
     */
    Mono<BatchRulesEvaluationResponseDTO> getHealthStatus();

    /**
     * Validates a batch evaluation request without executing it.
     * Performs comprehensive validation of request structure, rule codes,
     * input data formats, and batch configuration options.
     *
     * @param request the batch evaluation request to validate
     * @return a Mono emitting validation results and any detected issues
     */
    Mono<BatchRulesEvaluationResponseDTO> validateBatchRequest(
            BatchRulesEvaluationRequestDTO request);
}
