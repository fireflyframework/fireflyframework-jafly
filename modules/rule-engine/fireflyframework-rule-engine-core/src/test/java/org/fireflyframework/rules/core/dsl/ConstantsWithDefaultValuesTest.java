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

package org.fireflyframework.rules.core.dsl;

import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.services.ConstantService;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.fireflyframework.rules.interfaces.enums.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Test constants with default values functionality.
 * Verifies that:
 * 1. Database values override default values when available
 * 2. Default values are used when constants are missing from database
 * 3. Evaluation fails when constants are missing and no defaults provided
 */
@ExtendWith(MockitoExtension.class)
class ConstantsWithDefaultValuesTest {

    @Mock
    private ConstantService constantService;

    private ASTRulesEvaluationEngine evaluationEngine;
    private ASTRulesDSLParser parser;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, null, null);
    }

    @Test
    @DisplayName("Should use database values when available, ignoring defaults")
    void testDatabaseValuesOverrideDefaults() {
        // Mock database constants (higher values than defaults)
        ConstantDTO minCreditScore = ConstantDTO.builder()
                .code("MIN_CREDIT_SCORE")
                .name("Minimum Credit Score")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("700"))  // Database value: 700
                .build();

        ConstantDTO maxLoanAmount = ConstantDTO.builder()
                .code("MAX_LOAN_AMOUNT")
                .name("Maximum Loan Amount")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("600000"))  // Database value: 600000
                .build();

        when(constantService.getConstantsByCodes(anyList()))
                .thenReturn(Flux.just(minCreditScore, maxLoanAmount));

        String yamlRule = """
            name: "Credit Assessment with Database Override"
            description: "Tests that database values override defaults"
            
            constants:
              - code: MIN_CREDIT_SCORE
                defaultValue: 650           # Default: 650, but database has 700
              - code: MAX_LOAN_AMOUNT
                defaultValue: 500000        # Default: 500000, but database has 600000
            
            inputs:
              - creditScore
              - requestedAmount
            
            when:
              - creditScore at_least MIN_CREDIT_SCORE      # Should use 700 from database
              - requestedAmount less_than MAX_LOAN_AMOUNT  # Should use 600000 from database
            then:
              - set decision to "APPROVED"
              - set source to "database_values"
            else:
              - set decision to "DECLINED"
              - set source to "failed_conditions"
            
            output:
              decision: text
              source: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);  // Meets database requirement (700)
        inputData.put("requestedAmount", new BigDecimal("550000"));  // Meets database requirement (600000)

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getOutputData().get("decision"));
        assertEquals("database_values", result.getOutputData().get("source"));
    }

    @Test
    @DisplayName("Should use default values when constants missing from database")
    void testDefaultValuesWhenDatabaseMissing() {
        // Mock empty database response (no constants found)
        when(constantService.getConstantsByCodes(anyList()))
                .thenReturn(Flux.empty());

        String yamlRule = """
            name: "Credit Assessment with Default Fallback"
            description: "Tests that default values are used when database is empty"
            
            constants:
              - code: MIN_CREDIT_SCORE
                defaultValue: 650           # Should use this default
              - code: MAX_LOAN_AMOUNT
                defaultValue: 500000        # Should use this default
            
            inputs:
              - creditScore
              - requestedAmount
            
            when:
              - creditScore at_least MIN_CREDIT_SCORE      # Should use 650 from default
              - requestedAmount less_than MAX_LOAN_AMOUNT  # Should use 500000 from default
            then:
              - set decision to "APPROVED"
              - set source to "default_values"
            else:
              - set decision to "DECLINED"
              - set source to "failed_conditions"
            
            output:
              decision: text
              source: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 680);  // Meets default requirement (650)
        inputData.put("requestedAmount", new BigDecimal("450000"));  // Meets default requirement (500000)

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getOutputData().get("decision"));
        assertEquals("default_values", result.getOutputData().get("source"));
    }

    @Test
    @DisplayName("Should fail when constants missing and no defaults provided")
    void testFailureWhenNoDefaultsProvided() {
        // Mock empty database response (no constants found)
        when(constantService.getConstantsByCodes(anyList()))
                .thenReturn(Flux.empty());

        String yamlRule = """
            name: "Credit Assessment without Defaults"
            description: "Tests failure when constants missing and no defaults"
            
            inputs:
              - creditScore
              - requestedAmount
            
            when:
              - creditScore at_least MIN_CREDIT_SCORE      # No default provided
              - requestedAmount less_than MAX_LOAN_AMOUNT  # No default provided
            then:
              - set decision to "APPROVED"
            else:
              - set decision to "DECLINED"
            
            output:
              decision: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);
        inputData.put("requestedAmount", new BigDecimal("300000"));

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("Required constants not found in database and no default values provided"));
        assertTrue(result.getError().contains("MIN_CREDIT_SCORE"));
        assertTrue(result.getError().contains("MAX_LOAN_AMOUNT"));
    }

    @Test
    @DisplayName("Should handle mixed scenarios: some constants in database, some using defaults")
    void testMixedDatabaseAndDefaultValues() {
        // Mock partial database response (only one constant found)
        ConstantDTO minCreditScore = ConstantDTO.builder()
                .code("MIN_CREDIT_SCORE")
                .name("Minimum Credit Score")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("700"))  // Database value: 700
                .build();
        // MAX_LOAN_AMOUNT not in database, should use default

        when(constantService.getConstantsByCodes(anyList()))
                .thenReturn(Flux.just(minCreditScore));

        String yamlRule = """
            name: "Credit Assessment with Mixed Sources"
            description: "Tests mixed database and default values"
            
            constants:
              - code: MIN_CREDIT_SCORE
                defaultValue: 650           # Database has 700, should use 700
              - code: MAX_LOAN_AMOUNT
                defaultValue: 500000        # Not in database, should use 500000
            
            inputs:
              - creditScore
              - requestedAmount
            
            when:
              - creditScore at_least MIN_CREDIT_SCORE      # Should use 700 from database
              - requestedAmount less_than MAX_LOAN_AMOUNT  # Should use 500000 from default
            then:
              - set decision to "APPROVED"
              - set source to "mixed_sources"
            else:
              - set decision to "DECLINED"
              - set source to "failed_conditions"
            
            output:
              decision: text
              source: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);  // Meets database requirement (700)
        inputData.put("requestedAmount", new BigDecimal("450000"));  // Meets default requirement (500000)

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getOutputData().get("decision"));
        assertEquals("mixed_sources", result.getOutputData().get("source"));
    }

    @Test
    @DisplayName("Should support different data types for default values")
    void testDifferentDataTypesForDefaults() {
        // Mock empty database response
        when(constantService.getConstantsByCodes(anyList()))
                .thenReturn(Flux.empty());

        String yamlRule = """
            name: "Multi-Type Constants Test"
            description: "Tests different data types for default values"
            
            constants:
              - code: MIN_CREDIT_SCORE
                defaultValue: 650                    # Number
              - code: APPROVAL_MESSAGE
                defaultValue: "Loan approved"        # String
              - code: AUTO_APPROVE
                defaultValue: true                   # Boolean
              - code: INTEREST_RATE
                defaultValue: 0.045                  # Decimal
            
            inputs:
              - creditScore
            
            when:
              - creditScore at_least MIN_CREDIT_SCORE
              - AUTO_APPROVE equals true
            then:
              - set decision to APPROVAL_MESSAGE
              - set rate to INTEREST_RATE
              - set auto_approved to AUTO_APPROVE
            else:
              - set decision to "DECLINED"
            
            output:
              decision: text
              rate: number
              auto_approved: boolean
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 680);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals("Loan approved", result.getOutputData().get("decision"));
        assertEquals(0.045, result.getOutputData().get("rate")); // Default values come as their original type
        assertEquals(true, result.getOutputData().get("auto_approved"));
    }
}
