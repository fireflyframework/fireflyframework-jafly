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

package org.fireflyframework.rules.interfaces.dtos.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request DTO for YAML DSL validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateYamlRequest {

    /**
     * The YAML DSL content to validate
     */
    @NotBlank(message = "YAML content cannot be empty")
    @Size(max = 100000, message = "YAML content too large (max 100KB)")
    private String yamlContent;

    /**
     * Optional: Specific validation categories to run
     * If empty, all validations will be performed
     */
    private Set<ValidationCategory> categories;

    /**
     * Optional: Minimum severity level to include in results
     * Default: INFO (includes all issues)
     */
    private ValidationResult.ValidationSeverity minSeverity;

    /**
     * Optional: Include suggestions in the response
     * Default: true
     */
    @Builder.Default
    private Boolean includeSuggestions = true;

    /**
     * Optional: Include performance metrics in the response
     * Default: false
     */
    @Builder.Default
    private Boolean includeMetrics = false;

    /**
     * Validation categories that can be selectively enabled/disabled
     */
    public enum ValidationCategory {
        SYNTAX,          // YAML syntax and DSL structure
        NAMING,          // Variable naming conventions
        DEPENDENCIES,    // Variable dependencies and order-of-operations
        LOGIC,           // Business logic and rule semantics
        PERFORMANCE,     // Performance optimization suggestions
        BEST_PRACTICES   // Code quality and maintainability
    }
}
