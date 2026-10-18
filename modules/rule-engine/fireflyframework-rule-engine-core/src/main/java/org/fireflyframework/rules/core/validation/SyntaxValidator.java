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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates YAML syntax and DSL structure compliance
 */
@Component
@Slf4j
public class SyntaxValidator {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // Valid top-level fields in the DSL
    private static final Set<String> VALID_TOP_LEVEL_FIELDS = Set.of(
        "name", "description", "version", "metadata", "variables", "constants",
        "conditions", "output", "circuit_breaker", "inputs", "when", "then", 
        "else", "rules"
    );

    // Valid operators for conditions
    private static final Set<String> VALID_OPERATORS = Set.of(
        "equals", "not_equals", "greater_than", "less_than", "at_least", "at_most",
        "between", "not_between", "contains", "not_contains", "starts_with",
        "ends_with", "matches", "not_matches", "in_list", "not_in_list", "exists",
        // Basic validation operators
        "is_empty", "is_not_empty", "is_numeric", "is_not_numeric", "is_email",
        "is_phone", "is_date",
        // Financial validation operators
        "is_positive", "is_negative", "is_zero", "is_percentage", "is_currency",
        "is_credit_score", "is_ssn", "is_account_number", "is_routing_number",
        // Date/time validation operators
        "is_business_day", "is_weekend", "age_at_least", "age_less_than"
    );

    // Valid action keywords
    private static final Set<String> VALID_ACTION_KEYWORDS = Set.of(
        "set", "calculate", "add", "subtract", "multiply", "divide", "call", "if"
    );

    /**
     * Validate YAML syntax and DSL structure
     *
     * @param yamlContent the YAML content to validate
     * @return list of validation issues
     */
    public List<ValidationResult.ValidationIssue> validate(String yamlContent) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Basic null/empty checks
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            issues.add(createIssue("SYNTAX_001", ValidationResult.ValidationSeverity.CRITICAL,
                "Empty YAML content", "The YAML content is empty or null",
                "root", "Provide valid YAML DSL content"));
            return issues;
        }

        // Validate YAML syntax
        JsonNode rootNode;
        try {
            rootNode = yamlMapper.readTree(yamlContent);
        } catch (Exception e) {
            issues.add(createIssue("SYNTAX_002", ValidationResult.ValidationSeverity.CRITICAL,
                "Invalid YAML syntax", "YAML parsing failed: " + e.getMessage(),
                "root", "Fix YAML syntax errors"));
            return issues;
        }

        // Validate DSL structure
        issues.addAll(validateDslStructure(rootNode));

        // Validate required fields
        issues.addAll(validateRequiredFields(rootNode));

        // Validate field types
        issues.addAll(validateFieldTypes(rootNode));

        // Validate operators and keywords
        issues.addAll(validateOperatorsAndKeywords(rootNode));

        return issues;
    }

    /**
     * Validate overall DSL structure
     */
    private List<ValidationResult.ValidationIssue> validateDslStructure(JsonNode rootNode) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (!rootNode.isObject()) {
            issues.add(createIssue("SYNTAX_003", ValidationResult.ValidationSeverity.CRITICAL,
                "Invalid root structure", "Root element must be a YAML object",
                "root", "Ensure the YAML starts with key-value pairs"));
            return issues;
        }

        // Check for unknown top-level fields
        rootNode.fieldNames().forEachRemaining(fieldName -> {
            if (!VALID_TOP_LEVEL_FIELDS.contains(fieldName)) {
                issues.add(createIssue("SYNTAX_004", ValidationResult.ValidationSeverity.WARNING,
                    "Unknown field: " + fieldName, 
                    "Field '" + fieldName + "' is not a recognized DSL field",
                    "root." + fieldName, 
                    "Remove unknown field or check spelling. Valid fields: " + VALID_TOP_LEVEL_FIELDS));
            }
        });

        return issues;
    }

    /**
     * Validate required fields are present
     */
    private List<ValidationResult.ValidationIssue> validateRequiredFields(JsonNode rootNode) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Rule must have a name
        if (!rootNode.has("name") || rootNode.get("name").asText().trim().isEmpty()) {
            issues.add(createIssue("SYNTAX_005", ValidationResult.ValidationSeverity.ERROR,
                "Missing rule name", "Every rule must have a descriptive name",
                "root", "Add a 'name' field with a descriptive rule name"));
        }

        // Rule must have some form of logic
        boolean hasLogic = rootNode.has("when") || rootNode.has("conditions") || rootNode.has("rules");
        if (!hasLogic) {
            issues.add(createIssue("SYNTAX_006", ValidationResult.ValidationSeverity.ERROR,
                "Missing rule logic", "Rule must have 'when' conditions, 'conditions' block, or 'rules' array",
                "root", "Add rule logic using 'when', 'conditions', or 'rules'"));
        }

        // If using simplified syntax, must have 'then' when 'when' is present
        if (rootNode.has("when") && !rootNode.has("then")) {
            issues.add(createIssue("SYNTAX_007", ValidationResult.ValidationSeverity.ERROR,
                "Missing 'then' block", "When using 'when' conditions, must provide 'then' actions",
                "root", "Add a 'then' block with actions to execute"));
        }

        return issues;
    }

    /**
     * Validate field types are correct
     */
    private List<ValidationResult.ValidationIssue> validateFieldTypes(JsonNode rootNode) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Name should be string
        if (rootNode.has("name") && !rootNode.get("name").isTextual()) {
            issues.add(createIssue("SYNTAX_008", ValidationResult.ValidationSeverity.ERROR,
                "Invalid name type", "Rule name must be a string",
                "name", "Provide a string value for the rule name"));
        }

        // Inputs should be array
        if (rootNode.has("inputs") && !rootNode.get("inputs").isArray()) {
            issues.add(createIssue("SYNTAX_009", ValidationResult.ValidationSeverity.ERROR,
                "Invalid inputs type", "Inputs must be an array of variable names",
                "inputs", "Use array format: inputs: [var1, var2]"));
        }

        // When should be array
        if (rootNode.has("when") && !rootNode.get("when").isArray()) {
            issues.add(createIssue("SYNTAX_010", ValidationResult.ValidationSeverity.ERROR,
                "Invalid when type", "When conditions must be an array",
                "when", "Use array format: when: [condition1, condition2]"));
        }

        // Then should be array
        if (rootNode.has("then") && !rootNode.get("then").isArray()) {
            issues.add(createIssue("SYNTAX_011", ValidationResult.ValidationSeverity.ERROR,
                "Invalid then type", "Then actions must be an array",
                "then", "Use array format: then: [action1, action2]"));
        }

        return issues;
    }

    /**
     * Validate operators and action keywords
     */
    private List<ValidationResult.ValidationIssue> validateOperatorsAndKeywords(JsonNode rootNode) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate 'when' conditions
        if (rootNode.has("when") && rootNode.get("when").isArray()) {
            for (int i = 0; i < rootNode.get("when").size(); i++) {
                JsonNode condition = rootNode.get("when").get(i);
                if (condition.isTextual()) {
                    issues.addAll(validateConditionSyntax(condition.asText(), "when[" + i + "]"));
                }
            }
        }

        // Validate 'then' actions
        if (rootNode.has("then") && rootNode.get("then").isArray()) {
            for (int i = 0; i < rootNode.get("then").size(); i++) {
                JsonNode action = rootNode.get("then").get(i);
                if (action.isTextual()) {
                    issues.addAll(validateActionSyntax(action.asText(), "then[" + i + "]"));
                }
            }
        }

        return issues;
    }

    /**
     * Validate condition syntax
     */
    private List<ValidationResult.ValidationIssue> validateConditionSyntax(String condition, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (condition == null || condition.trim().isEmpty()) {
            issues.add(createIssue("SYNTAX_012", ValidationResult.ValidationSeverity.ERROR,
                "Empty condition", "Condition cannot be empty",
                location, "Provide a valid condition expression"));
            return issues;
        }

        // Check for valid operators
        boolean hasValidOperator = VALID_OPERATORS.stream()
            .anyMatch(op -> condition.contains(" " + op + " "));

        if (!hasValidOperator) {
            issues.add(createIssue("SYNTAX_013", ValidationResult.ValidationSeverity.WARNING,
                "Unrecognized operator", "Condition may contain unrecognized operator: " + condition,
                location, "Use valid operators: " + VALID_OPERATORS));
        }

        return issues;
    }

    /**
     * Validate action syntax
     */
    private List<ValidationResult.ValidationIssue> validateActionSyntax(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (action == null || action.trim().isEmpty()) {
            issues.add(createIssue("SYNTAX_014", ValidationResult.ValidationSeverity.ERROR,
                "Empty action", "Action cannot be empty",
                location, "Provide a valid action statement"));
            return issues;
        }

        String trimmedAction = action.trim();

        // Validate specific action patterns
        if (trimmedAction.startsWith("set ")) {
            issues.addAll(validateSetActionSyntax(trimmedAction, location));
        } else if (trimmedAction.startsWith("calculate ")) {
            issues.addAll(validateCalculateActionSyntax(trimmedAction, location));
        } else if (trimmedAction.startsWith("add ")) {
            issues.addAll(validateArithmeticActionSyntax(trimmedAction, "add", location));
        } else if (trimmedAction.startsWith("subtract ")) {
            issues.addAll(validateArithmeticActionSyntax(trimmedAction, "subtract", location));
        } else if (trimmedAction.startsWith("multiply ")) {
            issues.addAll(validateArithmeticActionSyntax(trimmedAction, "multiply", location));
        } else if (trimmedAction.startsWith("divide ")) {
            issues.addAll(validateDivideActionSyntax(trimmedAction, location));
        } else if (trimmedAction.startsWith("call ")) {
            issues.addAll(validateCallActionSyntax(trimmedAction, location));
        } else if (trimmedAction.startsWith("if ")) {
            issues.addAll(validateConditionalActionSyntax(trimmedAction, location));
        } else {
            // Check for valid action keywords
            boolean hasValidKeyword = VALID_ACTION_KEYWORDS.stream()
                .anyMatch(keyword -> trimmedAction.startsWith(keyword + " "));

            if (!hasValidKeyword) {
                issues.add(createIssue("SYNTAX_015", ValidationResult.ValidationSeverity.WARNING,
                    "Unrecognized action", "Action may contain unrecognized keyword: " + action,
                    location, "Use valid action keywords: " + VALID_ACTION_KEYWORDS));
            }
        }

        return issues;
    }

    /**
     * Validate set action syntax: "set variable to value"
     */
    private List<ValidationResult.ValidationIssue> validateSetActionSyntax(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (!action.contains(" to ")) {
            issues.add(createIssue("SYNTAX_016", ValidationResult.ValidationSeverity.ERROR,
                "Invalid set syntax", "Set action must use 'to' keyword: " + action,
                location, "Use format: set variable_name to value"));
        }

        return issues;
    }

    /**
     * Validate calculate action syntax: "calculate variable as expression"
     */
    private List<ValidationResult.ValidationIssue> validateCalculateActionSyntax(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (!action.contains(" as ")) {
            issues.add(createIssue("SYNTAX_017", ValidationResult.ValidationSeverity.ERROR,
                "Invalid calculate syntax", "Calculate action must use 'as' keyword: " + action,
                location, "Use format: calculate variable_name as expression"));
        }

        // Check for power operations (^) in expressions
        if (action.contains("^")) {
            // This is valid - power operations are supported
        }

        return issues;
    }

    /**
     * Validate arithmetic action syntax: "add/subtract/multiply value to/from/by variable"
     */
    private List<ValidationResult.ValidationIssue> validateArithmeticActionSyntax(String action, String operation, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        switch (operation) {
            case "add":
                if (!action.contains(" to ")) {
                    issues.add(createIssue("SYNTAX_018", ValidationResult.ValidationSeverity.ERROR,
                        "Invalid add syntax", "Add action must use 'to' keyword: " + action,
                        location, "Use format: add value to variable"));
                }
                break;
            case "subtract":
                if (!action.contains(" from ")) {
                    issues.add(createIssue("SYNTAX_019", ValidationResult.ValidationSeverity.ERROR,
                        "Invalid subtract syntax", "Subtract action must use 'from' keyword: " + action,
                        location, "Use format: subtract value from variable"));
                }
                break;
            case "multiply":
                if (!action.contains(" by ")) {
                    issues.add(createIssue("SYNTAX_020", ValidationResult.ValidationSeverity.ERROR,
                        "Invalid multiply syntax", "Multiply action must use 'by' keyword: " + action,
                        location, "Use format: multiply variable by value"));
                }
                break;
        }

        return issues;
    }

    /**
     * Validate divide action syntax: "divide variable by value"
     */
    private List<ValidationResult.ValidationIssue> validateDivideActionSyntax(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (!action.contains(" by ")) {
            issues.add(createIssue("SYNTAX_021", ValidationResult.ValidationSeverity.ERROR,
                "Invalid divide syntax", "Divide action must use 'by' keyword: " + action,
                location, "Use format: divide variable by value"));
        }

        // Check for potential division by zero
        if (action.contains(" by 0") || action.contains(" by 0.0")) {
            issues.add(createIssue("SYNTAX_022", ValidationResult.ValidationSeverity.WARNING,
                "Potential division by zero", "Division by zero detected: " + action,
                location, "Avoid dividing by zero"));
        }

        return issues;
    }

    /**
     * Validate call action syntax: "call function_name with [parameters]"
     */
    private List<ValidationResult.ValidationIssue> validateCallActionSyntax(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (action.contains(" with ")) {
            // New "call function_name with [parameters]" syntax
            String[] parts = action.substring(5).split(" with ", 2);
            if (parts.length == 2) {
                String parametersString = parts[1].trim();
                if (!parametersString.startsWith("[") || !parametersString.endsWith("]")) {
                    issues.add(createIssue("SYNTAX_023", ValidationResult.ValidationSeverity.ERROR,
                        "Invalid call parameters", "Call parameters must be in array format: " + action,
                        location, "Use format: call function_name with [param1, param2, ...]"));
                }
            }
        }
        // Note: Complex syntax validation is handled elsewhere

        return issues;
    }

    /**
     * Validate conditional action syntax: "if condition then action"
     */
    private List<ValidationResult.ValidationIssue> validateConditionalActionSyntax(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (!action.contains(" then ")) {
            issues.add(createIssue("SYNTAX_024", ValidationResult.ValidationSeverity.ERROR,
                "Invalid conditional syntax", "Conditional action must use 'then' keyword: " + action,
                location, "Use format: if condition then action"));
        }

        return issues;
    }

    /**
     * Helper method to create validation issues
     */
    private ValidationResult.ValidationIssue createIssue(
            String code, 
            ValidationResult.ValidationSeverity severity,
            String message, 
            String description, 
            String locationPath, 
            String suggestion) {
        
        return ValidationResult.ValidationIssue.builder()
            .code(code)
            .severity(severity)
            .message(message)
            .description(description)
            .location(ValidationResult.ValidationLocation.builder()
                .path(locationPath)
                .context(locationPath)
                .build())
            .suggestion(suggestion)
            .build();
    }
}
