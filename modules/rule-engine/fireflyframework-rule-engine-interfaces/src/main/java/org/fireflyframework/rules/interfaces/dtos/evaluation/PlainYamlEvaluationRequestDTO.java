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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for evaluating plain YAML rules (not base64 encoded).
 * Contains the YAML content and input data required for evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for evaluating plain YAML DSL rules")
public class PlainYamlEvaluationRequestDTO {

    /**
     * Plain YAML rules definition to evaluate
     */
    @NotBlank(message = "YAML content is required")
    @Size(min = 10, max = 50000, message = "YAML content must be between 10 and 50000 characters")
    @Schema(description = "Plain YAML DSL rules definition to evaluate. Must follow naming conventions: camelCase for inputs (creditScore, annualIncome), snake_case for computed variables (debt_to_income, is_eligible), UPPER_CASE for constants (MIN_CREDIT_SCORE).",
            example = "name: \"Credit Scoring Rule\"\ndescription: \"Basic credit assessment for loan applications\"\n\ninputs:\n  - creditScore        # camelCase input\n  - annualIncome       # camelCase input\n  - employmentYears    # camelCase input\n  - existingDebt       # camelCase input\n\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE\n  - annualIncome at_least 50000\n  - employmentYears at_least 2\n\nthen:\n  - calculate debt_to_income as existingDebt / annualIncome    # snake_case computed\n  - set is_eligible to true                                   # snake_case computed\n  - set approval_tier to \"STANDARD\"                          # snake_case computed\n\nelse:\n  - set is_eligible to false\n  - set approval_tier to \"DECLINED\"\n\noutput:\n  is_eligible: is_eligible\n  approval_tier: approval_tier\n  debt_to_income: debt_to_income",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String yamlContent;

    /**
     * Input data for rule evaluation
     */
    @NotNull(message = "Input data is required")
    @NotEmpty(message = "Input data cannot be empty")
    @Size(max = 1000, message = "Input data cannot contain more than 1000 entries")
    @ValidInputVariableNames
    @Schema(description = "Input data for rule evaluation. Variable names must follow camelCase convention (e.g., creditScore, annualIncome, employmentYears). These are dynamic values passed from your application.",
            example = "{\"creditScore\": 780, \"annualIncome\": 75000, \"employmentYears\": 3, \"existingDebt\": 25000}",
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
