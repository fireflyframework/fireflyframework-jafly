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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite to validate all DSL features according to the YAML DSL Reference specification.
 * This test ensures that all operators, functions, and syntax patterns defined in docs/yaml-dsl-reference.md
 * are properly implemented and working.
 */
@DisplayName("Comprehensive DSL Feature Tests")
class ComprehensiveDSLTest {

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

    @Nested
    @DisplayName("Numeric Comparison Operators")
    class NumericComparisonTests {

        @Test
        @DisplayName("Test greater_than operator")
        void testGreaterThan() {
            String rule = """
                name: "Greater Than Test"
                description: "Test greater_than operator"
                inputs:
                  - creditScore
                when:
                  - creditScore greater_than 700
                then:
                  - set result to "PASS"
                else:
                  - set result to "FAIL"
                output:
                  result: text
                """;

            Map<String, Object> inputData = Map.of("creditScore", 750);
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("PASS", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("Test at_least operator (alias for >=)")
        void testAtLeast() {
            String rule = """
                name: "At Least Test"
                description: "Test at_least operator"
                inputs:
                  - income
                when:
                  - income at_least 50000
                then:
                  - set eligible to true
                else:
                  - set eligible to false
                output:
                  eligible: boolean
                """;

            Map<String, Object> inputData = Map.of("income", 50000);
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("eligible"));
        }

        @Test
        @DisplayName("Test between operator")
        void testBetween() {
            String rule = """
                name: "Between Test"
                description: "Test between operator"
                inputs:
                  - age
                when:
                  - age between 18 and 65
                then:
                  - set eligible to true
                else:
                  - set eligible to false
                output:
                  eligible: boolean
                """;

            Map<String, Object> inputData = Map.of("age", 30);
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("eligible"));
        }
    }

    @Nested
    @DisplayName("String Comparison Operators")
    class StringComparisonTests {

        @Test
        @DisplayName("Test contains operator")
        void testContains() {
            String rule = """
                name: "Contains Test"
                description: "Test contains operator"
                inputs:
                  - companyName
                when:
                  - companyName contains "CORP"
                then:
                  - set is_corporation to true
                else:
                  - set is_corporation to false
                output:
                  is_corporation: boolean
                """;

            Map<String, Object> inputData = Map.of("companyName", "ACME CORP");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("is_corporation"));
        }

        @Test
        @DisplayName("Test starts_with operator")
        void testStartsWith() {
            String rule = """
                name: "Starts With Test"
                description: "Test starts_with operator"
                inputs:
                  - accountNumber
                when:
                  - accountNumber starts_with "CHK"
                then:
                  - set account_type to "CHECKING"
                else:
                  - set account_type to "OTHER"
                output:
                  account_type: text
                """;

            Map<String, Object> inputData = Map.of("accountNumber", "CHK123456789");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("CHECKING", result.getOutputData().get("account_type"));
        }

        @Test
        @DisplayName("Test matches operator (regex)")
        void testMatches() {
            String rule = """
                name: "Matches Test"
                description: "Test matches operator with regex"
                inputs:
                  - phoneNumber
                when:
                  - phoneNumber matches "^\\\\+1"
                then:
                  - set is_us_number to true
                else:
                  - set is_us_number to false
                output:
                  is_us_number: boolean
                """;

            Map<String, Object> inputData = Map.of("phoneNumber", "+1234567890");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("is_us_number"));
        }
    }

    @Nested
    @DisplayName("List/Array Comparison Operators")
    class ListComparisonTests {

        @Test
        @DisplayName("Test in_list operator")
        void testInList() {
            String rule = """
                name: "In List Test"
                description: "Test in_list operator"
                inputs:
                  - riskLevel
                when:
                  - riskLevel in_list ["HIGH", "CRITICAL"]
                then:
                  - set requires_review to true
                else:
                  - set requires_review to false
                output:
                  requires_review: boolean
                """;

            Map<String, Object> inputData = Map.of("riskLevel", "HIGH");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("requires_review"));
        }
    }

    @Nested
    @DisplayName("Validation Operators")
    class ValidationOperatorTests {

        @Test
        @DisplayName("Test is_empty operator")
        void testIsEmpty() {
            String rule = """
                name: "Is Empty Test"
                description: "Test is_empty operator"
                inputs:
                  - comments
                when:
                  - comments is_empty
                then:
                  - set has_comments to false
                else:
                  - set has_comments to true
                output:
                  has_comments: boolean
                """;

            Map<String, Object> inputData = Map.of("comments", "");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(false, result.getOutputData().get("has_comments"));
        }

        @Test
        @DisplayName("Test is_numeric operator")
        void testIsNumeric() {
            String rule = """
                name: "Is Numeric Test"
                description: "Test is_numeric operator"
                inputs:
                  - inputValue
                when:
                  - inputValue is_numeric
                then:
                  - set is_valid_number to true
                else:
                  - set is_valid_number to false
                output:
                  is_valid_number: boolean
                """;

            Map<String, Object> inputData = Map.of("inputValue", "123.45");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("is_valid_number"));
        }

        @Test
        @DisplayName("Test is_email operator")
        void testIsEmail() {
            String rule = """
                name: "Is Email Test"
                description: "Test is_email operator"
                inputs:
                  - contactEmail
                when:
                  - contactEmail is_email
                then:
                  - set valid_email to true
                else:
                  - set valid_email to false
                output:
                  valid_email: boolean
                """;

            Map<String, Object> inputData = Map.of("contactEmail", "user@example.com");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("valid_email"));
        }
    }

    @Nested
    @DisplayName("Financial and Business Operators")
    class FinancialOperatorTests {

        @Test
        @DisplayName("Test is_positive operator")
        void testIsPositive() {
            String rule = """
                name: "Is Positive Test"
                description: "Test is_positive operator"
                inputs:
                  - accountBalance
                when:
                  - accountBalance is_positive
                then:
                  - set account_status to "ACTIVE"
                else:
                  - set account_status to "OVERDRAWN"
                output:
                  account_status: text
                """;

            Map<String, Object> inputData = Map.of("accountBalance", 1500.50);
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("ACTIVE", result.getOutputData().get("account_status"));
        }

        @Test
        @DisplayName("Test is_credit_score operator")
        void testIsCreditScore() {
            String rule = """
                name: "Is Credit Score Test"
                description: "Test is_credit_score operator"
                inputs:
                  - creditScore
                when:
                  - creditScore is_credit_score
                then:
                  - set valid_score to true
                else:
                  - set valid_score to false
                output:
                  valid_score: boolean
                """;

            Map<String, Object> inputData = Map.of("creditScore", 750);
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("valid_score"));
        }

        @Test
        @DisplayName("Test age_at_least operator")
        void testAgeAtLeast() {
            String rule = """
                name: "Age At Least Test"
                description: "Test age_at_least operator"
                inputs:
                  - birthDate
                when:
                  - birthDate age_at_least 18
                then:
                  - set is_adult to true
                else:
                  - set is_adult to false
                output:
                  is_adult: boolean
                """;

            Map<String, Object> inputData = Map.of("birthDate", "1990-01-01");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("is_adult"));
        }
    }

    @Nested
    @DisplayName("Arithmetic Operations and Functions")
    class ArithmeticTests {

        @Test
        @DisplayName("Test basic arithmetic calculation")
        void testBasicArithmetic() {
            String rule = """
                name: "Basic Arithmetic Test"
                description: "Test basic arithmetic operations"
                inputs:
                  - principal
                  - rate
                  - time
                when:
                  - principal greater_than 0
                then:
                  - calculate simple_interest as principal * rate * time
                  - calculate total_amount as principal + simple_interest
                output:
                  simple_interest: number
                  total_amount: number
                """;

            Map<String, Object> inputData = Map.of(
                "principal", 10000,
                "rate", 0.05,
                "time", 2
            );
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(1000.0, ((Number) result.getOutputData().get("simple_interest")).doubleValue(), 0.01);
            assertEquals(11000.0, ((Number) result.getOutputData().get("total_amount")).doubleValue(), 0.01);
        }

        @Test
        @DisplayName("Test mathematical functions")
        void testMathematicalFunctions() {
            String rule = """
                name: "Mathematical Functions Test"
                description: "Test built-in mathematical functions"
                inputs:
                  - value1
                  - value2
                  - value3
                when:
                  - value1 greater_than 0
                then:
                  - run maximum as max(value1, value2, value3)
                  - run minimum as min(value1, value2, value3)
                  - run absolute as abs(value2)
                  - run rounded as round(value3)
                output:
                  maximum: number
                  minimum: number
                  absolute: number
                  rounded: number
                """;

            Map<String, Object> inputData = Map.of(
                "value1", 10,
                "value2", -5,
                "value3", 7.8
            );
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(10.0, ((Number) result.getOutputData().get("maximum")).doubleValue(), 0.01);
            assertEquals(-5.0, ((Number) result.getOutputData().get("minimum")).doubleValue(), 0.01);
            assertEquals(5.0, ((Number) result.getOutputData().get("absolute")).doubleValue(), 0.01);
            assertEquals(8.0, ((Number) result.getOutputData().get("rounded")).doubleValue(), 0.01);
        }
    }

    @Nested
    @DisplayName("Financial Functions")
    class FinancialFunctionTests {

        @Test
        @DisplayName("Test loan payment calculation")
        void testLoanPaymentCalculation() {
            String rule = """
                name: "Loan Payment Test"
                description: "Test calculate_loan_payment function"
                inputs:
                  - loanAmount
                  - interestRate
                  - termMonths
                when:
                  - loanAmount greater_than 0
                then:
                  - call calculate_loan_payment with [loanAmount, interestRate, termMonths, "monthly_payment"]
                output:
                  monthly_payment: number
                """;

            Map<String, Object> inputData = Map.of(
                "loanAmount", 200000,
                "interestRate", 0.045,
                "termMonths", 360
            );
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertNotNull(result.getOutputData().get("monthly_payment"));
            assertTrue(((Number) result.getOutputData().get("monthly_payment")).doubleValue() > 0);
        }

        @Test
        @DisplayName("Test debt-to-income ratio calculation")
        void testDebtToIncomeRatio() {
            String rule = """
                name: "DTI Ratio Test"
                description: "Test debt_to_income_ratio function"
                inputs:
                  - monthlyDebt
                  - monthlyIncome
                when:
                  - monthlyIncome greater_than 0
                then:
                  - run dti_ratio as debt_to_income_ratio(monthlyDebt, monthlyIncome)
                  - set dti_acceptable to (dti_ratio less_than_or_equal 0.43)
                output:
                  dti_ratio: number
                  dti_acceptable: boolean
                """;

            Map<String, Object> inputData = Map.of(
                "monthlyDebt", 2000,
                "monthlyIncome", 6000
            );
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(0.333, ((Number) result.getOutputData().get("dti_ratio")).doubleValue(), 0.01);
            assertEquals(true, result.getOutputData().get("dti_acceptable"));
        }
    }

    @Nested
    @DisplayName("Logical Operators and Complex Conditions")
    class LogicalOperatorTests {

        @Test
        @DisplayName("Test AND operator")
        void testAndOperator() {
            String rule = """
                name: "AND Operator Test"
                description: "Test AND logical operator"
                inputs:
                  - creditScore
                  - income
                when:
                  - creditScore at_least 650 AND income greater_than 40000
                then:
                  - set pre_approved to true
                else:
                  - set pre_approved to false
                output:
                  pre_approved: boolean
                """;

            Map<String, Object> inputData = Map.of(
                "creditScore", 700,
                "income", 50000
            );
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("pre_approved"));
        }

        @Test
        @DisplayName("Test OR operator")
        void testOrOperator() {
            String rule = """
                name: "OR Operator Test"
                description: "Test OR logical operator"
                inputs:
                  - creditScore
                  - income
                when:
                  - creditScore at_least 750 OR income greater_than 100000
                then:
                  - set premium_eligible to true
                else:
                  - set premium_eligible to false
                output:
                  premium_eligible: boolean
                """;

            Map<String, Object> inputData = Map.of(
                "creditScore", 680,
                "income", 120000
            );
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("premium_eligible"));
        }

        @Test
        @DisplayName("Test NOT operator")
        void testNotOperator() {
            String rule = """
                name: "NOT Operator Test"
                description: "Test NOT logical operator"
                inputs:
                  - accountStatus
                when:
                  - NOT (accountStatus equals "SUSPENDED")
                then:
                  - set account_active to true
                else:
                  - set account_active to false
                output:
                  account_active: boolean
                """;

            Map<String, Object> inputData = Map.of("accountStatus", "ACTIVE");
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("account_active"));
        }
    }
}
