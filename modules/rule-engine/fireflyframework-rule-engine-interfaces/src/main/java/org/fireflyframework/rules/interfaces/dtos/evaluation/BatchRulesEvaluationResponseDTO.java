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

package org.fireflyframework.rules.interfaces.dtos.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for batch rule evaluation operations.
 * Contains results for all rule evaluations in the batch along with summary statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for batch rule evaluation operations")
public class BatchRulesEvaluationResponseDTO {

    @Schema(description = "Overall batch processing status", 
            example = "SUCCESS")
    @JsonProperty("batchStatus")
    private BatchStatus batchStatus;

    @Schema(description = "List of individual rule evaluation results")
    @JsonProperty("evaluationResults")
    private List<SingleRuleEvaluationResult> evaluationResults;

    @Schema(description = "Batch processing summary and statistics")
    @JsonProperty("batchSummary")
    private BatchSummary batchSummary;

    @Schema(description = "Timestamp when batch processing started")
    @JsonProperty("startTime")
    private OffsetDateTime startTime;

    @Schema(description = "Timestamp when batch processing completed")
    @JsonProperty("endTime")
    private OffsetDateTime endTime;

    @Schema(description = "Total processing time in milliseconds")
    @JsonProperty("totalProcessingTimeMs")
    private Long totalProcessingTimeMs;

    /**
     * Individual rule evaluation result within a batch.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual rule evaluation result within a batch")
    public static class SingleRuleEvaluationResult {

        @Schema(description = "Request ID from the original request", 
                example = "req-001")
        @JsonProperty("requestId")
        private String requestId;

        @Schema(description = "Rule definition code that was evaluated", 
                example = "LOAN_APPROVAL_RULE")
        @JsonProperty("ruleDefinitionCode")
        private String ruleDefinitionCode;

        @Schema(description = "Evaluation status for this specific rule", 
                example = "SUCCESS")
        @JsonProperty("status")
        private EvaluationStatus status;

        @Schema(description = "Rule evaluation result if successful")
        @JsonProperty("result")
        private RulesEvaluationResponseDTO result;

        @Schema(description = "Error message if evaluation failed", 
                example = "Rule definition not found")
        @JsonProperty("errorMessage")
        private String errorMessage;

        @Schema(description = "Error code if evaluation failed", 
                example = "RULE_NOT_FOUND")
        @JsonProperty("errorCode")
        private String errorCode;

        @Schema(description = "Processing time for this evaluation in milliseconds")
        @JsonProperty("processingTimeMs")
        private Long processingTimeMs;

        @Schema(description = "Timestamp when this evaluation started")
        @JsonProperty("startTime")
        private OffsetDateTime startTime;

        @Schema(description = "Timestamp when this evaluation completed")
        @JsonProperty("endTime")
        private OffsetDateTime endTime;

        @Schema(description = "Priority of this evaluation request")
        @JsonProperty("priority")
        private Integer priority;
    }

    /**
     * Batch processing summary and statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Batch processing summary and statistics")
    public static class BatchSummary {

        @Schema(description = "Total number of evaluation requests in the batch")
        @JsonProperty("totalRequests")
        private Integer totalRequests;

        @Schema(description = "Number of successful evaluations")
        @JsonProperty("successfulEvaluations")
        private Integer successfulEvaluations;

        @Schema(description = "Number of failed evaluations")
        @JsonProperty("failedEvaluations")
        private Integer failedEvaluations;

        @Schema(description = "Number of skipped evaluations (due to fail-fast or other reasons)")
        @JsonProperty("skippedEvaluations")
        private Integer skippedEvaluations;

        @Schema(description = "Success rate as a percentage")
        @JsonProperty("successRate")
        private Double successRate;

        @Schema(description = "Average processing time per evaluation in milliseconds")
        @JsonProperty("averageProcessingTimeMs")
        private Double averageProcessingTimeMs;

        @Schema(description = "Minimum processing time among all evaluations in milliseconds")
        @JsonProperty("minProcessingTimeMs")
        private Long minProcessingTimeMs;

        @Schema(description = "Maximum processing time among all evaluations in milliseconds")
        @JsonProperty("maxProcessingTimeMs")
        private Long maxProcessingTimeMs;

        @Schema(description = "Number of cache hits during batch processing")
        @JsonProperty("cacheHits")
        private Integer cacheHits;

        @Schema(description = "Number of cache misses during batch processing")
        @JsonProperty("cacheMisses")
        private Integer cacheMisses;

        @Schema(description = "Cache hit rate as a percentage")
        @JsonProperty("cacheHitRate")
        private Double cacheHitRate;

        @Schema(description = "Additional performance metrics")
        @JsonProperty("performanceMetrics")
        private Map<String, Object> performanceMetrics;
    }

    /**
     * Overall batch processing status.
     */
    public enum BatchStatus {
        @Schema(description = "All evaluations completed successfully")
        SUCCESS,
        
        @Schema(description = "Some evaluations completed successfully, others failed")
        PARTIAL_SUCCESS,
        
        @Schema(description = "All evaluations failed")
        FAILED,
        
        @Schema(description = "Batch processing was cancelled or timed out")
        CANCELLED,
        
        @Schema(description = "Batch processing is still in progress")
        IN_PROGRESS
    }

    /**
     * Individual evaluation status.
     */
    public enum EvaluationStatus {
        @Schema(description = "Evaluation completed successfully")
        SUCCESS,
        
        @Schema(description = "Evaluation failed due to an error")
        FAILED,
        
        @Schema(description = "Evaluation was skipped")
        SKIPPED,
        
        @Schema(description = "Evaluation timed out")
        TIMEOUT
    }
}
