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

package org.fireflyframework.rules.core.dsl.visitor;

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.action.*;
import org.fireflyframework.rules.core.dsl.condition.ComparisonCondition;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.condition.ExpressionCondition;
import org.fireflyframework.rules.core.dsl.condition.LogicalCondition;
import org.fireflyframework.rules.core.dsl.expression.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AST visitor that validates nodes for semantic correctness.
 * Checks for type compatibility, variable references, and other semantic rules.
 */
@Slf4j
public class ValidationVisitor implements ASTVisitor<List<ValidationError>> {
    
    private final Set<String> availableVariables;
    private final Set<String> availableFunctions;
    
    public ValidationVisitor(Set<String> availableVariables, Set<String> availableFunctions) {
        this.availableVariables = availableVariables;
        this.availableFunctions = availableFunctions;
    }
    
    // Expression visitors
    
    @Override
    public List<ValidationError> visitBinaryExpression(BinaryExpression node) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate operands
        errors.addAll(node.getLeft().accept(this));
        errors.addAll(node.getRight().accept(this));
        
        // Validate operator compatibility
        ExpressionType leftType = node.getLeft().getExpressionType();
        ExpressionType rightType = node.getRight().getExpressionType();
        
        switch (node.getOperator()) {
            case GREATER_THAN, LESS_THAN, GREATER_EQUAL, LESS_EQUAL -> {
                // Only flag as error if we have definite non-numeric types
                // Allow ANY/UNKNOWN types (variables) since we can't determine their types at validation time
                if (isDefinitelyNonNumeric(leftType) || isDefinitelyNonNumeric(rightType)) {
                    errors.add(new ValidationError(
                        "Comparison operators require numeric operands",
                        node.getLocation(),
                        "VAL_001"
                    ));
                }
            }
            case CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES -> {
                // Only flag as error if we have definite non-string types
                if (isDefinitelyNonString(leftType) || isDefinitelyNonString(rightType)) {
                    errors.add(new ValidationError(
                        "String operators require string operands",
                        node.getLocation(),
                        "VAL_002"
                    ));
                }
            }
            case IN_LIST, NOT_IN_LIST -> {
                // Only flag as error if we have definite non-list types
                if (isDefinitelyNonList(rightType)) {
                    errors.add(new ValidationError(
                        "IN_LIST operator requires a list as right operand",
                        node.getLocation(),
                        "VAL_003"
                    ));
                }
            }
        }
        
        return errors;
    }
    
    @Override
    public List<ValidationError> visitUnaryExpression(UnaryExpression node) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate operand
        errors.addAll(node.getOperand().accept(this));
        
        // Validate operator compatibility
        ExpressionType operandType = node.getOperand().getExpressionType();
        
        switch (node.getOperator()) {
            case NEGATE, POSITIVE -> {
                if (isDefinitelyNonNumeric(operandType)) {
                    errors.add(new ValidationError(
                        "Arithmetic unary operators require numeric operands",
                        node.getLocation(),
                        "VAL_004"
                    ));
                }
            }
            case TO_UPPER, TO_LOWER, TRIM, LENGTH -> {
                if (isDefinitelyNonString(operandType)) {
                    errors.add(new ValidationError(
                        "String operators require string operands",
                        node.getLocation(),
                        "VAL_005"
                    ));
                }
            }
        }
        
        return errors;
    }
    
    @Override
    public List<ValidationError> visitVariableExpression(VariableExpression node) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Check if variable exists
        if (!availableVariables.contains(node.getVariableName())) {
            errors.add(new ValidationError(
                "Undefined variable: " + node.getVariableName(),
                node.getLocation(),
                "VAL_006"
            ));
        }
        
        // Validate index expression if present
        if (node.hasIndexAccess()) {
            errors.addAll(node.getIndexExpression().accept(this));
            
            // Index should be numeric
            if (node.getIndexExpression().getExpressionType() != ExpressionType.NUMBER) {
                errors.add(new ValidationError(
                    "Array index must be numeric",
                    node.getLocation(),
                    "VAL_007"
                ));
            }
        }
        
        return errors;
    }
    
    @Override
    public List<ValidationError> visitLiteralExpression(LiteralExpression node) {
        // Literals are always valid
        return new ArrayList<>();
    }
    
    @Override
    public List<ValidationError> visitFunctionCallExpression(FunctionCallExpression node) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Check if function exists
        if (!availableFunctions.contains(node.getFunctionName())) {
            errors.add(new ValidationError(
                "Undefined function: " + node.getFunctionName(),
                node.getLocation(),
                "VAL_008"
            ));
        }
        
        // Validate arguments
        if (node.hasArguments()) {
            for (Expression arg : node.getArguments()) {
                errors.addAll(arg.accept(this));
            }
        }
        
        return errors;
    }
    
    // Condition visitors
    
    @Override
    public List<ValidationError> visitComparisonCondition(ComparisonCondition node) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate operands
        errors.addAll(node.getLeft().accept(this));
        if (node.getRight() != null) {
            errors.addAll(node.getRight().accept(this));
        }
        if (node.getRangeEnd() != null) {
            errors.addAll(node.getRangeEnd().accept(this));
        }
        
        // Validate range operations
        if (node.isRangeComparison()) {
            if (node.getRangeEnd() == null) {
                errors.add(new ValidationError(
                    "Range comparison requires range end operand",
                    node.getLocation(),
                    "VAL_012"
                ));
            }
        }
        
        return errors;
    }
    
    @Override
    public List<ValidationError> visitLogicalCondition(LogicalCondition node) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate operands
        for (Condition operand : node.getOperands()) {
            errors.addAll(operand.accept(this));
        }
        
        // Validate operand count
        int operandCount = node.getOperandCount();
        int minOperands = node.getOperator().getMinOperands();
        int maxOperands = node.getOperator().getMaxOperands();
        
        if (operandCount < minOperands) {
            errors.add(new ValidationError(
                String.format("Logical operator %s requires at least %d operands, got %d",
                    node.getOperator().getSymbol(), minOperands, operandCount),
                node.getLocation(),
                "VAL_013"
            ));
        }
        
        if (operandCount > maxOperands) {
            errors.add(new ValidationError(
                String.format("Logical operator %s allows at most %d operands, got %d",
                    node.getOperator().getSymbol(), maxOperands, operandCount),
                node.getLocation(),
                "VAL_014"
            ));
        }
        
        return errors;
    }
    
    @Override
    public List<ValidationError> visitExpressionCondition(ExpressionCondition node) {
        return node.getExpression().accept(this);
    }
    
    // Action visitors
    
    @Override
    public List<ValidationError> visitFunctionCallAction(FunctionCallAction node) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Check if function exists
        if (!availableFunctions.contains(node.getFunctionName())) {
            errors.add(new ValidationError(
                "Undefined function: " + node.getFunctionName(),
                node.getLocation(),
                "VAL_016"
            ));
        }
        
        // Validate arguments
        if (node.hasArguments()) {
            for (Expression arg : node.getArguments()) {
                errors.addAll(arg.accept(this));
            }
        }
        
        return errors;
    }
    
    @Override
    public List<ValidationError> visitConditionalAction(ConditionalAction node) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Validate condition
        errors.addAll(node.getCondition().accept(this));
        
        // Validate then actions
        if (node.getThenActions() != null) {
            for (Action action : node.getThenActions()) {
                errors.addAll(action.accept(this));
            }
        }
        
        // Validate else actions
        if (node.getElseActions() != null) {
            for (Action action : node.getElseActions()) {
                errors.addAll(action.accept(this));
            }
        }
        
        return errors;
    }
    
    @Override
    public List<ValidationError> visitCalculateAction(CalculateAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate that calculate only uses mathematical expressions (no function calls)
        if (containsNonMathematicalOperation(node.getExpression())) {
            errors.add(new ValidationError(
                "The 'calculate' command can only be used for mathematical operations (arithmetic expressions with +, -, *, /, %, **). " +
                "Use 'run' for function calls (e.g., max(), min(), abs()), REST API calls (rest_get, rest_post), or JSON operations (json_get, json_exists).",
                node.getLocation(),
                "VAL_CALC_001"
            ));
        }

        // Validate expression
        errors.addAll(node.getExpression().accept(this));

        // Variable name validation
        if (node.getResultVariable() == null || node.getResultVariable().trim().isEmpty()) {
            errors.add(new ValidationError(
                "Calculate action requires a result variable name",
                node.getLocation(),
                "VAL_017"
            ));
        }

        return errors;
    }

    /**
     * Check if an expression contains non-mathematical operations (function calls, REST calls, JSON operations)
     */
    private boolean containsNonMathematicalOperation(Expression expression) {
        if (expression instanceof FunctionCallExpression) {
            return true;
        }
        if (expression instanceof RestCallExpression) {
            return true;
        }
        if (expression instanceof JsonPathExpression) {
            return true;
        }
        if (expression instanceof BinaryExpression binaryExpr) {
            return containsNonMathematicalOperation(binaryExpr.getLeft()) ||
                   containsNonMathematicalOperation(binaryExpr.getRight());
        }
        if (expression instanceof UnaryExpression unaryExpr) {
            return containsNonMathematicalOperation(unaryExpr.getOperand());
        }
        // LiteralExpression and VariableExpression are allowed
        return false;
    }

    @Override
    public List<ValidationError> visitRunAction(RunAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate expression
        errors.addAll(node.getExpression().accept(this));

        // Variable name validation
        if (node.getResultVariable() == null || node.getResultVariable().trim().isEmpty()) {
            errors.add(new ValidationError(
                "Run action requires a result variable name",
                node.getLocation(),
                "VAL_019"
            ));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitSetAction(SetAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate value expression
        errors.addAll(node.getValue().accept(this));

        // Variable name validation
        if (node.getVariableName() == null || node.getVariableName().trim().isEmpty()) {
            errors.add(new ValidationError(
                "Set action requires a variable name",
                node.getLocation(),
                "VAL_018"
            ));
        }

        return errors;
    }

    /**
     * Check if a type is definitely non-numeric (excludes ANY/UNKNOWN which could be numeric)
     */
    private boolean isDefinitelyNonNumeric(ExpressionType type) {
        return type == ExpressionType.STRING ||
               type == ExpressionType.BOOLEAN ||
               type == ExpressionType.LIST ||
               type == ExpressionType.OBJECT;
    }

    /**
     * Check if a type is definitely non-string (excludes ANY/UNKNOWN which could be string)
     */
    private boolean isDefinitelyNonString(ExpressionType type) {
        return type == ExpressionType.NUMBER ||
               type == ExpressionType.BOOLEAN ||
               type == ExpressionType.LIST ||
               type == ExpressionType.OBJECT;
    }

    /**
     * Check if a type is definitely non-list (excludes ANY/UNKNOWN which could be list)
     */
    private boolean isDefinitelyNonList(ExpressionType type) {
        return type == ExpressionType.NUMBER ||
               type == ExpressionType.STRING ||
               type == ExpressionType.BOOLEAN ||
               type == ExpressionType.OBJECT;
    }

    @Override
    public List<ValidationError> visitArithmeticAction(ArithmeticAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate value expression
        errors.addAll(node.getValue().accept(this));

        // Validate variable name
        if (node.getVariableName() == null || node.getVariableName().trim().isEmpty()) {
            errors.add(new ValidationError("Variable name cannot be empty", node.getLocation(), "VAL_003"));
        }

        // Check if the variable exists in available variables
        if (!availableVariables.contains(node.getVariableName())) {
            errors.add(new ValidationError("Undefined variable: " + node.getVariableName(), node.getLocation(), "VAL_002"));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitListAction(ListAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate value expression
        errors.addAll(node.getValue().accept(this));

        // Validate list variable name
        if (node.getListVariable() == null || node.getListVariable().trim().isEmpty()) {
            errors.add(new ValidationError("List variable name cannot be empty", node.getLocation(), "VAL_003"));
        }

        // Check if the list variable exists in available variables
        if (!availableVariables.contains(node.getListVariable())) {
            errors.add(new ValidationError("Undefined list variable: " + node.getListVariable(), node.getLocation(), "VAL_002"));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitCircuitBreakerAction(CircuitBreakerAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate message
        if (node.getMessage() == null || node.getMessage().trim().isEmpty()) {
            errors.add(new ValidationError("Circuit breaker message cannot be empty", node.getLocation(), "VAL_003"));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitForEachAction(ForEachAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate iteration variable name
        if (node.getIterationVariable() == null || node.getIterationVariable().trim().isEmpty()) {
            errors.add(new ValidationError(
                "forEach iteration variable name cannot be empty",
                node.getLocation(),
                "VAL_FOREACH_001"
            ));
        }

        // Validate index variable name if present
        if (node.hasIndexVariable() && (node.getIndexVariable() == null || node.getIndexVariable().trim().isEmpty())) {
            errors.add(new ValidationError(
                "forEach index variable name cannot be empty",
                node.getLocation(),
                "VAL_FOREACH_002"
            ));
        }

        // Validate list expression
        if (node.getListExpression() == null) {
            errors.add(new ValidationError(
                "forEach list expression cannot be null",
                node.getLocation(),
                "VAL_FOREACH_003"
            ));
        } else {
            errors.addAll(node.getListExpression().accept(this));
        }

        // Validate body actions
        if (node.getBodyActions() == null || node.getBodyActions().isEmpty()) {
            errors.add(new ValidationError(
                "forEach body cannot be empty - at least one action is required",
                node.getLocation(),
                "VAL_FOREACH_004"
            ));
        } else {
            // Validate each body action
            for (Action bodyAction : node.getBodyActions()) {
                errors.addAll(bodyAction.accept(this));
            }
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitWhileAction(WhileAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate condition
        if (node.getCondition() == null) {
            errors.add(new ValidationError(
                "while condition cannot be null",
                node.getLocation(),
                "VAL_WHILE_001"
            ));
        } else {
            errors.addAll(node.getCondition().accept(this));
        }

        // Validate body actions
        if (node.getBodyActions() == null || node.getBodyActions().isEmpty()) {
            errors.add(new ValidationError(
                "while body cannot be empty",
                node.getLocation(),
                "VAL_WHILE_002"
            ));
        } else {
            // Validate each body action
            for (Action bodyAction : node.getBodyActions()) {
                errors.addAll(bodyAction.accept(this));
            }
        }

        // Validate max iterations
        if (node.getMaxIterations() <= 0) {
            errors.add(new ValidationError(
                "while maxIterations must be positive",
                node.getLocation(),
                "VAL_WHILE_003"
            ));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitDoWhileAction(DoWhileAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate body actions
        if (node.getBodyActions() == null || node.getBodyActions().isEmpty()) {
            errors.add(new ValidationError(
                "do-while body cannot be empty",
                node.getLocation(),
                "VAL_DOWHILE_001"
            ));
        } else {
            // Validate each body action
            for (Action bodyAction : node.getBodyActions()) {
                errors.addAll(bodyAction.accept(this));
            }
        }

        // Validate condition
        if (node.getCondition() == null) {
            errors.add(new ValidationError(
                "do-while condition cannot be null",
                node.getLocation(),
                "VAL_DOWHILE_002"
            ));
        } else {
            errors.addAll(node.getCondition().accept(this));
        }

        // Validate max iterations
        if (node.getMaxIterations() <= 0) {
            errors.add(new ValidationError(
                "do-while maxIterations must be positive",
                node.getLocation(),
                "VAL_DOWHILE_003"
            ));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitJsonPathExpression(JsonPathExpression node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate source expression
        errors.addAll(node.getSourceExpression().accept(this));

        // Validate JSON path syntax
        if (node.getJsonPath() == null || node.getJsonPath().trim().isEmpty()) {
            errors.add(new ValidationError(
                "JSON path cannot be empty",
                node.getLocation(),
                "VAL_019"
            ));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitRestCallExpression(RestCallExpression node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate URL expression
        errors.addAll(node.getUrlExpression().accept(this));

        // Validate HTTP method
        if (node.getHttpMethod() == null || node.getHttpMethod().trim().isEmpty()) {
            errors.add(new ValidationError(
                "HTTP method cannot be empty",
                node.getLocation(),
                "VAL_020"
            ));
        }

        // Validate optional expressions
        if (node.getBodyExpression() != null) {
            errors.addAll(node.getBodyExpression().accept(this));
        }
        if (node.getHeadersExpression() != null) {
            errors.addAll(node.getHeadersExpression().accept(this));
        }
        if (node.getTimeoutExpression() != null) {
            errors.addAll(node.getTimeoutExpression().accept(this));
        }

        return errors;
    }
}
