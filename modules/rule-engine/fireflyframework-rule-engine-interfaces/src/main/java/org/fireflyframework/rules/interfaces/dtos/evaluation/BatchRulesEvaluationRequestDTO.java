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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for batch rule evaluation operations.
 * Allows evaluating multiple rules or rule sets in a single API call for improved performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for batch rule evaluation operations")
public class BatchRulesEvaluationRequestDTO {

    @Schema(description = "List of rule evaluation requests to process in batch", 
            example = "[{\"ruleDefinitionCode\": \"LOAN_APPROVAL\", \"inputData\": {\"creditScore\": 750}}, {\"ruleDefinitionCode\": \"RISK_ASSESSMENT\", \"inputData\": {\"amount\": 50000}}]")
    @JsonProperty("evaluationRequests")
    @NotNull(message = "Evaluation requests cannot be null")
    @NotEmpty(message = "At least one evaluation request is required")
    @Size(max = 100, message = "Maximum 100 evaluation requests allowed per batch")
    @Valid
    private List<SingleRuleEvaluationRequest> evaluationRequests;

    @Schema(description = "Global input data to be merged with individual request data", 
            example = "{\"userId\": \"user123\", \"timestamp\": \"2025-01-13T10:00:00Z\"}")
    @JsonProperty("globalInputData")
    private Map<String, Object> globalInputData;

    @Schema(description = "Batch processing options")
    @JsonProperty("batchOptions")
    @Valid
    private BatchOptions batchOptions;

    /**
     * Single rule evaluation request within a batch.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual rule evaluation request within a batch")
    public static class SingleRuleEvaluationRequest {

        @Schema(description = "Unique identifier for this evaluation request within the batch", 
                example = "req-001")
        @JsonProperty("requestId")
        private String requestId;

        @Schema(description = "Rule definition code to evaluate", 
                example = "LOAN_APPROVAL_RULE")
        @JsonProperty("ruleDefinitionCode")
        @NotNull(message = "Rule definition code cannot be null")
        @Size(min = 1, max = 100, message = "Rule definition code must be between 1 and 100 characters")
        private String ruleDefinitionCode;

        @Schema(description = "Input data for this specific rule evaluation", 
                example = "{\"creditScore\": 750, \"income\": 75000}")
        @JsonProperty("inputData")
        private Map<String, Object> inputData;

        @Schema(description = "Priority for this evaluation (higher numbers processed first)", 
                example = "1")
        @JsonProperty("priority")
        @Builder.Default
        private Integer priority = 0;

        @Schema(description = "Whether to continue batch processing if this evaluation fails", 
                example = "true")
        @JsonProperty("continueOnError")
        @Builder.Default
        private Boolean continueOnError = true;
    }

    /**
     * Batch processing options and configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Batch processing configuration options")
    public static class BatchOptions {

        @Schema(description = "Maximum number of concurrent evaluations", 
                example = "10")
        @JsonProperty("maxConcurrency")
        @Builder.Default
        private Integer maxConcurrency = 10;

        @Schema(description = "Timeout for the entire batch operation in seconds", 
                example = "300")
        @JsonProperty("timeoutSeconds")
        @Builder.Default
        private Integer timeoutSeconds = 300;

        @Schema(description = "Whether to fail the entire batch if any evaluation fails", 
                example = "false")
        @JsonProperty("failFast")
        @Builder.Default
        private Boolean failFast = false;

        @Schema(description = "Whether to return partial results if some evaluations fail", 
                example = "true")
        @JsonProperty("returnPartialResults")
        @Builder.Default
        private Boolean returnPartialResults = true;

        @Schema(description = "Whether to sort requests by priority before processing", 
                example = "true")
        @JsonProperty("sortByPriority")
        @Builder.Default
        private Boolean sortByPriority = false;

        @Schema(description = "Whether to enable detailed timing information for each evaluation", 
                example = "false")
        @JsonProperty("includeTimingInfo")
        @Builder.Default
        private Boolean includeTimingInfo = false;

        @Schema(description = "Whether to cache rule definitions during batch processing", 
                example = "true")
        @JsonProperty("enableCaching")
        @Builder.Default
        private Boolean enableCaching = true;
    }
}
