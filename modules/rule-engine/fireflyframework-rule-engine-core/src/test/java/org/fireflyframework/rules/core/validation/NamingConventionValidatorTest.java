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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NamingConventionValidator
 */
class NamingConventionValidatorTest {

    private NamingConventionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NamingConventionValidator();
    }

    // ===== Input Variable Tests (camelCase) =====

    @Test
    void testValidInputVariableNames() {
        // Given
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);
        inputData.put("annualIncome", 75000);
        inputData.put("employmentYears", 3);
        inputData.put("requestedAmount", 250000);

        // When
        List<String> errors = validator.validateInputVariableNames(inputData);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    void testInvalidInputVariableNames_UpperCase() {
        // Given
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("CREDIT_SCORE", 750);  // Should be creditScore
        inputData.put("ANNUAL_INCOME", 75000);  // Should be annualIncome

        // When
        List<String> errors = validator.validateInputVariableNames(inputData);

        // Then
        assertThat(errors).hasSize(2);
        assertThat(errors).anyMatch(error -> error.contains("CREDIT_SCORE") && error.contains("camelCase"));
        assertThat(errors).anyMatch(error -> error.contains("ANNUAL_INCOME") && error.contains("camelCase"));
    }

    @Test
    void testInvalidInputVariableNames_SnakeCase() {
        // Given
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("credit_score", 750);  // Should be creditScore
        inputData.put("annual_income", 75000);  // Should be annualIncome

        // When
        List<String> errors = validator.validateInputVariableNames(inputData);

        // Then
        assertThat(errors).hasSize(2);
        assertThat(errors).anyMatch(error -> error.contains("credit_score") && error.contains("camelCase"));
        assertThat(errors).anyMatch(error -> error.contains("annual_income") && error.contains("camelCase"));
    }

    @Test
    void testIsValidInputVariableName() {
        // Valid camelCase names
        assertThat(validator.isValidInputVariableName("creditScore")).isTrue();
        assertThat(validator.isValidInputVariableName("annualIncome")).isTrue();
        assertThat(validator.isValidInputVariableName("employmentYears")).isTrue();
        assertThat(validator.isValidInputVariableName("a")).isTrue();
        assertThat(validator.isValidInputVariableName("test123")).isTrue();

        // Invalid names
        assertThat(validator.isValidInputVariableName("CREDIT_SCORE")).isFalse();
        assertThat(validator.isValidInputVariableName("credit_score")).isFalse();
        assertThat(validator.isValidInputVariableName("CreditScore")).isFalse();
        assertThat(validator.isValidInputVariableName("123test")).isFalse();
        assertThat(validator.isValidInputVariableName("credit-score")).isFalse();
        assertThat(validator.isValidInputVariableName("")).isFalse();
        assertThat(validator.isValidInputVariableName(null)).isFalse();
    }

    // ===== Computed Variable Tests (snake_case) =====

    @Test
    void testValidComputedVariableNames() {
        assertThat(validator.validateComputedVariableName("debt_to_income")).isNull();
        assertThat(validator.validateComputedVariableName("loan_ratio")).isNull();
        assertThat(validator.validateComputedVariableName("final_score")).isNull();
        assertThat(validator.validateComputedVariableName("risk_factor")).isNull();
        assertThat(validator.validateComputedVariableName("a")).isNull();
        assertThat(validator.validateComputedVariableName("test123")).isNull();
    }

    @Test
    void testInvalidComputedVariableNames() {
        // UPPER_CASE (constant pattern)
        String error1 = validator.validateComputedVariableName("DEBT_TO_INCOME");
        assertThat(error1).contains("DEBT_TO_INCOME").contains("snake_case");

        // camelCase (input pattern)
        String error2 = validator.validateComputedVariableName("debtToIncome");
        assertThat(error2).contains("debtToIncome").contains("snake_case");

        // PascalCase
        String error3 = validator.validateComputedVariableName("DebtToIncome");
        assertThat(error3).contains("DebtToIncome").contains("snake_case");
    }

    @Test
    void testIsValidComputedVariableName() {
        // Valid snake_case names
        assertThat(validator.isValidComputedVariableName("debt_to_income")).isTrue();
        assertThat(validator.isValidComputedVariableName("final_score")).isTrue();
        assertThat(validator.isValidComputedVariableName("risk_factor")).isTrue();
        assertThat(validator.isValidComputedVariableName("a")).isTrue();
        assertThat(validator.isValidComputedVariableName("test123")).isTrue();

        // Invalid names
        assertThat(validator.isValidComputedVariableName("DEBT_TO_INCOME")).isFalse();
        assertThat(validator.isValidComputedVariableName("debtToIncome")).isFalse();
        assertThat(validator.isValidComputedVariableName("DebtToIncome")).isFalse();
        assertThat(validator.isValidComputedVariableName("debt-to-income")).isFalse();
        assertThat(validator.isValidComputedVariableName("")).isFalse();
        assertThat(validator.isValidComputedVariableName(null)).isFalse();
    }

    // ===== Constant Tests (UPPER_CASE_WITH_UNDERSCORES) =====

    @Test
    void testValidConstantNames() {
        assertThat(validator.validateConstantName("MIN_CREDIT_SCORE")).isNull();
        assertThat(validator.validateConstantName("MAX_LOAN_AMOUNT")).isNull();
        assertThat(validator.validateConstantName("RISK_MULTIPLIER")).isNull();
        assertThat(validator.validateConstantName("A")).isNull();
        assertThat(validator.validateConstantName("TEST123")).isNull();
        assertThat(validator.validateConstantName("MAX_AMOUNT_2024")).isNull();
    }

    @Test
    void testInvalidConstantNames() {
        // camelCase (input pattern)
        String error1 = validator.validateConstantName("minCreditScore");
        assertThat(error1).contains("minCreditScore").contains("UPPER_CASE_WITH_UNDERSCORES");

        // snake_case (computed pattern)
        String error2 = validator.validateConstantName("min_credit_score");
        assertThat(error2).contains("min_credit_score").contains("UPPER_CASE_WITH_UNDERSCORES");

        // Starting with number
        String error3 = validator.validateConstantName("2024_RATE");
        assertThat(error3).contains("2024_RATE").contains("UPPER_CASE_WITH_UNDERSCORES");
    }

    @Test
    void testIsValidConstantName() {
        // Valid UPPER_CASE names
        assertThat(validator.isValidConstantName("MIN_CREDIT_SCORE")).isTrue();
        assertThat(validator.isValidConstantName("MAX_LOAN_AMOUNT")).isTrue();
        assertThat(validator.isValidConstantName("RISK_MULTIPLIER")).isTrue();
        assertThat(validator.isValidConstantName("A")).isTrue();
        assertThat(validator.isValidConstantName("TEST123")).isTrue();

        // Invalid names
        assertThat(validator.isValidConstantName("minCreditScore")).isFalse();
        assertThat(validator.isValidConstantName("min_credit_score")).isFalse();
        assertThat(validator.isValidConstantName("MinCreditScore")).isFalse();
        assertThat(validator.isValidConstantName("2024_RATE")).isFalse();
        assertThat(validator.isValidConstantName("min-credit-score")).isFalse();
        assertThat(validator.isValidConstantName("")).isFalse();
        assertThat(validator.isValidConstantName(null)).isFalse();
    }

    // ===== Variable Type Detection Tests =====

    @Test
    void testDetectVariableType() {
        // Input variables (camelCase)
        assertThat(validator.detectVariableType("creditScore")).isEqualTo(NamingConventionValidator.VariableType.INPUT);
        assertThat(validator.detectVariableType("annualIncome")).isEqualTo(NamingConventionValidator.VariableType.INPUT);

        // Constants (UPPER_CASE)
        assertThat(validator.detectVariableType("MIN_CREDIT_SCORE")).isEqualTo(NamingConventionValidator.VariableType.CONSTANT);
        assertThat(validator.detectVariableType("MAX_LOAN_AMOUNT")).isEqualTo(NamingConventionValidator.VariableType.CONSTANT);

        // Computed variables (snake_case)
        assertThat(validator.detectVariableType("debt_to_income")).isEqualTo(NamingConventionValidator.VariableType.COMPUTED);
        assertThat(validator.detectVariableType("final_score")).isEqualTo(NamingConventionValidator.VariableType.COMPUTED);

        // Invalid
        assertThat(validator.detectVariableType("CreditScore")).isEqualTo(NamingConventionValidator.VariableType.INVALID);
        assertThat(validator.detectVariableType("credit-score")).isEqualTo(NamingConventionValidator.VariableType.INVALID);
        assertThat(validator.detectVariableType("")).isEqualTo(NamingConventionValidator.VariableType.INVALID);
        assertThat(validator.detectVariableType(null)).isEqualTo(NamingConventionValidator.VariableType.INVALID);
    }

    // ===== Helper Method Tests =====

    @Test
    void testGetExampleNames() {
        assertThat(validator.getExampleNames(NamingConventionValidator.VariableType.INPUT))
            .contains("creditScore", "annualIncome");
        assertThat(validator.getExampleNames(NamingConventionValidator.VariableType.CONSTANT))
            .contains("MIN_CREDIT_SCORE", "MAX_LOAN_AMOUNT");
        assertThat(validator.getExampleNames(NamingConventionValidator.VariableType.COMPUTED))
            .contains("debt_to_income", "final_score");
    }

    @Test
    void testGetNamingConvention() {
        assertThat(validator.getNamingConvention(NamingConventionValidator.VariableType.INPUT))
            .isEqualTo("camelCase");
        assertThat(validator.getNamingConvention(NamingConventionValidator.VariableType.CONSTANT))
            .isEqualTo("UPPER_CASE_WITH_UNDERSCORES");
        assertThat(validator.getNamingConvention(NamingConventionValidator.VariableType.COMPUTED))
            .isEqualTo("snake_case");
    }
}
