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

import org.fireflyframework.rules.core.dsl.SourceLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a token in the DSL lexical analysis.
 * Tokens are the basic building blocks produced by the lexer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token {
    
    /**
     * Type of the token
     */
    private TokenType type;
    
    /**
     * Lexeme (the actual text) of the token
     */
    private String lexeme;
    
    /**
     * Literal value for tokens that represent values (numbers, strings, etc.)
     */
    private Object literal;
    
    /**
     * Source location of this token
     */
    private SourceLocation location;
    
    /**
     * Create a token with type and lexeme
     */
    public static Token of(TokenType type, String lexeme, SourceLocation location) {
        return new Token(type, lexeme, null, location);
    }
    
    /**
     * Create a token with type, lexeme, and literal value
     */
    public static Token of(TokenType type, String lexeme, Object literal, SourceLocation location) {
        return new Token(type, lexeme, literal, location);
    }
    
    /**
     * Check if this token is of a specific type
     */
    public boolean is(TokenType type) {
        return this.type == type;
    }
    
    /**
     * Check if this token is one of the specified types
     */
    public boolean isOneOf(TokenType... types) {
        for (TokenType type : types) {
            if (this.type == type) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the string representation for debugging
     */
    @Override
    public String toString() {
        if (literal != null) {
            return String.format("%s('%s', %s)", type, lexeme, literal);
        } else {
            return String.format("%s('%s')", type, lexeme);
        }
    }
    
    /**
     * Check if this is an operator token
     */
    public boolean isOperator() {
        return type.isOperator();
    }
    
    /**
     * Check if this is a keyword token
     */
    public boolean isKeyword() {
        return type.isKeyword();
    }
    
    /**
     * Check if this is a literal token
     */
    public boolean isLiteral() {
        return type.isLiteral();
    }

    /**
     * Get the token type
     */
    public TokenType getType() {
        return type;
    }

    /**
     * Get the lexeme
     */
    public String getLexeme() {
        return lexeme;
    }

    /**
     * Get the literal value
     */
    public Object getLiteral() {
        return literal;
    }

    /**
     * Get the source location
     */
    public SourceLocation getLocation() {
        return location;
    }
}
