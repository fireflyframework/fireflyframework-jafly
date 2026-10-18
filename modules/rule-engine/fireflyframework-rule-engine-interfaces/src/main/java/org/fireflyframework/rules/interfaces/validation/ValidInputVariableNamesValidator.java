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

package org.fireflyframework.rules.interfaces.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validator implementation for {@link ValidInputVariableNames} annotation.
 * 
 * Validates that all keys in a Map follow camelCase naming convention:
 * - Must start with lowercase letter
 * - Can contain letters and numbers
 * - No underscores or special characters
 */
public class ValidInputVariableNamesValidator implements ConstraintValidator<ValidInputVariableNames, Map<String, Object>> {

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9]*$");

    @Override
    public void initialize(ValidInputVariableNames constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(Map<String, Object> inputData, ConstraintValidatorContext context) {
        if (inputData == null) {
            return true; // Let @NotNull handle null validation
        }

        // Check each key in the input data
        for (String variableName : inputData.keySet()) {
            if (!isValidCamelCase(variableName)) {
                // Build custom error message with specific variable name
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    String.format("Input variable '%s' must follow camelCase naming convention. " +
                                "Expected: camelCase (e.g., creditScore, annualIncome). " +
                                "Found: %s", variableName, detectNamingPattern(variableName)))
                    .addConstraintViolation();
                return false;
            }
        }

        return true;
    }

    /**
     * Check if a variable name follows camelCase convention
     *
     * @param name the variable name
     * @return true if valid camelCase
     */
    private boolean isValidCamelCase(String name) {
        return name != null && CAMEL_CASE_PATTERN.matcher(name).matches();
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

        if (name.matches("^[A-Z][A-Z0-9_]*$")) {
            return "UPPER_CASE_WITH_UNDERSCORES (use for constants only)";
        } else if (name.matches("^[a-z][a-z0-9_]*$")) {
            return "snake_case (use for computed variables only)";
        } else if (name.matches("^[A-Z][a-zA-Z0-9]*$")) {
            return "PascalCase (should be camelCase)";
        } else if (name.contains("_")) {
            return "mixed case with underscores";
        } else if (name.contains("-")) {
            return "kebab-case";
        } else {
            return "unrecognized pattern";
        }
    }
}
