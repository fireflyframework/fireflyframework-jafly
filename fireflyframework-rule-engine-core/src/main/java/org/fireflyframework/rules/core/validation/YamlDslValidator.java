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

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import org.fireflyframework.rules.core.dsl.action.*;
import org.fireflyframework.rules.core.dsl.condition.ComparisonCondition;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.condition.ExpressionCondition;
import org.fireflyframework.rules.core.dsl.condition.LogicalCondition;
import org.fireflyframework.rules.core.dsl.expression.*;
import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.visitor.ValidationError;
import org.fireflyframework.rules.core.dsl.visitor.ValidationVisitor;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive YAML DSL validator that orchestrates all validation components.
 * Provides syntax, semantic, naming convention, and best practice validation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YamlDslValidator {
    
    private final SyntaxValidator syntaxValidator;
    private final NamingConventionValidator namingValidator;
    private final ASTRulesDSLParser astParser;
    
    /**
     * Perform comprehensive validation of YAML DSL content
     *
     * @param yamlContent the YAML content to validate
     * @return comprehensive validation result
     */
    public ValidationResult validate(String yamlContent) {
        log.debug("Starting comprehensive YAML DSL validation");
        
        List<ValidationResult.ValidationIssue> allIssues = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Syntax validation (must pass before other validations)
            List<ValidationResult.ValidationIssue> syntaxIssues = syntaxValidator.validate(yamlContent);
            allIssues.addAll(syntaxIssues);
            
            // If syntax validation fails critically, stop here
            boolean hasCriticalSyntaxErrors = syntaxIssues.stream()
                    .anyMatch(issue -> issue.getSeverity() == ValidationResult.ValidationSeverity.CRITICAL);
            
            if (!hasCriticalSyntaxErrors) {
                // 2. Parse to AST for semantic validation
                try {
                    @SuppressWarnings("deprecation")
                    ASTRulesDSL rulesDSL = astParser.parseRules(yamlContent);
                    
                    // 3. DSL Reference Compliance validation
                    allIssues.addAll(performDSLReferenceValidation(rulesDSL));

                    // 4. Semantic validation using AST
                    allIssues.addAll(performSemanticValidation(rulesDSL));

                    // 5. Naming convention validation
                    allIssues.addAll(performNamingValidation(rulesDSL));

                    // 6. Operator and function validation
                    allIssues.addAll(performOperatorValidation(rulesDSL));

                    // 7. Best practices validation
                    allIssues.addAll(performBestPracticesValidation(rulesDSL));
                    
                } catch (Exception e) {
                    log.warn("Failed to parse YAML for semantic validation: {}", e.getMessage());
                    allIssues.add(createIssue("PARSE_001", ValidationResult.ValidationSeverity.ERROR,
                            "Parsing failed", "Could not parse YAML for semantic validation: " + e.getMessage(),
                            "root", "Fix syntax errors first"));
                }
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Build result
            return buildValidationResult(allIssues, executionTime);
            
        } catch (Exception e) {
            log.error("Validation failed with unexpected error", e);
            allIssues.add(createIssue("VAL_FATAL", ValidationResult.ValidationSeverity.CRITICAL,
                    "Validation failed", "Unexpected validation error: " + e.getMessage(),
                    "root", "Check YAML content and try again"));
            
            return buildValidationResult(allIssues, System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Perform semantic validation using AST
     */
    private List<ValidationResult.ValidationIssue> performSemanticValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        try {
            // Extract available variables and functions
            Set<String> availableVariables = extractAvailableVariables(rulesDSL);
            Set<String> availableFunctions = getBuiltInFunctions();
            
            // Create validation visitor
            ValidationVisitor validator = new ValidationVisitor(availableVariables, availableFunctions);
            
            // Validate all conditions
            if (rulesDSL.getWhenConditions() != null) {
                for (var condition : rulesDSL.getWhenConditions()) {
                    if (condition != null) {
                        List<ValidationError> errors = condition.accept(validator);
                        issues.addAll(convertValidationErrors(errors));
                    } else {
                        // Handle null condition - this indicates a parsing issue
                        issues.add(createIssue("PARSE_001", ValidationResult.ValidationSeverity.ERROR,
                                "Failed to parse condition", "One or more conditions could not be parsed",
                                "when", "Check condition syntax and ensure all operators are supported"));
                    }
                }
            }

            // Validate complex conditions block
            if (rulesDSL.getConditions() != null) {
                issues.addAll(validateComplexConditionsBlock(rulesDSL.getConditions(), validator));
            }

            // Validate multiple rules (complex syntax)
            if (rulesDSL.getRules() != null) {
                for (int i = 0; i < rulesDSL.getRules().size(); i++) {
                    var subRule = rulesDSL.getRules().get(i);
                    issues.addAll(validateSubRule(subRule, validator, i));
                }
            }
            
            // Validate all actions
            if (rulesDSL.getThenActions() != null) {
                for (var action : rulesDSL.getThenActions()) {
                    if (action != null) {
                        List<ValidationError> errors = action.accept(validator);
                        issues.addAll(convertValidationErrors(errors));
                    } else {
                        issues.add(createIssue("PARSE_002", ValidationResult.ValidationSeverity.ERROR,
                                "Failed to parse action", "One or more actions could not be parsed",
                                "then", "Check action syntax and ensure all functions are supported"));
                    }
                }
            }

            if (rulesDSL.getElseActions() != null) {
                for (var action : rulesDSL.getElseActions()) {
                    if (action != null) {
                        List<ValidationError> errors = action.accept(validator);
                        issues.addAll(convertValidationErrors(errors));
                    } else {
                        issues.add(createIssue("PARSE_003", ValidationResult.ValidationSeverity.ERROR,
                                "Failed to parse action", "One or more else actions could not be parsed",
                                "else", "Check action syntax and ensure all functions are supported"));
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Semantic validation failed: {}", e.getMessage());
            issues.add(createIssue("SEM_001", ValidationResult.ValidationSeverity.WARNING,
                    "Semantic validation incomplete", "Could not complete semantic validation: " + e.getMessage(),
                    "root", "Review rule structure"));
        }
        
        return issues;
    }
    
    /**
     * Perform naming convention validation
     */
    private List<ValidationResult.ValidationIssue> performNamingValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        // Validate input variable names (should be camelCase)
        if (rulesDSL.getInput() != null) {
            for (String inputName : rulesDSL.getInput().keySet()) {
                if (!namingValidator.isValidInputVariableName(inputName)) {
                    issues.add(createIssue("NAMING_001", ValidationResult.ValidationSeverity.WARNING,
                            "Invalid input variable name", 
                            "Input variable '" + inputName + "' should use camelCase (e.g., creditScore)",
                            "input." + inputName, "Use camelCase naming"));
                }
            }
        }
        
        // Validate constant names (should be UPPER_CASE)
        if (rulesDSL.getConstants() != null) {
            for (var constant : rulesDSL.getConstants()) {
                if (constant.getCode() != null && !namingValidator.isValidConstantName(constant.getCode())) {
                    issues.add(createIssue("NAMING_002", ValidationResult.ValidationSeverity.WARNING,
                            "Invalid constant name",
                            "Constant '" + constant.getCode() + "' should use UPPER_CASE_WITH_UNDERSCORES",
                            "constants." + constant.getCode(), "Use UPPER_CASE naming"));
                }
            }
        }
        
        return issues;
    }
    
    /**
     * Perform best practices validation
     */
    private List<ValidationResult.ValidationIssue> performBestPracticesValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        // Check for missing description
        if (rulesDSL.getDescription() == null || rulesDSL.getDescription().trim().isEmpty()) {
            issues.add(createIssue("BP_001", ValidationResult.ValidationSeverity.INFO,
                    "Missing description", "Rule should have a description for maintainability",
                    "description", "Add a meaningful description"));
        }
        
        // Check for missing version
        if (rulesDSL.getVersion() == null || rulesDSL.getVersion().trim().isEmpty()) {
            issues.add(createIssue("BP_002", ValidationResult.ValidationSeverity.INFO,
                    "Missing version", "Rule should have a version for tracking changes",
                    "version", "Add a version number"));
        }
        
        return issues;
    }

    /**
     * Validate complex conditions block
     */
    private List<ValidationResult.ValidationIssue> validateComplexConditionsBlock(
            ASTRulesDSL.ASTConditionalBlock conditionsBlock, ValidationVisitor validator) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        try {
            // Validate the if condition
            if (conditionsBlock.getIfCondition() != null) {
                List<ValidationError> errors = conditionsBlock.getIfCondition().accept(validator);
                issues.addAll(convertValidationErrors(errors));
            } else {
                issues.add(createIssue("COMPLEX_001", ValidationResult.ValidationSeverity.ERROR,
                        "Missing if condition", "Complex conditions block must have an 'if' condition",
                        "conditions.if", "Add an 'if' condition to the conditions block"));
            }

            // Validate then actions
            if (conditionsBlock.getThenBlock() != null) {
                issues.addAll(validateActionBlock(conditionsBlock.getThenBlock(), validator, "conditions.then"));
            } else {
                issues.add(createIssue("COMPLEX_002", ValidationResult.ValidationSeverity.ERROR,
                        "Missing then block", "Complex conditions block must have a 'then' block",
                        "conditions.then", "Add a 'then' block with actions"));
            }

            // Validate else actions (optional)
            if (conditionsBlock.getElseBlock() != null) {
                issues.addAll(validateActionBlock(conditionsBlock.getElseBlock(), validator, "conditions.else"));
            }

        } catch (Exception e) {
            log.warn("Complex conditions validation failed: {}", e.getMessage());
            issues.add(createIssue("COMPLEX_003", ValidationResult.ValidationSeverity.WARNING,
                    "Complex conditions validation incomplete", "Could not complete complex conditions validation: " + e.getMessage(),
                    "conditions", "Review conditions block structure"));
        }

        return issues;
    }

    /**
     * Validate action block (used in complex conditions)
     */
    private List<ValidationResult.ValidationIssue> validateActionBlock(
            ASTRulesDSL.ASTActionBlock actionBlock, ValidationVisitor validator, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate actions
        if (actionBlock.getActions() != null) {
            for (var action : actionBlock.getActions()) {
                if (action != null) {
                    List<ValidationError> errors = action.accept(validator);
                    issues.addAll(convertValidationErrors(errors));
                } else {
                    issues.add(createIssue("ACTION_BLOCK_001", ValidationResult.ValidationSeverity.ERROR,
                            "Failed to parse action", "One or more actions could not be parsed",
                            location, "Check action syntax"));
                }
            }
        }

        // Validate nested conditions (if present)
        if (actionBlock.getNestedConditions() != null) {
            issues.addAll(validateComplexConditionsBlock(actionBlock.getNestedConditions(), validator));
        }

        return issues;
    }

    /**
     * Validate sub-rule (used in multiple rules syntax)
     */
    private List<ValidationResult.ValidationIssue> validateSubRule(
            ASTRulesDSL.ASTSubRule subRule, ValidationVisitor validator, int ruleIndex) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        String ruleLocation = "rules[" + ruleIndex + "]";

        // Validate rule name
        if (subRule.getName() == null || subRule.getName().trim().isEmpty()) {
            issues.add(createIssue("SUB_RULE_001", ValidationResult.ValidationSeverity.WARNING,
                    "Missing sub-rule name", "Sub-rule should have a descriptive name",
                    ruleLocation + ".name", "Add a meaningful name for this sub-rule"));
        }

        // Validate that sub-rule has either simple or complex syntax
        boolean hasSimpleSyntax = subRule.getWhenConditions() != null && !subRule.getWhenConditions().isEmpty();
        boolean hasComplexSyntax = subRule.getConditions() != null;

        if (!hasSimpleSyntax && !hasComplexSyntax) {
            issues.add(createIssue("SUB_RULE_002", ValidationResult.ValidationSeverity.ERROR,
                    "Missing rule logic", "Sub-rule must have either 'when' conditions or 'conditions' block",
                    ruleLocation, "Add 'when' conditions or 'conditions' block"));
        }

        // Validate simple syntax
        if (hasSimpleSyntax) {
            for (var condition : subRule.getWhenConditions()) {
                if (condition != null) {
                    List<ValidationError> errors = condition.accept(validator);
                    issues.addAll(convertValidationErrors(errors));
                }
            }

            // Validate then actions
            if (subRule.getThenActions() != null) {
                for (var action : subRule.getThenActions()) {
                    if (action != null) {
                        List<ValidationError> errors = action.accept(validator);
                        issues.addAll(convertValidationErrors(errors));
                    }
                }
            }

            // Validate else actions
            if (subRule.getElseActions() != null) {
                for (var action : subRule.getElseActions()) {
                    if (action != null) {
                        List<ValidationError> errors = action.accept(validator);
                        issues.addAll(convertValidationErrors(errors));
                    }
                }
            }
        }

        // Validate complex syntax
        if (hasComplexSyntax) {
            issues.addAll(validateComplexConditionsBlock(subRule.getConditions(), validator));
        }

        return issues;
    }

    /**
     * Extract available variables from the rule definition
     * This includes input variables, constants, and computed variables from all rules
     */
    private Set<String> extractAvailableVariables(ASTRulesDSL rulesDSL) {
        Set<String> variables = new HashSet<>();

        // Add input variables
        if (rulesDSL.getInput() != null) {
            variables.addAll(rulesDSL.getInput().keySet());
        }

        // Add explicitly declared constants
        if (rulesDSL.getConstants() != null) {
            Set<String> explicitConstants = rulesDSL.getConstants().stream()
                    .map(ASTRulesDSL.ASTConstantDefinition::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            variables.addAll(explicitConstants);
        }

        // Add database constants - auto-detect UPPER_CASE variables
        // These are constants that follow the UPPER_CASE_WITH_UNDERSCORES naming convention
        // and are automatically loaded from the database at runtime
        Set<String> detectedConstants = extractConstantReferences(rulesDSL);
        variables.addAll(detectedConstants);

        // Add computed variables from all rules (critical for multi-rule DSL)
        // Variables computed in earlier rules are available in later rules
        Set<String> computedVariables = extractComputedVariables(rulesDSL);
        variables.addAll(computedVariables);

        return variables;
    }

    /**
     * Extract computed variables from all rules in the DSL
     * This includes variables created by 'set' and 'calculate' actions across all rules
     */
    private Set<String> extractComputedVariables(ASTRulesDSL rulesDSL) {
        Set<String> computedVariables = new HashSet<>();

        // Collect from simple syntax (when/then/else)
        if (rulesDSL.getThenActions() != null) {
            computedVariables.addAll(extractVariablesFromActions(rulesDSL.getThenActions()));
        }
        if (rulesDSL.getElseActions() != null) {
            computedVariables.addAll(extractVariablesFromActions(rulesDSL.getElseActions()));
        }

        // Collect from complex conditions syntax
        if (rulesDSL.getConditions() != null) {
            computedVariables.addAll(extractVariablesFromComplexConditions(rulesDSL.getConditions()));
        }

        // Collect from multiple rules syntax
        if (rulesDSL.getRules() != null) {
            for (var rule : rulesDSL.getRules()) {
                if (rule.getThenActions() != null) {
                    computedVariables.addAll(extractVariablesFromActions(rule.getThenActions()));
                }
                if (rule.getElseActions() != null) {
                    computedVariables.addAll(extractVariablesFromActions(rule.getElseActions()));
                }
                if (rule.getConditions() != null) {
                    computedVariables.addAll(extractVariablesFromComplexConditions(rule.getConditions()));
                }
            }
        }

        return computedVariables;
    }

    /**
     * Extract variables created by actions (set, calculate, run, etc.)
     */
    private Set<String> extractVariablesFromActions(List<Action> actions) {
        Set<String> variables = new HashSet<>();

        for (Action action : actions) {
            if (action instanceof SetAction setAction) {
                variables.add(setAction.getVariableName());
            } else if (action instanceof CalculateAction calculateAction) {
                variables.add(calculateAction.getResultVariable());
            } else if (action instanceof RunAction runAction) {
                // Run actions create computed variables (for function calls, REST calls, JSON operations)
                variables.add(runAction.getResultVariable());
            } else if (action instanceof ListAction listAction) {
                // List actions like append, prepend, remove also create/modify variables
                variables.add(listAction.getListVariable());
            } else if (action instanceof ArithmeticAction arithmeticAction) {
                // Arithmetic actions modify existing variables
                variables.add(arithmeticAction.getVariableName());
            } else if (action instanceof FunctionCallAction functionCallAction) {
                // Function calls can optionally store results in variables
                if (functionCallAction.getResultVariable() != null) {
                    variables.add(functionCallAction.getResultVariable());
                }
            }
            // Note: Other action types like ConditionalAction, CircuitBreakerAction don't create variables
        }

        return variables;
    }

    /**
     * Extract variables from complex conditions blocks
     */
    private Set<String> extractVariablesFromComplexConditions(ASTRulesDSL.ASTConditionalBlock conditions) {
        Set<String> variables = new HashSet<>();

        if (conditions.getThenBlock() != null && conditions.getThenBlock().getActions() != null) {
            variables.addAll(extractVariablesFromActions(conditions.getThenBlock().getActions()));
        }
        if (conditions.getElseBlock() != null && conditions.getElseBlock().getActions() != null) {
            variables.addAll(extractVariablesFromActions(conditions.getElseBlock().getActions()));
        }

        return variables;
    }

    /**
     * Extract constant references by scanning the AST for UPPER_CASE variable references
     * This mirrors the logic in ASTRulesEvaluationEngine.extractConstantReferences()
     */
    private Set<String> extractConstantReferences(ASTRulesDSL rulesDSL) {
        // Use a visitor to collect all variable references
        ValidationVariableCollector collector = new ValidationVariableCollector();

        // Collect from simple syntax (when/then/else)
        if (rulesDSL.getWhenConditions() != null) {
            for (var condition : rulesDSL.getWhenConditions()) {
                if (condition != null) {
                    condition.accept(collector);
                }
            }
        }

        if (rulesDSL.getThenActions() != null) {
            for (var action : rulesDSL.getThenActions()) {
                if (action != null) {
                    action.accept(collector);
                }
            }
        }

        if (rulesDSL.getElseActions() != null) {
            for (var action : rulesDSL.getElseActions()) {
                if (action != null) {
                    action.accept(collector);
                }
            }
        }

        // Collect from complex conditions syntax
        if (rulesDSL.getConditions() != null) {
            collectFromComplexConditions(rulesDSL.getConditions(), collector);
        }

        // Collect from multiple rules syntax
        if (rulesDSL.getRules() != null) {
            for (var rule : rulesDSL.getRules()) {
                if (rule.getWhenConditions() != null) {
                    for (var condition : rule.getWhenConditions()) {
                        if (condition != null) {
                            condition.accept(collector);
                        }
                    }
                }
                if (rule.getThenActions() != null) {
                    for (var action : rule.getThenActions()) {
                        if (action != null) {
                            action.accept(collector);
                        }
                    }
                }
                if (rule.getElseActions() != null) {
                    for (var action : rule.getElseActions()) {
                        if (action != null) {
                            action.accept(collector);
                        }
                    }
                }
                if (rule.getConditions() != null) {
                    collectFromComplexConditions(rule.getConditions(), collector);
                }
            }
        }

        // Get all variable references and filter for UPPER_CASE constants (database constants)
        return collector.getVariableReferences().stream()
                .filter(this::isConstantReference)
                .collect(Collectors.toSet());
    }

    /**
     * Collect variable references from complex conditions blocks
     */
    private void collectFromComplexConditions(ASTRulesDSL.ASTConditionalBlock conditions, ValidationVariableCollector collector) {
        if (conditions.getIfCondition() != null) {
            conditions.getIfCondition().accept(collector);
        }
        if (conditions.getThenBlock() != null && conditions.getThenBlock().getActions() != null) {
            for (var action : conditions.getThenBlock().getActions()) {
                if (action != null) {
                    action.accept(collector);
                }
            }
        }
        if (conditions.getElseBlock() != null && conditions.getElseBlock().getActions() != null) {
            for (var action : conditions.getElseBlock().getActions()) {
                if (action != null) {
                    action.accept(collector);
                }
            }
        }
    }

    /**
     * Check if a variable reference is a constant (UPPER_CASE_WITH_UNDERSCORES)
     */
    private boolean isConstantReference(String variableName) {
        if (variableName == null || variableName.isEmpty()) {
            return false;
        }

        // Constants follow UPPER_CASE_WITH_UNDERSCORES naming convention
        // Must start with uppercase letter and contain only uppercase letters, numbers, and underscores
        return variableName.matches("^[A-Z][A-Z0-9_]*$");
    }

    /**
     * Simple visitor class to collect all variable references from AST nodes
     * This is used for validation purposes to detect database constants
     */
    private static class ValidationVariableCollector implements ASTVisitor<Void> {
        private final Set<String> variableReferences = new HashSet<>();

        public Set<String> getVariableReferences() {
            return variableReferences;
        }

        @Override
        public Void visitVariableExpression(VariableExpression node) {
            variableReferences.add(node.getVariableName());
            return null;
        }

        // Default implementations for all other visitor methods
        @Override
        public Void visitBinaryExpression(BinaryExpression node) {
            if (node.getLeft() != null) node.getLeft().accept(this);
            if (node.getRight() != null) node.getRight().accept(this);
            return null;
        }

        @Override
        public Void visitUnaryExpression(UnaryExpression node) {
            if (node.getOperand() != null) node.getOperand().accept(this);
            return null;
        }

        @Override
        public Void visitLiteralExpression(LiteralExpression node) {
            return null; // Literals don't contain variable references
        }

        @Override
        public Void visitFunctionCallExpression(FunctionCallExpression node) {
            if (node.getArguments() != null) {
                node.getArguments().forEach(arg -> arg.accept(this));
            }
            return null;
        }

        @Override
        public Void visitComparisonCondition(ComparisonCondition node) {
            if (node.getLeft() != null) node.getLeft().accept(this);
            if (node.getRight() != null) node.getRight().accept(this);
            return null;
        }

        @Override
        public Void visitLogicalCondition(LogicalCondition node) {
            if (node.getOperands() != null) {
                node.getOperands().forEach(operand -> operand.accept(this));
            }
            return null;
        }

        @Override
        public Void visitExpressionCondition(ExpressionCondition node) {
            if (node.getExpression() != null) node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitFunctionCallAction(FunctionCallAction node) {
            if (node.getArguments() != null) {
                node.getArguments().forEach(arg -> arg.accept(this));
            }
            return null;
        }

        @Override
        public Void visitConditionalAction(ConditionalAction node) {
            if (node.getCondition() != null) node.getCondition().accept(this);
            if (node.getThenActions() != null) {
                node.getThenActions().forEach(action -> action.accept(this));
            }
            if (node.getElseActions() != null) {
                node.getElseActions().forEach(action -> action.accept(this));
            }
            return null;
        }

        @Override
        public Void visitCalculateAction(CalculateAction node) {
            if (node.getExpression() != null) node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitRunAction(RunAction node) {
            if (node.getExpression() != null) node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitSetAction(SetAction node) {
            if (node.getValue() != null) node.getValue().accept(this);
            return null;
        }

        @Override
        public Void visitArithmeticAction(ArithmeticAction node) {
            if (node.getValue() != null) node.getValue().accept(this);
            return null;
        }

        @Override
        public Void visitListAction(ListAction node) {
            if (node.getValue() != null) {
                node.getValue().accept(this);
            }
            return null;
        }

        @Override
        public Void visitCircuitBreakerAction(CircuitBreakerAction node) {
            // Circuit breaker actions don't contain variable references
            return null;
        }

        @Override
        public Void visitForEachAction(ForEachAction node) {
            // Collect variable references from list expression
            if (node.getListExpression() != null) {
                node.getListExpression().accept(this);
            }

            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            return null;
        }

        @Override
        public Void visitWhileAction(WhileAction node) {
            // Collect variable references from condition
            if (node.getCondition() != null) {
                node.getCondition().accept(this);
            }

            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            return null;
        }

        @Override
        public Void visitDoWhileAction(DoWhileAction node) {
            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            // Collect variable references from condition
            if (node.getCondition() != null) {
                node.getCondition().accept(this);
            }

            return null;
        }

        @Override
        public Void visitJsonPathExpression(JsonPathExpression node) {
            // Visit the source expression to collect any variable references
            if (node.getSourceExpression() != null) {
                node.getSourceExpression().accept(this);
            }
            return null;
        }

        @Override
        public Void visitRestCallExpression(RestCallExpression node) {
            // Visit all expressions to collect any variable references
            if (node.getUrlExpression() != null) {
                node.getUrlExpression().accept(this);
            }
            if (node.getBodyExpression() != null) {
                node.getBodyExpression().accept(this);
            }
            if (node.getHeadersExpression() != null) {
                node.getHeadersExpression().accept(this);
            }
            if (node.getTimeoutExpression() != null) {
                node.getTimeoutExpression().accept(this);
            }
            return null;
        }
    }
    
    /**
     * Get built-in function names
     */
    private Set<String> getBuiltInFunctions() {
        return Set.of(
                // Mathematical functions
                "max", "min", "abs", "round", "ceil", "floor", "sqrt", "pow",
                // String functions
                "length", "len", "substring", "substr", "upper", "lower", "trim",
                "contains", "startswith", "endswith", "replace",
                // Date/time functions
                "now", "today", "dateadd", "datediff", "time_hour", "format_date", "calculate_age",
                // List functions
                "size", "count", "sum", "avg", "average", "first", "last",
                // Type conversion functions
                "tonumber", "tostring", "toboolean", "number", "string", "boolean",
                // Financial calculation functions
                "calculate_loan_payment", "calculate_compound_interest", "calculate_amortization",
                "debt_to_income_ratio", "credit_utilization", "loan_to_value", "calculate_apr",
                "calculate_credit_score", "calculate_risk_score", "payment_history_score",
                // Financial validation functions
                "is_valid_credit_score", "is_valid_ssn", "is_valid_account", "is_valid_routing",
                "is_business_day", "age_meets_requirement", "validate_email", "validate_phone",
                // Utility functions
                "format_currency", "format_percentage", "generate_account_number",
                "generate_transaction_id", "distance_between", "is_valid", "in_range",
                // Logging/Auditing functions
                "audit", "audit_log", "send_notification", "log",
                // Data Security functions
                "encrypt", "decrypt", "mask_data",
                // Advanced Financial functions
                "calculate_debt_ratio", "calculate_ltv", "calculate_payment_schedule",
                // REST API functions
                "rest_get", "rest_post", "rest_put", "rest_delete", "rest_patch", "rest_call",
                // JSON path functions
                "json_get", "json_path", "json_exists", "json_size", "json_type"
        );
    }
    
    /**
     * Convert ValidationError objects to ValidationIssue objects
     */
    private List<ValidationResult.ValidationIssue> convertValidationErrors(List<ValidationError> errors) {
        return errors.stream()
                .map(error -> createIssueWithLocation(error.getErrorCode(), ValidationResult.ValidationSeverity.ERROR,
                        "Semantic error", error.getMessage(), error.getLocation(), "Fix the error"))
                .collect(Collectors.toList());
    }

    /**
     * Create a validation issue
     */
    private ValidationResult.ValidationIssue createIssue(String code, ValidationResult.ValidationSeverity severity,
                                                         String description, String message, String location, String suggestion) {
        return ValidationResult.ValidationIssue.builder()
                .code(code)
                .severity(severity)
                .description(description)
                .message(message)
                .location(ValidationResult.ValidationLocation.builder()
                        .path(location)
                        .build())
                .suggestion(suggestion)
                .build();
    }

    /**
     * Create a validation issue with enhanced location information
     */
    private ValidationResult.ValidationIssue createIssueWithLocation(String code, ValidationResult.ValidationSeverity severity,
                                                                     String description, String message, SourceLocation sourceLocation, String suggestion) {
        ValidationResult.ValidationLocation.ValidationLocationBuilder locationBuilder = ValidationResult.ValidationLocation.builder();

        if (sourceLocation != null) {
            // Use the SourceLocation information for better accuracy
            locationBuilder.path(sourceLocation.toString());
            locationBuilder.lineNumber(sourceLocation.getLine());

            // Determine section based on context or provide contextual information
            if (sourceLocation.hasContext()) {
                locationBuilder.context(sourceLocation.getContextualText(2));
            }

            // Try to determine the section based on the error message or context
            String section = determineSectionFromContext(message, sourceLocation);
            if (section != null) {
                locationBuilder.section(section);
            }
        } else {
            locationBuilder.path("unknown");
        }

        return ValidationResult.ValidationIssue.builder()
                .code(code)
                .severity(severity)
                .description(description)
                .message(message)
                .location(locationBuilder.build())
                .suggestion(suggestion)
                .build();
    }

    /**
     * Determine the YAML section based on error context
     */
    private String determineSectionFromContext(String message, SourceLocation sourceLocation) {
        if (message.contains("variable")) {
            return "when/then"; // Variables are typically used in conditions or actions
        } else if (message.contains("function")) {
            return "then"; // Functions are typically used in actions
        } else if (message.contains("operator")) {
            return "when"; // Operators are typically used in conditions
        }
        return null;
    }
    
    /**
     * Build the final validation result
     */
    private ValidationResult buildValidationResult(List<ValidationResult.ValidationIssue> issues, long executionTime) {
        // Determine overall status
        ValidationResult.ValidationStatus status = ValidationResult.ValidationStatus.VALID;
        if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationResult.ValidationSeverity.CRITICAL)) {
            status = ValidationResult.ValidationStatus.CRITICAL_ERROR;
        } else if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationResult.ValidationSeverity.ERROR)) {
            status = ValidationResult.ValidationStatus.ERROR;
        } else if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationResult.ValidationSeverity.WARNING)) {
            status = ValidationResult.ValidationStatus.WARNING;
        }

        // Calculate quality score (0-100)
        double qualityScore = calculateQualityScore(issues);

        // Build summary
        ValidationResult.ValidationSummary summary = ValidationResult.ValidationSummary.builder()
                .totalIssues(issues.size())
                .criticalErrors((int) issues.stream().filter(i -> i.getSeverity() == ValidationResult.ValidationSeverity.CRITICAL).count())
                .errors((int) issues.stream().filter(i -> i.getSeverity() == ValidationResult.ValidationSeverity.ERROR).count())
                .warnings((int) issues.stream().filter(i -> i.getSeverity() == ValidationResult.ValidationSeverity.WARNING).count())
                .suggestions((int) issues.stream().filter(i -> i.getSeverity() == ValidationResult.ValidationSeverity.INFO).count())
                .qualityScore(qualityScore)
                .build();

        // Group issues by category
        ValidationResult.ValidationIssues groupedIssues = ValidationResult.ValidationIssues.builder()
                .syntax(issues.stream().filter(i -> i.getCode().startsWith("SYNTAX")).collect(Collectors.toList()))
                .naming(issues.stream().filter(i -> i.getCode().startsWith("NAMING")).collect(Collectors.toList()))
                .logic(issues.stream().filter(i -> i.getCode().startsWith("SEM") || i.getCode().startsWith("VAL") || i.getCode().startsWith("PARSE") || i.getCode().startsWith("DSL_REF") || i.getCode().startsWith("META")).collect(Collectors.toList()))
                .bestPractices(issues.stream().filter(i -> i.getCode().startsWith("BP")).collect(Collectors.toList()))
                .build();

        return ValidationResult.builder()
                .status(status)
                .summary(summary)
                .issues(groupedIssues)
                .build();
    }
    
    /**
     * Calculate quality score based on issues
     */
    private double calculateQualityScore(List<ValidationResult.ValidationIssue> issues) {
        if (issues.isEmpty()) {
            return 100.0;
        }

        double score = 100.0;
        for (ValidationResult.ValidationIssue issue : issues) {
            switch (issue.getSeverity()) {
                case CRITICAL -> score -= 25;
                case ERROR -> score -= 15;
                case WARNING -> score -= 5;
                case INFO -> score -= 1;
            }
        }

        return Math.max(0.0, score);
    }

    /**
     * Perform DSL Reference compliance validation
     */
    private List<ValidationResult.ValidationIssue> performDSLReferenceValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate required sections
        if (rulesDSL.getName() == null || rulesDSL.getName().trim().isEmpty()) {
            issues.add(createIssue("DSL_REF_001", ValidationResult.ValidationSeverity.ERROR,
                    "Missing required 'name' field", "Rule must have a descriptive name",
                    "name", "Add a meaningful rule name"));
        }

        if (rulesDSL.getDescription() == null || rulesDSL.getDescription().trim().isEmpty()) {
            issues.add(createIssue("DSL_REF_002", ValidationResult.ValidationSeverity.ERROR,
                    "Missing required 'description' field", "Rule must have a description explaining its purpose",
                    "description", "Add a clear description of what this rule does"));
        }

        // Note: The AST model uses 'input' (Map) but the YAML DSL uses 'inputs' (List)
        // We need to check if inputs were parsed and converted to the input map
        if (rulesDSL.getInput() == null || rulesDSL.getInput().isEmpty()) {
            issues.add(createIssue("DSL_REF_003", ValidationResult.ValidationSeverity.ERROR,
                    "Missing required 'inputs' section", "Rule must declare inputs variables as a list",
                    "inputs", "Add input variables that will be provided via API (e.g., inputs: [creditScore, annualIncome])"));
        }

        if (rulesDSL.getOutput() == null || rulesDSL.getOutput().isEmpty()) {
            issues.add(createIssue("DSL_REF_004", ValidationResult.ValidationSeverity.ERROR,
                    "Missing required 'output' section", "Rule must define output variables and their types",
                    "output", "Add output section mapping computed variables to types"));
        }

        // Validate rule logic structure
        boolean hasSimpleSyntax = (rulesDSL.getWhenConditions() != null && !rulesDSL.getWhenConditions().isEmpty());
        boolean hasComplexConditions = (rulesDSL.getConditions() != null);
        boolean hasMultipleRules = (rulesDSL.getRules() != null && !rulesDSL.getRules().isEmpty());

        int syntaxCount = (hasSimpleSyntax ? 1 : 0) + (hasComplexConditions ? 1 : 0) + (hasMultipleRules ? 1 : 0);

        if (syntaxCount == 0) {
            issues.add(createIssue("DSL_REF_005", ValidationResult.ValidationSeverity.ERROR,
                    "Missing rule logic", "Rule must have 'when' conditions, 'conditions' block, or 'rules' array",
                    "root", "Add rule logic using 'when', 'conditions', or 'rules'"));
        } else if (syntaxCount > 1) {
            issues.add(createIssue("DSL_REF_007", ValidationResult.ValidationSeverity.WARNING,
                    "Mixed syntax patterns", "Rule uses multiple syntax patterns (when/conditions/rules). Consider using one consistent approach",
                    "root", "Choose one syntax pattern: simple (when/then), complex (conditions), or multiple rules (rules array)"));
        }

        // Validate metadata section if present
        if (rulesDSL.getMetadata() != null) {
            issues.addAll(validateMetadataSection(rulesDSL.getMetadata()));
        }

        // Validate circuit breaker configuration if present
        if (rulesDSL.getCircuitBreaker() != null) {
            issues.addAll(validateCircuitBreakerConfig(rulesDSL.getCircuitBreaker()));
        }

        // Validate when/then structure
        if (rulesDSL.getWhenConditions() != null && !rulesDSL.getWhenConditions().isEmpty()) {
            if (rulesDSL.getThenActions() == null || rulesDSL.getThenActions().isEmpty()) {
                issues.add(createIssue("DSL_REF_006", ValidationResult.ValidationSeverity.ERROR,
                        "Missing 'then' actions", "When using 'when' conditions, 'then' actions are required",
                        "then", "Add actions to execute when conditions are met"));
            }
        }

        return issues;
    }

    /**
     * Validate metadata section
     */
    private List<ValidationResult.ValidationIssue> validateMetadataSection(Map<String, Object> metadata) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate tags field
        if (metadata.containsKey("tags")) {
            Object tags = metadata.get("tags");
            if (!(tags instanceof List)) {
                issues.add(createIssue("META_001", ValidationResult.ValidationSeverity.WARNING,
                        "Invalid tags format", "Tags should be a list of strings",
                        "metadata.tags", "Use format: tags: [\"tag1\", \"tag2\"]"));
            } else {
                List<?> tagsList = (List<?>) tags;
                for (Object tag : tagsList) {
                    if (!(tag instanceof String)) {
                        issues.add(createIssue("META_002", ValidationResult.ValidationSeverity.WARNING,
                                "Invalid tag type", "All tags should be strings",
                                "metadata.tags", "Ensure all tags are quoted strings"));
                        break;
                    }
                }
            }
        }

        // Validate author field
        if (metadata.containsKey("author")) {
            Object author = metadata.get("author");
            if (!(author instanceof String) || ((String) author).trim().isEmpty()) {
                issues.add(createIssue("META_003", ValidationResult.ValidationSeverity.INFO,
                        "Invalid author format", "Author should be a non-empty string",
                        "metadata.author", "Use format: author: \"Team Name\""));
            }
        }

        // Validate category field
        if (metadata.containsKey("category")) {
            Object category = metadata.get("category");
            if (!(category instanceof String) || ((String) category).trim().isEmpty()) {
                issues.add(createIssue("META_004", ValidationResult.ValidationSeverity.INFO,
                        "Invalid category format", "Category should be a non-empty string",
                        "metadata.category", "Use format: category: \"Category Name\""));
            }
        }

        // Validate priority field
        if (metadata.containsKey("priority")) {
            Object priority = metadata.get("priority");
            if (!(priority instanceof Number)) {
                issues.add(createIssue("META_005", ValidationResult.ValidationSeverity.INFO,
                        "Invalid priority format", "Priority should be a number",
                        "metadata.priority", "Use format: priority: 1"));
            }
        }

        // Validate riskLevel field
        if (metadata.containsKey("riskLevel")) {
            Object riskLevel = metadata.get("riskLevel");
            if (!(riskLevel instanceof String)) {
                issues.add(createIssue("META_006", ValidationResult.ValidationSeverity.INFO,
                        "Invalid riskLevel format", "Risk level should be a string",
                        "metadata.riskLevel", "Use format: riskLevel: \"HIGH\""));
            } else {
                String level = ((String) riskLevel).toUpperCase();
                if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(level)) {
                    issues.add(createIssue("META_007", ValidationResult.ValidationSeverity.INFO,
                            "Unknown risk level", "Risk level should be LOW, MEDIUM, HIGH, or CRITICAL",
                            "metadata.riskLevel", "Use one of: LOW, MEDIUM, HIGH, CRITICAL"));
                }
            }
        }

        // Validate last_modified field
        if (metadata.containsKey("last_modified")) {
            Object lastModified = metadata.get("last_modified");
            if (!(lastModified instanceof String) || ((String) lastModified).trim().isEmpty()) {
                issues.add(createIssue("META_008", ValidationResult.ValidationSeverity.INFO,
                        "Invalid last_modified format", "Last modified should be a non-empty string (preferably ISO date)",
                        "metadata.last_modified", "Use format: last_modified: \"2025-01-15\""));
            }
        }

        // Validate review_date field
        if (metadata.containsKey("review_date")) {
            Object reviewDate = metadata.get("review_date");
            if (!(reviewDate instanceof String) || ((String) reviewDate).trim().isEmpty()) {
                issues.add(createIssue("META_009", ValidationResult.ValidationSeverity.INFO,
                        "Invalid review_date format", "Review date should be a non-empty string (preferably ISO date)",
                        "metadata.review_date", "Use format: review_date: \"2025-06-15\""));
            }
        }

        // Validate version field (if in metadata instead of top-level)
        if (metadata.containsKey("version")) {
            Object version = metadata.get("version");
            if (!(version instanceof String) || ((String) version).trim().isEmpty()) {
                issues.add(createIssue("META_010", ValidationResult.ValidationSeverity.INFO,
                        "Invalid version format", "Version should be a non-empty string",
                        "metadata.version", "Use format: version: \"1.0.0\""));
            }
        }

        return issues;
    }

    /**
     * Validate circuit breaker configuration
     */
    private List<ValidationResult.ValidationIssue> validateCircuitBreakerConfig(ASTRulesDSL.ASTCircuitBreakerConfig circuitBreaker) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Note: ASTCircuitBreakerConfig is referenced but may not be fully implemented yet
        // This is a placeholder for when the circuit breaker configuration is fully implemented

        log.debug("Circuit breaker configuration validation - implementation pending");

        return issues;
    }

    /**
     * Perform operator and function validation
     */
    private List<ValidationResult.ValidationIssue> performOperatorValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Get all documented operators from DSL reference
        Set<String> validOperators = getValidOperators();
        Set<String> validFunctions = getBuiltInFunctions();

        // Validate operators in conditions
        if (rulesDSL.getWhenConditions() != null) {
            for (var condition : rulesDSL.getWhenConditions()) {
                issues.addAll(validateOperatorsInCondition(condition, validOperators));
            }
        }

        // Validate functions in actions
        if (rulesDSL.getThenActions() != null) {
            for (var action : rulesDSL.getThenActions()) {
                issues.addAll(validateFunctionsInAction(action, validFunctions));
            }
        }

        if (rulesDSL.getElseActions() != null) {
            for (var action : rulesDSL.getElseActions()) {
                issues.addAll(validateFunctionsInAction(action, validFunctions));
            }
        }

        return issues;
    }

    /**
     * Get all valid operators from DSL reference
     */
    private Set<String> getValidOperators() {
        return Set.of(
                // Numeric comparisons
                "greater_than", ">", "less_than", "<", "at_least", ">=", "greater_than_or_equal",
                "less_than_or_equal", "<=", "at_most", "equals", "==", "not_equals", "!=",

                // Range comparisons
                "between", "not_between",

                // String comparisons
                "contains", "not_contains", "starts_with", "ends_with", "matches", "not_matches",

                // List comparisons
                "in_list", "in", "not_in_list", "not_in",

                // Validation operators (from docs)
                "is_null", "is_not_null", "is_empty", "is_not_empty", "is_numeric", "is_not_numeric",
                "is_email", "is_phone", "is_date", "is_number", "is_string", "is_boolean", "is_list",

                // Length operators
                "length_equals", "length_greater_than", "length_less_than",

                // Financial operators (from docs)
                "is_positive", "is_negative", "is_zero", "is_non_zero", "is_percentage", "is_currency",
                "is_credit_score", "is_ssn", "is_account_number", "is_routing_number", "is_business_day",
                "is_weekend", "age_at_least", "age_less_than",

                // Special operators
                "exists",

                // Logical operators
                "AND", "OR", "NOT", "and", "or", "not"
        );
    }

    /**
     * Validate operators in a condition
     */
    private List<ValidationResult.ValidationIssue> validateOperatorsInCondition(
            Condition condition, Set<String> validOperators) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // This would need to be implemented based on the specific condition structure
        // For now, we'll do basic validation through the AST visitor

        return issues;
    }

    /**
     * Validate functions in an action
     */
    private List<ValidationResult.ValidationIssue> validateFunctionsInAction(
            Action action, Set<String> validFunctions) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // This would need to be implemented based on the specific action structure
        // For now, we'll do basic validation through the AST visitor

        return issues;
    }
}
