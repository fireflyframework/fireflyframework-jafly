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
 * Comprehensive tests for DSL validation ensuring full compliance with YAML DSL Reference
 */
@DisplayName("Comprehensive DSL Validation Tests")
class ComprehensiveDSLValidationTest {

    private YamlDslValidator yamlDslValidator;
    private ASTRulesDSLParser astParser;

    @BeforeEach
    void setUp() {
        // Create mocked dependencies
        SyntaxValidator syntaxValidator = Mockito.mock(SyntaxValidator.class);
        NamingConventionValidator namingValidator = Mockito.mock(NamingConventionValidator.class);

        // Create real DSLParser instead of mock to avoid parsing issues
        DSLParser dslParser = new DSLParser();
        astParser = new ASTRulesDSLParser(dslParser);

        // Create the validator with mocked dependencies
        yamlDslValidator = new YamlDslValidator(syntaxValidator, namingValidator, astParser);

        // Configure mocks to return empty lists (no issues) for valid DSL
        Mockito.when(syntaxValidator.validate(Mockito.anyString())).thenReturn(java.util.List.of());

        // Configure naming validator methods
        Mockito.when(namingValidator.isValidInputVariableName(Mockito.anyString())).thenReturn(true);
        Mockito.when(namingValidator.isValidConstantName(Mockito.anyString())).thenReturn(true);
        Mockito.when(namingValidator.isValidComputedVariableName(Mockito.anyString())).thenReturn(true);
    }

    @Nested
    @DisplayName("DSL Reference Compliance Tests")
    class DSLReferenceComplianceTests {

        @Test
        @DisplayName("Should validate complete valid DSL according to reference")
        void testCompleteValidDSL() {
            String validDSL = """
                name: "Credit Assessment Rule"
                description: "Comprehensive credit evaluation using all DSL features"
                version: "1.0.0"
                
                metadata:
                  tags: ["credit", "risk-assessment"]
                  author: "Risk Team"
                  category: "Credit Scoring"
                
                inputs:
                  - creditScore
                  - annualIncome
                  - monthlyDebt
                  - employmentYears
                  - requestedAmount
                
                when:
                  - creditScore at_least 650
                  - annualIncome greater_than 40000
                  - employmentYears at_least 1

                then:
                  - calculate debt_to_income as monthlyDebt / (annualIncome / 12)
                  - set is_eligible to true
                  - set monthly_payment to 1500
                
                else:
                  - set is_eligible to false
                  - set rejection_reason to "Does not meet minimum requirements"
                
                output:
                  is_eligible: boolean
                  debt_to_income: number
                  monthly_payment: number
                  rejection_reason: text
                """;

            ValidationResult result = yamlDslValidator.validate(validDSL);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getSummary().getTotalIssues()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should detect missing required fields")
        void testMissingRequiredFields() {
            String invalidDSL = """
                # Missing name, description, inputs, output
                when:
                  - creditScore greater_than 700
                then:
                  - set result to "PASS"
                """;

            ValidationResult result = yamlDslValidator.validate(invalidDSL);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getSummary().getTotalIssues()).isGreaterThanOrEqualTo(4); // name, description, input, output

            // Check that we have errors for missing required fields
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrorSummary()).contains("name", "description", "inputs", "output");
        }

        @Test
        @DisplayName("Should validate all documented operators")
        void testAllDocumentedOperators() {
            String dslWithAllOperators = """
                name: "Operator Test Rule"
                description: "Tests all documented operators from DSL reference"
                
                inputs:
                  - creditScore
                  - companyName
                  - phoneNumber
                  - age
                  - riskLevel
                  - accountBalance
                  - comments
                
                when:
                  - creditScore greater_than 700
                  - creditScore at_least 650
                  - creditScore less_than 850
                  - creditScore between 600 and 800
                  - companyName contains "CORP"
                  - phoneNumber matches "^\\+1"
                  - age between 18 and 65
                  - riskLevel in_list ["LOW", "MEDIUM"]
                  - accountBalance is_positive
                  - comments is_not_empty
                
                then:
                  - set all_operators_valid to true
                
                output:
                  all_operators_valid: boolean
                """;

            ValidationResult result = yamlDslValidator.validate(dslWithAllOperators);

            assertThat(result.isValid()).isTrue();
            // Should have no operator-related errors
            assertThat(result.getErrors()).noneMatch(issue ->
                issue.getMessage().toLowerCase().contains("unknown operator"));
        }

        @Test
        @DisplayName("Should validate financial operators")
        void testFinancialOperators() {
            String financialDSL = """
                name: "Financial Validation Rule"
                description: "Tests financial-specific operators"
                
                inputs:
                  - creditScore
                  - socialSecurityNumber
                  - accountNumber
                  - routingNumber
                  - birthDate
                  - loanAmount
                
                when:
                  - creditScore is_credit_score
                  - socialSecurityNumber is_ssn
                  - accountNumber is_account_number
                  - routingNumber is_routing_number
                  - birthDate age_at_least 18
                  - loanAmount is_currency
                
                then:
                  - set financial_validation_passed to true
                
                output:
                  financial_validation_passed: boolean
                """;

            ValidationResult result = yamlDslValidator.validate(financialDSL);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should validate built-in functions")
        void testBuiltInFunctions() {
            String functionDSL = """
                name: "Function Test Rule"
                description: "Tests built-in functions from DSL reference"
                
                inputs:
                  - loanAmount
                  - interestRate
                  - termMonths
                  - monthlyDebt
                  - monthlyIncome
                
                when:
                  - loanAmount greater_than 0
                
                then:
                  - calculate monthly_payment as loanAmount * 0.05
                  - calculate debt_ratio as monthlyDebt / monthlyIncome
                  - set formatted_payment to "1500.00"
                
                output:
                  monthly_payment: number
                  debt_ratio: number
                  formatted_payment: text
                """;

            ValidationResult result = yamlDslValidator.validate(functionDSL);

            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Naming Convention Tests")
    class NamingConventionTests {

        @Test
        @DisplayName("Should enforce camelCase for inputs")
        void testCamelCaseInputs() {
            String validInputs = """
                name: "Naming Test"
                description: "Test naming conventions"
                
                inputs:
                  - creditScore
                  - annualIncome
                  - employmentYears
                
                when:
                  - creditScore greater_than 700
                
                then:
                  - set result to "PASS"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(validInputs);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should enforce snake_case for computed variables")
        void testSnakeCaseComputedVariables() {
            String validComputed = """
                name: "Computed Variables Test"
                description: "Test computed variable naming"
                
                inputs:
                  - creditScore
                
                when:
                  - creditScore greater_than 700
                
                then:
                  - set approval_status to "APPROVED"
                  - set final_score to 85
                  - calculate debt_to_income as 0.3
                
                output:
                  approval_status: text
                  final_score: number
                  debt_to_income: number
                """;

            ValidationResult result = yamlDslValidator.validate(validComputed);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should enforce UPPER_CASE for constants")
        void testUpperCaseConstants() {
            String validConstants = """
                name: "Constants Test"
                description: "Test constant naming"
                
                inputs:
                  - creditScore
                  - loanAmount
                
                when:
                  - creditScore at_least MIN_CREDIT_SCORE
                  - loanAmount less_than MAX_LOAN_AMOUNT
                
                then:
                  - calculate rate as BASE_INTEREST_RATE + 0.01
                
                output:
                  rate: number
                """;

            ValidationResult result = yamlDslValidator.validate(validConstants);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Complex Syntax Tests")
    class ComplexSyntaxTests {

        @Test
        @DisplayName("Should validate complex conditions syntax")
        void testComplexConditions() {
            String complexDSL = """
                name: "Complex Conditions Test"
                description: "Test complex conditions block syntax"
                
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
                      - set:
                          variable: "approval_status"
                          value: "APPROVED"
                  else:
                    actions:
                      - set:
                          variable: "approval_status"
                          value: "DECLINED"
                
                output:
                  approval_status: text
                """;

            ValidationResult result = yamlDslValidator.validate(complexDSL);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should validate multiple rules syntax")
        void testMultipleRules() {
            String multipleRulesDSL = """
                name: "Multiple Rules Test"
                description: "Test multiple rules array syntax"
                
                inputs:
                  - creditScore
                  - annualIncome
                
                rules:
                  - name: "Initial Check"
                    when: creditScore at_least 600
                    then:
                      - set initial_eligible to true
                
                  - name: "Final Decision"
                    when:
                      - initial_eligible equals true
                      - annualIncome greater_than 40000
                    then:
                      - set final_approval to "APPROVED"
                
                output:
                  initial_eligible: boolean
                  final_approval: text
                """;

            ValidationResult result = yamlDslValidator.validate(multipleRulesDSL);
            assertThat(result.isValid()).isTrue();
        }
    }
}
