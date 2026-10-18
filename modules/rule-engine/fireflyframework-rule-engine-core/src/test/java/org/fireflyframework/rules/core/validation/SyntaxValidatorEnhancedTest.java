package org.fireflyframework.rules.core.validation;

import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test enhanced syntax validation for new DSL patterns
 */
class SyntaxValidatorEnhancedTest {

    private SyntaxValidator syntaxValidator;

    @BeforeEach
    void setUp() {
        syntaxValidator = new SyntaxValidator();
    }

    @Test
    void testValidSetActionSyntax() {
        // Given - Valid set action
        String yamlContent = """
            name: "Test Set Action"
            inputs: [creditScore]
            when:
              - creditScore at_least 650
            then:
              - set approval_status to "APPROVED"
            output:
              approval_status: text
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isEmpty();
    }

    @Test
    void testInvalidSetActionSyntax() {
        // Given - Invalid set action (missing 'to')
        String yamlContent = """
            name: "Test Invalid Set Action"
            inputs: [creditScore]
            when:
              - creditScore at_least 650
            then:
              - set approval_status "APPROVED"
            output:
              approval_status: text
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isNotEmpty();
        assertThat(issues).anyMatch(issue -> 
            issue.getCode().equals("SYNTAX_016") && 
            issue.getMessage().contains("Invalid set syntax"));
    }

    @Test
    void testValidCalculateActionSyntax() {
        // Given - Valid calculate action with power operation
        String yamlContent = """
            name: "Test Calculate Action"
            inputs: [baseValue]
            when:
              - baseValue at_least 1
            then:
              - calculate square_value as baseValue ^ 2
              - calculate compound_interest as principal * (1 + rate) ^ years
            output:
              square_value: number
              compound_interest: number
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isEmpty();
    }

    @Test
    void testInvalidCalculateActionSyntax() {
        // Given - Invalid calculate action (missing 'as')
        String yamlContent = """
            name: "Test Invalid Calculate Action"
            inputs: [baseValue]
            when:
              - baseValue at_least 1
            then:
              - calculate square_value baseValue * baseValue
            output:
              square_value: number
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isNotEmpty();
        assertThat(issues).anyMatch(issue -> 
            issue.getCode().equals("SYNTAX_017") && 
            issue.getMessage().contains("Invalid calculate syntax"));
    }

    @Test
    void testValidArithmeticActionsSyntax() {
        // Given - Valid arithmetic actions
        String yamlContent = """
            name: "Test Arithmetic Actions"
            inputs: [balance, payment]
            when:
              - balance at_least 0
            then:
              - add payment to balance
              - subtract 10 from balance
              - multiply balance by 1.05
              - divide balance by 12
            output:
              balance: number
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isEmpty();
    }

    @Test
    void testInvalidArithmeticActionsSyntax() {
        // Given - Invalid arithmetic actions (wrong keywords)
        String yamlContent = """
            name: "Test Invalid Arithmetic Actions"
            inputs: [balance]
            when:
              - balance at_least 0
            then:
              - add payment into balance
              - subtract 10 away balance
              - multiply balance with 1.05
              - divide balance through 12
            output:
              balance: number
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).hasSize(4);
        assertThat(issues).anyMatch(issue -> issue.getCode().equals("SYNTAX_018"));
        assertThat(issues).anyMatch(issue -> issue.getCode().equals("SYNTAX_019"));
        assertThat(issues).anyMatch(issue -> issue.getCode().equals("SYNTAX_020"));
        assertThat(issues).anyMatch(issue -> issue.getCode().equals("SYNTAX_021"));
    }

    @Test
    void testValidCallActionWithSyntax() {
        // Given - Valid call action with 'with' keyword
        String yamlContent = """
            name: "Test Call Action"
            inputs: [monthlyDebt, annualIncome]
            when:
              - monthlyDebt at_least 0
            then:
              - call calculate_debt_ratio with [monthlyDebt, annualIncome / 12, "debt_to_income"]
              - call log with ["Processing application", "INFO"]
            output:
              debt_ratio: number
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isEmpty();
    }

    @Test
    void testInvalidCallActionSyntax() {
        // Given - Invalid call action (parameters not in array format)
        String yamlContent = """
            name: "Test Invalid Call Action"
            inputs: [monthlyDebt]
            when:
              - monthlyDebt at_least 0
            then:
              - call calculate_debt_ratio with monthlyDebt, 1000
            output:
              debt_ratio: number
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isNotEmpty();
        assertThat(issues).anyMatch(issue -> 
            issue.getCode().equals("SYNTAX_023") && 
            issue.getMessage().contains("Invalid call parameters"));
    }

    @Test
    void testValidConditionalActionSyntax() {
        // Given - Valid conditional action
        String yamlContent = """
            name: "Test Conditional Action"
            inputs: [creditScore]
            when:
              - creditScore at_least 0
            then:
              - if creditScore greater_than 700 then set risk_level to "LOW"
            output:
              risk_level: text
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isEmpty();
    }

    @Test
    void testInvalidConditionalActionSyntax() {
        // Given - Invalid conditional action (missing 'then')
        String yamlContent = """
            name: "Test Invalid Conditional Action"
            inputs: [creditScore]
            when:
              - creditScore at_least 0
            then:
              - if creditScore greater_than 700 set risk_level to "LOW"
            output:
              risk_level: text
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isNotEmpty();
        assertThat(issues).anyMatch(issue -> 
            issue.getCode().equals("SYNTAX_024") && 
            issue.getMessage().contains("Invalid conditional syntax"));
    }

    @Test
    void testDivisionByZeroWarning() {
        // Given - Division by zero
        String yamlContent = """
            name: "Test Division by Zero"
            inputs: [balance]
            when:
              - balance at_least 0
            then:
              - divide balance by 0
            output:
              balance: number
            """;

        // When
        List<ValidationResult.ValidationIssue> issues = syntaxValidator.validate(yamlContent);

        // Then
        assertThat(issues).isNotEmpty();
        assertThat(issues).anyMatch(issue -> 
            issue.getCode().equals("SYNTAX_022") && 
            issue.getSeverity() == ValidationResult.ValidationSeverity.WARNING &&
            issue.getMessage().contains("Potential division by zero"));
    }
}
