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

import org.fireflyframework.rules.core.dsl.condition.*;
import org.fireflyframework.rules.core.dsl.expression.Expression;
import org.fireflyframework.rules.core.dsl.lexer.Token;
import org.fireflyframework.rules.core.dsl.lexer.TokenType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for condition expressions.
 * Handles logical operations and comparisons.
 */
@Slf4j
public class ConditionParser extends BaseParser {
    
    private final ExpressionParser expressionParser;
    
    public ConditionParser(List<Token> tokens) {
        super(tokens);
        this.expressionParser = new ExpressionParser(tokens);
        // Sync the expression parser's position with this parser
        this.expressionParser.setCurrentPosition(this.current);
    }
    
    /**
     * Parse a condition expression
     * 
     * Condition grammar:
     * condition      → logicalOr
     * logicalOr      → logicalAnd ( "or" logicalAnd )*
     * logicalAnd     → logicalNot ( "and" logicalNot )*
     * logicalNot     → "not" logicalNot | comparison
     * comparison     → expression comparisonOp expression ( "and" expression )?
     * comparisonOp   → "equals" | "not_equals" | "greater_than" | "less_than" | 
     *                  "at_least" | "at_most" | "contains" | "starts_with" | 
     *                  "ends_with" | "matches" | "in_list" | "between" | "exists"
     */
    public Condition parseCondition() {
        // Sync positions
        this.expressionParser.setCurrentPosition(this.current);
        Condition condition = logicalOr();
        // Sync back
        this.current = this.expressionParser.getCurrentPosition();
        return condition;
    }
    
    /**
     * Parse logical OR conditions
     */
    private Condition logicalOr() {
        Condition condition = logicalAnd();
        
        if (match(TokenType.OR)) {
            List<Condition> operands = new ArrayList<>();
            operands.add(condition);
            
            do {
                operands.add(logicalAnd());
            } while (match(TokenType.OR));
            
            return new LogicalCondition(condition.getLocation(), LogicalOperator.OR, operands);
        }
        
        return condition;
    }
    
    /**
     * Parse logical AND conditions
     */
    private Condition logicalAnd() {
        Condition condition = logicalNot();
        
        if (match(TokenType.AND)) {
            List<Condition> operands = new ArrayList<>();
            operands.add(condition);
            
            do {
                operands.add(logicalNot());
            } while (match(TokenType.AND));
            
            return new LogicalCondition(condition.getLocation(), LogicalOperator.AND, operands);
        }
        
        return condition;
    }
    
    /**
     * Parse logical NOT conditions
     */
    private Condition logicalNot() {
        if (match(TokenType.NOT)) {
            Token notToken = previous();
            Condition operand = logicalNot();
            
            return new LogicalCondition(notToken.getLocation(), LogicalOperator.NOT, List.of(operand));
        }
        
        return primary();
    }

    /**
     * Parse primary conditions (parentheses and comparisons)
     */
    private Condition primary() {
        // Handle parentheses for grouping
        if (match(TokenType.LPAREN)) {
            Condition condition = parseCondition();
            consume(TokenType.RPAREN, "Expected ')' after condition");
            return condition;
        }

        return comparison();
    }

    /**
     * Parse comparison conditions
     */
    private Condition comparison() {
        // Sync positions before parsing expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression left = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();
        
        // Check for comparison operators
        if (checkAny(TokenType.EQUALS, TokenType.NOT_EQUALS, TokenType.GREATER_THAN,
                    TokenType.LESS_THAN, TokenType.GREATER_EQUAL, TokenType.LESS_EQUAL,
                    TokenType.CONTAINS, TokenType.NOT_CONTAINS, TokenType.STARTS_WITH,
                    TokenType.ENDS_WITH, TokenType.MATCHES, TokenType.NOT_MATCHES,
                    TokenType.IN_LIST, TokenType.NOT_IN_LIST, TokenType.BETWEEN,
                    TokenType.NOT_BETWEEN, TokenType.EXISTS,
                    // Basic validation operators
                    TokenType.IS_EMPTY, TokenType.IS_NOT_EMPTY, TokenType.IS_NUMERIC,
                    TokenType.IS_NOT_NUMERIC, TokenType.IS_EMAIL, TokenType.IS_PHONE,
                    TokenType.IS_DATE, TokenType.IS_NULL, TokenType.IS_NOT_NULL,
                    // Financial validation operators
                    TokenType.IS_POSITIVE, TokenType.IS_NEGATIVE, TokenType.IS_ZERO,
                    TokenType.IS_PERCENTAGE, TokenType.IS_CURRENCY, TokenType.IS_CREDIT_SCORE,
                    TokenType.IS_SSN, TokenType.IS_ACCOUNT_NUMBER, TokenType.IS_ROUTING_NUMBER,
                    // Date/time validation operators
                    TokenType.IS_BUSINESS_DAY, TokenType.IS_WEEKEND, TokenType.AGE_AT_LEAST,
                    TokenType.AGE_LESS_THAN)) {
            
            Token operator = advance();
            ComparisonOperator op = mapTokenToComparisonOperator(operator);

            // Handle unary operators (no right operand needed)
            if (isUnaryOperator(op)) {
                return new ComparisonCondition(left.getLocation(), left, op, null);
            }
            
            // Parse right operand
            this.expressionParser.setCurrentPosition(this.current);
            Expression right = this.expressionParser.parseExpressionWithoutLogical();
            this.current = this.expressionParser.getCurrentPosition();
            
            // Handle BETWEEN operator (ternary)
            if (op == ComparisonOperator.BETWEEN || op == ComparisonOperator.NOT_BETWEEN) {
                if (!match(TokenType.AND)) {
                    throw error(
                        "Expected 'and' after first operand in BETWEEN expression",
                        "PARSE_001",
                        List.of("Add 'and' followed by the upper bound value")
                    );
                }
                
                this.expressionParser.setCurrentPosition(this.current);
                Expression rangeEnd = this.expressionParser.parseExpressionWithoutLogical();
                this.current = this.expressionParser.getCurrentPosition();
                
                return new ComparisonCondition(left.getLocation(), left, op, right, rangeEnd);
            }
            
            return new ComparisonCondition(left.getLocation(), left, op, right);
        }
        
        // If no comparison operator, treat as expression condition
        return new ExpressionCondition(left.getLocation(), left);
    }
    
    /**
     * Map token type to comparison operator
     */
    private ComparisonOperator mapTokenToComparisonOperator(Token token) {
        return switch (token.getType()) {
            case EQUALS -> ComparisonOperator.EQUALS;
            case NOT_EQUALS -> ComparisonOperator.NOT_EQUALS;
            case GREATER_THAN -> ComparisonOperator.GREATER_THAN;
            case LESS_THAN -> ComparisonOperator.LESS_THAN;
            case GREATER_EQUAL -> ComparisonOperator.GREATER_EQUAL;
            case LESS_EQUAL -> ComparisonOperator.LESS_EQUAL;
            case CONTAINS -> ComparisonOperator.CONTAINS;
            case NOT_CONTAINS -> ComparisonOperator.NOT_CONTAINS;
            case STARTS_WITH -> ComparisonOperator.STARTS_WITH;
            case ENDS_WITH -> ComparisonOperator.ENDS_WITH;
            case MATCHES -> ComparisonOperator.MATCHES;
            case NOT_MATCHES -> ComparisonOperator.NOT_MATCHES;
            case IN_LIST -> ComparisonOperator.IN_LIST;
            case NOT_IN_LIST -> ComparisonOperator.NOT_IN_LIST;
            case BETWEEN -> ComparisonOperator.BETWEEN;
            case NOT_BETWEEN -> ComparisonOperator.NOT_BETWEEN;
            case EXISTS -> ComparisonOperator.EXISTS;
            case IS_NULL -> ComparisonOperator.IS_NULL;
            case IS_NOT_NULL -> ComparisonOperator.IS_NOT_NULL;

            // Basic validation operators
            case IS_EMPTY -> ComparisonOperator.IS_EMPTY;
            case IS_NOT_EMPTY -> ComparisonOperator.IS_NOT_EMPTY;
            case IS_NUMERIC -> ComparisonOperator.IS_NUMERIC;
            case IS_NOT_NUMERIC -> ComparisonOperator.IS_NOT_NUMERIC;
            case IS_EMAIL -> ComparisonOperator.IS_EMAIL;
            case IS_PHONE -> ComparisonOperator.IS_PHONE;
            case IS_DATE -> ComparisonOperator.IS_DATE;

            // Financial validation operators
            case IS_POSITIVE -> ComparisonOperator.IS_POSITIVE;
            case IS_NEGATIVE -> ComparisonOperator.IS_NEGATIVE;
            case IS_ZERO -> ComparisonOperator.IS_ZERO;
            case IS_PERCENTAGE -> ComparisonOperator.IS_PERCENTAGE;
            case IS_CURRENCY -> ComparisonOperator.IS_CURRENCY;
            case IS_CREDIT_SCORE -> ComparisonOperator.IS_CREDIT_SCORE;
            case IS_SSN -> ComparisonOperator.IS_SSN;
            case IS_ACCOUNT_NUMBER -> ComparisonOperator.IS_ACCOUNT_NUMBER;
            case IS_ROUTING_NUMBER -> ComparisonOperator.IS_ROUTING_NUMBER;

            // Date/time validation operators
            case IS_BUSINESS_DAY -> ComparisonOperator.IS_BUSINESS_DAY;
            case IS_WEEKEND -> ComparisonOperator.IS_WEEKEND;
            case AGE_AT_LEAST -> ComparisonOperator.AGE_AT_LEAST;
            case AGE_LESS_THAN -> ComparisonOperator.AGE_LESS_THAN;

            default -> throw new ParseException("Unknown comparison operator: " + token.getLexeme(), token);
        };
    }

    /**
     * Check if an operator is unary (doesn't require a right operand)
     */
    private boolean isUnaryOperator(ComparisonOperator op) {
        return switch (op) {
            case EXISTS, NOT_EXISTS, IS_NULL, IS_NOT_NULL,
                 IS_EMPTY, IS_NOT_EMPTY, IS_NUMERIC, IS_NOT_NUMERIC,
                 IS_EMAIL, IS_PHONE, IS_DATE,
                 IS_POSITIVE, IS_NEGATIVE, IS_ZERO, IS_NON_ZERO, IS_PERCENTAGE,
                 IS_CURRENCY, IS_CREDIT_SCORE, IS_SSN, IS_ACCOUNT_NUMBER,
                 IS_ROUTING_NUMBER, IS_BUSINESS_DAY, IS_WEEKEND -> true;
            default -> false;
        };
    }
}
