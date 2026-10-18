package org.fireflyframework.rules.core.validation;

import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for metadata validation in YamlDslValidator
 */
@DisplayName("Metadata Validation Tests")
class MetadataValidationTest {

    private YamlDslValidator yamlDslValidator;
    private SyntaxValidator syntaxValidator;
    private NamingConventionValidator namingValidator;
    private ASTRulesDSLParser astParser;

    @BeforeEach
    void setUp() {
        // Create mocked dependencies
        syntaxValidator = Mockito.mock(SyntaxValidator.class);
        namingValidator = Mockito.mock(NamingConventionValidator.class);
        DSLParser dslParser = new DSLParser(); // Use real DSLParser instead of mock
        astParser = new ASTRulesDSLParser(dslParser);
        
        // Create the validator
        yamlDslValidator = new YamlDslValidator(syntaxValidator, namingValidator, astParser);
        
        // Configure mocks for successful validation
        Mockito.when(syntaxValidator.validate(Mockito.anyString())).thenReturn(java.util.List.of());
        Mockito.when(namingValidator.isValidInputVariableName(Mockito.anyString())).thenReturn(true);
        Mockito.when(namingValidator.isValidConstantName(Mockito.anyString())).thenReturn(true);
        Mockito.when(namingValidator.isValidComputedVariableName(Mockito.anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Should validate correct metadata")
    void testValidMetadata() {
        String yamlRule = """
            name: "Valid Metadata Test"
            description: "Test rule with valid metadata"
            
            metadata:
              tags: ["test", "validation"]
              author: "Test Team"
              category: "Testing"
              priority: 1
              riskLevel: "LOW"
            
            inputs:
              - testValue
            
            when:
              - testValue greater_than 0
            
            then:
              - set result to "SUCCESS"
            
            output:
              result: text
            """;

        ValidationResult result = yamlDslValidator.validate(yamlRule);
        
        assertThat(result.isValid()).isTrue();
        // Should have no metadata-related errors
        assertThat(getAllIssues(result)).noneMatch(issue -> issue.getCode().startsWith("META_"));
    }

    @Test
    @DisplayName("Should detect invalid metadata tags format")
    void testInvalidTagsFormat() {
        String yamlRule = """
            name: "Invalid Tags Test"
            description: "Test rule with invalid tags format"
            
            metadata:
              tags: "not_a_list"
              author: "Test Team"
            
            inputs:
              - testValue
            
            when:
              - testValue greater_than 0
            
            then:
              - set result to "SUCCESS"
            
            output:
              result: text
            """;

        ValidationResult result = yamlDslValidator.validate(yamlRule);

        // Should have metadata validation warning
        assertThat(getAllIssues(result)).anyMatch(issue -> issue.getCode().equals("META_001"));
    }

    @Test
    @DisplayName("Should detect invalid risk level")
    void testInvalidRiskLevel() {
        String yamlRule = """
            name: "Invalid Risk Level Test"
            description: "Test rule with invalid risk level"
            
            metadata:
              tags: ["test"]
              riskLevel: "INVALID_LEVEL"
            
            inputs:
              - testValue
            
            when:
              - testValue greater_than 0
            
            then:
              - set result to "SUCCESS"
            
            output:
              result: text
            """;

        ValidationResult result = yamlDslValidator.validate(yamlRule);
        
        // Should have metadata validation info
        assertThat(getAllIssues(result)).anyMatch(issue -> issue.getCode().equals("META_007"));
    }

    @Test
    @DisplayName("Should detect empty author")
    void testEmptyAuthor() {
        String yamlRule = """
            name: "Empty Author Test"
            description: "Test rule with empty author"
            
            metadata:
              tags: ["test"]
              author: ""
            
            inputs:
              - testValue
            
            when:
              - testValue greater_than 0
            
            then:
              - set result to "SUCCESS"
            
            output:
              result: text
            """;

        ValidationResult result = yamlDslValidator.validate(yamlRule);
        
        // Should have metadata validation info
        assertThat(getAllIssues(result)).anyMatch(issue -> issue.getCode().equals("META_003"));
    }

    @Test
    @DisplayName("Should detect invalid priority type")
    void testInvalidPriorityType() {
        String yamlRule = """
            name: "Invalid Priority Test"
            description: "Test rule with invalid priority type"
            
            metadata:
              tags: ["test"]
              priority: "not_a_number"
            
            inputs:
              - testValue
            
            when:
              - testValue greater_than 0
            
            then:
              - set result to "SUCCESS"
            
            output:
              result: text
            """;

        ValidationResult result = yamlDslValidator.validate(yamlRule);
        
        // Should have metadata validation info
        assertThat(getAllIssues(result)).anyMatch(issue -> issue.getCode().equals("META_005"));
    }

    @Test
    @DisplayName("Should detect non-string tags in list")
    void testNonStringTagsInList() {
        String yamlRule = """
            name: "Non-String Tags Test"
            description: "Test rule with non-string tags in list"
            
            metadata:
              tags: ["valid_tag", 123, "another_valid_tag"]
            
            inputs:
              - testValue
            
            when:
              - testValue greater_than 0
            
            then:
              - set result to "SUCCESS"
            
            output:
              result: text
            """;

        ValidationResult result = yamlDslValidator.validate(yamlRule);
        
        // Should have metadata validation warning for non-string tag
        assertThat(getAllIssues(result)).anyMatch(issue -> issue.getCode().equals("META_002"));
    }

    @Test
    @DisplayName("Should detect empty category")
    void testEmptyCategory() {
        String yamlRule = """
            name: "Empty Category Test"
            description: "Test rule with empty category"
            
            metadata:
              category: ""
            
            inputs:
              - testValue
            
            when:
              - testValue greater_than 0
            
            then:
              - set result to "SUCCESS"
            
            output:
              result: text
            """;

        ValidationResult result = yamlDslValidator.validate(yamlRule);
        
        // Should have metadata validation info
        assertThat(getAllIssues(result)).anyMatch(issue -> issue.getCode().equals("META_004"));
    }

    @Test
    @DisplayName("Should accept valid risk levels")
    void testValidRiskLevels() {
        String[] validLevels = {"LOW", "MEDIUM", "HIGH", "CRITICAL", "low", "medium", "high", "critical"};
        
        for (String level : validLevels) {
            String yamlRule = String.format("""
                name: "Valid Risk Level Test"
                description: "Test rule with valid risk level: %s"
                
                metadata:
                  riskLevel: "%s"
                
                inputs:
                  - testValue
                
                when:
                  - testValue greater_than 0
                
                then:
                  - set result to "SUCCESS"
                
                output:
                  result: text
                """, level, level);

            ValidationResult result = yamlDslValidator.validate(yamlRule);
            
            // Should not have META_007 error for valid risk levels
            assertThat(getAllIssues(result))
                .noneMatch(issue -> issue.getCode().equals("META_007"))
                .as("Risk level %s should be valid", level);
        }
    }

    /**
     * Helper method to get all validation issues from a ValidationResult
     */
    private java.util.List<ValidationResult.ValidationIssue> getAllIssues(ValidationResult result) {
        java.util.List<ValidationResult.ValidationIssue> allIssues = new java.util.ArrayList<>();

        if (result.getIssues() != null) {
            if (result.getIssues().getSyntax() != null) allIssues.addAll(result.getIssues().getSyntax());
            if (result.getIssues().getNaming() != null) allIssues.addAll(result.getIssues().getNaming());
            if (result.getIssues().getDependencies() != null) allIssues.addAll(result.getIssues().getDependencies());
            if (result.getIssues().getLogic() != null) allIssues.addAll(result.getIssues().getLogic());
            if (result.getIssues().getPerformance() != null) allIssues.addAll(result.getIssues().getPerformance());
            if (result.getIssues().getBestPractices() != null) allIssues.addAll(result.getIssues().getBestPractices());
        }

        return allIssues;
    }
}
