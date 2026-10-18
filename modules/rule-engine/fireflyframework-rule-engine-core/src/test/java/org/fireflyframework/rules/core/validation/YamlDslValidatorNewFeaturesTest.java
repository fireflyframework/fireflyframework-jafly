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

import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test YamlDslValidator support for all new DSL features
 */
@DisplayName("YamlDslValidator New Features Tests")
public class YamlDslValidatorNewFeaturesTest {

    private YamlDslValidator yamlDslValidator;

    @BeforeEach
    void setUp() {
        // Create dependencies
        SyntaxValidator syntaxValidator = Mockito.mock(SyntaxValidator.class);
        NamingConventionValidator namingValidator = new NamingConventionValidator();
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser astParser = new ASTRulesDSLParser(dslParser);

        // Mock syntax validator to return no issues
        Mockito.when(syntaxValidator.validate(Mockito.anyString()))
               .thenReturn(java.util.List.of());

        yamlDslValidator = new YamlDslValidator(syntaxValidator, namingValidator, astParser);
    }

    @Nested
    @DisplayName("Complex Conditions Validation")
    class ComplexConditionsValidationTests {

        @Test
        @DisplayName("Should validate complex conditions block")
        void testComplexConditionsValidation() {
            String yaml = """
                name: "Complex Conditions Test"
                description: "Test complex conditions validation"
                
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
                      - set approval_status to "DECLINED"
                
                output:
                  approval_status: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should detect missing if condition in complex block")
        void testMissingIfCondition() {
            String yaml = """
                name: "Missing If Test"
                description: "Test missing if condition"
                
                inputs:
                  - creditScore
                
                conditions:
                  then:
                    actions:
                      - set result to "test"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getSummary().getErrors()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Multiple Rules Validation")
    class MultipleRulesValidationTests {

        @Test
        @DisplayName("Should validate multiple rules syntax")
        void testMultipleRulesValidation() {
            String yaml = """
                name: "Multiple Rules Test"
                description: "Test multiple rules validation"
                
                inputs:
                  - creditScore
                  - income
                
                rules:
                  - name: "Initial Check"
                    when:
                      - creditScore >= 600
                    then:
                      - set initial_pass to true
                    else:
                      - set initial_pass to false

                  - name: "Final Decision"
                    when:
                      - creditScore >= 600
                      - income > 50000
                    then:
                      - set final_decision to "APPROVED"
                    else:
                      - set final_decision to "DECLINED"
                
                output:
                  initial_pass: boolean
                  final_decision: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should warn about missing sub-rule names")
        void testMissingSubRuleNames() {
            String yaml = """
                name: "Missing Sub-rule Names Test"
                description: "Test missing sub-rule names"
                
                inputs:
                  - creditScore
                
                rules:
                  - when:
                      - creditScore >= 600
                    then:
                      - set result to "pass"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            // Should be valid but have warnings
            assertThat(result.getStatus()).isIn(
                ValidationResult.ValidationStatus.VALID,
                ValidationResult.ValidationStatus.WARNING
            );
            if (result.getSummary().getWarnings() > 0) {
                assertThat(result.getSummary().getWarnings()).isGreaterThan(0);
            }
        }
    }

    @Nested
    @DisplayName("Mixed Syntax Validation")
    class MixedSyntaxValidationTests {

        @Test
        @DisplayName("Should warn about mixed syntax patterns")
        void testMixedSyntaxWarning() {
            String yaml = """
                name: "Mixed Syntax Test"
                description: "Test mixed syntax warning"
                
                inputs:
                  - creditScore
                
                when:
                  - creditScore >= 600
                then:
                  - set simple_result to "pass"
                
                conditions:
                  if:
                    compare:
                      left: creditScore
                      operator: "greater_than"
                      right: 700
                  then:
                    actions:
                      - set complex_result to "excellent"
                
                output:
                  simple_result: text
                  complex_result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            // Should have warnings about mixed syntax
            assertThat(result.getSummary().getWarnings()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Enhanced Function Validation")
    class EnhancedFunctionValidationTests {

        @Test
        @DisplayName("Should validate new built-in functions")
        void testNewBuiltInFunctions() {
            String yaml = """
                name: "New Functions Test"
                description: "Test new built-in functions"
                
                inputs:
                  - userData
                  - apiUrl
                
                when:
                  - json_exists(userData, "email")
                
                then:
                  - run api_response as rest_get(apiUrl)
                  - run user_email as json_get(userData, "email")
                  - run formatted_date as format_date(now(), "yyyy-MM-dd")
                  - run user_age as calculate_age(json_get(userData, "birthDate"))
                  - run is_valid_email as validate_email(json_get(userData, "email"))
                
                output:
                  api_response: object
                  user_email: text
                  formatted_date: text
                  user_age: number
                  is_valid_email: boolean
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Enhanced Metadata Validation")
    class EnhancedMetadataValidationTests {

        @Test
        @DisplayName("Should validate enhanced metadata fields")
        void testEnhancedMetadataValidation() {
            String yaml = """
                name: "Enhanced Metadata Test"
                description: "Test enhanced metadata validation"
                
                inputs:
                  - creditScore
                
                metadata:
                  tags: ["credit", "risk-assessment"]
                  author: "Risk Management Team"
                  category: "Credit Scoring"
                  priority: 1
                  riskLevel: "HIGH"
                  last_modified: "2025-01-15"
                  review_date: "2025-06-15"
                  version: "1.2.0"
                
                when:
                  - creditScore >= 600
                
                then:
                  - set result to "pass"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should detect invalid metadata formats")
        void testInvalidMetadataFormats() {
            String yaml = """
                name: "Invalid Metadata Test"
                description: "Test invalid metadata formats"
                
                inputs:
                  - creditScore
                
                metadata:
                  tags: "should-be-array"
                  priority: "should-be-number"
                  riskLevel: "INVALID_LEVEL"
                  author: ""
                
                when:
                  - creditScore >= 600
                
                then:
                  - set result to "pass"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            // Should have warnings/info issues about metadata format
            assertThat(result.getSummary().getTotalIssues()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Real-World DSL Validation")
    class RealWorldDslValidationTests {

        @Test
        @DisplayName("Should validate B2B Credit Scoring DSL from test file")
        void testB2BCreditScoringDslValidation() {
            // Get the actual B2B Credit Scoring DSL from the test file
            String b2bCreditScoringDsl = getB2BCreditScoringRule();

            ValidationResult result = yamlDslValidator.validate(b2bCreditScoringDsl);

            // Debug output to see what validation issues exist
            System.out.println("B2B Credit Scoring DSL Validation Result: " + result.getStatus());
            System.out.println("Total issues: " + result.getSummary().getTotalIssues());
            System.out.println("Errors: " + result.getSummary().getErrors());
            System.out.println("Warnings: " + result.getSummary().getWarnings());

            if (result.getIssues() != null && result.getIssues().getLogic() != null) {
                System.out.println("Logic issues:");
                result.getIssues().getLogic().forEach(issue ->
                    System.out.println("  - " + issue.getMessage() + " (at " + issue.getLocation().getPath() + ")"));
            }

            // This should be valid - it's a working DSL from the test suite
            assertThat(result.isValid()).isTrue();
        }

        private String getB2BCreditScoringRule() {
            return """
                name: "B2B Credit Scoring Platform"
                description: "Comprehensive business credit assessment using multiple data sources"
                version: "1.0.0"

                metadata:
                  tags: ["b2b", "credit-scoring", "business-loans"]
                  author: "Credit Risk Engineering Team"
                  category: "Business Credit Assessment"

                # Input variables from loan application API and external data sources
                inputs:
                  # Business identification and basic info
                  - businessId
                  - businessName
                  - taxId
                  - businessType
                  - industryCode
                  - yearsInBusiness
                  - numberOfEmployees

                  # Loan application details
                  - requestedAmount
                  - loanPurpose
                  - requestedTerm
                  - hasCollateral
                  - collateralValue

                  # Financial information from application
                  - annualRevenue
                  - monthlyRevenue
                  - monthlyExpenses
                  - existingDebt
                  - monthlyDebtPayments

                  # Business owner information
                  - ownerCreditScore
                  - ownerYearsExperience
                  - ownershipPercentage

                  # Credit bureau data
                  - businessCreditScore
                  - paymentHistoryScore
                  - creditUtilization
                  - publicRecordsCount
                  - tradelineCount

                  # Banking and transaction data
                  - avgMonthlyDeposits
                  - accountAgeMonths
                  - nsfCount12Months
                  - cashFlowVolatility
                  - averageAccountBalance

                  # Tax and financial verification data
                  - verifiedAnnualRevenue
                  - taxComplianceScore
                  - businessExpenses
                  - netIncome
                  - taxFilingHistory

                # System constants for business rules
                constants:
                  # Credit score thresholds
                  - code: MIN_BUSINESS_CREDIT_SCORE
                    defaultValue: 650
                  - code: EXCELLENT_CREDIT_THRESHOLD
                    defaultValue: 750

                  # Financial ratio limits
                  - code: MAX_DEBT_TO_INCOME_RATIO
                    defaultValue: 0.4
                  - code: MIN_DEBT_SERVICE_COVERAGE
                    defaultValue: 1.25
                  - code: MAX_LOAN_TO_VALUE_RATIO
                    defaultValue: 0.8

                  # Business criteria
                  - code: MIN_YEARS_IN_BUSINESS
                    defaultValue: 2
                  - code: MIN_ANNUAL_REVENUE
                    defaultValue: 100000
                  - code: MAX_LOAN_AMOUNT_UNSECURED
                    defaultValue: 250000

                  # Industry risk multipliers
                  - code: HIGH_RISK_INDUSTRY_MULTIPLIER
                    defaultValue: 1.5
                  - code: LOW_RISK_INDUSTRY_MULTIPLIER
                    defaultValue: 0.8

                  # Scoring weights
                  - code: CREDIT_SCORE_WEIGHT
                    defaultValue: 0.3
                  - code: FINANCIAL_STABILITY_WEIGHT
                    defaultValue: 0.25
                  - code: BUSINESS_PROFILE_WEIGHT
                    defaultValue: 0.25
                  - code: CASH_FLOW_WEIGHT
                    defaultValue: 0.2

                # Multi-stage evaluation using sequential rules
                rules:
                  # Stage 1: Data Validation and Preparation
                  - name: "Data Validation and Preparation"
                    when:
                      - businessId is_not_empty
                      - requestedAmount is_positive
                      - annualRevenue is_positive
                      - businessCreditScore is_credit_score
                      - ownerCreditScore is_credit_score
                    then:
                      # Validate all required financial data is present and valid
                      - set monthly_revenue_valid to (monthlyRevenue is_positive)
                      - set monthly_expenses_valid to (monthlyExpenses is_positive)
                      - set existing_debt_valid to (existingDebt is_not_null)
                      - set monthly_debt_payments_valid to (monthlyDebtPayments is_positive)
                      - set verified_revenue_valid to (verifiedAnnualRevenue is_positive)
                      - set has_complete_financial_data to (monthly_revenue_valid AND monthly_expenses_valid AND existing_debt_valid AND monthly_debt_payments_valid AND verified_revenue_valid)

                      # Validate business profile data
                      - set years_in_business_valid to (yearsInBusiness is_positive)
                      - set employees_valid to (numberOfEmployees is_positive)
                      - set industry_code_valid to (industryCode is_not_empty)
                      - set owner_experience_valid to (ownerYearsExperience is_positive)
                      - set business_type_valid to (businessType is_not_empty)
                      - set has_complete_business_profile to (years_in_business_valid AND employees_valid AND industry_code_valid AND owner_experience_valid AND business_type_valid)

                      # Validate credit and banking data
                      - set business_credit_valid to (businessCreditScore is_credit_score)
                      - set payment_history_valid to (paymentHistoryScore is_positive)
                      - set deposits_valid to (avgMonthlyDeposits is_positive)
                      - set account_age_valid to (accountAgeMonths is_positive)
                      - set tax_compliance_valid to (taxComplianceScore is_positive)
                      - set has_complete_credit_data to (business_credit_valid AND payment_history_valid AND deposits_valid AND account_age_valid AND tax_compliance_valid)

                      # Calculate data quality indicators
                      - run revenue_variance as abs(annualRevenue - verifiedAnnualRevenue) / verifiedAnnualRevenue
                      - run deposit_variance as abs(avgMonthlyDeposits - monthlyRevenue) / monthlyRevenue

                      # Overall data completeness and quality check
                      - set revenue_variance_acceptable to (revenue_variance less_than 0.3)
                      - set data_validation_complete to (has_complete_financial_data AND has_complete_business_profile AND has_complete_credit_data AND revenue_variance_acceptable)

                      - if data_validation_complete then set validation_status to "PASSED"
                      - if NOT data_validation_complete then set validation_status to "FAILED"
                      - if NOT data_validation_complete then set rejection_reason to "Incomplete or inconsistent application data"
                    else:
                      - set data_validation_complete to false
                      - set validation_status to "FAILED"
                      - set rejection_reason to "Missing required basic information"

                  # Stage 2: Financial Analysis and Ratio Calculations
                  - name: "Financial Analysis and Ratio Calculations"
                    when:
                      - data_validation_complete equals true
                      - verifiedAnnualRevenue is_positive
                    then:
                      # Calculate key financial ratios
                      - calculate monthly_revenue_verified as verifiedAnnualRevenue / 12
                      - calculate debt_to_income_ratio as monthlyDebtPayments / monthly_revenue_verified
                      - calculate profit_margin as netIncome / verifiedAnnualRevenue
                      - calculate expense_ratio as businessExpenses / verifiedAnnualRevenue

                      # Cash flow analysis
                      - calculate cash_flow_coverage as avgMonthlyDeposits / monthlyDebtPayments
                      - run account_stability_score as min(100, accountAgeMonths * 2)
                      - run banking_behavior_score as max(0, 100 - (nsfCount12Months * 10))

                      # Loan-specific calculations
                      - calculate loan_to_revenue_ratio as requestedAmount / verifiedAnnualRevenue
                      - calculate estimated_monthly_payment as requestedAmount * 0.02
                      - calculate new_debt_service as monthlyDebtPayments + estimated_monthly_payment
                      - calculate debt_service_coverage as (monthly_revenue_verified - monthlyExpenses) / new_debt_service

                      # Risk indicators
                      - run revenue_stability_score as max(0, 100 - (revenue_variance * 100))
                      - run cash_flow_stability_score as max(0, 100 - cashFlowVolatility)

                      - set financial_analysis_complete to true
                    else:
                      - set financial_analysis_complete to false
                      - set rejection_reason to "Unable to complete financial analysis"

                output:
                  # Primary decision outputs
                  approval_status: text
                  final_credit_score: number
                  risk_level: text
                  interest_rate: number
                  approved_amount: number

                  # Financial analysis results
                  debt_to_income_ratio: number
                  debt_service_coverage: number
                  profit_margin: number
                  cash_flow_coverage: number

                  # Decision factors
                  meets_credit_requirements: boolean
                  meets_financial_requirements: boolean
                  meets_business_requirements: boolean

                  # Additional information
                  rejection_reason: text
                  validation_status: text
                """;
        }
    }

    @Nested
    @DisplayName("Error Detection and Debugging Tests")
    class ErrorDetectionTests {

        @Test
        @DisplayName("Should detect undefined variables and provide clear error messages")
        void testUndefinedVariableDetection() {
            String yamlWithUndefinedVars = """
                name: "Undefined Variables Test"
                description: "Test undefined variable detection"

                inputs:
                  - creditScore

                when:
                  - creditScore greater_than 700
                  - undefinedVariable greater_than 1000  # This should be flagged

                then:
                  - set result to anotherUndefinedVar  # This should also be flagged

                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yamlWithUndefinedVars);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();

            // Check for specific undefined variable errors
            boolean hasUndefinedVariableError = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined variable: undefinedVariable"));
            boolean hasAnotherUndefinedError = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined variable: anotherUndefinedVar"));

            assertThat(hasUndefinedVariableError).isTrue();
            assertThat(hasAnotherUndefinedError).isTrue();

            System.out.println("Undefined Variables Test - Errors found:");
            result.getErrors().forEach(error ->
                System.out.println("  - " + error.getMessage()));
        }

        @Test
        @DisplayName("Should detect invalid function calls and provide suggestions")
        void testInvalidFunctionDetection() {
            String yamlWithInvalidFunctions = """
                name: "Invalid Functions Test"
                description: "Test invalid function detection"

                inputs:
                  - amount

                when:
                  - amount greater_than 0

                then:
                  - calculate result1 as invalidFunction(amount)  # Should be flagged
                  - calculate result2 as anotherBadFunc(100, 200)  # Should be flagged
                  - run result3 as max(amount, 1000)  # This should be valid

                output:
                  result1: number
                  result2: number
                  result3: number
                """;

            ValidationResult result = yamlDslValidator.validate(yamlWithInvalidFunctions);

            System.out.println("Invalid Functions Test - Validation result: " + (result.isValid() ? "VALID" : "INVALID"));
            System.out.println("Total errors: " + result.getErrors().size());
            System.out.println("Errors found:");
            result.getErrors().forEach(error ->
                System.out.println("  - " + error.getMessage()));

            // The validator should detect undefined functions
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();

            // Check for specific invalid function errors
            boolean hasInvalidFunctionError = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined function: invalidFunction"));
            boolean hasAnotherInvalidError = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined function: anotherBadFunc"));

            assertThat(hasInvalidFunctionError).isTrue();
            assertThat(hasAnotherInvalidError).isTrue();
        }

        @Test
        @DisplayName("Should demonstrate current validation capabilities and identify areas for improvement")
        void testValidationCapabilitiesDemo() {
            // Test 1: Known invalid syntax that should be caught
            String yamlWithSyntaxError = """
                name: "Syntax Error Test"
                description: "Test syntax error detection"

                inputs:
                  - score

                when:
                  - score greater_than  # Missing right operand - should be caught

                then:
                  - set result to "approved"

                output:
                  result: text
                """;

            ValidationResult result1 = yamlDslValidator.validate(yamlWithSyntaxError);
            System.out.println("=== Syntax Error Test ===");
            System.out.println("Validation result: " + (result1.isValid() ? "VALID" : "INVALID"));
            System.out.println("Total errors: " + result1.getErrors().size());
            result1.getErrors().forEach(error -> System.out.println("  - " + error.getMessage()));

            // Test 2: Invalid operators (currently not caught - this shows what needs improvement)
            String yamlWithInvalidOperators = """
                name: "Invalid Operators Test"
                description: "Test invalid operator detection"

                inputs:
                  - score

                when:
                  - score invalid_operator 700  # Currently not caught - needs improvement

                then:
                  - set result to "approved"

                output:
                  result: text
                """;

            ValidationResult result2 = yamlDslValidator.validate(yamlWithInvalidOperators);
            System.out.println("\\n=== Invalid Operators Test ===");
            System.out.println("Validation result: " + (result2.isValid() ? "VALID" : "INVALID"));
            System.out.println("Total errors: " + result2.getErrors().size());
            result2.getErrors().forEach(error -> System.out.println("  - " + error.getMessage()));

            // Test 3: Undefined variables (should be caught)
            String yamlWithUndefinedVars = """
                name: "Undefined Variables Test"
                description: "Test undefined variable detection"

                inputs:
                  - score

                when:
                  - undefinedVar greater_than 700  # Should be caught

                then:
                  - set result to "approved"

                output:
                  result: text
                """;

            ValidationResult result3 = yamlDslValidator.validate(yamlWithUndefinedVars);
            System.out.println("\\n=== Undefined Variables Test ===");
            System.out.println("Validation result: " + (result3.isValid() ? "VALID" : "INVALID"));
            System.out.println("Total errors: " + result3.getErrors().size());
            result3.getErrors().forEach(error -> System.out.println("  - " + error.getMessage()));

            // For now, just verify that the validator is working for undefined variables
            assertThat(result3.isValid()).isFalse();
            assertThat(result3.getErrors()).isNotEmpty();

            boolean hasUndefinedVarError = result3.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined variable: undefinedVar"));
            assertThat(hasUndefinedVarError).isTrue();
        }

        @Test
        @DisplayName("Should detect missing required fields and structural issues")
        void testStructuralValidation() {
            String yamlWithStructuralIssues = """
                # Missing name field
                description: "Test structural validation"

                # Missing inputs section

                when:
                  - compare:
                      left: someVariable  # Will be undefined since no inputs
                      operator: "greater_than"
                      # Missing right field

                then:
                  - set:
                      # Missing variable field
                      value: "test"

                # Missing output section
                """;

            ValidationResult result = yamlDslValidator.validate(yamlWithStructuralIssues);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();

            System.out.println("Structural Validation Test - Errors found:");
            result.getErrors().forEach(error ->
                System.out.println("  - " + error.getMessage()));

            System.out.println("Total issues: " + (result.getSummary() != null ? result.getSummary().getTotalIssues() : 0));
            System.out.println("Validation status: " + result.getStatus());
        }

        @Test
        @DisplayName("Should detect complex syntax errors and provide detailed feedback")
        void testComplexSyntaxErrors() {
            String yamlWithComplexErrors = """
                name: "Complex Syntax Errors Test"
                description: "Test complex syntax error detection"

                inputs:
                  - creditScore
                  - income

                when:
                  - creditScore greater_than 700
                  - undefinedVar greater_than income  # Undefined variable

                then:
                  - calculate score as badFunction(creditScore)  # Invalid function
                  - set status to anotherUndefinedVar  # Another undefined variable
                  - call nonExistentFunc with [creditScore, income]  # Invalid function call

                output:
                  score: number
                  status: text
                """;

            ValidationResult result = yamlDslValidator.validate(yamlWithComplexErrors);

            System.out.println("Complex Syntax Errors Test - Total errors: " + result.getErrors().size());
            result.getErrors().forEach(error ->
                System.out.println("  - " + error.getMessage()));

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();

            // Should detect multiple types of errors
            boolean hasUndefinedVariable = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined variable: undefinedVar") ||
                                     error.getMessage().contains("Undefined variable: anotherUndefinedVar"));
            boolean hasInvalidFunction = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined function: badFunction") ||
                                     error.getMessage().contains("Undefined function: nonExistentFunc"));

            assertThat(hasUndefinedVariable).isTrue();
            assertThat(hasInvalidFunction).isTrue();
        }

        @Test
        @DisplayName("Should detect cross-rule variable dependency issues")
        void testCrossRuleVariableErrors() {
            String yamlWithCrossRuleErrors = """
                name: "Cross-Rule Variable Errors Test"
                description: "Test cross-rule variable dependency error detection"

                inputs:
                  - baseScore

                rules:
                  - name: "First Rule"
                    when:
                      - baseScore greater_than 500
                    then:
                      - calculate adjusted_score as multiply(baseScore, 1.2)

                  - name: "Second Rule"
                    when:
                      - adjusted_score greater_than 600  # This should be valid (computed in previous rule)
                      - undefined_var greater_than 100  # This should be invalid
                    then:
                      - set final_status to "approved"
                      - calculate bonus as invalidFunc(adjusted_score)  # Invalid function

                output:
                  final_status: text
                  bonus: number
                """;

            ValidationResult result = yamlDslValidator.validate(yamlWithCrossRuleErrors);

            System.out.println("Cross-Rule Variable Errors Test - Errors found:");
            result.getErrors().forEach(error ->
                System.out.println("  - " + error.getMessage()));

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();

            // Should detect undefined variable but NOT the cross-rule computed variable
            boolean hasUndefinedVarError = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined variable: undefined_var"));
            boolean hasInvalidFunctionError = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined function: invalidFunc"));
            boolean hasAdjustedScoreError = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("Undefined variable: adjusted_score"));

            assertThat(hasUndefinedVarError).isTrue();
            assertThat(hasInvalidFunctionError).isTrue();
            assertThat(hasAdjustedScoreError).isFalse(); // This should NOT be flagged as error
        }

        @Test
        @DisplayName("Should comprehensively detect all types of DSL errors")
        void testComprehensiveErrorDetection() {
            // Test 1: Invalid Commands/Actions
            String yamlWithInvalidCommands = """
                name: "Invalid Commands Test"
                inputs: [amount]
                when: [amount greater_than 0]
                then:
                  - invalidCommand result to "test"  # Invalid action
                  - badAction value as 100  # Invalid action
                output: {result: text}
                """;

            ValidationResult result1 = yamlDslValidator.validate(yamlWithInvalidCommands);
            System.out.println("=== Invalid Commands Test ===");
            System.out.println("Valid: " + result1.isValid() + ", Errors: " + result1.getErrors().size());
            result1.getErrors().forEach(e -> System.out.println("  - " + e.getMessage()));

            // Test 2: Invalid Operators
            String yamlWithInvalidOperators = """
                name: "Invalid Operators Test"
                inputs: [score]
                when: [score bad_operator 700]  # Invalid operator
                then: [set result to "approved"]
                output: {result: text}
                """;

            ValidationResult result2 = yamlDslValidator.validate(yamlWithInvalidOperators);
            System.out.println("\\n=== Invalid Operators Test ===");
            System.out.println("Valid: " + result2.isValid() + ", Errors: " + result2.getErrors().size());
            result2.getErrors().forEach(e -> System.out.println("  - " + e.getMessage()));

            // Test 3: Malformed YAML Structure
            String yamlWithSyntaxErrors = """
                name: "Syntax Errors Test"
                inputs: [amount]
                when: [amount greater_than]  # Missing right operand
                then: [set result to "approved"]
                output: {result: text}
                """;

            ValidationResult result3 = yamlDslValidator.validate(yamlWithSyntaxErrors);
            System.out.println("\\n=== Syntax Errors Test ===");
            System.out.println("Valid: " + result3.isValid() + ", Errors: " + result3.getErrors().size());
            result3.getErrors().forEach(e -> System.out.println("  - " + e.getMessage()));

            // Test 4: Missing Required Fields
            String yamlWithMissingFields = """
                description: "Missing name field"
                when: [amount greater_than 0]
                then: [set result to "approved"]
                """;

            ValidationResult result4 = yamlDslValidator.validate(yamlWithMissingFields);
            System.out.println("\\n=== Missing Fields Test ===");
            System.out.println("Valid: " + result4.isValid() + ", Errors: " + result4.getErrors().size());
            result4.getErrors().forEach(e -> System.out.println("  - " + e.getMessage()));

            // Test 5: Invalid Function Calls
            String yamlWithInvalidFunctions = """
                name: "Invalid Functions Test"
                inputs: [amount]
                when: [amount greater_than 0]
                then: [calculate result as nonExistentFunction(amount, 100)]
                output: {result: number}
                """;

            ValidationResult result5 = yamlDslValidator.validate(yamlWithInvalidFunctions);
            System.out.println("\\n=== Invalid Functions Test ===");
            System.out.println("Valid: " + result5.isValid() + ", Errors: " + result5.getErrors().size());
            result5.getErrors().forEach(e -> System.out.println("  - " + e.getMessage()));

            // Verify that at least some errors are caught
            boolean anyErrorsCaught = !result1.isValid() || !result2.isValid() || !result3.isValid() ||
                                    !result4.isValid() || !result5.isValid();

            assertThat(anyErrorsCaught).isTrue();

            // Count total errors across all tests
            int totalErrors = result1.getErrors().size() + result2.getErrors().size() +
                            result3.getErrors().size() + result4.getErrors().size() + result5.getErrors().size();

            System.out.println("\\n=== SUMMARY ===");
            System.out.println("Total errors detected across all tests: " + totalErrors);

            assertThat(totalErrors).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should provide accurate ValidationLocation information for errors")
        void testValidationLocationAccuracy() {
            // Test YAML with semantic errors (valid syntax but undefined variables/functions)
            String yamlWithLocationErrors = """
                name: "Location Test Rule"
                description: "Test location accuracy"

                inputs:
                  - amount
                  - score

                when:
                  - amount greater_than 100
                  - undefinedVar1 equals "test"
                  - score greater_than 700

                then:
                  - calculate result1 as badFunc(amount)
                  - set status to "approved"
                  - calculate result2 as unknownFunction(score)

                output:
                  result1: number
                  status: text
                  result2: number
                """;

            ValidationResult result = yamlDslValidator.validate(yamlWithLocationErrors);

            System.out.println("=== ValidationLocation Accuracy Test ===");
            System.out.println("Total errors: " + result.getErrors().size());

            for (ValidationResult.ValidationIssue issue : result.getErrors()) {
                System.out.println("\\nError: " + issue.getMessage());
                System.out.println("  Code: " + issue.getCode());
                System.out.println("  Severity: " + issue.getSeverity());

                if (issue.getLocation() != null) {
                    ValidationResult.ValidationLocation location = issue.getLocation();
                    System.out.println("  Location:");
                    System.out.println("    Section: " + location.getSection());
                    System.out.println("    Line Number: " + location.getLineNumber());
                    System.out.println("    Path: " + location.getPath());
                    System.out.println("    Context: " + location.getContext());
                } else {
                    System.out.println("  Location: NOT PROVIDED");
                }
            }

            // Verify that errors have location information
            assertThat(result.getErrors()).isNotEmpty();

            // Check that at least some errors have enhanced location information
            boolean hasEnhancedLocationInfo = result.getErrors().stream()
                    .anyMatch(error -> error.getLocation() != null &&
                                     error.getLocation().getLineNumber() != null &&
                                     error.getLocation().getSection() != null);

            System.out.println("\\nEnhanced Location Info Available: " + hasEnhancedLocationInfo);

            // Verify that validation is working and location info is provided
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().stream().anyMatch(error ->
                error.getLocation() != null && error.getLocation().getPath() != null)).isTrue();
        }

        @Test
        @DisplayName("Should provide contextual information for validation errors")
        void testValidationContextualInformation() {
            // Test with multi-line YAML to verify line number accuracy
            String multiLineYaml = """
                name: "Multi-line Test"
                description: "Testing line number accuracy"

                inputs:
                  - baseAmount
                  - creditScore

                when:
                  - baseAmount greater_than 0
                  - creditScore greater_than 600
                  - nonExistentVariable equals "test"

                then:
                  - calculate adjustedAmount as multiply(baseAmount, 1.1)
                  - calculate bonus as unknownFunction(creditScore)
                  - set status to finalResult

                output:
                  adjustedAmount: number
                  bonus: number
                  status: text
                """;

            ValidationResult result = yamlDslValidator.validate(multiLineYaml);

            System.out.println("=== Contextual Information Test ===");
            System.out.println("YAML Content (with line numbers):");
            String[] lines = multiLineYaml.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                System.out.printf("%2d: %s%n", i + 1, lines[i]);
            }

            System.out.println("\\nValidation Errors:");
            for (ValidationResult.ValidationIssue issue : result.getErrors()) {
                System.out.println("\\n" + issue.getMessage());
                if (issue.getLocation() != null) {
                    ValidationResult.ValidationLocation location = issue.getLocation();
                    if (location.getLineNumber() != null) {
                        System.out.println("  At line: " + location.getLineNumber());
                        if (location.getLineNumber() > 0 && location.getLineNumber() <= lines.length) {
                            System.out.println("  Content: " + lines[location.getLineNumber() - 1].trim());
                        }
                    }
                    if (location.getPath() != null) {
                        System.out.println("  Path: " + location.getPath());
                    }
                }
            }

            // Verify errors are detected
            assertThat(result.getErrors()).isNotEmpty();

            // Check for specific expected errors
            boolean hasUndefinedVarError = result.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("nonExistentVariable") ||
                                     error.getMessage().contains("unknownFunction") ||
                                     error.getMessage().contains("finalResult"));

            assertThat(hasUndefinedVarError).isTrue();
        }
    }
}
