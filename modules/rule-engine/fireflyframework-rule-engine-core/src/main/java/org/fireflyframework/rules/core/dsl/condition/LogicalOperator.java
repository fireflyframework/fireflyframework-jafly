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

package org.fireflyframework.rules.core.dsl.condition;

/**
 * Enumeration of logical operators.
 */
public enum LogicalOperator {
    AND("and", "&&", 2),
    OR("or", "||", 1),
    NOT("not", "!", 8);
    
    private final String keyword;
    private final String symbol;
    private final int precedence;
    
    LogicalOperator(String keyword, String symbol, int precedence) {
        this.keyword = keyword;
        this.symbol = symbol;
        this.precedence = precedence;
    }
    
    public String getKeyword() {
        return keyword;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public int getPrecedence() {
        return precedence;
    }

    public int getMinOperands() {
        return switch (this) {
            case NOT -> 1;
            case AND, OR -> 2;
        };
    }

    public int getMaxOperands() {
        return switch (this) {
            case NOT -> 1;
            case AND, OR -> Integer.MAX_VALUE; // Can take multiple operands
        };
    }
    
    public static LogicalOperator fromKeyword(String keyword) {
        for (LogicalOperator op : values()) {
            if (op.keyword.equals(keyword)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown logical operator: " + keyword);
    }
    
    public static LogicalOperator fromSymbol(String symbol) {
        for (LogicalOperator op : values()) {
            if (symbol.equals(op.symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown logical operator symbol: " + symbol);
    }
}
