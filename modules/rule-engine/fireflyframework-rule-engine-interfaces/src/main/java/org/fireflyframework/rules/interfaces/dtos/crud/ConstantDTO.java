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

import org.fireflyframework.rules.interfaces.enums.ValueType;
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
 * DTO for system constants used in rule evaluation.
 * Constants are predefined values that don't change during rule execution,
 * acting as a feature store for the rule engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "System constant used in rule evaluation. Constants follow UPPER_CASE_WITH_UNDERSCORES naming convention.")
public class ConstantDTO {

    /**
     * UUID primary key
     */
    @Schema(description = "Unique identifier for the constant",
            example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    /**
     * Unique constant code (e.g., MINIMUM_CREDIT_SCORE, MAX_LOAN_AMOUNT)
     */
    @NotBlank(message = "Constant code is required")
    @Size(min = 2, max = 100, message = "Constant code must be between 2 and 100 characters")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Constant code must start with uppercase letter and contain only uppercase letters, numbers, and underscores")
    @Schema(description = "Unique constant code following UPPER_CASE_WITH_UNDERSCORES convention. Used in YAML DSL rules to reference this constant.",
            example = "MIN_CREDIT_SCORE")
    private String code;

    /**
     * Human-readable constant name
     */
    @NotBlank(message = "Constant name is required")
    @Size(min = 2, max = 255, message = "Constant name must be between 2 and 255 characters")
    @Schema(description = "Human-readable name for the constant",
            example = "Minimum Credit Score")
    private String name;

    /**
     * Data type (STRING, NUMBER, BOOLEAN, DATE, OBJECT)
     */
    @NotNull(message = "Value type is required")
    @Schema(description = "Data type of the constant value",
            example = "NUMBER")
    private ValueType valueType;

    /**
     * Whether constant must have value for evaluation
     */
    @NotNull(message = "Required flag must be specified")
    @Schema(description = "Whether this constant must have a value for rule evaluation",
            example = "true")
    private Boolean required;

    /**
     * Optional constant description
     */
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Schema(description = "Optional description explaining the purpose of this constant",
            example = "Minimum credit score required for loan approval")
    private String description;

    /**
     * Current value of the constant (stored as JSON for flexibility)
     */
    @Schema(description = "Current value of the constant. Type should match the valueType field.",
            example = "650")
    private Object currentValue;

    /**
     * Creation timestamp
     */
    @Schema(description = "Timestamp when the constant was created",
            example = "2025-01-12T10:30:00Z")
    private OffsetDateTime createdAt;

    /**
     * Last update timestamp
     */
    @Schema(description = "Timestamp when the constant was last updated",
            example = "2025-01-12T15:45:00Z")
    private OffsetDateTime updatedAt;
}
