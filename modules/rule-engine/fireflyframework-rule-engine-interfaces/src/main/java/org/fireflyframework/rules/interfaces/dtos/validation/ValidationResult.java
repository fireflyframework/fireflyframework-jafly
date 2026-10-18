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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive validation result for YAML DSL rules.
 * Acts as a static code analyzer providing detailed feedback on rule quality.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    /**
     * Overall validation status
     */
    private ValidationStatus status;

    /**
     * Summary of validation results
     */
    private ValidationSummary summary;

    /**
     * Detailed validation issues grouped by category
     */
    private ValidationIssues issues;

    /**
     * Suggestions for improving the rule
     */
    private List<ValidationSuggestion> suggestions;

    /**
     * Metadata about the validation process
     */
    private ValidationMetadata metadata;

    /**
     * Overall validation status
     */
    public enum ValidationStatus {
        VALID,           // No errors, rule is ready for deployment
        WARNING,         // Has warnings but can be deployed
        ERROR,           // Has errors that prevent deployment
        CRITICAL_ERROR   // Has critical errors that could cause system issues
    }

    /**
     * Summary statistics of validation results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationSummary {
        private int totalIssues;
        private int criticalErrors;
        private int errors;
        private int warnings;
        private int suggestions;
        private double qualityScore; // 0-100 score based on issues found
    }

    /**
     * Validation issues grouped by category
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationIssues {
        private List<ValidationIssue> syntax;
        private List<ValidationIssue> naming;
        private List<ValidationIssue> dependencies;
        private List<ValidationIssue> logic;
        private List<ValidationIssue> performance;
        private List<ValidationIssue> bestPractices;
    }

    /**
     * Individual validation issue
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationIssue {
        private String code;              // Error code (e.g., "NAMING_001", "SYNTAX_002")
        private ValidationSeverity severity;
        private String message;           // Human-readable error message
        private String description;       // Detailed explanation
        private ValidationLocation location; // Where the issue occurs
        private String suggestion;        // How to fix it
        private List<String> examples;    // Code examples showing correct usage
    }

    /**
     * Severity levels for validation issues
     */
    public enum ValidationSeverity {
        CRITICAL,  // Will cause runtime failures
        ERROR,     // Prevents rule from working correctly
        WARNING,   // Potential issues or bad practices
        INFO       // Suggestions for improvement
    }

    /**
     * Location information for validation issues
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationLocation {
        private String section;     // e.g., "inputs", "when", "then", "else"
        private Integer lineNumber; // Line number in YAML (if available)
        private String path;        // JSONPath to the problematic element
        private String context;     // Surrounding context for clarity
    }

    /**
     * Validation suggestions for improvement
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationSuggestion {
        private String category;    // e.g., "performance", "readability", "maintainability"
        private String title;       // Short description
        private String description; // Detailed explanation
        private String example;     // Example of improved code
        private int impact;         // 1-5 scale of improvement impact
    }

    /**
     * Metadata about the validation process
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationMetadata {
        private String validatorVersion;
        private Instant validatedAt;
        private long validationTimeMs;
        private String ruleName;
        private Map<String, Object> statistics; // Additional stats like complexity metrics
    }

    /**
     * Helper method to check if the validation result is valid (no errors or critical errors)
     */
    public boolean isValid() {
        return status == ValidationStatus.VALID || status == ValidationStatus.WARNING;
    }

    /**
     * Helper method to get all errors as a list
     */
    public List<ValidationIssue> getErrors() {
        if (issues == null) {
            return List.of();
        }

        List<ValidationIssue> allErrors = new java.util.ArrayList<>();
        if (issues.getSyntax() != null) allErrors.addAll(issues.getSyntax());
        if (issues.getNaming() != null) allErrors.addAll(issues.getNaming());
        if (issues.getDependencies() != null) allErrors.addAll(issues.getDependencies());
        if (issues.getLogic() != null) allErrors.addAll(issues.getLogic());
        if (issues.getPerformance() != null) allErrors.addAll(issues.getPerformance());
        if (issues.getBestPractices() != null) allErrors.addAll(issues.getBestPractices());

        return allErrors.stream()
                .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR ||
                               issue.getSeverity() == ValidationSeverity.CRITICAL)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Helper method to get a summary of errors
     */
    public String getErrorSummary() {
        if (isValid()) {
            return "No errors found";
        }

        List<ValidationIssue> errors = getErrors();
        if (errors.isEmpty()) {
            return "No errors found";
        }

        return errors.stream()
                .map(ValidationIssue::getMessage)
                .collect(java.util.stream.Collectors.joining("; "));
    }
}
