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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify the rule engine works with basic operations
 */
@DisplayName("Simple Rule Engine Tests")
class SimpleRuleTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);

        // Mock constant service to return empty flux (no constants from database)
        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, null, null);
    }

    @Test
    @DisplayName("Test basic rule evaluation")
    void testBasicRuleEvaluation() {
        String yamlRule = """
            when:
              - creditScore at_least 650
            then:
              - set approval to "APPROVED"
            else:
              - set approval to "DECLINED"
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getOutputData().get("approval"));
    }

    @Test
    @DisplayName("Test calculation action")
    void testCalculationAction() {
        String yamlRule = """
            then:
              - calculate monthlyIncome as annualIncome / 12
              - calculate debtRatio as monthlyDebt / monthlyIncome * 100
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("annualIncome", new BigDecimal("60000"));
        inputData.put("monthlyDebt", new BigDecimal("1500"));

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        
        BigDecimal monthlyIncome = (BigDecimal) result.getOutputData().get("monthlyIncome");
        assertNotNull(monthlyIncome);
        assertEquals(0, monthlyIncome.compareTo(new BigDecimal("5000.00")));

        BigDecimal debtRatio = (BigDecimal) result.getOutputData().get("debtRatio");
        assertNotNull(debtRatio);
        assertEquals(0, debtRatio.compareTo(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("Test multiple conditions")
    void testMultipleConditions() {
        String yamlRule = """
            when:
              - creditScore at_least 650
              - annualIncome at_least 50000
              - employmentYears at_least 2
            then:
              - set eligibility to "QUALIFIED"
              - calculate maxLoan as annualIncome * 4
            else:
              - set eligibility to "NOT_QUALIFIED"
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);
        inputData.put("annualIncome", new BigDecimal("75000"));
        inputData.put("employmentYears", 5);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals("QUALIFIED", result.getOutputData().get("eligibility"));

        BigDecimal maxLoan = (BigDecimal) result.getOutputData().get("maxLoan");
        assertNotNull(maxLoan);
        assertEquals(0, maxLoan.compareTo(new BigDecimal("300000")));
    }

    @Test
    @DisplayName("Test validation operators")
    void testValidationOperators() {
        String yamlRule = """
            when:
              - email is_email
              - phoneNumber is_phone
              - amount is_positive
            then:
              - set validation to "PASSED"
            else:
              - set validation to "FAILED"
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("email", "test@example.com");
        inputData.put("phoneNumber", "+1-555-123-4567");
        inputData.put("amount", new BigDecimal("100.50"));

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals("PASSED", result.getOutputData().get("validation"));
    }

    @Test
    @DisplayName("Test constants usage")
    void testConstantsUsage() {
        String yamlRule = """
            constants:
              - code: MIN_CREDIT_SCORE
                defaultValue: 650
              - code: MAX_LOAN_AMOUNT
                defaultValue: 500000
            
            when:
              - creditScore at_least MIN_CREDIT_SCORE
              - requestedAmount less_than MAX_LOAN_AMOUNT
            then:
              - set decision to "APPROVED"
            else:
              - set decision to "DECLINED"
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);
        inputData.put("requestedAmount", new BigDecimal("300000"));

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getOutputData().get("decision"));
    }
}
