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

import org.fireflyframework.rules.core.dsl.lexer.TokenType;

/**
 * Enumeration of unary operators.
 */
public enum UnaryOperator {
    // Arithmetic operators
    NEGATE("-", ExpressionType.NUMBER),
    POSITIVE("+", ExpressionType.NUMBER),
    MINUS("-", ExpressionType.NUMBER),  // Alias for NEGATE
    PLUS("+", ExpressionType.NUMBER),   // Alias for POSITIVE

    // Logical operators
    NOT("not", ExpressionType.BOOLEAN),

    // Existence operators
    EXISTS("exists", ExpressionType.BOOLEAN),
    IS_NULL("is_null", ExpressionType.BOOLEAN),
    IS_NOT_NULL("is_not_null", ExpressionType.BOOLEAN),

    // Type checking operators
    IS_NUMBER("is_number", ExpressionType.BOOLEAN),
    IS_STRING("is_string", ExpressionType.BOOLEAN),
    IS_BOOLEAN("is_boolean", ExpressionType.BOOLEAN),
    IS_LIST("is_list", ExpressionType.BOOLEAN),

    // String operators
    TO_UPPER("to_upper", ExpressionType.STRING),
    TO_LOWER("to_lower", ExpressionType.STRING),
    TRIM("trim", ExpressionType.STRING),
    LENGTH("length", ExpressionType.NUMBER),

    // Validation operators (return boolean)
    IS_POSITIVE("is_positive", ExpressionType.BOOLEAN),
    IS_NEGATIVE("is_negative", ExpressionType.BOOLEAN),
    IS_ZERO("is_zero", ExpressionType.BOOLEAN),
    IS_EMPTY("is_empty", ExpressionType.BOOLEAN),
    IS_NOT_EMPTY("is_not_empty", ExpressionType.BOOLEAN),
    IS_NUMERIC("is_numeric", ExpressionType.BOOLEAN),
    IS_NOT_NUMERIC("is_not_numeric", ExpressionType.BOOLEAN),
    IS_EMAIL("is_email", ExpressionType.BOOLEAN),
    IS_PHONE("is_phone", ExpressionType.BOOLEAN),
    IS_DATE("is_date", ExpressionType.BOOLEAN),
    IS_PERCENTAGE("is_percentage", ExpressionType.BOOLEAN),
    IS_CURRENCY("is_currency", ExpressionType.BOOLEAN),
    IS_CREDIT_SCORE("is_credit_score", ExpressionType.BOOLEAN),
    IS_SSN("is_ssn", ExpressionType.BOOLEAN),
    IS_ACCOUNT_NUMBER("is_account_number", ExpressionType.BOOLEAN),
    IS_ROUTING_NUMBER("is_routing_number", ExpressionType.BOOLEAN),
    IS_BUSINESS_DAY("is_business_day", ExpressionType.BOOLEAN),
    IS_WEEKEND("is_weekend", ExpressionType.BOOLEAN);

    private final String symbol;
    private final ExpressionType resultType;

    UnaryOperator(String symbol, ExpressionType resultType) {
        this.symbol = symbol;
        this.resultType = resultType;
    }
    
    public String getSymbol() {
        return symbol;
    }

    public ExpressionType getResultType() {
        return resultType;
    }
    
    public static UnaryOperator fromSymbol(String symbol) {
        for (UnaryOperator op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown unary operator: " + symbol);
    }

    /**
     * Create UnaryOperator from TokenType
     */
    public static UnaryOperator fromToken(TokenType tokenType) {
        return switch (tokenType) {
            case MINUS -> NEGATE;
            case PLUS -> POSITIVE;
            case NOT -> NOT;
            case EXISTS -> EXISTS;
            case IS_NULL -> IS_NULL;
            case IS_NOT_NULL -> IS_NOT_NULL;
            case IS_NUMBER -> IS_NUMBER;
            case IS_STRING -> IS_STRING;
            case IS_BOOLEAN -> IS_BOOLEAN;
            case IS_LIST -> IS_LIST;
            case IS_POSITIVE -> IS_POSITIVE;
            case IS_NEGATIVE -> IS_NEGATIVE;
            case IS_ZERO -> IS_ZERO;
            case IS_EMPTY -> IS_EMPTY;
            case IS_NOT_EMPTY -> IS_NOT_EMPTY;
            case IS_NUMERIC -> IS_NUMERIC;
            case IS_NOT_NUMERIC -> IS_NOT_NUMERIC;
            case IS_EMAIL -> IS_EMAIL;
            case IS_PHONE -> IS_PHONE;
            case IS_DATE -> IS_DATE;
            case IS_PERCENTAGE -> IS_PERCENTAGE;
            case IS_CURRENCY -> IS_CURRENCY;
            case IS_CREDIT_SCORE -> IS_CREDIT_SCORE;
            case IS_SSN -> IS_SSN;
            case IS_ACCOUNT_NUMBER -> IS_ACCOUNT_NUMBER;
            case IS_ROUTING_NUMBER -> IS_ROUTING_NUMBER;
            case IS_BUSINESS_DAY -> IS_BUSINESS_DAY;
            case IS_WEEKEND -> IS_WEEKEND;
            default -> throw new IllegalArgumentException("Token type " + tokenType + " is not a unary operator");
        };
    }
}
