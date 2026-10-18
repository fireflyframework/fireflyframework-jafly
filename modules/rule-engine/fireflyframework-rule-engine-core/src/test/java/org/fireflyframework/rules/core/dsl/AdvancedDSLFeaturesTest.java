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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test advanced DSL features like complex syntax, multiple rules, and mixed syntax
 */
@DisplayName("Advanced DSL Features Tests")
public class AdvancedDSLFeaturesTest {

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
    @DisplayName("Complex Conditions Syntax")
    class ComplexConditionsSyntaxTests {

        @Test
        @DisplayName("Test complex conditions block with structured syntax")
        void testComplexConditionsBlock() {
            String yamlRule = """
                name: "Complex Conditions Test"
                description: "Test structured conditions syntax"
                
                inputs:
                  - creditScore
                  - annualIncome
                
                conditions:
                  if:
                    and:
                      - compare:
                          left: creditScore
                          operator: "at_least"
                          right: 650
                      - compare:
                          left: annualIncome
                          operator: "greater_than"
                          right: 50000
                  then:
                    actions:
                      - set approval_status to "APPROVED"
                  else:
                    actions:
                      - set approval_status to "REJECTED"
                
                output:
                  approval_status: text
                """;

            Map<String, Object> inputData = Map.of(
                "creditScore", 700,
                "annualIncome", 60000
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
            
            assertTrue(result.isSuccess());
            assertEquals("APPROVED", result.getOutputData().get("approval_status"));
        }
    }

    @Nested
    @DisplayName("Multiple Rules Syntax")
    class MultipleRulesSyntaxTests {

        @Test
        @DisplayName("Test multiple sub-rules with sequential execution")
        void testMultipleRules() {
            String yamlRule = """
                name: "Multiple Rules Test"
                description: "Test multiple sub-rules execution"
                
                inputs:
                  - creditScore
                  - annualIncome
                
                rules:
                  - name: "Initial Check"
                    when:
                      - creditScore at_least 600
                    then:
                      - set initial_eligible to true
                    else:
                      - set initial_eligible to false
                      
                  - name: "Final Decision"
                    when:
                      - initial_eligible equals true
                      - annualIncome greater_than 40000
                    then:
                      - set final_approval to "APPROVED"
                    else:
                      - set final_approval to "REJECTED"
                
                output:
                  initial_eligible: boolean
                  final_approval: text
                """;

            Map<String, Object> inputData = Map.of(
                "creditScore", 650,
                "annualIncome", 50000
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
            
            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("initial_eligible"));
            assertEquals("APPROVED", result.getOutputData().get("final_approval"));
        }
    }

    @Nested
    @DisplayName("Mixed Simple and Complex Syntax")
    class MixedSyntaxTests {

        @Test
        @DisplayName("Test mixing simple and complex syntax in sub-rules")
        void testMixedSimpleAndComplexSyntax() {
            String yamlRule = """
                name: "Mixed Syntax Test"
                description: "Test mixing simple and complex syntax"
                
                inputs:
                  - creditScore
                  - annualIncome
                  - accountAge
                
                rules:
                  - name: "Simple Initial Check"
                    when:
                      - creditScore at_least 600
                    then:
                      - set initial_eligible to true
                    else:
                      - set initial_eligible to false
                      
                  - name: "Complex Final Decision"
                    conditions:
                      if:
                        and:
                          - compare:
                              left: initial_eligible
                              operator: "equals"
                              right: true
                          - or:
                              - compare:
                                  left: annualIncome
                                  operator: "greater_than"
                                  right: 75000
                              - compare:
                                  left: accountAge
                                  operator: "at_least"
                                  right: 24
                      then:
                        actions:
                          - set final_decision to "PREMIUM_APPROVED"
                      else:
                        actions:
                          - set final_decision to "STANDARD_APPROVED"
                
                output:
                  initial_eligible: boolean
                  final_decision: text
                """;

            Map<String, Object> inputData = Map.of(
                "creditScore", 650,
                "annualIncome", 60000,
                "accountAge", 30
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
            
            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputData().get("initial_eligible"));
            assertEquals("PREMIUM_APPROVED", result.getOutputData().get("final_decision"));
        }
    }

    @Nested
    @DisplayName("Call Function Syntax")
    class CallFunctionSyntaxTests {

        @Test
        @DisplayName("Test call function with parameters")
        void testCallFunctionSyntax() {
            String yamlRule = """
                name: "Call Function Test"
                description: "Test call function syntax"
                
                inputs:
                  - amount
                  - rate
                  - term
                
                when:
                  - amount greater_than 0
                
                then:
                  - call calculate_loan_payment with [amount, rate, term, "monthly_payment"]
                  - set status to "CALCULATED"
                
                output:
                  monthly_payment: number
                  status: text
                """;

            Map<String, Object> inputData = Map.of(
                "amount", 100000,
                "rate", 0.05,
                "term", 30
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
            
            assertTrue(result.isSuccess());
            assertNotNull(result.getOutputData().get("monthly_payment"));
            assertEquals("CALCULATED", result.getOutputData().get("status"));
        }
    }

    @Nested
    @DisplayName("Metadata and Circuit Breaker Support")
    class MetadataAndCircuitBreakerTests {

        @Test
        @DisplayName("Test metadata and circuit breaker configuration")
        void testMetadataAndCircuitBreaker() {
            String yamlRule = """
                name: "Metadata Test"
                description: "Test metadata and circuit breaker support"
                version: "1.2.0"

                metadata:
                  tags: ["test", "metadata"]
                  author: "Test Team"
                  category: "Testing"
                  riskLevel: "LOW"

                circuit_breaker:
                  enabled: true
                  failure_threshold: 5
                  timeout_duration: "30s"
                  recovery_timeout: "60s"

                inputs:
                  - testValue

                when:
                  - testValue greater_than 0

                then:
                  - set result to "SUCCESS"

                output:
                  result: text
                """;

            Map<String, Object> inputData = Map.of("testValue", 10);

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("SUCCESS", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("Test metadata validation")
        void testMetadataValidation() {
            String yamlRule = """
                name: "Metadata Validation Test"
                description: "Test metadata validation features"
                version: "1.0.0"

                metadata:
                  tags: ["validation", "test"]
                  author: "Test Team"
                  category: "Testing"
                  priority: 1
                  riskLevel: "LOW"
                  businessOwner: "Test Department"

                inputs:
                  - testValue

                when:
                  - testValue greater_than 0

                then:
                  - set result to "VALIDATED"

                output:
                  result: text
                """;

            Map<String, Object> inputData = Map.of("testValue", 5);

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("VALIDATED", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("Test metadata validation with invalid data")
        void testMetadataValidationErrors() {
            String yamlRule = """
                name: "Invalid Metadata Test"
                description: "Test metadata validation with invalid data"

                metadata:
                  tags: "invalid_tags_format"
                  author: ""
                  priority: "not_a_number"
                  riskLevel: "INVALID_LEVEL"

                inputs:
                  - testValue

                when:
                  - testValue greater_than 0

                then:
                  - set result to "PROCESSED"

                output:
                  result: text
                """;

            // This should still parse and execute, but validation should catch the metadata issues
            Map<String, Object> inputData = Map.of("testValue", 5);
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("PROCESSED", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("Test dateadd function")
        void testDateAddFunction() {
            String yamlRule = """
                name: "Date Add Test"
                description: "Test dateadd function with various units"

                inputs:
                  - startDate

                when:
                  - startDate is_not_null

                then:
                  - run futureDate as dateadd(startDate, 30, "days")
                  - run futureMonth as dateadd(startDate, 1, "months")
                  - run futureYear as dateadd(startDate, 1, "years")
                  - set result to "CALCULATED"

                output:
                  futureDate: text
                  futureMonth: text
                  futureYear: text
                  result: text
                """;

            Map<String, Object> inputData = Map.of("startDate", "2024-01-01");

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("2024-01-31", result.getOutputData().get("futureDate"));
            assertEquals("2024-02-01", result.getOutputData().get("futureMonth"));
            assertEquals("2025-01-01", result.getOutputData().get("futureYear"));
            assertEquals("CALCULATED", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("Test datediff function")
        void testDateDiffFunction() {
            String yamlRule = """
                name: "Date Diff Test"
                description: "Test datediff function with various units"

                inputs:
                  - startDate
                  - endDate

                when:
                  - startDate is_not_null
                  - endDate is_not_null

                then:
                  - run daysDiff as datediff(startDate, endDate, "days")
                  - run weeksDiff as datediff(startDate, endDate, "weeks")
                  - run monthsDiff as datediff(startDate, endDate, "months")
                  - set result to "CALCULATED"

                output:
                  daysDiff: number
                  weeksDiff: number
                  monthsDiff: number
                  result: text
                """;

            Map<String, Object> inputData = Map.of(
                "startDate", "2024-01-01",
                "endDate", "2024-02-01"
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(31, ((Number) result.getOutputData().get("daysDiff")).intValue());
            assertEquals(4, ((Number) result.getOutputData().get("weeksDiff")).intValue());
            assertEquals(1, ((Number) result.getOutputData().get("monthsDiff")).intValue());
            assertEquals("CALCULATED", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("Test date functions with different formats")
        void testDateFunctionsWithDifferentFormats() {
            String yamlRule = """
                name: "Date Format Test"
                description: "Test date functions with different date formats"

                inputs:
                  - usDate
                  - isoDate

                when:
                  - usDate is_not_null
                  - isoDate is_not_null

                then:
                  - run daysBetween as datediff(usDate, isoDate, "days")
                  - run futureFromUs as dateadd(usDate, 7, "days")
                  - set result to "SUCCESS"

                output:
                  daysBetween: number
                  futureFromUs: text
                  result: text
                """;

            Map<String, Object> inputData = Map.of(
                "usDate", "01/15/2024",
                "isoDate", "2024-01-20"
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals(5, ((Number) result.getOutputData().get("daysBetween")).intValue());
            assertEquals("2024-01-22", result.getOutputData().get("futureFromUs"));
            assertEquals("SUCCESS", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("dateadd succeeds for valid dates")
        void testDateAddValid() {
            String yamlRule = """
                name: "Date Add Happy Path"
                inputs:
                  - validDate

                then:
                  - run validResult as dateadd(validDate, 1, "days")
                  - set result to "COMPLETED"

                output:
                  validResult: text
                  result: text
                """;

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(
                    yamlRule, Map.of("validDate", "2024-01-01"));

            assertTrue(result.isSuccess());
            assertEquals("2024-01-02", result.getOutputData().get("validResult"));
            assertEquals("COMPLETED", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("dateadd surfaces unparseable dates as a clean rule failure")
        void testDateAddInvalidDateFailsLoud() {
            String yamlRule = """
                name: "Date Add Invalid"
                inputs:
                  - badDate

                then:
                  - run result as dateadd(badDate, 1, "days")

                output:
                  result: text
                """;

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(
                    yamlRule, Map.of("badDate", "not-a-date"));

            assertFalse(result.isSuccess());
            assertThat(result.getError()).contains("dateadd");
        }

        @Test
        @DisplayName("dateadd surfaces unknown units as a clean rule failure")
        void testDateAddInvalidUnitFailsLoud() {
            String yamlRule = """
                name: "Date Add Invalid Unit"
                inputs:
                  - validDate

                then:
                  - run result as dateadd(validDate, 1, "invalid_unit")

                output:
                  result: text
                """;

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(
                    yamlRule, Map.of("validDate", "2024-01-01"));

            assertFalse(result.isSuccess());
            assertThat(result.getError()).contains("dateadd");
            assertThat(result.getError()).contains("invalid_unit");
        }

        @Test
        @DisplayName("Test operator aliases recognition")
        void testOperatorAliases() {
            String yamlRule = """
                name: "Operator Aliases Test"
                description: "Test various operator aliases"

                inputs:
                  - creditScore
                  - income
                  - status

                when:
                  - creditScore >= 650                    # Symbolic alias
                  - income greater_than_or_equal 50000    # Long form alias
                  - status == "ACTIVE"                    # Symbolic equality
                  - creditScore at_least 600              # Natural language alias

                then:
                  - set result to "APPROVED"

                output:
                  result: text
                """;

            Map<String, Object> inputData = Map.of(
                "creditScore", 700,
                "income", 60000,
                "status", "ACTIVE"
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("APPROVED", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("Test list operator aliases (in/not_in)")
        void testListOperatorAliases() {
            String yamlRule = """
                name: "List Operator Aliases Test"
                description: "Test in and not_in aliases for list operations"

                inputs:
                  - riskLevel
                  - status
                  - category

                when:
                  - riskLevel in ["HIGH", "CRITICAL"]        # Short alias
                  - status not_in ["CLOSED", "SUSPENDED"]    # Short alias
                  - category in_list ["PREMIUM", "GOLD"]     # Full form

                then:
                  - set result to "QUALIFIED"

                output:
                  result: text
                """;

            Map<String, Object> inputData = Map.of(
                "riskLevel", "HIGH",
                "status", "ACTIVE",
                "category", "PREMIUM"
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("QUALIFIED", result.getOutputData().get("result"));
        }

        @Test
        @DisplayName("Test complex nested expressions with multiple parentheses")
        void testComplexNestedExpressions() {
            String yamlRule = """
                name: "Complex Nested Expressions Test"
                description: "Test complex nested expressions with multiple parentheses levels"

                inputs:
                  - creditScore
                  - income
                  - debt
                  - age
                  - hasGuarantor

                when:
                  - ((creditScore >= 650 AND income > 50000) OR (creditScore >= 700 AND income > 30000)) AND (age >= 18 AND age <= 65)
                  - (debt / income) < 0.4 OR hasGuarantor == true

                then:
                  - calculate complex_score as ((creditScore * 0.4) + (income / 1000 * 0.3) + ((100 - age) * 0.3))
                  - calculate debt_ratio as (debt / income) * 100
                  - set approval_status to "APPROVED"

                output:
                  complex_score: number
                  debt_ratio: number
                  approval_status: text
                """;

            Map<String, Object> inputData = Map.of(
                "creditScore", 720,
                "income", 75000,
                "debt", 25000,
                "age", 35,
                "hasGuarantor", false
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertEquals("APPROVED", result.getOutputData().get("approval_status"));

            // Verify complex calculations
            double expectedComplexScore = (720 * 0.4) + (75000 / 1000 * 0.3) + ((100 - 35) * 0.3);
            double actualComplexScore = ((Number) result.getOutputData().get("complex_score")).doubleValue();
            assertEquals(expectedComplexScore, actualComplexScore, 0.01);

            double expectedDebtRatio = (25000.0 / 75000.0) * 100;
            double actualDebtRatio = ((Number) result.getOutputData().get("debt_ratio")).doubleValue();
            assertEquals(expectedDebtRatio, actualDebtRatio, 0.01);
        }

        @Test
        @DisplayName("Test advanced expression parsing with nested function calls")
        void testAdvancedExpressionParsing() {
            String yamlRule = """
                name: "Advanced Expression Parsing Test"
                description: "Test advanced expression parsing with nested calculations"

                inputs:
                  - principal
                  - rate
                  - term

                when:
                  - principal > 0
                  - rate > 0
                  - term > 0

                then:
                  - calculate monthly_rate as rate / 12
                  - calculate num_payments as term * 12
                  - calculate payment_factor as (1 + monthly_rate) ** num_payments
                  - calculate monthly_payment as principal * (monthly_rate * payment_factor) / (payment_factor - 1)
                  - calculate total_payment as monthly_payment * num_payments
                  - calculate total_interest as total_payment - principal
                  - set calculation_complete to true

                output:
                  monthly_payment: number
                  total_payment: number
                  total_interest: number
                  calculation_complete: boolean
                """;

            Map<String, Object> inputData = Map.of(
                "principal", 200000,
                "rate", 0.05,
                "term", 30
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertTrue((Boolean) result.getOutputData().get("calculation_complete"));

            // Verify the mortgage calculation is reasonable
            double monthlyPayment = ((Number) result.getOutputData().get("monthly_payment")).doubleValue();
            assertTrue(monthlyPayment > 1000 && monthlyPayment < 1200); // Should be around $1073 for these inputs
        }

        @Test
        @DisplayName("Test comprehensive advanced DSL features integration")
        void testComprehensiveAdvancedDSLFeatures() {
            String yamlRule = """
                name: "Comprehensive Advanced DSL Features Test"
                description: "Integration test for all advanced DSL features"
                version: "2.0.0"

                metadata:
                  tags: ["advanced", "integration", "comprehensive"]
                  author: "Test Team"
                  category: "Integration Testing"
                  priority: 1
                  riskLevel: "MEDIUM"

                circuit_breaker:
                  enabled: true
                  failure_threshold: 3
                  timeout_duration: "30s"
                  recovery_timeout: "60s"

                inputs:
                  - creditScore
                  - annualIncome
                  - monthlyDebt
                  - applicationDate
                  - riskCategory
                  - hasGuarantor

                when:
                  # Complex nested expressions with multiple operator aliases
                  - ((creditScore >= 650 AND annualIncome greater_than_or_equal 50000) OR (creditScore at_least 700 AND annualIncome > 30000))
                  - riskCategory in ["LOW", "MEDIUM"] AND riskCategory not_in ["HIGH", "CRITICAL"]
                  - (monthlyDebt / annualIncome) < 0.4 OR hasGuarantor == true
                  - applicationDate is_not_null

                then:
                  # Complex date calculations
                  - run processing_deadline as dateadd(applicationDate, 30, "days")
                  - run days_since_application as datediff(applicationDate, "2024-12-31", "days")

                  # Complex mathematical expressions with proper precedence
                  - calculate debt_to_income_ratio as ((monthlyDebt * 12) / annualIncome) * 100
                  - calculate risk_adjusted_score as ((creditScore * 0.6) + ((annualIncome / 1000) * 0.3) + 50)
                  - calculate compound_factor as (1 + 0.05) ** 2

                  # Conditional actions with complex expressions
                  - if debt_to_income_ratio <= 30 AND risk_adjusted_score >= 500 then set approval_tier to "PREMIUM"
                  - if debt_to_income_ratio > 30 AND debt_to_income_ratio <= 40 AND risk_adjusted_score >= 400 then set approval_tier to "STANDARD"
                  - if debt_to_income_ratio > 40 then set approval_tier to "SUBPRIME"

                  # Circuit breaker action for high risk
                  - if risk_adjusted_score < 300 then circuit_breaker "HIGH_RISK_APPLICATION"

                  - set processing_complete to true

                output:
                  processing_deadline: text
                  days_since_application: number
                  debt_to_income_ratio: number
                  risk_adjusted_score: number
                  compound_factor: number
                  approval_tier: text
                  processing_complete: boolean
                """;

            Map<String, Object> inputData = Map.of(
                "creditScore", 720,
                "annualIncome", 80000,
                "monthlyDebt", 2000,
                "applicationDate", "2024-01-15",
                "riskCategory", "LOW",
                "hasGuarantor", true
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            assertTrue(result.isSuccess());
            assertTrue((Boolean) result.getOutputData().get("processing_complete"));
            assertEquals("PREMIUM", result.getOutputData().get("approval_tier"));

            // Verify complex calculations
            double debtToIncomeRatio = ((Number) result.getOutputData().get("debt_to_income_ratio")).doubleValue();
            assertEquals(30.0, debtToIncomeRatio, 0.01); // (2000 * 12) / 80000 * 100 = 30%

            double riskAdjustedScore = ((Number) result.getOutputData().get("risk_adjusted_score")).doubleValue();
            double expectedScore = (720 * 0.6) + ((80000 / 1000) * 0.3) + 50; // 432 + 24 + 50 = 506
            assertEquals(expectedScore, riskAdjustedScore, 0.01);

            double compoundFactor = ((Number) result.getOutputData().get("compound_factor")).doubleValue();
            assertEquals(1.1025, compoundFactor, 0.0001); // (1 + 0.05)^2

            // Verify date calculations
            assertEquals("2024-02-14", result.getOutputData().get("processing_deadline"));

            int daysSinceApplication = ((Number) result.getOutputData().get("days_since_application")).intValue();
            assertTrue(daysSinceApplication > 300); // Should be around 350+ days
        }
    }

    @Nested
    @DisplayName("Else Actions Support")
    class ElseActionsSupportTests {

        @Test
        @DisplayName("Test else actions in simple syntax")
        void testElseActionsSimpleSyntax() {
            String yamlRule = """
                name: "Else Actions Test"
                description: "Test else actions support"
                
                inputs:
                  - creditScore
                
                when:
                  - creditScore at_least 700
                
                then:
                  - set approval_status to "APPROVED"
                  - set tier to "PREMIUM"
                
                else:
                  - set approval_status to "REJECTED"
                  - set tier to "STANDARD"
                  - set rejection_reason to "Credit score too low"
                
                output:
                  approval_status: text
                  tier: text
                  rejection_reason: text
                """;

            Map<String, Object> inputData = Map.of("creditScore", 650);

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);
            
            assertTrue(result.isSuccess());
            assertEquals("REJECTED", result.getOutputData().get("approval_status"));
            assertEquals("STANDARD", result.getOutputData().get("tier"));
            assertEquals("Credit score too low", result.getOutputData().get("rejection_reason"));
        }
    }
}
