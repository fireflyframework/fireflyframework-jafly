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

package org.fireflyframework.rules.core.dsl.parser;

import org.fireflyframework.rules.core.dsl.SourceLocation;
import org.fireflyframework.rules.core.dsl.condition.*;
import org.fireflyframework.rules.core.dsl.expression.Expression;
import org.fireflyframework.rules.core.dsl.expression.LiteralExpression;
import org.fireflyframework.rules.core.dsl.expression.VariableExpression;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parser for complex conditions syntax that handles structured YAML objects.
 * This supports the advanced conditions block syntax with compare objects,
 * logical operators (and/or), and nested structures.
 */
@RequiredArgsConstructor
@Slf4j
public class ComplexConditionsParser {
    
    private final DSLParser dslParser;
    
    /**
     * Parse a complex condition from a structured YAML object
     */
    @SuppressWarnings("unchecked")
    public Condition parseComplexCondition(Object conditionObj) {
        if (conditionObj instanceof String) {
            // Simple string condition - delegate to simple parser
            return dslParser.parseCondition((String) conditionObj);
        }
        
        if (conditionObj instanceof Map) {
            Map<String, Object> conditionMap = (Map<String, Object>) conditionObj;
            
            // Handle logical operators
            if (conditionMap.containsKey("and")) {
                return parseLogicalCondition(LogicalOperator.AND, conditionMap.get("and"));
            }
            
            if (conditionMap.containsKey("or")) {
                return parseLogicalCondition(LogicalOperator.OR, conditionMap.get("or"));
            }
            
            if (conditionMap.containsKey("not")) {
                return parseLogicalCondition(LogicalOperator.NOT, conditionMap.get("not"));
            }
            
            // Handle comparison condition
            if (conditionMap.containsKey("compare")) {
                return parseComparisonCondition((Map<String, Object>) conditionMap.get("compare"));
            }
            
            throw new ParseException("Unknown complex condition structure: " + conditionMap.keySet(),
                                    SourceLocation.at(1, 1), "COMPLEX_CONDITION_UNKNOWN");
        }

        throw new ParseException("Invalid condition object type: " + conditionObj.getClass(),
                                SourceLocation.at(1, 1), "COMPLEX_CONDITION_INVALID_TYPE");
    }
    
    /**
     * Parse a logical condition (and/or/not)
     */
    @SuppressWarnings("unchecked")
    private Condition parseLogicalCondition(LogicalOperator operator, Object operandsObj) {
        SourceLocation location = SourceLocation.at(1, 1);
        
        if (operandsObj instanceof List) {
            List<Object> operandsList = (List<Object>) operandsObj;
            List<Condition> operands = operandsList.stream()
                    .map(this::parseComplexCondition)
                    .collect(Collectors.toList());
            
            return new LogicalCondition(location, operator, operands);
        }
        
        if (operandsObj instanceof Map || operandsObj instanceof String) {
            // Single operand (for NOT operator or single-item lists)
            Condition operand = parseComplexCondition(operandsObj);
            return new LogicalCondition(location, operator, List.of(operand));
        }
        
        throw new ParseException("Invalid operands for logical operator " + operator + ": " + operandsObj,
                                SourceLocation.at(1, 1), "LOGICAL_OPERATOR_INVALID_OPERANDS");
    }
    
    /**
     * Parse a comparison condition from a compare object
     */
    @SuppressWarnings("unchecked")
    private Condition parseComparisonCondition(Map<String, Object> compareMap) {
        SourceLocation location = SourceLocation.at(1, 1);
        
        // Extract left, operator, and right
        Object leftObj = compareMap.get("left");
        String operatorStr = (String) compareMap.get("operator");
        Object rightObj = compareMap.get("right");
        
        if (leftObj == null || operatorStr == null || rightObj == null) {
            throw new ParseException("Compare condition must have 'left', 'operator', and 'right' fields",
                                    SourceLocation.at(1, 1), "COMPARE_CONDITION_MISSING_FIELDS");
        }
        
        // Parse expressions
        Expression left = parseExpression(leftObj);
        Expression right = parseExpression(rightObj);
        
        // Map operator string to ComparisonOperator
        ComparisonOperator operator = mapStringToComparisonOperator(operatorStr);
        
        // Handle ternary operators like BETWEEN
        if (operator == ComparisonOperator.BETWEEN || operator == ComparisonOperator.NOT_BETWEEN) {
            Object rangeEndObj = compareMap.get("rangeEnd");
            if (rangeEndObj != null) {
                Expression rangeEnd = parseExpression(rangeEndObj);
                return new ComparisonCondition(location, left, operator, right, rangeEnd);
            }
        }
        
        return new ComparisonCondition(location, left, operator, right);
    }
    
    /**
     * Parse an expression from various object types
     */
    private Expression parseExpression(Object obj) {
        SourceLocation location = SourceLocation.at(1, 1);
        
        if (obj instanceof String) {
            String str = (String) obj;
            // Check if it's a variable name or a string literal
            if (isVariableName(str)) {
                return new VariableExpression(location, str, null);
            } else {
                // Explicitly use the constructor with SourceLocation first
                LiteralExpression expr = new LiteralExpression(location, (Object) str);
                return expr;
            }
        }

        if (obj instanceof Number) {
            return new LiteralExpression(location, obj);
        }

        if (obj instanceof Boolean) {
            return new LiteralExpression(location, obj);
        }

        if (obj == null) {
            return new LiteralExpression(location, (Object) null);
        }

        throw new ParseException("Cannot parse expression from object: " + obj,
                                SourceLocation.at(1, 1), "EXPRESSION_PARSE_ERROR");
    }
    
    /**
     * Check if a string represents a variable name (not a string literal)
     */
    private boolean isVariableName(String str) {
        // Variable names don't contain spaces and follow identifier patterns
        return str.matches("[a-zA-Z_][a-zA-Z0-9_]*") || 
               str.matches("[A-Z_][A-Z0-9_]*"); // CONSTANT_NAME pattern
    }
    
    /**
     * Map operator string to ComparisonOperator enum
     */
    private ComparisonOperator mapStringToComparisonOperator(String operatorStr) {
        return switch (operatorStr.toLowerCase()) {
            case "equals", "==" -> ComparisonOperator.EQUALS;
            case "not_equals", "!=" -> ComparisonOperator.NOT_EQUALS;
            case "greater_than", ">" -> ComparisonOperator.GREATER_THAN;
            case "less_than", "<" -> ComparisonOperator.LESS_THAN;
            case "greater_than_or_equal", "at_least", ">=" -> ComparisonOperator.GREATER_EQUAL;
            case "less_than_or_equal", "at_most", "<=" -> ComparisonOperator.LESS_EQUAL;
            case "contains" -> ComparisonOperator.CONTAINS;
            case "not_contains" -> ComparisonOperator.NOT_CONTAINS;
            case "starts_with" -> ComparisonOperator.STARTS_WITH;
            case "ends_with" -> ComparisonOperator.ENDS_WITH;
            case "matches" -> ComparisonOperator.MATCHES;
            case "not_matches" -> ComparisonOperator.NOT_MATCHES;
            case "in_list", "in" -> ComparisonOperator.IN_LIST;
            case "not_in_list", "not_in" -> ComparisonOperator.NOT_IN_LIST;
            case "between" -> ComparisonOperator.BETWEEN;
            case "not_between" -> ComparisonOperator.NOT_BETWEEN;
            case "exists" -> ComparisonOperator.EXISTS;
            case "not_exists" -> ComparisonOperator.NOT_EXISTS;
            case "is_null" -> ComparisonOperator.IS_NULL;
            case "is_not_null" -> ComparisonOperator.IS_NOT_NULL;
            case "is_empty" -> ComparisonOperator.IS_EMPTY;
            case "is_not_empty" -> ComparisonOperator.IS_NOT_EMPTY;
            case "is_numeric" -> ComparisonOperator.IS_NUMERIC;
            case "is_not_numeric" -> ComparisonOperator.IS_NOT_NUMERIC;
            case "is_email" -> ComparisonOperator.IS_EMAIL;
            case "is_phone" -> ComparisonOperator.IS_PHONE;
            case "is_date" -> ComparisonOperator.IS_DATE;
            case "is_positive" -> ComparisonOperator.IS_POSITIVE;
            case "is_negative" -> ComparisonOperator.IS_NEGATIVE;
            case "is_zero" -> ComparisonOperator.IS_ZERO;
            case "is_percentage" -> ComparisonOperator.IS_PERCENTAGE;
            case "is_currency" -> ComparisonOperator.IS_CURRENCY;
            case "is_credit_score" -> ComparisonOperator.IS_CREDIT_SCORE;
            case "is_ssn" -> ComparisonOperator.IS_SSN;
            case "is_account_number" -> ComparisonOperator.IS_ACCOUNT_NUMBER;
            case "is_routing_number" -> ComparisonOperator.IS_ROUTING_NUMBER;
            case "is_business_day" -> ComparisonOperator.IS_BUSINESS_DAY;
            case "is_weekend" -> ComparisonOperator.IS_WEEKEND;
            case "age_at_least" -> ComparisonOperator.AGE_AT_LEAST;
            case "age_less_than" -> ComparisonOperator.AGE_LESS_THAN;
            default -> throw new ParseException("Unknown comparison operator: " + operatorStr,
                                               SourceLocation.at(1, 1), "UNKNOWN_COMPARISON_OPERATOR");
        };
    }
}
