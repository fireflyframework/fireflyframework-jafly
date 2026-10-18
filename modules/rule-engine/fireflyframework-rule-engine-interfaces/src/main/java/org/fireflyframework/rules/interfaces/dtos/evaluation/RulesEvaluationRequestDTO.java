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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for rules evaluation operations.
 * Contains the input data required for rules evaluation.
 * Supports both single rule and multiple rules evaluation scenarios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for rules evaluation containing input data")
public class RulesEvaluationRequestDTO {

    /**
     * Base64 encoded YAML rules definition to evaluate
     */
    @Size(max = 100000, message = "Base64 encoded rules definition cannot exceed 100000 characters")
    @Schema(description = "Base64 encoded YAML rules definition to evaluate. Must follow naming conventions: camelCase for inputs, snake_case for computed variables, UPPER_CASE for constants.",
            example = "bmFtZTogIkNyZWRpdCBBc3Nlc3NtZW50IgpkZXNjcmlwdGlvbjogIkJhc2ljIGNyZWRpdCBlbGlnaWJpbGl0eSBjaGVjayIKCmlucHV0czoKICAtIGNyZWRpdFNjb3JlICAgICAgIyBjYW1lbENhc2UgaW5wdXQKICAtIGFubual SW5jb21lICAgICAgIyBjYW1lbENhc2UgaW5wdXQKCndoZW46CiAgLSBjcmVkaXRTY29yZSBhdF9sZWFzdCBNSU5fQ1JFRElUX1NDT1JFICMgVVBQRVJfQ0FTRSBjb25zdGFudAogIC0gYW5udWFsSW5jb21lIGF0X2xlYXN0IDUwMDAwCgp0aGVuOgogIC0gY2FsY3VsYXRlIGRlYnRfdG9faW5jb21lIGFzIGV4aXN0aW5nRGVidCAvIGFubual SW5jb21lICMgc25ha2VfY2FzZSBjb21wdXRlZAogIC0gc2V0IGNyZWRpdF90aWVyIHRvICJQUklNRSIgICMgc25ha2VfY2FzZSBjb21wdXRlZAogIC0gc2V0IGlzX2VsaWdpYmxlIHRvIHRydWUgICMgc25ha2VfY2FzZSBjb21wdXRlZAoKZWxzZToKICAtIHNldCBjcmVkaXRfdGllciB0byAiU1RBTkRBUkQiCiAgLSBzZXQgaXNfZWxpZ2libGUgdG8gZmFsc2UKCm91dHB1dDoKICBjcmVkaXRfdGllcjogY3JlZGl0X3RpZXIKICBpc19lbGlnaWJsZTogaXNfZWxpZ2libGUKICBkZWJ0X3RvX2luY29tZTogZGVidF90b19pbmNvbWU=",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String rulesDefinitionBase64;

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
