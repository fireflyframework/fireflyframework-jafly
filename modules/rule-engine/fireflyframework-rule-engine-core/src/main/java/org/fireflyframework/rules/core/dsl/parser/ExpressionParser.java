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
import org.fireflyframework.rules.core.dsl.expression.*;
import org.fireflyframework.rules.core.dsl.lexer.Token;
import org.fireflyframework.rules.core.dsl.lexer.TokenType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for expressions.
 * Handles operator precedence and associativity correctly.
 */
@Slf4j
public class ExpressionParser extends BaseParser {
    
    public ExpressionParser(List<Token> tokens) {
        super(tokens);
    }
    
    /**
     * Parse an expression with proper precedence handling
     * 
     * Expression grammar:
     * expression     → logicalOr
     * logicalOr      → logicalAnd ( "or" logicalAnd )*
     * logicalAnd     → equality ( "and" equality )*
     * equality       → comparison ( ( "equals" | "not_equals" ) comparison )*
     * comparison     → term ( ( "greater_than" | "less_than" | "at_least" | "at_most" | 
     *                          "contains" | "starts_with" | "ends_with" | "matches" |
     *                          "in_list" | "between" ) term )*
     * term           → factor ( ( "+" | "-" ) factor )*
     * factor         → unary ( ( "*" | "/" | "%" ) unary )*
     * unary          → ( "not" | "-" | "+" | "exists" | "is_null" | "is_not_null" ) unary | power
     * power          → primary ( "**" unary )*
     * primary        → NUMBER | STRING | BOOLEAN | NULL | IDENTIFIER | functionCall | "(" expression ")"
     */
    public Expression parseExpression() {
        // Include logical operators (AND/OR) for complex boolean expressions
        return logicalOr();
    }

    /**
     * Parse expression without logical operators (for use in BETWEEN expressions)
     */
    public Expression parseExpressionWithoutLogical() {
        return equality();
    }
    
    /**
     * Parse logical OR expressions (lowest precedence)
     */
    private Expression logicalOr() {
        Expression expr = logicalAnd();
        
        while (match(TokenType.OR)) {
            BinaryOperator operator = BinaryOperator.OR;
            Expression right = logicalAnd();
            expr = new BinaryExpression(expr.getLocation(), expr, operator, right);
        }
        
        return expr;
    }
    
    /**
     * Parse logical AND expressions
     */
    private Expression logicalAnd() {
        Expression expr = equality();
        
        while (match(TokenType.AND)) {
            BinaryOperator operator = BinaryOperator.AND;
            Expression right = equality();
            expr = new BinaryExpression(expr.getLocation(), expr, operator, right);
        }
        
        return expr;
    }
    
    /**
     * Parse equality expressions
     */
    private Expression equality() {
        Expression expr = comparison();
        
        while (match(TokenType.EQUALS, TokenType.NOT_EQUALS)) {
            Token operator = previous();
            BinaryOperator op = switch (operator.getType()) {
                case EQUALS -> BinaryOperator.EQUALS;
                case NOT_EQUALS -> BinaryOperator.NOT_EQUALS;
                default -> throw new ParseException("Unexpected operator: " + operator.getLexeme(), operator);
            };
            Expression right = comparison();
            expr = new BinaryExpression(expr.getLocation(), expr, op, right);
        }
        
        return expr;
    }
    
    /**
     * Parse comparison expressions
     */
    private Expression comparison() {
        Expression expr = term();

        while (match(TokenType.GREATER_THAN, TokenType.LESS_THAN, TokenType.GREATER_EQUAL,
                     TokenType.LESS_EQUAL, TokenType.CONTAINS, TokenType.NOT_CONTAINS,
                     TokenType.STARTS_WITH, TokenType.ENDS_WITH, TokenType.MATCHES,
                     TokenType.NOT_MATCHES, TokenType.IN_LIST, TokenType.NOT_IN_LIST,
                     // Add validation operators to support them in expressions
                     TokenType.IS_POSITIVE, TokenType.IS_NEGATIVE, TokenType.IS_ZERO,
                     TokenType.IS_EMPTY, TokenType.IS_NOT_EMPTY, TokenType.IS_NUMERIC,
                     TokenType.IS_NOT_NUMERIC, TokenType.IS_EMAIL, TokenType.IS_PHONE,
                     TokenType.IS_DATE, TokenType.IS_PERCENTAGE, TokenType.IS_CURRENCY,
                     TokenType.IS_CREDIT_SCORE, TokenType.IS_SSN, TokenType.IS_ACCOUNT_NUMBER,
                     TokenType.IS_ROUTING_NUMBER, TokenType.IS_BUSINESS_DAY, TokenType.IS_WEEKEND,
                     TokenType.AGE_AT_LEAST, TokenType.AGE_LESS_THAN, TokenType.IS_NUMBER,
                     TokenType.IS_STRING, TokenType.IS_BOOLEAN, TokenType.IS_LIST,
                     TokenType.IS_NULL, TokenType.IS_NOT_NULL)) {
                     // BETWEEN and NOT_BETWEEN are handled in ConditionParser, not here
            Token operator = previous();

            // Check if it's a validation operator first (unary)
            if (isValidationOperator(operator.getType())) {
                // For validation operators, create a unary expression
                return new UnaryExpression(expr.getLocation(), UnaryOperator.fromToken(operator.getType()), expr);
            } else {
                // For binary operators, parse the right operand
                BinaryOperator op = mapTokenToBinaryOperator(operator);
                Expression right = term();
                expr = new BinaryExpression(expr.getLocation(), expr, op, right);
            }
        }

        return expr;
    }

    /**
     * Check if a token type represents a validation operator (unary)
     */
    private boolean isValidationOperator(TokenType tokenType) {
        return switch (tokenType) {
            case IS_POSITIVE, IS_NEGATIVE, IS_ZERO, IS_EMPTY, IS_NOT_EMPTY,
                 IS_NUMERIC, IS_NOT_NUMERIC, IS_EMAIL, IS_PHONE, IS_DATE,
                 IS_PERCENTAGE, IS_CURRENCY, IS_CREDIT_SCORE, IS_SSN,
                 IS_ACCOUNT_NUMBER, IS_ROUTING_NUMBER, IS_BUSINESS_DAY,
                 IS_WEEKEND, IS_NUMBER, IS_STRING, IS_BOOLEAN, IS_LIST,
                 IS_NULL, IS_NOT_NULL -> true;
            case AGE_AT_LEAST, AGE_LESS_THAN -> false; // These need a right operand
            default -> false;
        };
    }

    /**
     * Parse term expressions (addition and subtraction)
     */
    private Expression term() {
        Expression expr = factor();
        
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            BinaryOperator op = switch (operator.getType()) {
                case PLUS -> BinaryOperator.ADD;
                case MINUS -> BinaryOperator.SUBTRACT;
                default -> throw new ParseException("Unexpected operator: " + operator.getLexeme(), operator);
            };
            Expression right = factor();
            expr = new BinaryExpression(expr.getLocation(), expr, op, right);
        }
        
        return expr;
    }
    
    /**
     * Parse factor expressions (multiplication, division, modulo)
     */
    private Expression factor() {
        Expression expr = unary();
        
        while (match(TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.MODULO)) {
            Token operator = previous();
            BinaryOperator op = switch (operator.getType()) {
                case MULTIPLY -> BinaryOperator.MULTIPLY;
                case DIVIDE -> BinaryOperator.DIVIDE;
                case MODULO -> BinaryOperator.MODULO;
                default -> throw new ParseException("Unexpected operator: " + operator.getLexeme(), operator);
            };
            Expression right = unary();
            expr = new BinaryExpression(expr.getLocation(), expr, op, right);
        }
        
        return expr;
    }
    
    /**
     * Parse unary expressions
     */
    private Expression unary() {
        if (match(TokenType.NOT, TokenType.MINUS, TokenType.PLUS, TokenType.EXISTS,
                  TokenType.IS_NULL, TokenType.IS_NOT_NULL, TokenType.IS_NUMBER,
                  TokenType.IS_STRING, TokenType.IS_BOOLEAN, TokenType.IS_LIST)) {
            Token operator = previous();
            UnaryOperator op = mapTokenToUnaryOperator(operator);
            Expression right = unary();
            return new UnaryExpression(operator.getLocation(), op, right);
        }
        
        return power();
    }
    
    /**
     * Parse power expressions (right-associative)
     */
    private Expression power() {
        Expression expr = primary();
        
        if (match(TokenType.POWER)) {
            BinaryOperator operator = BinaryOperator.POWER;
            Expression right = unary(); // Right-associative
            expr = new BinaryExpression(expr.getLocation(), expr, operator, right);
        }
        
        return expr;
    }
    
    /**
     * Parse primary expressions
     */
    private Expression primary() {
        if (match(TokenType.BOOLEAN)) {
            Token token = previous();
            return new LiteralExpression(token.getLocation(), token.getLiteral());
        }
        
        if (match(TokenType.NULL)) {
            return new LiteralExpression(previous().getLocation(), (Object) null);
        }
        
        if (match(TokenType.NUMBER)) {
            Token token = previous();
            return new LiteralExpression(token.getLocation(), token.getLiteral());
        }
        
        if (match(TokenType.STRING)) {
            Token token = previous();
            return new LiteralExpression(token.getLocation(), token.getLiteral());
        }
        
        if (match(TokenType.IDENTIFIER)) {
            Token identifier = previous();
            
            // Check for function call
            if (check(TokenType.LPAREN)) {
                return functionCall(identifier);
            }
            
            // Check for property access
            List<String> propertyPath = new ArrayList<>();
            while (match(TokenType.DOT)) {
                if (!check(TokenType.IDENTIFIER)) {
                    throw new ParseException(
                        "Expected property name after '.'", 
                        peek(),
                        List.of("Add a valid property name after the dot")
                    );
                }
                propertyPath.add(advance().getLexeme());
            }
            
            // Check for array/list indexing
            Expression indexExpression = null;
            if (match(TokenType.LBRACKET)) {
                indexExpression = parseExpression();
                consume(TokenType.RBRACKET, "Expected ']' after array index");
            }

            VariableExpression variable = new VariableExpression(
                    identifier.getLocation(),
                    identifier.getLexeme(),
                    propertyPath.isEmpty() ? null : propertyPath);
            if (indexExpression != null) {
                variable.setIndexExpression(indexExpression);
            }
            return variable;
        }
        
        if (match(TokenType.LPAREN)) {
            Expression expr = parseExpression();
            consume(TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }

        // Array literal: [item1, item2, item3]
        if (match(TokenType.LBRACKET)) {
            return parseArrayLiteral();
        }

        throw new ParseException(
            "Expected expression",
            peek(),
            List.of("Add a number, string, boolean, variable, or function call")
        );
    }

    /**
     * Parse array literal: [item1, item2, item3]
     */
    private Expression parseArrayLiteral() {
        SourceLocation location = previous().getLocation();
        List<Object> elements = new ArrayList<>();

        // Handle empty array
        if (check(TokenType.RBRACKET)) {
            advance(); // consume ]
            return new LiteralExpression(location, elements);
        }

        // Parse array elements
        do {
            Expression element = parseExpression();
            // Evaluate constant expressions to get the literal value
            if (element instanceof LiteralExpression) {
                elements.add(((LiteralExpression) element).getValue());
            } else {
                // For non-literal expressions, store the expression itself
                // This will be evaluated at runtime
                elements.add(element);
            }
        } while (match(TokenType.COMMA));

        consume(TokenType.RBRACKET, "Expected ']' after array elements");
        return new LiteralExpression(location, elements);
    }
    
    /**
     * Parse function call expression
     */
    private Expression functionCall(Token identifier) {
        consume(TokenType.LPAREN, "Expected '(' after function name");
        
        List<Expression> arguments = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                arguments.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        
        consume(TokenType.RPAREN, "Expected ')' after function arguments");
        
        return new FunctionCallExpression(identifier.getLocation(), identifier.getLexeme(), arguments);
    }
    
    /**
     * Map token type to binary operator
     */
    private BinaryOperator mapTokenToBinaryOperator(Token token) {
        return switch (token.getType()) {
            case EQUALS -> BinaryOperator.EQUALS;
            case NOT_EQUALS -> BinaryOperator.NOT_EQUALS;
            case GREATER_THAN -> BinaryOperator.GREATER_THAN;
            case LESS_THAN -> BinaryOperator.LESS_THAN;
            case GREATER_EQUAL -> BinaryOperator.GREATER_EQUAL;
            case LESS_EQUAL -> BinaryOperator.LESS_EQUAL;
            case CONTAINS -> BinaryOperator.CONTAINS;
            case NOT_CONTAINS -> BinaryOperator.NOT_CONTAINS;
            case STARTS_WITH -> BinaryOperator.STARTS_WITH;
            case ENDS_WITH -> BinaryOperator.ENDS_WITH;
            case MATCHES -> BinaryOperator.MATCHES;
            case NOT_MATCHES -> BinaryOperator.NOT_MATCHES;
            case IN_LIST -> BinaryOperator.IN_LIST;
            case NOT_IN_LIST -> BinaryOperator.NOT_IN_LIST;
            // BETWEEN and NOT_BETWEEN are handled in ConditionParser, not here
            case AGE_AT_LEAST -> BinaryOperator.AGE_AT_LEAST;
            case AGE_LESS_THAN -> BinaryOperator.AGE_LESS_THAN;
            case AND -> BinaryOperator.AND;
            case OR -> BinaryOperator.OR;
            case PLUS -> BinaryOperator.ADD;
            case MINUS -> BinaryOperator.SUBTRACT;
            case MULTIPLY -> BinaryOperator.MULTIPLY;
            case DIVIDE -> BinaryOperator.DIVIDE;
            case MODULO -> BinaryOperator.MODULO;
            case POWER -> BinaryOperator.POWER;
            default -> throw new ParseException("Unknown binary operator: " + token.getLexeme(), token);
        };
    }
    
    /**
     * Map token type to unary operator
     */
    private UnaryOperator mapTokenToUnaryOperator(Token token) {
        return switch (token.getType()) {
            case NOT -> UnaryOperator.NOT;
            case MINUS -> UnaryOperator.NEGATE;
            case PLUS -> UnaryOperator.POSITIVE;
            case EXISTS -> UnaryOperator.EXISTS;
            case IS_NULL -> UnaryOperator.IS_NULL;
            case IS_NOT_NULL -> UnaryOperator.IS_NOT_NULL;
            case IS_NUMBER -> UnaryOperator.IS_NUMBER;
            case IS_STRING -> UnaryOperator.IS_STRING;
            case IS_BOOLEAN -> UnaryOperator.IS_BOOLEAN;
            case IS_LIST -> UnaryOperator.IS_LIST;
            default -> throw new ParseException("Unknown unary operator: " + token.getLexeme(), token);
        };
    }
}
