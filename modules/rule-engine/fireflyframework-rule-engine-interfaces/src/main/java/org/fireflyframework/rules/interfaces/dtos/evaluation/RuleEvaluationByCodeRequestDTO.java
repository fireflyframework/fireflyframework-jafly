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

import org.fireflyframework.rules.interfaces.validation.ValidInputVariableNames;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for evaluating rules by stored rule definition code.
 * Contains the rule code and input data required for evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for evaluating a stored rule definition by code")
public class RuleEvaluationByCodeRequestDTO {

    /**
     * Code of the stored rule definition to evaluate
     */
    @NotBlank(message = "Rule definition code is required")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", 
             message = "Code must start with a letter and contain only letters, numbers, and underscores")
    @Schema(description = "Code of the stored rule definition to evaluate",
            example = "credit_scoring_v1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleDefinitionCode;

    /**
     * Input data for rule evaluation
     */
    @NotNull(message = "Input data is required")
    @NotEmpty(message = "Input data cannot be empty")
    @Size(max = 1000, message = "Input data cannot contain more than 1000 entries")
    @ValidInputVariableNames
    @Schema(description = "Input data for rule evaluation. Variable names must follow camelCase convention (e.g., creditScore, annualIncome, employmentYears). These are dynamic values passed from your application.",
            example = "{\"creditScore\": 780, \"annualIncome\": 75000, \"employmentYears\": 3, \"existingDebt\": 25000, \"requestedAmount\": 200000}",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> inputData;

    /**
     * Optional metadata for the evaluation request
     */
    @Size(max = 100, message = "Metadata cannot contain more than 100 entries")
    @Schema(description = "Optional metadata for the evaluation request. Use snake_case for metadata keys.",
            example = "{\"request_id\": \"req-123\", \"source_system\": \"loan_application\", \"user_id\": \"user-456\", \"session_id\": \"sess-789\"}")
    private Map<String, Object> metadata;

    /**
     * Whether to include detailed execution information in the response
     */
    @Schema(description = "Whether to include detailed execution information", example = "false")
    private Boolean includeDetails;

    /**
     * Whether to enable debug mode for detailed logging
     */
    @Schema(description = "Whether to enable debug mode for detailed logging", example = "false")
    private Boolean debugMode;
}
