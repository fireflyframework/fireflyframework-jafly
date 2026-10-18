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
import org.fireflyframework.rules.core.dsl.function.RuleFunction;
import org.fireflyframework.rules.core.services.JsonPathService;
import org.fireflyframework.rules.core.services.RestCallService;
import org.fireflyframework.rules.core.utils.JsonLogger;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AST visitor that evaluates expressions to their values.
 * Replaces the string-based expression evaluation with structured AST traversal.
 */
@Slf4j
public class ExpressionEvaluator implements ASTVisitor<Object> {

    private final EvaluationContext context;
    private final RestCallService restCallService;
    private final JsonPathService jsonPathService;
    private final CustomFunctionRegistry customFunctions;
    private final org.fireflyframework.rules.core.dsl.function.RuleInvoker ruleInvoker;

    private static final int REGEX_CACHE_SIZE = 64;
    @SuppressWarnings("serial")
    private static final Map<String, Pattern> REGEX_CACHE = new LinkedHashMap<>(REGEX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
            return size() > REGEX_CACHE_SIZE;
        }
    };

    public ExpressionEvaluator(EvaluationContext context) {
        this(context, null, null, null, null);
    }

    public ExpressionEvaluator(EvaluationContext context, RestCallService restCallService, JsonPathService jsonPathService) {
        this(context, restCallService, jsonPathService, null, null);
    }

    public ExpressionEvaluator(EvaluationContext context,
                               RestCallService restCallService,
                               JsonPathService jsonPathService,
                               CustomFunctionRegistry customFunctions) {
        this(context, restCallService, jsonPathService, customFunctions, null);
    }

    public ExpressionEvaluator(EvaluationContext context,
                               RestCallService restCallService,
                               JsonPathService jsonPathService,
                               CustomFunctionRegistry customFunctions,
                               org.fireflyframework.rules.core.dsl.function.RuleInvoker ruleInvoker) {
        this.context = context;
        this.restCallService = restCallService;
        this.jsonPathService = jsonPathService;
        this.customFunctions = customFunctions;
        this.ruleInvoker = ruleInvoker;
    }
    
    // Expression visitors
    
    @Override
    public Object visitBinaryExpression(BinaryExpression node) {
        // Short-circuit AND/OR: evaluate right side only when needed
        if (node.getOperator() == BinaryOperator.AND) {
            return toBoolean(node.getLeft().accept(this)) && toBoolean(node.getRight().accept(this));
        }
        if (node.getOperator() == BinaryOperator.OR) {
            return toBoolean(node.getLeft().accept(this)) || toBoolean(node.getRight().accept(this));
        }

        Object leftValue = node.getLeft().accept(this);
        Object rightValue = node.getRight().accept(this);

        return switch (node.getOperator()) {
            case ADD -> add(leftValue, rightValue);
            case SUBTRACT -> subtract(leftValue, rightValue);
            case MULTIPLY -> multiply(leftValue, rightValue);
            case DIVIDE -> divide(leftValue, rightValue);
            case MODULO -> modulo(leftValue, rightValue);
            case POWER -> power(leftValue, rightValue);
            case EQUALS -> equals(leftValue, rightValue);
            case NOT_EQUALS -> !equals(leftValue, rightValue);
            case GREATER_THAN -> greaterThan(leftValue, rightValue);
            case LESS_THAN -> lessThan(leftValue, rightValue);
            case GREATER_EQUAL -> greaterThanOrEqual(leftValue, rightValue);
            case LESS_EQUAL -> lessThanOrEqual(leftValue, rightValue);
            case CONTAINS -> contains(leftValue, rightValue);
            case NOT_CONTAINS -> !contains(leftValue, rightValue);
            case STARTS_WITH -> startsWith(leftValue, rightValue);
            case ENDS_WITH -> endsWith(leftValue, rightValue);
            case MATCHES -> matches(leftValue, rightValue);
            case NOT_MATCHES -> !matches(leftValue, rightValue);
            case IN_LIST -> inList(leftValue, rightValue);
            case NOT_IN_LIST -> !inList(leftValue, rightValue);
            case BETWEEN -> throw new IllegalStateException("BETWEEN operator should be handled in ComparisonCondition, not BinaryExpression");
            case NOT_BETWEEN -> throw new IllegalStateException("NOT_BETWEEN operator should be handled in ComparisonCondition, not BinaryExpression");
            case AGE_AT_LEAST -> ageAtLeast(leftValue, rightValue);
            case AGE_LESS_THAN -> ageLessThan(leftValue, rightValue);
            case AND, OR -> throw new IllegalStateException("AND/OR already handled above");
            // Handle additional aliases and operators
            case LESS_THAN_OR_EQUAL -> lessThanOrEqual(leftValue, rightValue);
            case GREATER_THAN_OR_EQUAL -> greaterThanOrEqual(leftValue, rightValue);
            case IN -> inList(leftValue, rightValue);
        };
    }
    
    @Override
    public Object visitUnaryExpression(UnaryExpression node) {
        Object operandValue = node.getOperand().accept(this);
        
        return switch (node.getOperator()) {
            case NEGATE -> negate(operandValue);
            case POSITIVE -> operandValue; // No-op for positive
            case NOT -> !toBoolean(operandValue);
            case EXISTS -> operandValue != null;
            case IS_NULL -> operandValue == null;
            case IS_NOT_NULL -> operandValue != null;
            case IS_NUMBER -> operandValue instanceof Number;
            case IS_STRING -> operandValue instanceof String;
            case IS_BOOLEAN -> operandValue instanceof Boolean;
            case IS_LIST -> operandValue instanceof List;
            case TO_UPPER -> operandValue != null ? operandValue.toString().toUpperCase() : null;
            case TO_LOWER -> operandValue != null ? operandValue.toString().toLowerCase() : null;
            case TRIM -> operandValue != null ? operandValue.toString().trim() : null;
            case LENGTH -> operandValue != null ? operandValue.toString().length() : 0;

            // Validation operators
            case IS_POSITIVE -> isPositive(operandValue);
            case IS_NEGATIVE -> isNegative(operandValue);
            case IS_ZERO -> isZero(operandValue);
            case IS_EMPTY -> isEmpty(operandValue);
            case IS_NOT_EMPTY -> !isEmpty(operandValue);
            case IS_NUMERIC -> isNumeric(operandValue);
            case IS_NOT_NUMERIC -> !isNumeric(operandValue);
            case IS_EMAIL -> isEmail(operandValue);
            case IS_PHONE -> isPhone(operandValue);
            case IS_DATE -> isDate(operandValue);
            case IS_PERCENTAGE -> isPercentage(operandValue);
            case IS_CURRENCY -> isCurrency(operandValue);
            case IS_CREDIT_SCORE -> isCreditScore(operandValue);
            case IS_SSN -> isSSN(operandValue);
            case IS_ACCOUNT_NUMBER -> isAccountNumber(operandValue);
            case IS_ROUTING_NUMBER -> isRoutingNumber(operandValue);
            case IS_BUSINESS_DAY -> isBusinessDay(operandValue);
            case IS_WEEKEND -> isWeekend(operandValue);

            // Handle aliases
            case MINUS -> negate(operandValue);
            case PLUS -> operandValue; // No-op for positive
        };
    }
    
    @Override
    public Object visitVariableExpression(VariableExpression node) {
        Object value = context.getVariable(node.getVariableName());
        
        // Handle property access
        if (node.hasPropertyAccess() && value != null) {
            String propertyPath = String.join(".", node.getPropertyPath());
            return evaluatePropertyAccess(value, propertyPath);
        }
        
        // Handle index access
        if (node.hasIndexAccess() && value instanceof List) {
            Object indexValue = node.getIndexExpression().accept(this);
            if (indexValue instanceof Number) {
                int index = ((Number) indexValue).intValue();
                List<?> list = (List<?>) value;
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                }
            }
        }
        
        return value;
    }
    
    @Override
    public Object visitLiteralExpression(LiteralExpression node) {
        return node.getValue();
    }
    
    @Override
    public Object visitFunctionCallExpression(FunctionCallExpression node) {
        String functionName = node.getFunctionName();
        Object[] args = node.hasArguments() ?
            node.getArguments().stream().map(arg -> arg.accept(this)).toArray() :
            new Object[0];

        // User-registered functions are checked first so they may extend or override built-ins.
        if (customFunctions != null) {
            RuleFunction custom = customFunctions.lookup(functionName).orElse(null);
            if (custom != null) {
                return custom.apply(args);
            }
        }

        // Built-in catalog. Unknown names throw rather than silently returning null --
        // the user almost certainly typo'd a function name and silent null masks that.
        return switch (functionName.toLowerCase()) {
            case "max" -> evaluateMax(args);
            case "min" -> evaluateMin(args);
            case "abs" -> evaluateAbs(args);
            case "round" -> evaluateRound(args);
            case "ceil" -> evaluateCeil(args);
            case "floor" -> evaluateFloor(args);
            case "sqrt" -> evaluateSqrt(args);
            case "pow" -> evaluatePow(args);
            case "exp" -> evaluateUnaryMath(args, "exp", Math::exp);
            case "ln" -> evaluateUnaryMath(args, "ln", Math::log);
            case "log10" -> evaluateUnaryMath(args, "log10", Math::log10);
            case "sin" -> evaluateUnaryMath(args, "sin", Math::sin);
            case "cos" -> evaluateUnaryMath(args, "cos", Math::cos);
            case "tan" -> evaluateUnaryMath(args, "tan", Math::tan);
            case "atan2" -> evaluateBinaryMath(args, "atan2", Math::atan2);
            case "hash" -> evaluateHash(args);
            case "log" -> evaluateLog(args);

            // String functions
            case "length", "len" -> evaluateLength(args);
            case "substring", "substr" -> evaluateSubstring(args);
            case "upper", "uppercase" -> evaluateUpper(args);
            case "lower", "lowercase" -> evaluateLower(args);
            case "trim" -> evaluateTrim(args);
            case "contains" -> evaluateContains(args);
            case "startswith" -> evaluateStartsWith(args);
            case "endswith" -> evaluateEndsWith(args);
            case "replace" -> evaluateReplace(args);

            // Date/time functions
            case "now" -> evaluateNow(args);
            case "today" -> evaluateToday(args);
            case "dateadd" -> evaluateDateAdd(args);
            case "datediff" -> evaluateDateDiff(args);
            case "calculate_age" -> evaluateCalculateAge(args);
            case "format_date" -> evaluateFormatDate(args);
            case "current_iso", "now_iso" -> java.time.OffsetDateTime.now().toString();
            case "year_of" -> evaluateDatePart(args, "year_of", java.time.LocalDate::getYear);
            case "month_of" -> evaluateDatePart(args, "month_of", d -> d.getMonthValue());
            case "day_of_month" -> evaluateDatePart(args, "day_of_month", java.time.LocalDate::getDayOfMonth);
            case "day_of_week" -> evaluateDatePart(args, "day_of_week", d -> d.getDayOfWeek().getValue());

            // Validation functions (function-call form complements the `is_email`/`is_phone` operators)
            case "validate_email" -> isEmail(args.length > 0 ? args[0] : null);
            case "validate_phone" -> isPhone(args.length > 0 ? args[0] : null);

            // List functions
            case "size", "count" -> evaluateSize(args);
            case "sum" -> evaluateSum(args);
            case "avg", "average" -> evaluateAverage(args);
            case "first" -> evaluateFirst(args);
            case "last" -> evaluateLast(args);
            case "filter" -> evaluateFilter(args);
            case "map" -> evaluateMap(args);
            case "reduce" -> evaluateReduce(args);
            case "find" -> evaluateFind(args);
            case "sort" -> evaluateSort(args);
            case "reverse" -> evaluateReverse(args);
            case "distinct" -> evaluateDistinct(args);

            // Statistical functions
            case "median" -> evaluateMedian(args);
            case "stddev" -> evaluateStddev(args);
            case "variance" -> evaluateVariance(args);
            case "percentile" -> evaluatePercentile(args);

            // String formatting
            case "format" -> evaluateStringFormat(args);
            case "concat" -> evaluateConcat(args);

            // Type conversion functions
            case "tonumber", "number" -> evaluateToNumber(args);
            case "tostring", "string" -> evaluateToString(args);
            case "toboolean", "boolean" -> evaluateToBoolean(args);

            // Null-handling / conditional helpers
            case "coalesce" -> evaluateCoalesce(args);
            case "if_else", "ifelse" -> evaluateIfElse(args);
            case "is_in_range" -> inRange(args);

            // Financial calculation functions
            case "calculate_loan_payment" -> calculateLoanPayment(args);
            case "calculate_compound_interest" -> calculateCompoundInterest(args);
            case "calculate_amortization" -> calculateAmortization(args);
            case "debt_to_income_ratio" -> debtToIncomeRatio(args);
            case "credit_utilization" -> creditUtilization(args);
            case "loan_to_value" -> loanToValue(args);
            case "calculate_apr" -> calculateAPR(args);
            case "calculate_credit_score" -> calculateCreditScore(args);
            case "calculate_risk_score" -> calculateRiskScore(args);
            case "payment_history_score" -> paymentHistoryScore(args);

            // Financial validation functions
            case "is_valid_credit_score" -> isValidCreditScore(args);
            case "is_valid_ssn" -> isValidSSN(args);
            case "is_valid_account" -> isValidAccount(args);
            case "is_valid_routing" -> isValidRouting(args);
            case "is_business_day" -> isBusinessDayFunction(args);
            case "age_meets_requirement" -> ageMeetsRequirement(args);

            // Utility functions
            case "format_currency" -> formatCurrency(args);
            case "format_percentage" -> formatPercentage(args);
            case "generate_account_number" -> generateAccountNumber(args);
            case "generate_transaction_id" -> generateTransactionId(args);
            case "distance_between" -> distanceBetween(args);
            case "time_hour" -> timeHour(args);
            case "is_valid" -> isValid(args);
            case "in_range" -> inRange(args);

            // Logging/Auditing functions
            case "audit" -> audit(args);
            case "audit_log" -> auditLog(args);
            case "send_notification" -> sendNotification(args);

            // Data Security functions
            case "encrypt" -> encrypt(args);
            case "decrypt" -> decrypt(args);
            case "mask_data" -> maskData(args);

            // Advanced Financial functions
            case "calculate_debt_ratio" -> calculateDebtRatio(args);
            case "calculate_ltv" -> calculateLTV(args);
            case "calculate_payment_schedule" -> calculatePaymentSchedule(args);

            // REST API functions
            case "rest_get" -> restGet(args);
            case "rest_post" -> restPost(args);
            case "rest_put" -> restPut(args);
            case "rest_delete" -> restDelete(args);
            case "rest_patch" -> restPatch(args);
            case "rest_call" -> restCall(args);

            // Rule composition -- delegate to a stored rule by code
            case "invoke_rule" -> evaluateInvokeRule(args);

            // JSON path functions
            case "json_get", "json_path" -> jsonGet(args);
            case "json_exists" -> jsonExists(args);
            case "json_size" -> jsonSize(args);
            case "json_type" -> jsonType(args);

            default -> throw new IllegalArgumentException(
                    "Unknown function '" + functionName + "'. Register it via CustomFunctionRegistry or "
                            + "check spelling against the built-in catalog.");
        };
    }

    // Condition visitors (return Boolean)
    
    @Override
    public Object visitComparisonCondition(ComparisonCondition node) {
        Object leftValue = node.getLeft().accept(this);
        Object rightValue = node.getRight() != null ? node.getRight().accept(this) : null;
        Object rangeEndValue = node.getRangeEnd() != null ? node.getRangeEnd().accept(this) : null;
        
        return switch (node.getOperator()) {
            case EQUALS -> equals(leftValue, rightValue);
            case NOT_EQUALS -> !equals(leftValue, rightValue);
            case GREATER_THAN -> greaterThan(leftValue, rightValue);
            case LESS_THAN -> lessThan(leftValue, rightValue);
            case GREATER_EQUAL -> greaterThanOrEqual(leftValue, rightValue);
            case LESS_EQUAL -> lessThanOrEqual(leftValue, rightValue);
            case CONTAINS -> contains(leftValue, rightValue);
            case NOT_CONTAINS -> !contains(leftValue, rightValue);
            case STARTS_WITH -> startsWith(leftValue, rightValue);
            case ENDS_WITH -> endsWith(leftValue, rightValue);
            case MATCHES -> matches(leftValue, rightValue);
            case NOT_MATCHES -> !matches(leftValue, rightValue);
            case IN_LIST -> inList(leftValue, rightValue);
            case NOT_IN_LIST -> !inList(leftValue, rightValue);
            case BETWEEN -> between(leftValue, rightValue, rangeEndValue);
            case NOT_BETWEEN -> !between(leftValue, rightValue, rangeEndValue);
            case EXISTS -> leftValue != null;
            case NOT_EXISTS -> leftValue == null;
            case IS_NULL -> leftValue == null;
            case IS_NOT_NULL -> leftValue != null;

            // Basic validation operators
            case IS_EMPTY -> isEmpty(leftValue);
            case IS_NOT_EMPTY -> !isEmpty(leftValue);
            case IS_NUMERIC -> isNumeric(leftValue);
            case IS_NOT_NUMERIC -> !isNumeric(leftValue);
            case IS_EMAIL -> isEmail(leftValue);
            case IS_PHONE -> isPhone(leftValue);
            case IS_DATE -> isDate(leftValue);

            // Financial validation operators
            case IS_POSITIVE -> isPositive(leftValue);
            case IS_NEGATIVE -> isNegative(leftValue);
            case IS_ZERO -> isZero(leftValue);
            case IS_PERCENTAGE -> isPercentage(leftValue);
            case IS_CURRENCY -> isCurrency(leftValue);
            case IS_CREDIT_SCORE -> isCreditScore(leftValue);
            case IS_SSN -> isSSN(leftValue);
            case IS_ACCOUNT_NUMBER -> isAccountNumber(leftValue);
            case IS_ROUTING_NUMBER -> isRoutingNumber(leftValue);

            // Date/time validation operators
            case IS_BUSINESS_DAY -> isBusinessDay(leftValue);
            case IS_WEEKEND -> isWeekend(leftValue);
            case AGE_AT_LEAST -> ageAtLeast(leftValue, rightValue);
            case AGE_LESS_THAN -> ageLessThan(leftValue, rightValue);

            // Length operators
            case LENGTH_EQUALS -> lengthEquals(leftValue, rightValue);
            case LENGTH_GREATER_THAN -> lengthGreaterThan(leftValue, rightValue);
            case LENGTH_LESS_THAN -> lengthLessThan(leftValue, rightValue);

            // Additional financial operators
            case IS_NON_ZERO -> isNonZero(leftValue);

            // Handle aliases
            case LESS_THAN_OR_EQUAL -> lessThanOrEqual(leftValue, rightValue);
            case GREATER_THAN_OR_EQUAL -> greaterThanOrEqual(leftValue, rightValue);
            case IN -> inList(leftValue, rightValue);
            case NOT_IN -> !inList(leftValue, rightValue);
        };
    }
    
    @Override
    public Object visitLogicalCondition(LogicalCondition node) {
        return switch (node.getOperator()) {
            case AND -> node.getOperands().stream()
                    .allMatch(operand -> toBoolean(operand.accept(this)));
            case OR -> node.getOperands().stream()
                    .anyMatch(operand -> toBoolean(operand.accept(this)));
            case NOT -> !toBoolean(node.getOperands().get(0).accept(this));
        };
    }
    
    @Override
    public Object visitExpressionCondition(ExpressionCondition node) {
        return toBoolean(node.getExpression().accept(this));
    }
    
    // Action visitors (not used in expression evaluation)

    @Override
    public Object visitFunctionCallAction(FunctionCallAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }
    
    @Override
    public Object visitConditionalAction(ConditionalAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }
    
    @Override
    public Object visitCalculateAction(CalculateAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitRunAction(RunAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitSetAction(SetAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitArithmeticAction(ArithmeticAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitListAction(ListAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitCircuitBreakerAction(CircuitBreakerAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitForEachAction(ForEachAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitWhileAction(WhileAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitDoWhileAction(DoWhileAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    // Helper methods for operations
    
    private Object add(Object left, Object right) {
        if (left instanceof String || right instanceof String) {
            return toString(left) + toString(right);
        }
        return toNumberSafe(left).add(toNumberSafe(right));
    }
    
    private Object subtract(Object left, Object right) {
        return toNumberSafe(left).subtract(toNumberSafe(right));
    }
    
    private Object multiply(Object left, Object right) {
        return toNumberSafe(left).multiply(toNumberSafe(right));
    }
    
    private Object divide(Object left, Object right) {
        BigDecimal rightNum = toNumberSafe(right);
        if (rightNum.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return toNumberSafe(left).divide(rightNum, 10, RoundingMode.HALF_UP);
    }
    
    private Object modulo(Object left, Object right) {
        BigDecimal rightNum = toNumberSafe(right);
        if (rightNum.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Modulo by zero");
        }
        return toNumberSafe(left).remainder(rightNum);
    }
    
    private Object power(Object left, Object right) {
        return BigDecimal.valueOf(Math.pow(toNumberSafe(left).doubleValue(), toNumberSafe(right).doubleValue()));
    }
    
    private Object negate(Object value) {
        return toNumberSafe(value).negate();
    }
    
    private boolean equals(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        
        if (left instanceof Number && right instanceof Number) {
            return toNumber(left).compareTo(toNumber(right)) == 0;
        }
        
        return left.equals(right);
    }
    
    private boolean greaterThan(Object left, Object right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot compare null values: " + left + " > " + right);
        }
        return toNumber(left).compareTo(toNumber(right)) > 0;
    }

    private boolean lessThan(Object left, Object right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot compare null values: " + left + " < " + right);
        }
        return toNumber(left).compareTo(toNumber(right)) < 0;
    }

    private boolean greaterThanOrEqual(Object left, Object right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot compare null values: " + left + " >= " + right);
        }
        return toNumber(left).compareTo(toNumber(right)) >= 0;
    }

    private boolean lessThanOrEqual(Object left, Object right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot compare null values: " + left + " <= " + right);
        }
        return toNumber(left).compareTo(toNumber(right)) <= 0;
    }
    
    private boolean contains(Object left, Object right) {
        return toString(left).contains(toString(right));
    }
    
    private boolean startsWith(Object left, Object right) {
        return toString(left).startsWith(toString(right));
    }
    
    private boolean endsWith(Object left, Object right) {
        return toString(left).endsWith(toString(right));
    }
    
    private boolean matches(Object left, Object right) {
        String leftStr = toString(left);
        String rightStr = toString(right);
        try {
            Pattern pattern = REGEX_CACHE.computeIfAbsent(rightStr, Pattern::compile);
            return pattern.matcher(leftStr).find();
        } catch (java.util.regex.PatternSyntaxException e) {
            // Treat a broken pattern as an authoring bug rather than "no match" -- the
            // latter silently flips rules to the wrong branch.
            throw new IllegalArgumentException(
                    "Invalid regex pattern '" + rightStr + "': " + e.getDescription(), e);
        }
    }
    
    private boolean inList(Object left, Object right) {
        if (right instanceof List) {
            return ((List<?>) right).contains(left);
        }
        return false;
    }
    
    private boolean between(Object value, Object min, Object max) {
        if (max == null) return false;
        BigDecimal val = toNumber(value);
        BigDecimal minVal = toNumber(min);
        BigDecimal maxVal = toNumber(max);
        return val.compareTo(minVal) >= 0 && val.compareTo(maxVal) <= 0;
    }
    
    private BigDecimal toNumber(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot convert null to number");
        }
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert '" + value + "' to number");
        }
    }

    /**
     * Wrap an exception thrown from a built-in function in an {@link IllegalArgumentException}
     * tagged with the function name -- unless the exception already carries good diagnostic
     * context, in which case it is rethrown unchanged. Used by the financial / formatting /
     * date built-ins to give consistent error messages and avoid the previous catch-and-null
     * silent-failure pattern.
     */
    private static IllegalArgumentException wrapFunctionError(String functionName, RuntimeException cause) {
        if (cause instanceof IllegalArgumentException && cause.getMessage() != null
                && cause.getMessage().startsWith(functionName)) {
            return (IllegalArgumentException) cause;
        }
        return new IllegalArgumentException(
                functionName + ": " + (cause.getMessage() != null ? cause.getMessage()
                        : cause.getClass().getSimpleName()),
                cause);
    }

    /**
     * Number conversion that treats null as zero (matching null-as-no-value semantics
     * common in financial and SQL contexts), but raises IllegalArgumentException for
     * non-numeric strings so type bugs surface instead of silently producing zero.
     */
    private BigDecimal toNumberSafe(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Cannot convert '" + value + "' (type " + value.getClass().getSimpleName()
                            + ") to a number for arithmetic; check that the operand is numeric");
        }
    }
    
    private boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof java.util.Collection) return !((java.util.Collection<?>) value).isEmpty();
        if (value instanceof java.util.Map) return !((java.util.Map<?, ?>) value).isEmpty();
        return true;
    }
    
    private String toString(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Numeric coercion shared by all built-in functions (sum, max, financial calcs, etc).
     * <p>
     * Delegates to {@link #toNumberSafe(Object)} so the entire engine has one consistent
     * coercion contract: null treated as zero (matches SQL/financial semantics for missing
     * values in aggregations), but any non-numeric string raises
     * {@link IllegalArgumentException} so type bugs in rule authoring surface immediately
     * rather than being silently zeroed.
     */
    private BigDecimal toBigDecimal(Object value) {
        return toNumberSafe(value);
    }

    // Built-in function implementations

    private Object evaluateMax(Object[] args) {
        if (args.length == 0) return null;
        BigDecimal max = toBigDecimal(args[0]);
        for (int i = 1; i < args.length; i++) {
            BigDecimal current = toBigDecimal(args[i]);
            if (current != null && (max == null || current.compareTo(max) > 0)) {
                max = current;
            }
        }
        return max;
    }

    private Object evaluateMin(Object[] args) {
        if (args.length == 0) return null;
        BigDecimal min = toBigDecimal(args[0]);
        for (int i = 1; i < args.length; i++) {
            BigDecimal current = toBigDecimal(args[i]);
            if (current != null && (min == null || current.compareTo(min) < 0)) {
                min = current;
            }
        }
        return min;
    }

    private Object evaluateAbs(Object[] args) {
        if (args.length != 1) return null;
        BigDecimal value = toBigDecimal(args[0]);
        return value != null ? value.abs() : null;
    }

    private Object evaluateRound(Object[] args) {
        if (args.length == 0 || args.length > 2) return null;
        BigDecimal value = toBigDecimal(args[0]);
        if (value == null) return null;

        int scale = args.length > 1 ? toBigDecimal(args[1]).intValue() : 0;
        return value.setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    private Object evaluateCeil(Object[] args) {
        if (args.length != 1) return null;
        BigDecimal value = toBigDecimal(args[0]);
        return value != null ? value.setScale(0, java.math.RoundingMode.CEILING) : null;
    }

    private Object evaluateFloor(Object[] args) {
        if (args.length != 1) return null;
        BigDecimal value = toBigDecimal(args[0]);
        return value != null ? value.setScale(0, java.math.RoundingMode.FLOOR) : null;
    }

    private Object evaluateSqrt(Object[] args) {
        if (args.length != 1) return null;
        BigDecimal value = toBigDecimal(args[0]);
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) return null;
        return BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
    }

    private Object evaluatePow(Object[] args) {
        if (args.length != 2) return null;
        BigDecimal base = toBigDecimal(args[0]);
        BigDecimal exponent = toBigDecimal(args[1]);
        if (base == null || exponent == null) return null;
        return BigDecimal.valueOf(Math.pow(base.doubleValue(), exponent.doubleValue()));
    }

    private Object evaluateUnaryMath(Object[] args, String name, java.util.function.DoubleUnaryOperator op) {
        if (args.length != 1) {
            throw new IllegalArgumentException(name + "() requires exactly 1 argument");
        }
        BigDecimal v = toBigDecimal(args[0]);
        if (v == null) return null;
        double result = op.applyAsDouble(v.doubleValue());
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException(name + "() produced a non-finite result for input " + v);
        }
        return BigDecimal.valueOf(result);
    }

    private Object evaluateBinaryMath(Object[] args, String name, java.util.function.DoubleBinaryOperator op) {
        if (args.length != 2) {
            throw new IllegalArgumentException(name + "() requires exactly 2 arguments");
        }
        BigDecimal a = toBigDecimal(args[0]);
        BigDecimal b = toBigDecimal(args[1]);
        if (a == null || b == null) return null;
        double result = op.applyAsDouble(a.doubleValue(), b.doubleValue());
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException(name + "() produced a non-finite result");
        }
        return BigDecimal.valueOf(result);
    }

    private Object evaluateHash(Object[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("hash(value [, algorithm]) requires 1 or 2 arguments");
        }
        String input = args[0] == null ? "" : args[0].toString();
        String algorithm = args.length == 2 && args[1] != null ? args[1].toString() : "SHA-256";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("hash() unknown algorithm '" + algorithm + "'. Supported: MD5, SHA-1, SHA-256, SHA-512");
        }
    }

    @SuppressWarnings("unchecked")
    private Object evaluateInvokeRule(Object[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "invoke_rule(code [, inputs | key, value, ...]) requires at least the rule code argument");
        }
        if (ruleInvoker == null) {
            throw new IllegalStateException(
                    "invoke_rule() requires a RuleInvoker bean. Wire RuleInvokerImpl into the application context "
                            + "or evaluate via the full Spring-managed engine.");
        }
        String code = args[0] == null ? null : args[0].toString();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("invoke_rule(code, ...): first argument must be a non-empty rule code");
        }
        java.util.Map<String, Object> inputs;
        if (args.length == 1) {
            inputs = java.util.Collections.emptyMap();
        } else if (args.length == 2 && args[1] instanceof java.util.Map<?, ?> m) {
            inputs = (java.util.Map<String, Object>) m;
        } else {
            // Alternating-key/value form: invoke_rule("code", "k1", v1, "k2", v2, ...)
            if ((args.length - 1) % 2 != 0) {
                throw new IllegalArgumentException(
                        "invoke_rule: trailing arguments must be alternating key/value pairs (odd count)");
            }
            java.util.Map<String, Object> built = new java.util.LinkedHashMap<>();
            for (int i = 1; i < args.length; i += 2) {
                if (args[i] == null) {
                    throw new IllegalArgumentException("invoke_rule: input key at position " + i + " is null");
                }
                built.put(args[i].toString(), args[i + 1]);
            }
            inputs = built;
        }
        return ruleInvoker.invokeBlocking(code, inputs);
    }

    private Object evaluateLog(Object[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("log(message [, level]) requires 1 or 2 arguments");
        }
        String message = args[0] == null ? "null" : args[0].toString();
        String level = args.length == 2 && args[1] != null ? args[1].toString().toUpperCase() : "INFO";
        switch (level) {
            case "TRACE" -> log.trace("[rule-log] {}", message);
            case "DEBUG" -> log.debug("[rule-log] {}", message);
            case "WARN", "WARNING" -> log.warn("[rule-log] {}", message);
            case "ERROR" -> log.error("[rule-log] {}", message);
            default -> log.info("[rule-log] {}", message);
        }
        return message;
    }

    // String functions

    private Object evaluateLength(Object[] args) {
        if (args.length != 1) return null;
        String str = toString(args[0]);
        return BigDecimal.valueOf(str.length());
    }

    private Object evaluateSubstring(Object[] args) {
        if (args.length < 2 || args.length > 3) return null;
        String str = toString(args[0]);
        int start = toBigDecimal(args[1]).intValue();
        int end = args.length > 2 ? toBigDecimal(args[2]).intValue() : str.length();

        try {
            return str.substring(Math.max(0, start), Math.min(str.length(), end));
        } catch (Exception e) {
            return "";
        }
    }

    private Object evaluateUpper(Object[] args) {
        if (args.length != 1) return null;
        return toString(args[0]).toUpperCase();
    }

    private Object evaluateLower(Object[] args) {
        if (args.length != 1) return null;
        return toString(args[0]).toLowerCase();
    }

    private Object evaluateTrim(Object[] args) {
        if (args.length != 1) return null;
        return toString(args[0]).trim();
    }

    private Object evaluateContains(Object[] args) {
        if (args.length != 2) return null;
        String str = toString(args[0]);
        String search = toString(args[1]);
        return str.contains(search);
    }

    private Object evaluateStartsWith(Object[] args) {
        if (args.length != 2) return null;
        String str = toString(args[0]);
        String prefix = toString(args[1]);
        return str.startsWith(prefix);
    }

    private Object evaluateEndsWith(Object[] args) {
        if (args.length != 2) return null;
        String str = toString(args[0]);
        String suffix = toString(args[1]);
        return str.endsWith(suffix);
    }

    private Object evaluateReplace(Object[] args) {
        if (args.length != 3) return null;
        String str = toString(args[0]);
        String search = toString(args[1]);
        String replacement = toString(args[2]);
        return str.replace(search, replacement);
    }

    // Date/time functions (simplified implementations)

    private Object evaluateNow(Object[] args) {
        return java.time.LocalDateTime.now().toString();
    }

    private Object evaluateToday(Object[] args) {
        return java.time.LocalDate.now().toString();
    }

    private Object evaluateDateAdd(Object[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "dateadd(date, amount, unit) requires exactly 3 arguments; got " + args.length);
        }
        java.time.LocalDate date = parseDate(args[0]);
        if (date == null) {
            throw new IllegalArgumentException("dateadd: unparseable date '" + args[0] + "'");
        }
        int amount = toNumber(args[1]).intValue();
        String unit = toString(args[2]).toLowerCase();
        java.time.LocalDate result = switch (unit) {
            case "days", "day", "d" -> date.plusDays(amount);
            case "weeks", "week", "w" -> date.plusWeeks(amount);
            case "months", "month", "m" -> date.plusMonths(amount);
            case "years", "year", "y" -> date.plusYears(amount);
            default -> throw new IllegalArgumentException(
                    "dateadd: unsupported unit '" + unit + "'. Supported: days, weeks, months, years (and short forms d/w/m/y).");
        };
        return result.toString();
    }

    private Object evaluateDateDiff(Object[] args) {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "datediff(date1, date2[, unit]) requires 2 or 3 arguments; got " + args.length);
        }
        java.time.LocalDate date1 = parseDate(args[0]);
        if (date1 == null) {
            throw new IllegalArgumentException("datediff: unparseable date1 '" + args[0] + "'");
        }
        java.time.LocalDate date2 = parseDate(args[1]);
        if (date2 == null) {
            throw new IllegalArgumentException("datediff: unparseable date2 '" + args[1] + "'");
        }
        String unit = args.length > 2 ? toString(args[2]).toLowerCase() : "days";
        java.time.Period period = java.time.Period.between(date1, date2);
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(date1, date2);
        return switch (unit) {
            case "days", "day", "d" -> BigDecimal.valueOf(daysDiff);
            case "weeks", "week", "w" -> BigDecimal.valueOf(daysDiff / 7);
            case "months", "month", "m" -> BigDecimal.valueOf(period.toTotalMonths());
            case "years", "year", "y" -> BigDecimal.valueOf(period.getYears());
            default -> throw new IllegalArgumentException(
                    "datediff: unsupported unit '" + unit + "'. Supported: days, weeks, months, years (and short forms d/w/m/y).");
        };
    }

    /**
     * Compute age in years from a birth date. Accepts ISO {@code yyyy-MM-dd} or any string the
     * shared {@link #parseDate(Object)} helper recognises. With one argument, the reference date
     * is "today"; with two, the caller supplies the reference date (useful for "age at policy
     * inception").
     */
    private Object evaluateCalculateAge(Object[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "calculate_age(birthDate[, asOfDate]) takes 1 or 2 arguments, got " + args.length);
        }
        java.time.LocalDate birth = parseDate(args[0]);
        if (birth == null) {
            throw new IllegalArgumentException("calculate_age: unparseable birth date '" + args[0] + "'");
        }
        java.time.LocalDate asOf = args.length == 2 ? parseDate(args[1]) : java.time.LocalDate.now();
        if (asOf == null) {
            throw new IllegalArgumentException("calculate_age: unparseable reference date '" + args[1] + "'");
        }
        return BigDecimal.valueOf(java.time.Period.between(birth, asOf).getYears());
    }

    /**
     * Format a date using a {@link java.time.format.DateTimeFormatter} pattern.
     * Default pattern is ISO {@code yyyy-MM-dd}.
     */
    private Object evaluateFormatDate(Object[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "format_date(date[, pattern]) takes 1 or 2 arguments, got " + args.length);
        }
        java.time.LocalDate date = parseDate(args[0]);
        if (date == null) {
            throw new IllegalArgumentException("format_date: unparseable date '" + args[0] + "'");
        }
        String pattern = args.length == 2 ? toString(args[1]) : "yyyy-MM-dd";
        try {
            return date.format(java.time.format.DateTimeFormatter.ofPattern(pattern));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("format_date: invalid pattern '" + pattern + "': " + e.getMessage(), e);
        }
    }

    // List functions

    private Object evaluateSize(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            return BigDecimal.valueOf(((List<?>) value).size());
        }
        if (value instanceof String) {
            return BigDecimal.valueOf(((String) value).length());
        }
        return BigDecimal.ZERO;
    }

    private Object evaluateSum(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            BigDecimal sum = BigDecimal.ZERO;
            for (Object item : list) {
                BigDecimal num = toBigDecimal(item);
                if (num != null) {
                    sum = sum.add(num);
                }
            }
            return sum;
        }
        return BigDecimal.ZERO;
    }

    private Object evaluateAverage(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) return BigDecimal.ZERO;

            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            for (Object item : list) {
                BigDecimal num = toBigDecimal(item);
                if (num != null) {
                    sum = sum.add(num);
                    count++;
                }
            }
            return count > 0 ? sum.divide(BigDecimal.valueOf(count), 10, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    private Object evaluateFirst(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : list.get(0);
        }
        return null;
    }

    private Object evaluateLast(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }
        return null;
    }

    // Type conversion functions

    private Object evaluateToNumber(Object[] args) {
        if (args.length != 1) return null;
        return toBigDecimal(args[0]);
    }

    private Object evaluateToString(Object[] args) {
        if (args.length != 1) return null;
        return toString(args[0]);
    }

    private Object evaluateToBoolean(Object[] args) {
        if (args.length != 1) return null;
        return toBoolean(args[0]);
    }

    /**
     * Return the first non-null argument. Common pattern for default values:
     * {@code coalesce(user.preferred_name, user.legal_name, "Unknown")}.
     * <p>
     * Note: arguments are evaluated eagerly (the parser already resolved them before
     * the function was called); this differs from short-circuit semantics in some other
     * DSLs but matches the rest of this engine's function-call evaluation model.
     */
    private Object evaluateCoalesce(Object[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("coalesce(...) requires at least one argument");
        }
        for (Object arg : args) {
            if (arg != null) {
                return arg;
            }
        }
        return null;
    }

    /**
     * Inline conditional expression: {@code if_else(condition, thenValue, elseValue)}.
     * <p>
     * Equivalent to a ternary operator inside an expression context (such as a
     * {@code calculate} or {@code run} action), avoiding a full {@code if/then/else}
     * action block when you only need to pick between two values.
     * <p>
     * Note: both branches are evaluated up-front, matching the eager-argument contract
     * of the rest of the function-call layer.
     */
    private Object evaluateIfElse(Object[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "if_else(condition, then, else) requires exactly 3 arguments; got " + args.length);
        }
        return toBoolean(args[0]) ? args[1] : args[2];
    }

    // ---------------------------------------------------------------------------------
    // Date field extractors
    // ---------------------------------------------------------------------------------

    /**
     * Generic date-field extractor: parses arg[0] as a date and runs the given accessor.
     * Used for {@code year_of}, {@code month_of}, {@code day_of_month}, {@code day_of_week}.
     */
    private Object evaluateDatePart(Object[] args, String fnName, java.util.function.ToIntFunction<java.time.LocalDate> accessor) {
        if (args.length != 1) {
            throw new IllegalArgumentException(fnName + "(date) requires exactly 1 argument; got " + args.length);
        }
        java.time.LocalDate date = parseDate(args[0]);
        if (date == null) {
            throw new IllegalArgumentException(fnName + ": unparseable date '" + args[0] + "'");
        }
        return java.math.BigDecimal.valueOf(accessor.applyAsInt(date));
    }

    // ---------------------------------------------------------------------------------
    // Functional list operations -- filter/map/reduce/find by named function
    //
    // The DSL doesn't (yet) have an inline-lambda syntax, so these higher-order helpers
    // take the predicate / transformer as a STRING function name. The named function is
    // resolved through the same lookup ExpressionEvaluator uses for any other function
    // call -- {@link CustomFunctionRegistry} first, then the built-in catalog -- so
    // user-registered Spring beans and engine built-ins work identically.
    // ---------------------------------------------------------------------------------

    private Object callFunctionByName(String name, Object[] args) {
        java.util.List<Expression> argExprs = new java.util.ArrayList<>(args.length);
        for (Object a : args) argExprs.add(new LiteralExpression(null, a));
        FunctionCallExpression call = new FunctionCallExpression(null, name, argExprs);
        return visitFunctionCallExpression(call);
    }

    private static List<?> requireList(String fnName, Object value) {
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException(fnName + ": first argument must be a list, got "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return (List<?>) value;
    }

    private static String requireFunctionName(String fnName, Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(fnName + ": function name must be a string, got "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return (String) value;
    }

    private Object evaluateFilter(Object[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("filter(list, function_name) requires 2 arguments; got " + args.length);
        }
        List<?> input = requireList("filter", args[0]);
        String fn = requireFunctionName("filter", args[1]);
        List<Object> result = new java.util.ArrayList<>();
        for (Object item : input) {
            if (toBoolean(callFunctionByName(fn, new Object[]{item}))) {
                result.add(item);
            }
        }
        return result;
    }

    private Object evaluateMap(Object[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("map(list, function_name) requires 2 arguments; got " + args.length);
        }
        List<?> input = requireList("map", args[0]);
        String fn = requireFunctionName("map", args[1]);
        List<Object> result = new java.util.ArrayList<>(input.size());
        for (Object item : input) {
            result.add(callFunctionByName(fn, new Object[]{item}));
        }
        return result;
    }

    private Object evaluateReduce(Object[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("reduce(list, initial, function_name) requires 3 arguments; got " + args.length);
        }
        List<?> input = requireList("reduce", args[0]);
        Object acc = args[1];
        String fn = requireFunctionName("reduce", args[2]);
        for (Object item : input) {
            acc = callFunctionByName(fn, new Object[]{acc, item});
        }
        return acc;
    }

    private Object evaluateFind(Object[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("find(list, function_name) requires 2 arguments; got " + args.length);
        }
        List<?> input = requireList("find", args[0]);
        String fn = requireFunctionName("find", args[1]);
        for (Object item : input) {
            if (toBoolean(callFunctionByName(fn, new Object[]{item}))) return item;
        }
        return null;
    }

    private Object evaluateSort(Object[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("sort(list) requires exactly 1 argument; got " + args.length);
        }
        List<?> input = requireList("sort", args[0]);
        List<Object> copy = new java.util.ArrayList<>(input);
        @SuppressWarnings({"unchecked", "rawtypes"})
        java.util.Comparator<Object> cmp = (a, b) -> {
            if (a instanceof Number && b instanceof Number) {
                return toNumberSafe(a).compareTo(toNumberSafe(b));
            }
            return ((Comparable) a).compareTo(b);
        };
        copy.sort(cmp);
        return copy;
    }

    private Object evaluateReverse(Object[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("reverse(list) requires exactly 1 argument; got " + args.length);
        }
        List<?> input = requireList("reverse", args[0]);
        List<Object> copy = new java.util.ArrayList<>(input);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private Object evaluateDistinct(Object[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("distinct(list) requires exactly 1 argument; got " + args.length);
        }
        List<?> input = requireList("distinct", args[0]);
        // LinkedHashSet preserves insertion order while deduplicating.
        return new java.util.ArrayList<>(new java.util.LinkedHashSet<>(input));
    }

    // ---------------------------------------------------------------------------------
    // Statistical aggregates -- median, stddev, variance
    // ---------------------------------------------------------------------------------

    private List<java.math.BigDecimal> asNumericList(String fnName, Object[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(fnName + "(list) requires exactly 1 argument; got " + args.length);
        }
        List<?> input = requireList(fnName, args[0]);
        List<java.math.BigDecimal> nums = new java.util.ArrayList<>(input.size());
        for (Object o : input) nums.add(toNumberSafe(o));
        return nums;
    }

    private Object evaluateMedian(Object[] args) {
        List<java.math.BigDecimal> nums = asNumericList("median", args);
        if (nums.isEmpty()) return java.math.BigDecimal.ZERO;
        nums.sort(java.math.BigDecimal::compareTo);
        int mid = nums.size() / 2;
        if (nums.size() % 2 == 1) return nums.get(mid);
        return nums.get(mid - 1).add(nums.get(mid))
                .divide(java.math.BigDecimal.valueOf(2), 10, java.math.RoundingMode.HALF_UP);
    }

    private Object evaluateVariance(Object[] args) {
        List<java.math.BigDecimal> nums = asNumericList("variance", args);
        if (nums.size() < 2) return java.math.BigDecimal.ZERO;
        // Sample variance: sum((x - mean)^2) / (n - 1)
        java.math.BigDecimal mean = nums.stream().reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .divide(java.math.BigDecimal.valueOf(nums.size()), 10, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal sqSum = nums.stream()
                .map(x -> x.subtract(mean).pow(2))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return sqSum.divide(java.math.BigDecimal.valueOf(nums.size() - 1L), 10, java.math.RoundingMode.HALF_UP);
    }

    private Object evaluateStddev(Object[] args) {
        java.math.BigDecimal variance = (java.math.BigDecimal) evaluateVariance(args);
        return java.math.BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }

    private Object evaluatePercentile(Object[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("percentile(list, p) requires 2 arguments where p is in [0, 100]");
        }
        if (!(args[0] instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("percentile(list, p): first argument must be a non-empty list");
        }
        java.math.BigDecimal p = toBigDecimal(args[1]);
        if (p == null || p.compareTo(java.math.BigDecimal.ZERO) < 0 || p.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percentile(list, p): p must be in [0, 100], got " + p);
        }
        List<java.math.BigDecimal> nums = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            java.math.BigDecimal n = toBigDecimal(item);
            if (n == null) throw new IllegalArgumentException("percentile: list contains non-numeric value " + item);
            nums.add(n);
        }
        java.util.Collections.sort(nums);
        // Linear interpolation, NIST/Excel definition
        double rank = p.doubleValue() / 100.0 * (nums.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return nums.get(lo);
        double frac = rank - lo;
        return nums.get(lo).multiply(java.math.BigDecimal.valueOf(1 - frac))
                .add(nums.get(hi).multiply(java.math.BigDecimal.valueOf(frac)));
    }

    // ---------------------------------------------------------------------------------
    // String formatting -- format(template, args...) + concat(...)
    // ---------------------------------------------------------------------------------

    private Object evaluateStringFormat(Object[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("format(template, args...) requires a template argument");
        }
        if (!(args[0] instanceof String)) {
            throw new IllegalArgumentException("format: first argument must be a template string");
        }
        String template = (String) args[0];
        StringBuilder out = new StringBuilder(template.length());
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < template.length()) {
                // Find closing brace
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String indexStr = template.substring(i + 1, end);
                    try {
                        int idx = Integer.parseInt(indexStr);
                        if (idx + 1 < args.length) {
                            out.append(args[idx + 1]);
                        } else {
                            throw new IllegalArgumentException("format: template references {" + idx
                                    + "} but only " + (args.length - 1) + " arguments were supplied");
                        }
                        i = end;
                        continue;
                    } catch (NumberFormatException nfe) {
                        // not a numeric placeholder; emit verbatim
                    }
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private Object evaluateConcat(Object[] args) {
        StringBuilder out = new StringBuilder();
        for (Object a : args) out.append(a == null ? "" : a.toString());
        return out.toString();
    }

    // Property access implementation

    private Object evaluatePropertyAccess(Object object, String propertyPath) {
        if (object == null || propertyPath == null) return null;

        String[] parts = propertyPath.split("\\.");
        Object current = object;

        for (String part : parts) {
            if (current == null) return null;

            // Handle Map access
            if (current instanceof java.util.Map) {
                current = ((java.util.Map<?, ?>) current).get(part);
            }
            // Handle List access with numeric index
            else if (current instanceof List && part.matches("\\d+")) {
                List<?> list = (List<?>) current;
                int index = Integer.parseInt(part);
                current = (index >= 0 && index < list.size()) ? list.get(index) : null;
            }
            // Handle bean property access using reflection
            else {
                current = getPropertyValue(current, part);
            }
        }

        return current;
    }

    private Object getPropertyValue(Object object, String propertyName) {
        // Map-valued objects: property access is a key lookup. Missing key -> null
        // (legitimate "missing value" semantics, mirrored by json_get).
        if (object instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) object).get(propertyName);
        }
        // Bean access via reflection: try get<Prop> then is<Prop>; throw if neither exists.
        // A missing getter is almost always a typo in the rule, not a legitimate runtime
        // nullable, so surface it rather than masking it as a null value.
        String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        Class<?> cls = object.getClass();
        java.lang.reflect.Method accessor = findAccessor(cls, "get" + suffix);
        if (accessor == null) {
            accessor = findAccessor(cls, "is" + suffix);
        }
        if (accessor == null) {
            throw new IllegalArgumentException(
                    "No accessor 'get" + suffix + "' or 'is" + suffix + "' on " + cls.getName()
                            + "; check that property '" + propertyName + "' exists on the bean");
        }
        try {
            return accessor.invoke(object);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException(
                    "Failed to read property '" + propertyName + "' on " + cls.getName()
                            + ": " + cause.getMessage(), cause);
        }
    }

    private static java.lang.reflect.Method findAccessor(Class<?> cls, String name) {
        try {
            java.lang.reflect.Method m = cls.getMethod(name);
            return m.getParameterCount() == 0 ? m : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // Validation helper methods

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).trim().isEmpty();
        if (value instanceof java.util.Collection) return ((java.util.Collection<?>) value).isEmpty();
        if (value instanceof java.util.Map) return ((java.util.Map<?, ?>) value).isEmpty();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value) == 0;
        return false;
    }

    private boolean isNumeric(Object value) {
        if (value == null) return false;
        if (value instanceof Number) return true;
        if (value instanceof String) {
            try {
                new java.math.BigDecimal((String) value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private boolean isEmail(Object value) {
        if (value == null) return false;
        String str = toString(value);
        // Basic email validation regex
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return str.matches(emailRegex);
    }

    private boolean isPhone(Object value) {
        if (value == null) return false;
        String str = toString(value).replaceAll("[\\s\\-\\(\\)\\.]", "");
        // Basic phone validation - 10 or 11 digits, optionally starting with +1
        return str.matches("^(\\+?1)?[0-9]{10}$");
    }

    private boolean isDate(Object value) {
        if (value == null) return false;
        if (value instanceof java.time.LocalDate || value instanceof java.time.LocalDateTime ||
            value instanceof java.util.Date) return true;

        String str = toString(value);
        // Try common date formats
        String[] dateFormats = {
            "\\d{4}-\\d{2}-\\d{2}",           // YYYY-MM-DD
            "\\d{2}/\\d{2}/\\d{4}",           // MM/DD/YYYY
            "\\d{2}-\\d{2}-\\d{4}",           // MM-DD-YYYY
            "\\d{4}/\\d{2}/\\d{2}"            // YYYY/MM/DD
        };

        for (String format : dateFormats) {
            if (str.matches(format)) return true;
        }
        return false;
    }

    /**
     * Parse a date from various formats
     */
    private java.time.LocalDate parseDate(Object value) {
        if (value == null) return null;

        // Handle already parsed date objects
        if (value instanceof java.time.LocalDate) {
            return (java.time.LocalDate) value;
        }
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();
        }

        // Parse string dates
        String str = toString(value);

        // Try ISO format first (YYYY-MM-DD)
        try {
            return java.time.LocalDate.parse(str);
        } catch (Exception ignored) {}

        // Try other common formats
        java.time.format.DateTimeFormatter[] formatters = {
            java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
        };

        for (java.time.format.DateTimeFormatter formatter : formatters) {
            try {
                return java.time.LocalDate.parse(str, formatter);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private boolean isPositive(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isNegative(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.ZERO) < 0;
    }

    private boolean isZero(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isPercentage(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.ZERO) >= 0 && num.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    private boolean isCurrency(Object value) {
        if (value == null) return false;
        String str = toString(value);
        // Basic currency validation - optional $ sign, digits with optional decimal
        return str.matches("^\\$?\\d+(\\.\\d{2})?$");
    }

    private boolean isCreditScore(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.valueOf(300)) >= 0 && num.compareTo(BigDecimal.valueOf(850)) <= 0;
    }

    private boolean isSSN(Object value) {
        if (value == null) return false;
        String str = toString(value).replaceAll("[\\s\\-]", "");
        // SSN validation - 9 digits
        return str.matches("^\\d{9}$");
    }

    private boolean isAccountNumber(Object value) {
        if (value == null) return false;
        String str = toString(value).replaceAll("[\\s\\-]", "");
        // Account number validation - 8 to 17 digits
        return str.matches("^\\d{8,17}$");
    }

    private boolean isRoutingNumber(Object value) {
        if (value == null) return false;
        String str = toString(value).replaceAll("[\\s\\-]", "");
        // Routing number validation - exactly 9 digits with checksum
        if (!str.matches("^\\d{9}$")) return false;

        // ABA routing number checksum validation
        int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(str.charAt(i)) * weights[i];
        }
        return sum % 10 == 0;
    }

    private boolean isBusinessDay(Object value) {
        if (value == null) return false;

        try {
            java.time.LocalDate date;
            if (value instanceof java.time.LocalDate) {
                date = (java.time.LocalDate) value;
            } else if (value instanceof java.time.LocalDateTime) {
                date = ((java.time.LocalDateTime) value).toLocalDate();
            } else {
                // Try to parse string as date
                String str = toString(value);
                date = java.time.LocalDate.parse(str);
            }

            java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
            return dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWeekend(Object value) {
        return !isBusinessDay(value) && isDate(value);
    }

    private boolean ageAtLeast(Object birthDateValue, Object minAgeValue) {
        if (birthDateValue == null || minAgeValue == null) return false;

        try {
            java.time.LocalDate birthDate;
            if (birthDateValue instanceof java.time.LocalDate) {
                birthDate = (java.time.LocalDate) birthDateValue;
            } else {
                String str = toString(birthDateValue);
                birthDate = java.time.LocalDate.parse(str);
            }

            int minAge = toNumber(minAgeValue).intValue();
            java.time.LocalDate now = java.time.LocalDate.now();
            int actualAge = java.time.Period.between(birthDate, now).getYears();

            return actualAge >= minAge;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean ageLessThan(Object birthDateValue, Object maxAgeValue) {
        if (birthDateValue == null || maxAgeValue == null) return false;

        try {
            java.time.LocalDate birthDate;
            if (birthDateValue instanceof java.time.LocalDate) {
                birthDate = (java.time.LocalDate) birthDateValue;
            } else {
                String str = toString(birthDateValue);
                birthDate = java.time.LocalDate.parse(str);
            }

            int maxAge = toNumber(maxAgeValue).intValue();
            java.time.LocalDate now = java.time.LocalDate.now();
            int actualAge = java.time.Period.between(birthDate, now).getYears();

            return actualAge < maxAge;
        } catch (Exception e) {
            return false;
        }
    }

    // Length validation operators

    private boolean lengthEquals(Object value, Object expectedLength) {
        if (value == null || expectedLength == null) return false;

        int actualLength = getLength(value);
        int expected = toNumber(expectedLength).intValue();

        return actualLength == expected;
    }

    private boolean lengthGreaterThan(Object value, Object minLength) {
        if (value == null || minLength == null) return false;

        int actualLength = getLength(value);
        int min = toNumber(minLength).intValue();

        return actualLength > min;
    }

    private boolean lengthLessThan(Object value, Object maxLength) {
        if (value == null || maxLength == null) return false;

        int actualLength = getLength(value);
        int max = toNumber(maxLength).intValue();

        return actualLength < max;
    }

    private int getLength(Object value) {
        if (value instanceof String) {
            return ((String) value).length();
        }
        if (value instanceof List) {
            return ((List<?>) value).size();
        }
        if (value instanceof Object[]) {
            return ((Object[]) value).length;
        }
        return 0;
    }

    // Additional financial operators

    private boolean isNonZero(Object value) {
        if (value == null) return false;

        try {
            BigDecimal number = toNumber(value);
            return number.compareTo(BigDecimal.ZERO) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Financial calculation functions

    private Object calculateLoanPayment(Object[] args) {
        if (args.length < 3) return null;
        try {
            BigDecimal principal = toNumber(args[0]);
            BigDecimal annualRate = toNumber(args[1]);
            int termMonths = toNumber(args[2]).intValue();

            if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
                return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
            }

            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal onePlusRPowN = onePlusR.pow(termMonths);

            BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowN);
            BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);

            BigDecimal result = numerator.divide(denominator, 2, RoundingMode.HALF_UP);

            // If 4th parameter is provided, it's the result variable name - store the result
            if (args.length >= 4 && args[3] != null && context != null) {
                String resultVar = args[3].toString();
                context.setComputedVariable(resultVar, result);
            }

            return result;
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_loan_payment", e);
        }
    }

    private Object calculateCompoundInterest(Object[] args) {
        if (args.length < 4) return null;
        try {
            BigDecimal principal = toNumber(args[0]);
            BigDecimal annualRate = toNumber(args[1]);
            int compoundingFrequency = toNumber(args[2]).intValue();
            int years = toNumber(args[3]).intValue();

            BigDecimal rate = annualRate.divide(BigDecimal.valueOf(100 * compoundingFrequency), 10, RoundingMode.HALF_UP);
            int totalPeriods = compoundingFrequency * years;

            BigDecimal onePlusRate = BigDecimal.ONE.add(rate);
            BigDecimal compoundFactor = onePlusRate.pow(totalPeriods);

            return principal.multiply(compoundFactor);
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_compound_interest", e);
        }
    }

    private Object calculateAmortization(Object[] args) {
        if (args.length < 3) return null;
        try {
            BigDecimal principal = toNumber(args[0]);
            BigDecimal annualRate = toNumber(args[1]);
            int termMonths = toNumber(args[2]).intValue();

            BigDecimal monthlyPayment = (BigDecimal) calculateLoanPayment(args);
            if (monthlyPayment == null) return null;

            BigDecimal totalPayments = monthlyPayment.multiply(BigDecimal.valueOf(termMonths));
            BigDecimal totalInterest = totalPayments.subtract(principal);

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("monthly_payment", monthlyPayment);
            result.put("total_payments", totalPayments);
            result.put("total_interest", totalInterest);
            result.put("principal", principal);

            return result;
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_amortization", e);
        }
    }

    private Object debtToIncomeRatio(Object[] args) {
        if (args.length < 2) return null;
        try {
            BigDecimal monthlyDebt = toNumber(args[0]);
            BigDecimal monthlyIncome = toNumber(args[1]);

            if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0) return null;

            // Return as decimal ratio (0.333), not percentage (33.33)
            return monthlyDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP);
        } catch (RuntimeException e) {
            throw wrapFunctionError("debt_to_income_ratio", e);
        }
    }

    private Object creditUtilization(Object[] args) {
        if (args.length < 2) return null;
        try {
            BigDecimal currentBalance = toNumber(args[0]);
            BigDecimal creditLimit = toNumber(args[1]);

            if (creditLimit.compareTo(BigDecimal.ZERO) == 0) return null;

            return currentBalance.divide(creditLimit, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } catch (RuntimeException e) {
            throw wrapFunctionError("credit_utilization", e);
        }
    }

    private Object loanToValue(Object[] args) {
        if (args.length < 2) return null;
        try {
            BigDecimal loanAmount = toNumber(args[0]);
            BigDecimal propertyValue = toNumber(args[1]);

            if (propertyValue.compareTo(BigDecimal.ZERO) == 0) return null;

            return loanAmount.divide(propertyValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } catch (RuntimeException e) {
            throw wrapFunctionError("loan_to_value", e);
        }
    }

    private Object calculateAPR(Object[] args) {
        if (args.length < 4) return null;
        try {
            BigDecimal loanAmount = toNumber(args[0]);
            BigDecimal totalFees = toNumber(args[1]);
            BigDecimal monthlyPayment = toNumber(args[2]);
            int termMonths = toNumber(args[3]).intValue();

            // Simplified APR calculation
            BigDecimal totalPayments = monthlyPayment.multiply(BigDecimal.valueOf(termMonths));
            BigDecimal totalCost = totalPayments.add(totalFees);
            BigDecimal totalInterest = totalCost.subtract(loanAmount);

            BigDecimal avgBalance = loanAmount.divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
            BigDecimal annualInterest = totalInterest.divide(BigDecimal.valueOf(termMonths / 12.0), 10, RoundingMode.HALF_UP);

            return annualInterest.divide(avgBalance, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_apr", e);
        }
    }

    private Object calculateCreditScore(Object[] args) {
        // Simplified credit score calculation based on common factors
        if (args.length < 5) return null;
        try {
            BigDecimal paymentHistory = toNumber(args[0]); // 0-100
            BigDecimal creditUtilization = toNumber(args[1]); // 0-100
            BigDecimal creditHistoryLength = toNumber(args[2]); // years
            BigDecimal creditMix = toNumber(args[3]); // 0-100
            BigDecimal newCredit = toNumber(args[4]); // 0-100

            // FICO score weights: Payment History (35%), Credit Utilization (30%),
            // Credit History Length (15%), Credit Mix (10%), New Credit (10%)
            BigDecimal score = paymentHistory.multiply(BigDecimal.valueOf(0.35))
                    .add(creditUtilization.multiply(BigDecimal.valueOf(0.30)))
                    .add(creditHistoryLength.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.15))) // Scale years
                    .add(creditMix.multiply(BigDecimal.valueOf(0.10)))
                    .add(newCredit.multiply(BigDecimal.valueOf(0.10)));

            // Scale to 300-850 range
            BigDecimal scaledScore = score.multiply(BigDecimal.valueOf(5.5)).add(BigDecimal.valueOf(300));

            // Ensure within valid range
            if (scaledScore.compareTo(BigDecimal.valueOf(300)) < 0) return 300;
            if (scaledScore.compareTo(BigDecimal.valueOf(850)) > 0) return 850;

            return scaledScore.intValue();
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_credit_score", e);
        }
    }

    private Object calculateRiskScore(Object[] args) {
        // Simplified risk score calculation
        if (args.length < 4) return null;
        try {
            BigDecimal creditScore = toNumber(args[0]);
            BigDecimal debtToIncome = toNumber(args[1]);
            BigDecimal employmentYears = toNumber(args[2]);
            BigDecimal loanToValue = toNumber(args[3]);

            BigDecimal riskScore = BigDecimal.ZERO;

            // Credit score factor (lower score = higher risk)
            if (creditScore.compareTo(BigDecimal.valueOf(650)) < 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(30));
            } else if (creditScore.compareTo(BigDecimal.valueOf(700)) < 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(15));
            }

            // Debt-to-income factor
            if (debtToIncome.compareTo(BigDecimal.valueOf(40)) > 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(25));
            } else if (debtToIncome.compareTo(BigDecimal.valueOf(30)) > 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(10));
            }

            // Employment stability factor
            if (employmentYears.compareTo(BigDecimal.valueOf(2)) < 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(20));
            }

            // Loan-to-value factor
            if (loanToValue.compareTo(BigDecimal.valueOf(80)) > 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(15));
            }

            return riskScore;
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_risk_score", e);
        }
    }

    private Object paymentHistoryScore(Object[] args) {
        if (args.length < 3) return null;
        try {
            int totalPayments = toNumber(args[0]).intValue();
            int latePayments = toNumber(args[1]).intValue();
            int missedPayments = toNumber(args[2]).intValue();

            if (totalPayments == 0) return 0;

            BigDecimal onTimePayments = BigDecimal.valueOf(totalPayments - latePayments - missedPayments);
            BigDecimal onTimeRatio = onTimePayments.divide(BigDecimal.valueOf(totalPayments), 4, RoundingMode.HALF_UP);

            // Penalize late and missed payments more heavily
            BigDecimal latePenalty = BigDecimal.valueOf(latePayments * 5);
            BigDecimal missedPenalty = BigDecimal.valueOf(missedPayments * 15);

            BigDecimal score = onTimeRatio.multiply(BigDecimal.valueOf(100))
                    .subtract(latePenalty)
                    .subtract(missedPenalty);

            // Ensure score is between 0 and 100
            if (score.compareTo(BigDecimal.ZERO) < 0) return 0;
            if (score.compareTo(BigDecimal.valueOf(100)) > 0) return 100;

            return score;
        } catch (RuntimeException e) {
            throw wrapFunctionError("payment_history_score", e);
        }
    }

    // Financial validation functions

    private Object isValidCreditScore(Object[] args) {
        if (args.length < 1) return false;
        return isCreditScore(args[0]);
    }

    private Object isValidSSN(Object[] args) {
        if (args.length < 1) return false;
        return isSSN(args[0]);
    }

    private Object isValidAccount(Object[] args) {
        if (args.length < 1) return false;
        return isAccountNumber(args[0]);
    }

    private Object isValidRouting(Object[] args) {
        if (args.length < 1) return false;
        return isRoutingNumber(args[0]);
    }

    private Object isBusinessDayFunction(Object[] args) {
        if (args.length < 1) return false;
        return isBusinessDay(args[0]);
    }

    private Object ageMeetsRequirement(Object[] args) {
        if (args.length < 2) return false;
        return ageAtLeast(args[0], args[1]);
    }

    // Utility functions

    private Object formatCurrency(Object[] args) {
        if (args.length < 1) return null;
        try {
            BigDecimal amount = toNumber(args[0]);
            String currency = args.length > 1 ? toString(args[1]) : "USD";

            java.text.NumberFormat formatter = java.text.NumberFormat.getCurrencyInstance();
            if ("USD".equals(currency)) {
                formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US);
            }

            return formatter.format(amount);
        } catch (RuntimeException e) {
            throw wrapFunctionError("format_currency", e);
        }
    }

    private Object formatPercentage(Object[] args) {
        if (args.length < 1) return null;
        try {
            BigDecimal value = toNumber(args[0]);
            int decimals = args.length > 1 ? toNumber(args[1]).intValue() : 2;

            java.text.NumberFormat formatter = java.text.NumberFormat.getPercentInstance();
            formatter.setMaximumFractionDigits(decimals);
            formatter.setMinimumFractionDigits(decimals);

            return formatter.format(value.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        } catch (RuntimeException e) {
            throw wrapFunctionError("format_percentage", e);
        }
    }

    private Object generateAccountNumber(Object[] args) {
        // Generate a random 12-digit account number
        java.util.Random random = new java.util.Random();
        StringBuilder accountNumber = new StringBuilder();

        for (int i = 0; i < 12; i++) {
            accountNumber.append(random.nextInt(10));
        }

        return accountNumber.toString();
    }

    private Object generateTransactionId(Object[] args) {
        // Generate a UUID-based transaction ID
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private Object distanceBetween(Object[] args) {
        if (args.length < 4) return null;
        try {
            double lat1 = toNumber(args[0]).doubleValue();
            double lon1 = toNumber(args[1]).doubleValue();
            double lat2 = toNumber(args[2]).doubleValue();
            double lon2 = toNumber(args[3]).doubleValue();

            // Haversine formula for calculating distance between two points on Earth
            double R = 6371; // Earth's radius in kilometers

            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);

            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            double distance = R * c;

            return BigDecimal.valueOf(distance);
        } catch (RuntimeException e) {
            throw wrapFunctionError("distance_between", e);
        }
    }

    private Object timeHour(Object[] args) {
        if (args.length < 1) return null;
        try {
            String timestamp = toString(args[0]);

            // Try to parse as ISO timestamp
            if (timestamp.contains("T")) {
                java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timestamp.substring(0, 19));
                return dateTime.getHour();
            }

            // Try to parse as time only (HH:mm:ss)
            if (timestamp.contains(":")) {
                String[] parts = timestamp.split(":");
                return Integer.parseInt(parts[0]);
            }

            return null;
        } catch (RuntimeException e) {
            throw wrapFunctionError("time_hour", e);
        }
    }

    private Object isValid(Object[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "is_valid(value, type) requires 2 arguments (value, type); got " + args.length);
        }
        Object value = args[0];
        String validationType = toString(args[1]);
        return switch (validationType.toLowerCase()) {
            case "email", "email_format" -> isEmail(value);
            case "phone", "phone_format" -> isPhone(value);
            case "ssn", "ssn_format" -> isSSN(value);
            case "credit_score" -> isCreditScore(value);
            case "account_number" -> isAccountNumber(value);
            case "routing_number" -> isRoutingNumber(value);
            case "numeric" -> isNumeric(value);
            case "date" -> isDate(value);
            case "positive" -> isPositive(value);
            case "negative" -> isNegative(value);
            case "currency" -> isCurrency(value);
            case "percentage" -> isPercentage(value);
            default -> throw new IllegalArgumentException(
                    "is_valid: unknown validation type '" + validationType + "'. "
                            + "Known types: email, phone, ssn, credit_score, account_number, "
                            + "routing_number, numeric, date, positive, negative, currency, percentage.");
        };
    }

    private Object inRange(Object[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "is_in_range(value, low, high) / in_range(...) requires 3 arguments; got " + args.length);
        }
        try {
            BigDecimal value = toNumber(args[0]);
            BigDecimal min = toNumber(args[1]);
            BigDecimal max = toNumber(args[2]);
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
        } catch (RuntimeException e) {
            throw wrapFunctionError("in_range", e);
        }
    }

    // Logging/Auditing functions

    private Object audit(Object[] args) {
        if (args.length < 2) return null;

        String event = toString(args[0]);
        String details = toString(args[1]);

        log.info("AUDIT: {} - {}", event, details);

        // In a real implementation, this would write to an audit log
        return true;
    }

    private Object auditLog(Object[] args) {
        if (args.length < 1) return null;

        String message = toString(args[0]);
        String level = args.length > 1 ? toString(args[1]) : "INFO";

        switch (level.toUpperCase()) {
            case "ERROR" -> log.error("AUDIT: {}", message);
            case "WARN" -> log.warn("AUDIT: {}", message);
            case "DEBUG" -> log.debug("AUDIT: {}", message);
            default -> log.info("AUDIT: {}", message);
        }

        return true;
    }

    private Object sendNotification(Object[] args) {
        if (args.length < 2) return null;

        String recipient = toString(args[0]);
        String message = toString(args[1]);
        String type = args.length > 2 ? toString(args[2]) : "EMAIL";

        log.info("NOTIFICATION [{}] to {}: {}", type, recipient, message);

        // In a real implementation, this would send actual notifications
        return true;
    }

    // Data Security functions

    private Object encrypt(Object[] args) {
        if (args.length < 1) return null;

        String data = toString(args[0]);
        String algorithm = args.length > 1 ? toString(args[1]) : "AES";

        // Simplified encryption simulation
        String encrypted = "ENC[" + algorithm + "]:" + java.util.Base64.getEncoder().encodeToString(data.getBytes());

        log.debug("Encrypted data using {}", algorithm);
        return encrypted;
    }

    private Object decrypt(Object[] args) {
        if (args.length < 1) return null;

        String encryptedData = toString(args[0]);

        // Simplified decryption simulation
        if (encryptedData.startsWith("ENC[") && encryptedData.contains("]:")) {
            String base64Data = encryptedData.substring(encryptedData.indexOf("]:") + 2);
            try {
                String decrypted = new String(java.util.Base64.getDecoder().decode(base64Data));
                log.debug("Decrypted data");
                return decrypted;
            } catch (Exception e) {
                log.warn("Failed to decrypt data: {}", e.getMessage());
                return null;
            }
        }

        return encryptedData; // Return as-is if not encrypted
    }

    private Object maskData(Object[] args) {
        if (args.length < 1) return null;

        String data = toString(args[0]);
        String maskChar = args.length > 1 ? toString(args[1]) : "*";
        int visibleChars = args.length > 2 ? toNumber(args[2]).intValue() : 4;

        if (data.length() <= visibleChars) {
            return maskChar.repeat(data.length());
        }

        String masked = data.substring(0, visibleChars) + maskChar.repeat(data.length() - visibleChars);
        log.debug("Masked data, showing {} characters", visibleChars);
        return masked;
    }

    // Advanced Financial functions

    private Object calculateDebtRatio(Object[] args) {
        if (args.length < 2) return null;

        try {
            BigDecimal totalDebt = toNumber(args[0]);
            BigDecimal totalIncome = toNumber(args[1]);

            if (totalIncome.compareTo(BigDecimal.ZERO) == 0) {
                return null; // Avoid division by zero
            }

            BigDecimal ratio = totalDebt.divide(totalIncome, 4, RoundingMode.HALF_UP);
            return ratio.multiply(BigDecimal.valueOf(100)); // Return as percentage
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_debt_ratio", e);
        }
    }

    private Object calculateLTV(Object[] args) {
        if (args.length < 2) return null;

        try {
            BigDecimal loanAmount = toNumber(args[0]);
            BigDecimal propertyValue = toNumber(args[1]);

            if (propertyValue.compareTo(BigDecimal.ZERO) == 0) {
                return null; // Avoid division by zero
            }

            BigDecimal ltv = loanAmount.divide(propertyValue, 4, RoundingMode.HALF_UP);
            return ltv.multiply(BigDecimal.valueOf(100)); // Return as percentage
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_ltv", e);
        }
    }

    private Object calculatePaymentSchedule(Object[] args) {
        if (args.length < 3) return null;

        try {
            BigDecimal principal = toNumber(args[0]);
            BigDecimal annualRate = toNumber(args[1]);
            int termMonths = toNumber(args[2]).intValue();

            BigDecimal monthlyPayment = (BigDecimal) calculateLoanPayment(args);
            if (monthlyPayment == null) return null;

            java.util.List<java.util.Map<String, Object>> schedule = new java.util.ArrayList<>();
            BigDecimal remainingBalance = principal;
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);

            for (int month = 1; month <= termMonths; month++) {
                BigDecimal interestPayment = remainingBalance.multiply(monthlyRate);
                BigDecimal principalPayment = monthlyPayment.subtract(interestPayment);
                remainingBalance = remainingBalance.subtract(principalPayment);

                java.util.Map<String, Object> payment = new java.util.HashMap<>();
                payment.put("month", month);
                payment.put("payment", monthlyPayment);
                payment.put("principal", principalPayment);
                payment.put("interest", interestPayment);
                payment.put("balance", remainingBalance);

                schedule.add(payment);

                if (remainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
            }

            return schedule;
        } catch (RuntimeException e) {
            throw wrapFunctionError("calculate_payment_schedule", e);
        }
    }

    // REST API Functions

    private Object restGet(Object[] args) {
        if (restCallService == null) {
            log.warn("RestCallService not available");
            return createErrorResponse("RestCallService not available");
        }

        if (args.length < 1) {
            return createErrorResponse("rest_get requires at least 1 argument: url");
        }

        try {
            String url = args[0].toString();
            @SuppressWarnings("unchecked")
            Map<String, String> headers = args.length > 1 ? (Map<String, String>) args[1] : null;
            Long timeout = args.length > 2 ? toLong(args[2]) : null;

            return restCallService.get(url, headers, timeout).block();
        } catch (Exception e) {
            return createErrorResponse("REST GET failed: " + e.getMessage());
        }
    }

    private Object restPost(Object[] args) {
        if (restCallService == null) {
            log.warn("RestCallService not available");
            return createErrorResponse("RestCallService not available");
        }

        if (args.length < 2) {
            return createErrorResponse("rest_post requires at least 2 arguments: url, body");
        }

        try {
            String url = args[0].toString();
            Object body = args[1];
            @SuppressWarnings("unchecked")
            Map<String, String> headers = args.length > 2 ? (Map<String, String>) args[2] : null;
            Long timeout = args.length > 3 ? toLong(args[3]) : null;

            return restCallService.post(url, body, headers, timeout).block();
        } catch (Exception e) {
            return createErrorResponse("REST POST failed: " + e.getMessage());
        }
    }

    private Object restPut(Object[] args) {
        if (restCallService == null) {
            log.warn("RestCallService not available");
            return createErrorResponse("RestCallService not available");
        }

        if (args.length < 2) {
            return createErrorResponse("rest_put requires at least 2 arguments: url, body");
        }

        try {
            String url = args[0].toString();
            Object body = args[1];
            @SuppressWarnings("unchecked")
            Map<String, String> headers = args.length > 2 ? (Map<String, String>) args[2] : null;
            Long timeout = args.length > 3 ? toLong(args[3]) : null;

            return restCallService.put(url, body, headers, timeout).block();
        } catch (Exception e) {
            return createErrorResponse("REST PUT failed: " + e.getMessage());
        }
    }

    private Object restDelete(Object[] args) {
        if (restCallService == null) {
            log.warn("RestCallService not available");
            return createErrorResponse("RestCallService not available");
        }

        if (args.length < 1) {
            return createErrorResponse("rest_delete requires at least 1 argument: url");
        }

        try {
            String url = args[0].toString();
            @SuppressWarnings("unchecked")
            Map<String, String> headers = args.length > 1 ? (Map<String, String>) args[1] : null;
            Long timeout = args.length > 2 ? toLong(args[2]) : null;

            return restCallService.delete(url, headers, timeout).block();
        } catch (Exception e) {
            return createErrorResponse("REST DELETE failed: " + e.getMessage());
        }
    }

    private Object restPatch(Object[] args) {
        if (restCallService == null) {
            log.warn("RestCallService not available");
            return createErrorResponse("RestCallService not available");
        }

        if (args.length < 2) {
            return createErrorResponse("rest_patch requires at least 2 arguments: url, body");
        }

        try {
            String url = args[0].toString();
            Object body = args[1];
            @SuppressWarnings("unchecked")
            Map<String, String> headers = args.length > 2 ? (Map<String, String>) args[2] : null;
            Long timeout = args.length > 3 ? toLong(args[3]) : null;

            return restCallService.patch(url, body, headers, timeout).block();
        } catch (Exception e) {
            return createErrorResponse("REST PATCH failed: " + e.getMessage());
        }
    }

    private Object restCall(Object[] args) {
        if (restCallService == null) {
            log.warn("RestCallService not available");
            return createErrorResponse("RestCallService not available");
        }

        if (args.length < 2) {
            return createErrorResponse("rest_call requires at least 2 arguments: method, url");
        }

        try {
            String method = args[0].toString();
            String url = args[1].toString();
            Object body = args.length > 2 ? args[2] : null;
            @SuppressWarnings("unchecked")
            Map<String, String> headers = args.length > 3 ? (Map<String, String>) args[3] : null;
            Long timeout = args.length > 4 ? toLong(args[4]) : null;

            return restCallService.request(method, url, body, headers, timeout).block();
        } catch (Exception e) {
            return createErrorResponse("REST call failed: " + e.getMessage());
        }
    }

    // JSON Path Functions

    private Object jsonGet(Object[] args) {
        if (jsonPathService == null) {
            log.warn("JsonPathService not available");
            return null;
        }

        if (args.length < 2) {
            log.warn("json_get requires 2 arguments: jsonData, path");
            return null;
        }

        try {
            Object jsonData = args[0];
            String path = args[1].toString();

            return jsonPathService.extractValue(jsonData, path);
        } catch (Exception e) {
            log.warn("Error in json_get: {}", e.getMessage());
            return null;
        }
    }

    private Object jsonExists(Object[] args) {
        if (jsonPathService == null) {
            log.warn("JsonPathService not available");
            return false;
        }

        if (args.length < 2) {
            log.warn("json_exists requires 2 arguments: jsonData, path");
            return false;
        }

        try {
            Object jsonData = args[0];
            String path = args[1].toString();

            return jsonPathService.pathExists(jsonData, path);
        } catch (Exception e) {
            log.warn("Error in json_exists: {}", e.getMessage());
            return false;
        }
    }

    private Object jsonSize(Object[] args) {
        if (jsonPathService == null) {
            log.warn("JsonPathService not available");
            return -1;
        }

        if (args.length < 2) {
            log.warn("json_size requires 2 arguments: jsonData, path");
            return -1;
        }

        try {
            Object jsonData = args[0];
            String path = args[1].toString();

            return jsonPathService.getSize(jsonData, path);
        } catch (Exception e) {
            log.warn("Error in json_size: {}", e.getMessage());
            return -1;
        }
    }

    private Object jsonType(Object[] args) {
        if (jsonPathService == null) {
            log.warn("JsonPathService not available");
            return null;
        }

        if (args.length < 2) {
            log.warn("json_type requires 2 arguments: jsonData, path");
            return null;
        }

        try {
            Object jsonData = args[0];
            String path = args[1].toString();

            Class<?> type = jsonPathService.getValueType(jsonData, path);
            return type != null ? type.getSimpleName() : null;
        } catch (Exception e) {
            log.warn("Error in json_type: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> errorResponse = new java.util.HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", true);
        errorResponse.put("message", message);
        return errorResponse;
    }

    /**
     * Convert an object to Long
     */
    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Cannot convert '" + value + "' to a long; expected integer-like input", e);
        }
    }
}
