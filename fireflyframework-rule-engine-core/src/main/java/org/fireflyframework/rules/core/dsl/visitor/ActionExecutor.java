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
import org.fireflyframework.rules.core.dsl.condition.ExpressionCondition;
import org.fireflyframework.rules.core.dsl.condition.LogicalCondition;
import org.fireflyframework.rules.core.dsl.expression.*;
import org.fireflyframework.rules.core.dsl.function.CustomFunctionRegistry;
import org.fireflyframework.rules.core.services.JsonPathService;
import org.fireflyframework.rules.core.services.RestCallService;
import lombok.extern.slf4j.Slf4j;

/**
 * AST visitor that executes actions.
 * Replaces the string-based action execution with structured AST traversal.
 */
@Slf4j
public class ActionExecutor implements ASTVisitor<Void> {

    private final EvaluationContext context;
    private final ExpressionEvaluator expressionEvaluator;

    public ActionExecutor(EvaluationContext context) {
        this(context, null, null, null);
    }

    public ActionExecutor(EvaluationContext context, RestCallService restCallService, JsonPathService jsonPathService) {
        this(context, restCallService, jsonPathService, null);
    }

    public ActionExecutor(EvaluationContext context,
                          RestCallService restCallService,
                          JsonPathService jsonPathService,
                          CustomFunctionRegistry customFunctions) {
        this.context = context;
        this.expressionEvaluator = new ExpressionEvaluator(context, restCallService, jsonPathService, customFunctions);
    }
    
    // Action visitors
    
    @Override
    public Void visitFunctionCallAction(FunctionCallAction node) {
        // Evaluate arguments
        Object[] args = node.getArguments().stream()
                .map(arg -> arg.accept(expressionEvaluator))
                .toArray();
        
        // Execute function call
        Object result = executeFunction(node.getFunctionName(), args);
        log.info("Executing function call: {} with {} arguments",
            node.getFunctionName(), args.length);

        // If there's a result variable, store the result
        if (node.hasResultVariable()) {
            context.setComputedVariable(node.getResultVariable(), result);
        }
        
        return null;
    }
    
    @Override
    public Void visitConditionalAction(ConditionalAction node) {
        // Evaluate condition
        Object conditionResult = node.getCondition().accept(expressionEvaluator);
        boolean conditionMet = toBoolean(conditionResult);
        
        log.debug("Conditional action condition evaluated to: {}", conditionMet);
        
        if (conditionMet) {
            // Execute then actions
            if (node.getThenActions() != null) {
                for (Action action : node.getThenActions()) {
                    action.accept(this);
                }
            }
        } else {
            // Execute else actions
            if (node.getElseActions() != null) {
                for (Action action : node.getElseActions()) {
                    action.accept(this);
                }
            }
        }
        
        return null;
    }
    
    @Override
    public Void visitCalculateAction(CalculateAction node) {
        // Validate that calculate only uses mathematical expressions (no function calls)
        if (containsNonMathematicalOperation(node.getExpression())) {
            throw new IllegalArgumentException(
                "The 'calculate' command can only be used for mathematical operations. " +
                "Use 'run' for function calls, REST API calls, or JSON operations. " +
                "Expression: " + node.getExpression()
            );
        }

        Object result = node.getExpression().accept(expressionEvaluator);
        context.setComputedVariable(node.getResultVariable(), result);

        log.debug("Calculated {} = {}", node.getResultVariable(), result);
        return null;
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
    public Void visitRunAction(RunAction node) {
        Object result = node.getExpression().accept(expressionEvaluator);
        context.setComputedVariable(node.getResultVariable(), result);

        log.debug("Run {} = {}", node.getResultVariable(), result);
        return null;
    }

    @Override
    public Void visitSetAction(SetAction node) {
        Object value = node.getValue().accept(expressionEvaluator);
        context.setComputedVariable(node.getVariableName(), value);

        log.debug("Set {} = {}", node.getVariableName(), value);
        return null;
    }
    
    // Expression visitors (not used in action execution)
    
    @Override
    public Void visitBinaryExpression(BinaryExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitUnaryExpression(UnaryExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitVariableExpression(VariableExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitLiteralExpression(LiteralExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitFunctionCallExpression(FunctionCallExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    // Condition visitors (not used in action execution)
    
    @Override
    public Void visitComparisonCondition(ComparisonCondition node) {
        throw new UnsupportedOperationException("Conditions cannot be executed as actions");
    }
    
    @Override
    public Void visitLogicalCondition(LogicalCondition node) {
        throw new UnsupportedOperationException("Conditions cannot be executed as actions");
    }
    
    @Override
    public Void visitExpressionCondition(ExpressionCondition node) {
        throw new UnsupportedOperationException("Conditions cannot be executed as actions");
    }
    
    // Helper methods
    
    private boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        return true;
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) return java.math.BigDecimal.ZERO;
        if (value instanceof java.math.BigDecimal) return (java.math.BigDecimal) value;
        if (value instanceof Number) return java.math.BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private Object executeFunction(String functionName, Object[] args) {
        // Built-in action functions
        return switch (functionName.toLowerCase()) {
            case "log", "print" -> {
                String message = args.length > 0 ? args[0].toString() : "";
                String level = args.length > 1 ? args[1].toString() : "INFO";
                log.info("[{}] {}", level, message);
                yield null;
            }
            case "validate", "check" -> {
                // Simple validation function
                boolean isValid = args.length > 0 ? toBoolean(args[0]) : false;
                String message = args.length > 1 ? args[1].toString() : "Validation";
                if (!isValid) {
                    log.warn("Validation failed: {}", message);
                }
                yield isValid;
            }
            case "format" -> {
                // Simple string formatting
                String template = args.length > 0 ? args[0].toString() : "";
                for (int i = 1; i < args.length; i++) {
                    template = template.replace("{" + (i-1) + "}", args[i].toString());
                }
                yield template;
            }
            case "notify", "alert" -> {
                String message = args.length > 0 ? args[0].toString() : "";
                log.info("NOTIFICATION: {}", message);
                yield null;
            }
            case "audit_log" -> {
                String event = args.length > 0 ? args[0].toString() : "";
                String details = args.length > 1 ? args[1].toString() : "";
                log.info("AUDIT: {} - {}", event, details);
                yield null;
            }
            case "send_notification" -> {
                String recipient = args.length > 0 ? args[0].toString() : "";
                String message = args.length > 1 ? args[1].toString() : "";
                log.info("NOTIFICATION TO {}: {}", recipient, message);
                yield null;
            }
            // Delegate calculation functions to expression evaluator
            case "calculate_loan_payment", "calculate_compound_interest", "calculate_amortization",
                 "debt_to_income_ratio", "credit_utilization", "loan_to_value", "calculate_apr",
                 "calculate_credit_score", "calculate_risk_score", "payment_history_score",
                 "is_valid_credit_score", "is_valid_ssn", "is_valid_account", "is_valid_routing",
                 "is_business_day", "age_meets_requirement", "format_currency", "format_percentage",
                 "generate_account_number", "generate_transaction_id", "distance_between",
                 "time_hour", "is_valid", "in_range" -> {
                // These functions are handled by the expression evaluator
                yield expressionEvaluator.visitFunctionCallExpression(
                    new FunctionCallExpression(
                        null, functionName,
                        java.util.Arrays.stream(args)
                            .map(arg -> new LiteralExpression(null, arg))
                            .collect(java.util.stream.Collectors.toList())
                    )
                );
            }
            // Any other name -- custom-registered or any expression-tier built-in -- delegates
            // to the expression evaluator, which checks the custom registry first and throws
            // IllegalArgumentException with the registry-aware diagnostic if still unknown.
            default -> expressionEvaluator.visitFunctionCallExpression(
                    new FunctionCallExpression(
                            null, functionName,
                            java.util.Arrays.stream(args)
                                    .map(arg -> new LiteralExpression(null, arg))
                                    .collect(java.util.stream.Collectors.toList())
                    ));
        };
    }

    @Override
    public Void visitArithmeticAction(ArithmeticAction node) {
        log.debug("Executing arithmetic action: {} {} {} {}",
                 node.getOperation().getKeyword(), node.getValue(),
                 node.getOperation().getPreposition(), node.getVariableName());

        Object value = node.getValue().accept(expressionEvaluator);
        Object current = context.getVariable(node.getVariableName());

        if (!(current instanceof Number) || !(value instanceof Number)) {
            throw new IllegalArgumentException(
                    "Arithmetic action '" + node.getOperation().getKeyword() + "' on '"
                            + node.getVariableName() + "' requires numeric operands. Got current="
                            + describeType(current) + ", value=" + describeType(value));
        }

        java.math.BigDecimal currentNum = toBigDecimal(current);
        java.math.BigDecimal valueNum = toBigDecimal(value);
        java.math.BigDecimal result = switch (node.getOperation()) {
            case ADD -> currentNum.add(valueNum);
            case SUBTRACT -> currentNum.subtract(valueNum);
            case MULTIPLY -> currentNum.multiply(valueNum);
            case DIVIDE -> {
                if (valueNum.compareTo(java.math.BigDecimal.ZERO) == 0) {
                    throw new ArithmeticException("Division by zero in arithmetic action on '"
                            + node.getVariableName() + "'");
                }
                yield currentNum.divide(valueNum, 10, java.math.RoundingMode.HALF_UP);
            }
        };
        context.setComputedVariable(node.getVariableName(), result);
        return null;
    }

    private static String describeType(Object o) {
        if (o == null) return "null";
        return o + " (" + o.getClass().getSimpleName() + ")";
    }

    @Override
    public Void visitListAction(ListAction node) {
        log.debug("Executing list action: {} {} {} {}",
                 node.getOperation().getKeyword(), node.getValue(),
                 node.getOperation().getPreposition(), node.getListVariable());

        Object value = node.getValue().accept(expressionEvaluator);
        Object current = context.getVariable(node.getListVariable());

        java.util.List<Object> list;
        if (current instanceof java.util.List) {
            list = new java.util.ArrayList<>((java.util.List<?>) current);
        } else {
            list = new java.util.ArrayList<>();
            if (current != null) {
                list.add(current);
            }
        }

        switch (node.getOperation()) {
            case APPEND -> list.add(value);
            case PREPEND -> list.add(0, value);
            case REMOVE -> list.remove(value);
        }

        context.setComputedVariable(node.getListVariable(), list);
        return null;
    }

    @Override
    public Void visitCircuitBreakerAction(CircuitBreakerAction node) {
        log.info("Circuit breaker triggered: {}", node.getMessage());

        // Trigger circuit breaker in context
        context.triggerCircuitBreaker(node.getMessage());

        // Throw circuit breaker exception to stop rule execution
        throw new org.fireflyframework.rules.core.dsl.exception.CircuitBreakerException(
            node.getMessage(),
            node.getErrorCode()
        );
    }

    @Override
    public Void visitForEachAction(ForEachAction node) {
        log.debug("Executing forEach action: {} in {}", node.getIterationVariable(), node.getListExpression());

        // Evaluate the list expression
        Object listValue = node.getListExpression().accept(expressionEvaluator);

        // Convert to list if needed
        java.util.List<?> list;
        if (listValue instanceof java.util.List) {
            list = (java.util.List<?>) listValue;
        } else if (listValue == null) {
            log.warn("forEach list expression evaluated to null, skipping iteration");
            return null;
        } else {
            // Wrap single value in a list
            list = java.util.List.of(listValue);
        }

        // Iterate over the list
        int index = 0;
        for (Object item : list) {
            // Set the iteration variable in the context
            context.setComputedVariable(node.getIterationVariable(), item);

            // Set the index variable if present
            if (node.hasIndexVariable()) {
                context.setComputedVariable(node.getIndexVariable(), index);
            }

            // Execute body actions
            for (Action bodyAction : node.getBodyActions()) {
                try {
                    bodyAction.accept(this);
                } catch (Exception e) {
                    log.error("Error executing forEach body action at index {}: {}", index, e.getMessage(), e);
                    throw e;
                }
            }

            index++;
        }

        log.debug("forEach completed: {} iterations", list.size());
        return null;
    }

    @Override
    public Void visitWhileAction(WhileAction node) {
        log.debug("Executing while loop");

        int iterations = 0;
        int maxIterations = node.getMaxIterations();

        // Evaluate condition before each iteration
        while (iterations < maxIterations) {
            // Evaluate the condition
            Object conditionResult = node.getCondition().accept(expressionEvaluator);
            boolean conditionMet = toBoolean(conditionResult);

            if (!conditionMet) {
                break;
            }

            // Execute body actions
            for (Action action : node.getBodyActions()) {
                try {
                    action.accept(this);
                } catch (Exception e) {
                    log.error("Error executing while body action at iteration {}: {}", iterations, e.getMessage(), e);
                    throw e;
                }
            }

            iterations++;
        }

        if (iterations >= maxIterations) {
            log.warn("while loop reached maximum iterations limit: {}", maxIterations);
        }

        log.debug("while completed: {} iterations", iterations);
        return null;
    }

    @Override
    public Void visitDoWhileAction(DoWhileAction node) {
        log.debug("Executing do-while loop");

        int iterations = 0;
        int maxIterations = node.getMaxIterations();

        // Execute body at least once, then check condition
        do {
            // Execute body actions
            for (Action action : node.getBodyActions()) {
                try {
                    action.accept(this);
                } catch (Exception e) {
                    log.error("Error executing do-while body action at iteration {}: {}", iterations, e.getMessage(), e);
                    throw e;
                }
            }

            iterations++;

            if (iterations >= maxIterations) {
                log.warn("do-while loop reached maximum iterations limit: {}", maxIterations);
                break;
            }

            // Evaluate the condition after executing the body
            Object conditionResult = node.getCondition().accept(expressionEvaluator);
            boolean conditionMet = toBoolean(conditionResult);

            if (!conditionMet) {
                break;
            }

        } while (iterations < maxIterations);

        log.debug("do-while completed: {} iterations", iterations);
        return null;
    }

    @Override
    public Void visitJsonPathExpression(JsonPathExpression node) {
        // Expressions don't execute actions, so return null
        return null;
    }

    @Override
    public Void visitRestCallExpression(RestCallExpression node) {
        // Expressions don't execute actions, so return null
        return null;
    }
}
