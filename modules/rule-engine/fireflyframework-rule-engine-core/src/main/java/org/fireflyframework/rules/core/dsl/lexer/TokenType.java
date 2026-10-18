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

package org.fireflyframework.rules.core.dsl.lexer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Enumeration of all token types in the DSL.
 */
public enum TokenType {
    // Literals
    NUMBER("number", TokenCategory.LITERAL),
    STRING("string", TokenCategory.LITERAL),
    BOOLEAN("boolean", TokenCategory.LITERAL),
    NULL("null", TokenCategory.LITERAL),
    
    // Identifiers
    IDENTIFIER("identifier", TokenCategory.IDENTIFIER),
    
    // Comparison operators (support both symbolic and keyword forms)
    EQUALS("==", TokenCategory.OPERATOR),
    NOT_EQUALS("!=", TokenCategory.OPERATOR),
    GREATER_THAN(">", TokenCategory.OPERATOR),
    LESS_THAN("<", TokenCategory.OPERATOR),
    GREATER_EQUAL(">=", TokenCategory.OPERATOR),
    LESS_EQUAL("<=", TokenCategory.OPERATOR),
    
    // String operators
    CONTAINS("contains", TokenCategory.OPERATOR),
    NOT_CONTAINS("not_contains", TokenCategory.OPERATOR),
    STARTS_WITH("starts_with", TokenCategory.OPERATOR),
    ENDS_WITH("ends_with", TokenCategory.OPERATOR),
    MATCHES("matches", TokenCategory.OPERATOR),
    NOT_MATCHES("not_matches", TokenCategory.OPERATOR),
    
    // List operators
    IN_LIST("in_list", TokenCategory.OPERATOR),
    NOT_IN_LIST("not_in_list", TokenCategory.OPERATOR),
    
    // Range operators
    BETWEEN("between", TokenCategory.OPERATOR),
    NOT_BETWEEN("not_between", TokenCategory.OPERATOR),
    
    // Logical operators
    AND("and", TokenCategory.OPERATOR),
    OR("or", TokenCategory.OPERATOR),
    NOT("not", TokenCategory.OPERATOR),

    // Assignment operator
    ASSIGN("=", TokenCategory.OPERATOR),
    
    // Arithmetic operators
    PLUS("+", TokenCategory.OPERATOR),
    MINUS("-", TokenCategory.OPERATOR),
    MULTIPLY("*", TokenCategory.OPERATOR),
    DIVIDE("/", TokenCategory.OPERATOR),
    MODULO("%", TokenCategory.OPERATOR),
    POWER("**", TokenCategory.OPERATOR),
    
    // Existence operators
    EXISTS("exists", TokenCategory.OPERATOR),
    IS_NULL("is_null", TokenCategory.OPERATOR),
    IS_NOT_NULL("is_not_null", TokenCategory.OPERATOR),

    // Type checking operators
    IS_NUMBER("is_number", TokenCategory.OPERATOR),
    IS_STRING("is_string", TokenCategory.OPERATOR),
    IS_BOOLEAN("is_boolean", TokenCategory.OPERATOR),
    IS_LIST("is_list", TokenCategory.OPERATOR),

    // Basic validation operators
    IS_EMPTY("is_empty", TokenCategory.OPERATOR),
    IS_NOT_EMPTY("is_not_empty", TokenCategory.OPERATOR),
    IS_NUMERIC("is_numeric", TokenCategory.OPERATOR),
    IS_NOT_NUMERIC("is_not_numeric", TokenCategory.OPERATOR),
    IS_EMAIL("is_email", TokenCategory.OPERATOR),
    IS_PHONE("is_phone", TokenCategory.OPERATOR),
    IS_DATE("is_date", TokenCategory.OPERATOR),

    // Financial validation operators
    IS_POSITIVE("is_positive", TokenCategory.OPERATOR),
    IS_NEGATIVE("is_negative", TokenCategory.OPERATOR),
    IS_ZERO("is_zero", TokenCategory.OPERATOR),
    IS_PERCENTAGE("is_percentage", TokenCategory.OPERATOR),
    IS_CURRENCY("is_currency", TokenCategory.OPERATOR),
    IS_CREDIT_SCORE("is_credit_score", TokenCategory.OPERATOR),
    IS_SSN("is_ssn", TokenCategory.OPERATOR),
    IS_ACCOUNT_NUMBER("is_account_number", TokenCategory.OPERATOR),
    IS_ROUTING_NUMBER("is_routing_number", TokenCategory.OPERATOR),

    // Date/time validation operators
    IS_BUSINESS_DAY("is_business_day", TokenCategory.OPERATOR),
    IS_WEEKEND("is_weekend", TokenCategory.OPERATOR),
    AGE_AT_LEAST("age_at_least", TokenCategory.OPERATOR),
    AGE_LESS_THAN("age_less_than", TokenCategory.OPERATOR),
    
    // Keywords
    IF("if", TokenCategory.KEYWORD),
    THEN("then", TokenCategory.KEYWORD),
    ELSE("else", TokenCategory.KEYWORD),
    WHEN("when", TokenCategory.KEYWORD),
    SET("set", TokenCategory.KEYWORD),
    TO("to", TokenCategory.KEYWORD),
    CALCULATE("calculate", TokenCategory.KEYWORD),
    RUN("run", TokenCategory.KEYWORD),
    AS("as", TokenCategory.KEYWORD),
    CALL("call", TokenCategory.KEYWORD),
    WITH("with", TokenCategory.KEYWORD),

    // Arithmetic action keywords
    ADD("add", TokenCategory.KEYWORD),
    SUBTRACT("subtract", TokenCategory.KEYWORD),
    FROM("from", TokenCategory.KEYWORD),
    BY("by", TokenCategory.KEYWORD),

    // List operation keywords
    APPEND("append", TokenCategory.KEYWORD),
    PREPEND("prepend", TokenCategory.KEYWORD),
    REMOVE("remove", TokenCategory.KEYWORD),

    // Circuit breaker keyword
    CIRCUIT_BREAKER("circuit_breaker", TokenCategory.KEYWORD),

    // Loop keywords
    FOR("for", TokenCategory.KEYWORD),
    FOREACH("forEach", TokenCategory.KEYWORD),
    IN("in", TokenCategory.KEYWORD),
    DO("do", TokenCategory.KEYWORD),
    WHILE("while", TokenCategory.KEYWORD),

    // Punctuation
    LPAREN("(", TokenCategory.PUNCTUATION),
    RPAREN(")", TokenCategory.PUNCTUATION),
    LBRACKET("[", TokenCategory.PUNCTUATION),
    RBRACKET("]", TokenCategory.PUNCTUATION),
    COMMA(",", TokenCategory.PUNCTUATION),
    DOT(".", TokenCategory.PUNCTUATION),
    COLON(":", TokenCategory.PUNCTUATION),
    SEMICOLON(";", TokenCategory.PUNCTUATION),

    // Special
    EOF("EOF", TokenCategory.SPECIAL),
    NEWLINE("\\n", TokenCategory.SPECIAL),
    WHITESPACE(" ", TokenCategory.SPECIAL);
    
    private final String symbol;
    private final TokenCategory category;
    
    // Static map for keyword lookup
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    private static final Map<String, TokenType> OPERATORS = new HashMap<>();

    static {
        for (TokenType type : values()) {
            if (type.isKeyword()) {
                KEYWORDS.put(type.symbol, type);
            }
            if (type.isOperator()) {
                OPERATORS.put(type.symbol, type);
            }
        }

        // Add keyword aliases for comparison operators
        OPERATORS.put("equals", EQUALS);
        OPERATORS.put("not_equals", NOT_EQUALS);
        OPERATORS.put("greater_than", GREATER_THAN);
        OPERATORS.put("less_than", LESS_THAN);
        OPERATORS.put("at_least", GREATER_EQUAL);
        OPERATORS.put("greater_than_or_equal", GREATER_EQUAL);
        OPERATORS.put("at_most", LESS_EQUAL);
        OPERATORS.put("less_than_or_equal", LESS_EQUAL);

        // Add list operation aliases
        OPERATORS.put("in", IN_LIST);
        OPERATORS.put("not_in", NOT_IN_LIST);

        // Add uppercase aliases for logical operators (DSL spec uses uppercase)
        OPERATORS.put("AND", AND);
        OPERATORS.put("OR", OR);
        OPERATORS.put("NOT", NOT);
    }
    
    TokenType(String symbol, TokenCategory category) {
        this.symbol = symbol;
        this.category = category;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public TokenCategory getCategory() {
        return category;
    }
    
    public boolean isOperator() {
        return category == TokenCategory.OPERATOR;
    }
    
    public boolean isKeyword() {
        return category == TokenCategory.KEYWORD;
    }
    
    public boolean isLiteral() {
        return category == TokenCategory.LITERAL;
    }
    
    public boolean isPunctuation() {
        return category == TokenCategory.PUNCTUATION;
    }
    
    public boolean isSpecial() {
        return category == TokenCategory.SPECIAL;
    }
    
    /**
     * Look up a keyword token type by its symbol
     */
    public static TokenType getKeyword(String symbol) {
        return KEYWORDS.get(symbol);
    }
    
    /**
     * Look up an operator token type by its symbol
     */
    public static TokenType getOperator(String symbol) {
        return OPERATORS.get(symbol);
    }
    
    /**
     * Check if a string is a keyword
     */
    public static boolean isKeyword(String symbol) {
        return KEYWORDS.containsKey(symbol);
    }
    
    /**
     * Check if a string is an operator
     */
    public static boolean isOperator(String symbol) {
        return OPERATORS.containsKey(symbol);
    }
    
    /**
     * Get all keyword symbols
     */
    public static Set<String> getKeywordSymbols() {
        return KEYWORDS.keySet();
    }
    
    /**
     * Get all operator symbols
     */
    public static Set<String> getOperatorSymbols() {
        return OPERATORS.keySet();
    }
}

/**
 * Categories of tokens for classification
 */
enum TokenCategory {
    LITERAL,
    IDENTIFIER,
    OPERATOR,
    KEYWORD,
    PUNCTUATION,
    SPECIAL
}
