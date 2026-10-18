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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test suite for conditional logic patterns in the Firefly Rule Engine.
 * Tests when/then/else, if/then/else, complex conditions, and nested conditionals.
 */
@DisplayName("Conditional Logic Comprehensive Tests")
class ConditionalLogicComprehensiveTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);

        // Mock constant service to return empty flux (no constants from database)
        when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        // Use the constructor with default REST and JSON services
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    @DisplayName("Test basic when/then/else logic")
    void testBasicWhenThenElse() {
        String yaml = """
                name: "Basic When Then Else Test"
                description: "Test basic conditional logic"
                
                inputs:
                  - creditScore
                
                when:
                  - creditScore >= 700
                then:
                  - set approval_status to "APPROVED"
                  - set interest_rate to 3.5
                else:
                  - set approval_status to "DECLINED"
                  - set interest_rate to 5.5
                """;

        // Test condition true case
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getOutputData().get("approval_status"));
        assertEquals(3.5, ((Number) result.getOutputData().get("interest_rate")).doubleValue(), 0.01);

        // Test condition false case
        inputData.put("creditScore", 650);
        result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("DECLINED", result.getOutputData().get("approval_status"));
        assertEquals(5.5, ((Number) result.getOutputData().get("interest_rate")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Test complex rules with multiple when/then/else blocks")
    void testComplexRulesWithMultipleConditionals() {
        String yaml = """
                name: "Complex Rules Test"
                description: "Test multiple conditional blocks"
                
                inputs:
                  - creditScore
                  - annualIncome
                  - employmentYears
                
                rules:
                  - name: "Credit Assessment"
                    when:
                      - creditScore >= 750
                    then:
                      - set credit_tier to "EXCELLENT"
                      - set base_rate to 3.0
                    else:
                      - set credit_tier to "GOOD"
                      - set base_rate to 4.0
                  
                  - name: "Income Assessment"
                    when:
                      - annualIncome >= 100000
                    then:
                      - set income_tier to "HIGH"
                      - set income_adjustment to -0.5
                    else:
                      - set income_tier to "STANDARD"
                      - set income_adjustment to 0.0

                  - name: "Employment Assessment"
                    when:
                      - employmentYears >= 5
                    then:
                      - set employment_tier to "STABLE"
                      - set employment_adjustment to -0.25
                    else:
                      - set employment_tier to "NEW"
                      - set employment_adjustment to 0.25
                  
                  - name: "Final Decision"
                    then:
                      - calculate total_adjustment as income_adjustment + employment_adjustment
                      - calculate final_rate as base_rate + total_adjustment
                      - set decision to "APPROVED"
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 780);
        inputData.put("annualIncome", 120000);
        inputData.put("employmentYears", 7);
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify all conditional outcomes
        assertEquals("EXCELLENT", result.getOutputData().get("credit_tier"));
        assertEquals("HIGH", result.getOutputData().get("income_tier"));
        assertEquals("STABLE", result.getOutputData().get("employment_tier"));
        assertEquals("APPROVED", result.getOutputData().get("decision"));
        
        // Verify rate calculation: 3.0 + (-0.5 + -0.25) = 2.25
        assertEquals(-0.75, ((Number) result.getOutputData().get("total_adjustment")).doubleValue(), 0.01);
        assertEquals(2.25, ((Number) result.getOutputData().get("final_rate")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Test nested conditional logic with rules")
    void testNestedConditionalLogic() {
        String yaml = """
                name: "Nested Conditional Test"
                description: "Test nested conditional logic using multiple rules"

                inputs:
                  - age
                  - income
                  - hasJob

                rules:
                  - name: "Age Check"
                    when:
                      - age >= 18
                    then:
                      - set age_category to "ADULT"
                    else:
                      - set age_category to "MINOR"
                      - set final_status to "INELIGIBLE"

                  - name: "Income Check - High"
                    when:
                      - age_category == "ADULT"
                      - income >= 50000
                    then:
                      - set income_category to "HIGH"

                  - name: "Income Check - Low"
                    when:
                      - age_category == "ADULT"
                      - income < 50000
                    then:
                      - set income_category to "LOW"
                      - set final_status to "DECLINED"

                  - name: "Job Check - Approved"
                    when:
                      - age_category == "ADULT"
                      - income_category == "HIGH"
                      - hasJob == true
                    then:
                      - set final_status to "APPROVED"

                  - name: "Job Check - Review"
                    when:
                      - age_category == "ADULT"
                      - income_category == "HIGH"
                      - hasJob == false
                    then:
                      - set final_status to "REVIEW"
                """;

        // Test adult with high income and job
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("age", 30);
        inputData.put("income", 75000);
        inputData.put("hasJob", true);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("ADULT", result.getOutputData().get("age_category"));
        assertEquals("HIGH", result.getOutputData().get("income_category"));
        assertEquals("APPROVED", result.getOutputData().get("final_status"));
    }

    @Test
    @DisplayName("Test logical operators in conditions")
    void testLogicalOperatorsInConditions() {
        String yaml = """
                name: "Logical Operators Test"
                description: "Test AND, OR, NOT operators in conditions"
                
                inputs:
                  - creditScore
                  - income
                  - hasCollateral
                  - accountStatus
                
                rules:
                  - name: "AND Condition Test"
                    when:
                      - creditScore >= 700 AND income >= 50000
                    then:
                      - set and_result to "PASS"
                    else:
                      - set and_result to "FAIL"
                  
                  - name: "OR Condition Test"
                    when:
                      - creditScore >= 750 OR hasCollateral == true
                    then:
                      - set or_result to "PASS"
                    else:
                      - set or_result to "FAIL"
                  
                  - name: "NOT Condition Test"
                    when:
                      - NOT (accountStatus == "SUSPENDED")
                    then:
                      - set not_result to "PASS"
                    else:
                      - set not_result to "FAIL"
                  
                  - name: "Complex Logical Test"
                    when:
                      - (creditScore >= 650 AND income >= 40000) OR hasCollateral == true
                    then:
                      - set complex_result to "PASS"
                    else:
                      - set complex_result to "FAIL"
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);
        inputData.put("income", 60000);
        inputData.put("hasCollateral", false);
        inputData.put("accountStatus", "ACTIVE");
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify logical operator results
        assertEquals("PASS", result.getOutputData().get("and_result")); // 720 >= 700 AND 60000 >= 50000
        assertEquals("FAIL", result.getOutputData().get("or_result"));  // 720 >= 750 is false AND hasCollateral is false
        assertEquals("PASS", result.getOutputData().get("not_result")); // NOT ("ACTIVE" == "SUSPENDED")
        assertEquals("PASS", result.getOutputData().get("complex_result")); // (720 >= 650 AND 60000 >= 40000) is true
    }

    @Test
    @DisplayName("Test conditional actions within rules")
    void testConditionalActionsWithinRules() {
        String yaml = """
                name: "Conditional Actions Test"
                description: "Test conditional logic within action blocks using rules"

                inputs:
                  - transactionAmount
                  - accountType
                  - customerTier

                rules:
                  - name: "Calculate Base Fee"
                    then:
                      - calculate base_fee as transactionAmount * 0.01

                  - name: "Set Fee Multiplier"
                    when:
                      - accountType == "PREMIUM"
                    then:
                      - set fee_multiplier to 0.5
                    else:
                      - set fee_multiplier to 1.0

                  - name: "Set Fee Discount"
                    when:
                      - customerTier == "VIP"
                    then:
                      - set fee_discount to 5.0
                    else:
                      - set fee_discount to 0.0

                  - name: "Calculate Final Fee"
                    then:
                      - calculate temp_fee as base_fee * fee_multiplier
                      - calculate final_fee as temp_fee - fee_discount

                  - name: "Enforce Minimum Fee"
                    when:
                      - final_fee < 0
                    then:
                      - set final_fee to 0.0
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("transactionAmount", 1000);
        inputData.put("accountType", "PREMIUM");
        inputData.put("customerTier", "VIP");

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());

        // Verify calculations: base_fee = 1000 * 0.01 = 10.0
        // fee_multiplier = 0.5 (PREMIUM), fee_discount = 5.0 (VIP)
        // final_fee = (10.0 * 0.5) - 5.0 = 0.0 (minimum enforced)
        assertEquals(10.0, ((Number) result.getOutputData().get("base_fee")).doubleValue(), 0.01);
        assertEquals(0.5, ((Number) result.getOutputData().get("fee_multiplier")).doubleValue(), 0.01);
        assertEquals(5.0, ((Number) result.getOutputData().get("fee_discount")).doubleValue(), 0.01);
        assertEquals(0.0, ((Number) result.getOutputData().get("final_fee")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Test when condition without else block")
    void testWhenWithoutElse() {
        String yaml = """
                name: "When Without Else Test"
                description: "Test when condition without else block"

                inputs:
                  - score

                when:
                  - score >= 80
                then:
                  - set grade to "A"
                  - set passed to true
                """;

        // Test condition true case
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("score", 85);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("A", result.getOutputData().get("grade"));
        assertEquals(true, result.getOutputData().get("passed"));

        // Test condition false case - no actions should execute
        inputData.put("score", 75);
        result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNull(result.getOutputData().get("grade"));
        assertNull(result.getOutputData().get("passed"));
    }

    @Test
    @DisplayName("Test multiple conditions with AND/OR combinations")
    void testMultipleConditionsWithLogicalOperators() {
        String yaml = """
                name: "Multiple Conditions Test"
                description: "Test complex condition combinations"

                inputs:
                  - age
                  - income
                  - creditScore
                  - hasJob
                  - hasCollateral

                rules:
                  - name: "Loan Eligibility"
                    when:
                      - age >= 18 AND age <= 65
                      - income >= 30000 OR hasCollateral == true
                      - creditScore >= 600
                      - hasJob == true
                    then:
                      - set eligible to true
                      - set loan_type to "STANDARD"
                    else:
                      - set eligible to false
                      - set loan_type to "NONE"

                  - name: "Premium Eligibility"
                    when:
                      - eligible == true
                      - creditScore >= 750 AND income >= 75000
                    then:
                      - set loan_type to "PREMIUM"
                      - set interest_rate to 2.5

                  - name: "Standard Interest Rate"
                    when:
                      - eligible == true
                      - loan_type == "STANDARD"
                    then:
                      - set interest_rate to 4.5
                """;

        // Test premium eligibility case
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("age", 35);
        inputData.put("income", 80000);
        inputData.put("creditScore", 780);
        inputData.put("hasJob", true);
        inputData.put("hasCollateral", false);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(true, result.getOutputData().get("eligible"));
        assertEquals("PREMIUM", result.getOutputData().get("loan_type"));
        assertEquals(2.5, ((Number) result.getOutputData().get("interest_rate")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Test conditional expressions using rules")
    void testTernaryLikeConditionals() {
        String yaml = """
                name: "Conditional Expressions Test"
                description: "Test conditional expressions using rules instead of ternary"

                inputs:
                  - balance
                  - accountType
                  - transactionAmount

                rules:
                  - name: "Set Fee Rate"
                    when:
                      - accountType == "PREMIUM"
                    then:
                      - set fee_rate to 0.001
                    else:
                      - set fee_rate to 0.005

                  - name: "Set Overdraft Limit"
                    when:
                      - balance > 1000
                    then:
                      - set overdraft_limit to 500
                    else:
                      - set overdraft_limit to 100

                  - name: "Calculate Transaction Fee"
                    then:
                      - calculate transaction_fee as transactionAmount * fee_rate

                  - name: "Check Overdraft Capability"
                    when:
                      - balance + overdraft_limit >= transactionAmount
                    then:
                      - set can_overdraft to true
                    else:
                      - set can_overdraft to false
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("balance", 1500);
        inputData.put("accountType", "PREMIUM");
        inputData.put("transactionAmount", 1800);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());

        // Verify conditional calculations
        assertEquals(0.001, ((Number) result.getOutputData().get("fee_rate")).doubleValue(), 0.0001);
        assertEquals(500, ((Number) result.getOutputData().get("overdraft_limit")).doubleValue(), 0.01);
        assertEquals(1.8, ((Number) result.getOutputData().get("transaction_fee")).doubleValue(), 0.01);
        assertEquals(true, result.getOutputData().get("can_overdraft")); // 1500 + 500 >= 1800
    }

    @Test
    @DisplayName("Test conditional logic with function calls")
    void testConditionalLogicWithFunctionCalls() {
        String yaml = """
                name: "Conditional with Functions Test"
                description: "Test conditional logic using function calls"

                inputs:
                  - userData

                rules:
                  - name: "User Validation"
                    when:
                      - json_exists(userData, "email")
                      - json_exists(userData, "age")
                    then:
                      - run user_age as json_get(userData, "age")
                      - run user_email as json_get(userData, "email")
                      - set validation_passed to true
                    else:
                      - set validation_passed to false
                      - set error_message to "Missing required fields"

                  - name: "Senior Category Assignment"
                    when:
                      - validation_passed == true
                      - user_age >= 65
                    then:
                      - set category to "SENIOR"
                      - set eligible_for_services to true

                  - name: "Adult Category Assignment"
                    when:
                      - validation_passed == true
                      - user_age >= 18
                      - user_age < 65
                    then:
                      - set category to "ADULT"
                      - set eligible_for_services to true

                  - name: "Minor Category Assignment"
                    when:
                      - validation_passed == true
                      - user_age < 18
                    then:
                      - set category to "MINOR"
                      - set eligible_for_services to false
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", "john@example.com");
        userData.put("age", 45);
        userData.put("name", "John Doe");
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());

        // Verify function-based conditional logic
        assertEquals(true, result.getOutputData().get("validation_passed"));
        assertEquals(45, ((Number) result.getOutputData().get("user_age")).intValue());
        assertEquals("john@example.com", result.getOutputData().get("user_email"));
        assertEquals("ADULT", result.getOutputData().get("category"));
        assertEquals(true, result.getOutputData().get("eligible_for_services"));
    }
}
