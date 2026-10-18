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

package org.fireflyframework.rules.interfaces.dtos.crud;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for RuleDefinition entity.
 * Used for API requests and responses when managing YAML DSL rule definitions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Rule definition for storing and managing YAML DSL rules")
public class RuleDefinitionDTO {

    /**
     * UUID primary key
     */
    @Schema(description = "Unique identifier for the rule definition",
            example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    /**
     * Unique code identifier for the rule definition.
     * Must follow naming convention: alphanumeric with underscores, starting with letter.
     */
    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
             message = "Code must start with a letter and contain only letters, numbers, and underscores")
    @Size(min = 3, max = 100, message = "Code must be between 3 and 100 characters")
    @Schema(description = "Unique code identifier for the rule definition. Must start with a letter and contain only letters, numbers, and underscores.",
            example = "credit_scoring_v1")
    private String code;

    /**
     * Human-readable name for the rule definition
     */
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 200, message = "Name must be between 3 and 200 characters")
    @Schema(description = "Human-readable name for the rule definition",
            example = "Credit Scoring Rule v1")
    private String name;

    /**
     * Detailed description of what this rule definition does
     */
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Schema(description = "Detailed description of what this rule definition does",
            example = "Basic credit scoring rule for loan applications. Evaluates creditworthiness based on credit score, income, and debt ratios.")
    private String description;

    /**
     * The YAML DSL content as a string.
     * This content will be validated before storage.
     */
    @NotBlank(message = "YAML content is required")
    @Size(min = 10, max = 50000, message = "YAML content must be between 10 and 50000 characters")
    @Schema(description = "The YAML DSL content as a string. Must follow naming conventions: camelCase for inputs (creditScore, annualIncome), snake_case for computed variables (debt_to_income, is_eligible), UPPER_CASE for constants (MIN_CREDIT_SCORE).",
            example = "name: \"Credit Scoring\"\ndescription: \"Basic credit assessment\"\n\ninputs:\n  - creditScore        # camelCase input\n  - annualIncome       # camelCase input\n  - employmentYears    # camelCase input\n\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE    # UPPER_CASE constant\n  - annualIncome at_least 50000\n\nthen:\n  - calculate debt_to_income as existingDebt / annualIncome    # snake_case computed\n  - set credit_tier to \"PRIME\"                                # snake_case computed\n  - set is_eligible to true                                   # snake_case computed\n\nelse:\n  - set credit_tier to \"STANDARD\"\n  - set is_eligible to false\n\noutput:\n  credit_tier: credit_tier\n  is_eligible: is_eligible\n  debt_to_income: debt_to_income")
    private String yamlContent;

    /**
     * Version of the rule definition for tracking changes
     */
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$",
             message = "Version must follow semantic versioning format (e.g., 1.0.0)")
    @Schema(description = "Version of the rule definition for tracking changes. Must follow semantic versioning format.",
            example = "1.0.0")
    private String version;

    /**
     * Whether this rule definition is currently active and can be used for evaluation
     */
    @NotNull(message = "Active status is required")
    @Schema(description = "Whether this rule definition is currently active and can be used for evaluation",
            example = "true")
    private Boolean isActive;

    /**
     * Tags for categorizing and searching rule definitions (comma-separated)
     */
    @Size(max = 500, message = "Tags cannot exceed 500 characters")
    @Schema(description = "Tags for categorizing and searching rule definitions (comma-separated)",
            example = "credit,scoring,loan,banking")
    private String tags;

    /**
     * User who created this rule definition
     */
    @Schema(description = "User who created this rule definition",
            example = "john.doe")
    private String createdBy;

    /**
     * User who last modified this rule definition
     */
    @Schema(description = "User who last modified this rule definition",
            example = "jane.smith")
    private String updatedBy;

    /**
     * Timestamp when the rule definition was created
     */
    @Schema(description = "Timestamp when the rule definition was created",
            example = "2025-01-12T10:30:00Z")
    private OffsetDateTime createdAt;

    /**
     * Timestamp when the rule definition was last modified
     */
    @Schema(description = "Timestamp when the rule definition was last modified",
            example = "2025-01-12T15:45:00Z")
    private OffsetDateTime updatedAt;
}
