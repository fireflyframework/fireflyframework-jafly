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

package org.fireflyframework.rules.core.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for validating variable naming conventions in the Firefly Rule Engine.
 * 
 * Enforces the following naming conventions:
 * - Input Variables: camelCase (e.g., creditScore, annualIncome)
 * - System Constants: UPPER_CASE_WITH_UNDERSCORES (e.g., MIN_CREDIT_SCORE, MAX_LOAN_AMOUNT)
 * - Computed Variables: snake_case (e.g., debt_to_income, final_score)
 */
@Component
@Slf4j
public class NamingConventionValidator {

    // Regex patterns for different naming conventions
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern UPPER_CASE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    /**
     * Validate that input variable names follow camelCase convention
     *
     * @param inputData the input data map to validate
     * @return list of validation errors (empty if valid)
     */
    public List<String> validateInputVariableNames(Map<String, Object> inputData) {
        List<String> errors = new ArrayList<>();
        
        if (inputData == null) {
            return errors;
        }

        for (String variableName : inputData.keySet()) {
            if (!isValidInputVariableName(variableName)) {
                errors.add(String.format(
                    "Input variable '%s' must follow camelCase naming convention (e.g., creditScore, annualIncome). " +
                    "Found: %s", variableName, detectNamingPattern(variableName)));
            }
        }

        return errors;
    }

    /**
     * Validate that a computed variable name follows snake_case convention
     *
     * @param variableName the variable name to validate
     * @return validation error message, or null if valid
     */
    public String validateComputedVariableName(String variableName) {
        if (!isValidComputedVariableName(variableName)) {
            return String.format(
                "Computed variable '%s' must follow snake_case naming convention (e.g., debt_to_income, final_score). " +
                "Found: %s", variableName, detectNamingPattern(variableName));
        }
        return null;
    }

    /**
     * Validate that a constant name follows UPPER_CASE_WITH_UNDERSCORES convention
     *
     * @param constantName the constant name to validate
     * @return validation error message, or null if valid
     */
    public String validateConstantName(String constantName) {
        if (!isValidConstantName(constantName)) {
            return String.format(
                "Constant '%s' must follow UPPER_CASE_WITH_UNDERSCORES naming convention (e.g., MIN_CREDIT_SCORE, MAX_LOAN_AMOUNT). " +
                "Found: %s", constantName, detectNamingPattern(constantName));
        }
        return null;
    }

    /**
     * Check if a variable name follows camelCase convention
     *
     * @param name the variable name
     * @return true if valid camelCase
     */
    public boolean isValidInputVariableName(String name) {
        return name != null && CAMEL_CASE_PATTERN.matcher(name).matches();
    }

    /**
     * Check if a variable name follows snake_case convention
     *
     * @param name the variable name
     * @return true if valid snake_case
     */
    public boolean isValidComputedVariableName(String name) {
        return name != null && SNAKE_CASE_PATTERN.matcher(name).matches();
    }

    /**
     * Check if a constant name follows UPPER_CASE_WITH_UNDERSCORES convention
     *
     * @param name the constant name
     * @return true if valid UPPER_CASE
     */
    public boolean isValidConstantName(String name) {
        return name != null && UPPER_CASE_PATTERN.matcher(name).matches();
    }

    /**
     * Determine the variable type based on naming convention
     *
     * @param name the variable name
     * @return the detected variable type
     */
    public VariableType detectVariableType(String name) {
        if (isValidConstantName(name)) {
            return VariableType.CONSTANT;
        } else if (isValidComputedVariableName(name)) {
            return VariableType.COMPUTED;
        } else if (isValidInputVariableName(name)) {
            return VariableType.INPUT;
        } else {
            return VariableType.INVALID;
        }
    }

    /**
     * Detect the naming pattern of a variable for error messages
     *
     * @param name the variable name
     * @return description of the detected pattern
     */
    private String detectNamingPattern(String name) {
        if (name == null || name.isEmpty()) {
            return "empty or null";
        }

        if (UPPER_CASE_PATTERN.matcher(name).matches()) {
            return "UPPER_CASE_WITH_UNDERSCORES (constant pattern)";
        } else if (SNAKE_CASE_PATTERN.matcher(name).matches()) {
            return "snake_case (computed variable pattern)";
        } else if (CAMEL_CASE_PATTERN.matcher(name).matches()) {
            return "camelCase (input variable pattern)";
        } else if (name.contains("_")) {
            return "mixed case with underscores";
        } else if (name.contains("-")) {
            return "kebab-case";
        } else if (Character.isUpperCase(name.charAt(0))) {
            return "PascalCase";
        } else {
            return "unrecognized pattern";
        }
    }

    /**
     * Enum representing different variable types based on naming conventions
     */
    public enum VariableType {
        INPUT,      // camelCase
        CONSTANT,   // UPPER_CASE_WITH_UNDERSCORES
        COMPUTED,   // snake_case
        INVALID     // doesn't match any pattern
    }

    /**
     * Get example names for each variable type
     *
     * @param type the variable type
     * @return example names
     */
    public String getExampleNames(VariableType type) {
        return switch (type) {
            case INPUT -> "creditScore, annualIncome, employmentYears";
            case CONSTANT -> "MIN_CREDIT_SCORE, MAX_LOAN_AMOUNT, RISK_MULTIPLIER";
            case COMPUTED -> "debt_to_income, loan_ratio, final_score";
            case INVALID -> "N/A";
        };
    }

    /**
     * Get naming convention description for each variable type
     *
     * @param type the variable type
     * @return convention description
     */
    public String getNamingConvention(VariableType type) {
        return switch (type) {
            case INPUT -> "camelCase";
            case CONSTANT -> "UPPER_CASE_WITH_UNDERSCORES";
            case COMPUTED -> "snake_case";
            case INVALID -> "invalid";
        };
    }
}
