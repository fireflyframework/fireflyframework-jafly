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

package org.fireflyframework.rules.core.dsl.expression;

/**
 * Enumeration of binary operators.
 */
public enum BinaryOperator {
    // Arithmetic operators
    ADD("+", ExpressionType.NUMBER, 6),
    SUBTRACT("-", ExpressionType.NUMBER, 6),
    MULTIPLY("*", ExpressionType.NUMBER, 7),
    DIVIDE("/", ExpressionType.NUMBER, 7),
    MODULO("%", ExpressionType.NUMBER, 7),

    // Comparison operators
    EQUALS("==", ExpressionType.BOOLEAN, 4),
    NOT_EQUALS("!=", ExpressionType.BOOLEAN, 4),
    LESS_THAN("<", ExpressionType.BOOLEAN, 5),
    LESS_THAN_OR_EQUAL("<=", ExpressionType.BOOLEAN, 5),
    GREATER_THAN(">", ExpressionType.BOOLEAN, 5),
    GREATER_THAN_OR_EQUAL(">=", ExpressionType.BOOLEAN, 5),
    GREATER_EQUAL(">=", ExpressionType.BOOLEAN, 5),  // Alias
    LESS_EQUAL("<=", ExpressionType.BOOLEAN, 5),     // Alias

    // String operators
    CONTAINS("contains", ExpressionType.BOOLEAN, 4),
    NOT_CONTAINS("not_contains", ExpressionType.BOOLEAN, 4),
    STARTS_WITH("starts_with", ExpressionType.BOOLEAN, 4),
    ENDS_WITH("ends_with", ExpressionType.BOOLEAN, 4),
    MATCHES("matches", ExpressionType.BOOLEAN, 4),
    NOT_MATCHES("not_matches", ExpressionType.BOOLEAN, 4),

    // Logical operators
    AND("and", ExpressionType.BOOLEAN, 2),
    OR("or", ExpressionType.BOOLEAN, 1),

    // Special operators
    BETWEEN("between", ExpressionType.BOOLEAN, 4),
    NOT_BETWEEN("not_between", ExpressionType.BOOLEAN, 4),
    IN("in", ExpressionType.BOOLEAN, 4),
    IN_LIST("in", ExpressionType.BOOLEAN, 4),        // Alias
    NOT_IN_LIST("not_in", ExpressionType.BOOLEAN, 4),

    // Age validation operators
    AGE_AT_LEAST("age_at_least", ExpressionType.BOOLEAN, 4),
    AGE_LESS_THAN("age_less_than", ExpressionType.BOOLEAN, 4),

    // Power operator
    POWER("^", ExpressionType.NUMBER, 8);

    private final String symbol;
    private final ExpressionType resultType;
    private final int precedence;

    BinaryOperator(String symbol, ExpressionType resultType, int precedence) {
        this.symbol = symbol;
        this.resultType = resultType;
        this.precedence = precedence;
    }
    
    public String getSymbol() {
        return symbol;
    }

    public ExpressionType getResultType() {
        return resultType;
    }

    public int getPrecedence() {
        return precedence;
    }
    
    public static BinaryOperator fromSymbol(String symbol) {
        for (BinaryOperator op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown binary operator: " + symbol);
    }
}
