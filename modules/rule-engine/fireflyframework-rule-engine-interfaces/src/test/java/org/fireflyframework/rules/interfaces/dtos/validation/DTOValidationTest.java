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

import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RulesEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RulesEvaluationResponseDTO;
import org.fireflyframework.rules.interfaces.enums.ValueType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive validation tests for all DTOs in the rules engine.
 * Tests ensure that validation annotations are working correctly.
 */
class DTOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ===== ConstantDTO Validation Tests =====

    @Test
    void testConstantDTO_ValidData_ShouldPassValidation() {
        // Given
        ConstantDTO constantDTO = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("MINIMUM_CREDIT_SCORE")
                .name("Minimum Credit Score")
                .valueType(ValueType.NUMBER)
                .required(true)
                .description("The minimum credit score required for loan approval")
                .currentValue(650)
                .build();

        // When
        Set<ConstraintViolation<ConstantDTO>> violations = validator.validate(constantDTO);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void testConstantDTO_BlankCode_ShouldFailValidation() {
        // Given
        ConstantDTO constantDTO = ConstantDTO.builder()
                .code("")
                .name("Test Name")
                .valueType(ValueType.STRING)
                .required(true)
                .build();

        // When
        Set<ConstraintViolation<ConstantDTO>> violations = validator.validate(constantDTO);

        // Then
        assertThat(violations).hasSize(3); // NotBlank, Size, and Pattern violations
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Constant code is required"));
    }

    @Test
    void testConstantDTO_InvalidCodePattern_ShouldFailValidation() {
        // Given
        ConstantDTO constantDTO = ConstantDTO.builder()
                .code("invalid-code-with-dashes")
                .name("Test Name")
                .valueType(ValueType.STRING)
                .required(true)
                .build();

        // When
        Set<ConstraintViolation<ConstantDTO>> violations = validator.validate(constantDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("uppercase letter and contain only uppercase letters"));
    }

    @Test
    void testConstantDTO_CodeTooLong_ShouldFailValidation() {
        // Given
        String longCode = "A".repeat(101); // 101 characters
        ConstantDTO constantDTO = ConstantDTO.builder()
                .code(longCode)
                .name("Test Name")
                .valueType(ValueType.STRING)
                .required(true)
                .build();

        // When
        Set<ConstraintViolation<ConstantDTO>> violations = validator.validate(constantDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("between 2 and 100 characters"));
    }

    @Test
    void testConstantDTO_NullValueType_ShouldFailValidation() {
        // Given
        ConstantDTO constantDTO = ConstantDTO.builder()
                .code("VALID_CODE")
                .name("Test Name")
                .valueType(null)
                .required(true)
                .build();

        // When
        Set<ConstraintViolation<ConstantDTO>> violations = validator.validate(constantDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Value type is required"));
    }

    @Test
    void testConstantDTO_DescriptionTooLong_ShouldFailValidation() {
        // Given
        String longDescription = "A".repeat(1001); // 1001 characters
        ConstantDTO constantDTO = ConstantDTO.builder()
                .code("VALID_CODE")
                .name("Test Name")
                .valueType(ValueType.STRING)
                .required(true)
                .description(longDescription)
                .build();

        // When
        Set<ConstraintViolation<ConstantDTO>> violations = validator.validate(constantDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("cannot exceed 1000 characters"));
    }

    // ===== RulesEvaluationRequestDTO Validation Tests =====

    @Test
    void testRulesEvaluationRequestDTO_ValidData_ShouldPassValidation() {
        // Given
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);  // camelCase input variables
        inputData.put("annualIncome", 75000);

        RulesEvaluationRequestDTO requestDTO = RulesEvaluationRequestDTO.builder()
                .rulesDefinitionBase64(java.util.Base64.getEncoder().encodeToString("name: \"Test Rule\"\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE".getBytes()))
                .inputData(inputData)
                .includeDetails(false)
                .debugMode(false)
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationRequestDTO>> violations = validator.validate(requestDTO);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void testRulesEvaluationRequestDTO_InvalidInputVariableNames_ShouldFailValidation() {
        // Given
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("CREDIT_SCORE", 750);  // Should be camelCase
        inputData.put("annual_income", 75000);  // Should be camelCase

        RulesEvaluationRequestDTO requestDTO = RulesEvaluationRequestDTO.builder()
                .rulesDefinitionBase64(java.util.Base64.getEncoder().encodeToString("name: \"Test Rule\"".getBytes()))
                .inputData(inputData)
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationRequestDTO>> violations = validator.validate(requestDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("camelCase"));
    }

    @Test
    void testRulesEvaluationRequestDTO_NullInputData_ShouldFailValidation() {
        // Given
        RulesEvaluationRequestDTO requestDTO = RulesEvaluationRequestDTO.builder()
                .rulesDefinitionBase64(java.util.Base64.getEncoder().encodeToString("name: \"Test Rule\"".getBytes()))
                .inputData(null)
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationRequestDTO>> violations = validator.validate(requestDTO);

        // Then
        assertThat(violations).hasSize(2); // NotNull and NotEmpty violations
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Input data is required"));
    }

    @Test
    void testRulesEvaluationRequestDTO_EmptyInputData_ShouldFailValidation() {
        // Given
        RulesEvaluationRequestDTO requestDTO = RulesEvaluationRequestDTO.builder()
                .rulesDefinitionBase64(java.util.Base64.getEncoder().encodeToString("name: \"Test Rule\"".getBytes()))
                .inputData(new HashMap<>())
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationRequestDTO>> violations = validator.validate(requestDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Input data cannot be empty"));
    }

    @Test
    void testRulesEvaluationRequestDTO_RulesDefinitionTooLong_ShouldFailValidation() {
        // Given
        String longRulesDefinition = "A".repeat(100001); // 100001 characters
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("test", "value");

        RulesEvaluationRequestDTO requestDTO = RulesEvaluationRequestDTO.builder()
                .rulesDefinitionBase64(longRulesDefinition) // Direct assignment, not base64 encoded
                .inputData(inputData)
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationRequestDTO>> violations = validator.validate(requestDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("cannot exceed 100000 characters"));
    }

    // ===== RulesEvaluationResponseDTO Validation Tests =====

    @Test
    void testRulesEvaluationResponseDTO_ValidData_ShouldPassValidation() {
        // Given
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("decision", "approved");
        outputData.put("score", 85);

        RulesEvaluationResponseDTO responseDTO = RulesEvaluationResponseDTO.builder()
                .success(true)
                .conditionResult(true)
                .outputData(outputData)
                .executionTimeMs(25L)
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationResponseDTO>> violations = validator.validate(responseDTO);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void testRulesEvaluationResponseDTO_NullSuccess_ShouldFailValidation() {
        // Given
        RulesEvaluationResponseDTO responseDTO = RulesEvaluationResponseDTO.builder()
                .success(null)
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationResponseDTO>> violations = validator.validate(responseDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Success flag is required"));
    }

    @Test
    void testRulesEvaluationResponseDTO_NegativeExecutionTime_ShouldFailValidation() {
        // Given
        RulesEvaluationResponseDTO responseDTO = RulesEvaluationResponseDTO.builder()
                .success(true)
                .executionTimeMs(-5L)
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationResponseDTO>> violations = validator.validate(responseDTO);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("cannot be negative"));
    }

    @Test
    void testRulesEvaluationResponseDTO_ErrorMessageTooLong_ShouldFailValidation() {
        // Given
        String longError = "A".repeat(1001); // 1001 characters
        RulesEvaluationResponseDTO responseDTO = RulesEvaluationResponseDTO.builder()
                .success(false)
                .error(longError)
                .build();

        // When
        Set<ConstraintViolation<RulesEvaluationResponseDTO>> violations = validator.validate(responseDTO);

        // Then
        assertThat(violations).hasSize(1); // Only Size for error (success is set to false)
        assertThat(violations).anyMatch(v -> v.getMessage().contains("cannot exceed 1000 characters"));
    }
}
