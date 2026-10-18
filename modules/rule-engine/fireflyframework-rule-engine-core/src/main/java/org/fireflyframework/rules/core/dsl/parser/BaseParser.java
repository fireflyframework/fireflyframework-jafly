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

import org.fireflyframework.rules.core.dsl.lexer.Token;
import org.fireflyframework.rules.core.dsl.lexer.TokenType;

import java.util.List;

/**
 * Base class for parsers providing common parsing utilities.
 */
public abstract class BaseParser {
    
    protected final List<Token> tokens;
    protected int current = 0;
    
    public BaseParser(List<Token> tokens) {
        this.tokens = tokens;
    }
    
    /**
     * Check if we've reached the end of tokens
     */
    protected boolean isAtEnd() {
        return peek().getType() == TokenType.EOF;
    }
    
    /**
     * Get the current token without consuming it
     */
    protected Token peek() {
        return tokens.get(current);
    }
    
    /**
     * Get the previous token
     */
    protected Token previous() {
        return tokens.get(current - 1);
    }
    
    /**
     * Advance to the next token and return the current one
     */
    protected Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }
    
    /**
     * Check if the current token is of the specified type
     */
    protected boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }
    
    /**
     * Check if the current token matches any of the specified types
     */
    protected boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }
    
    /**
     * Consume a token of the specified type or throw an error
     */
    protected Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        
        throw new ParseException(
            message + " (found: " + peek().getType() + ")",
            peek(),
            List.of("Expected " + type + " but found " + peek().getType())
        );
    }
    
    /**
     * Synchronize after a parse error by advancing to the next statement boundary
     */
    protected void synchronize() {
        advance();
        
        while (!isAtEnd()) {
            if (previous().getType() == TokenType.NEWLINE) return;
            
            switch (peek().getType()) {
                case IF, WHEN, SET, CALCULATE, CALL -> {
                    return;
                }
            }
            
            advance();
        }
    }
    
    /**
     * Look ahead at the next token without consuming it
     */
    protected Token peekNext() {
        if (current + 1 >= tokens.size()) {
            return tokens.get(tokens.size() - 1); // Return EOF token
        }
        return tokens.get(current + 1);
    }
    
    /**
     * Look ahead at a token at the specified offset
     */
    protected Token peekAhead(int offset) {
        int index = current + offset;
        if (index >= tokens.size()) {
            return tokens.get(tokens.size() - 1); // Return EOF token
        }
        return tokens.get(index);
    }
    
    /**
     * Check if the current token is one of the specified types
     */
    protected boolean checkAny(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Skip whitespace and newline tokens
     */
    protected void skipWhitespace() {
        while (match(TokenType.WHITESPACE, TokenType.NEWLINE)) {
            // Skip whitespace
        }
    }
    
    /**
     * Get the current position in the token stream
     */
    protected int getCurrentPosition() {
        return current;
    }
    
    /**
     * Set the current position in the token stream (for backtracking)
     */
    protected void setCurrentPosition(int position) {
        this.current = position;
    }
    
    /**
     * Create a parse exception with context
     */
    protected ParseException error(String message) {
        return new ParseException(message, peek());
    }
    
    /**
     * Create a parse exception with suggestions
     */
    protected ParseException error(String message, List<String> suggestions) {
        return new ParseException(message, peek(), suggestions);
    }
    
    /**
     * Create a parse exception with error code and suggestions
     */
    protected ParseException error(String message, String errorCode, List<String> suggestions) {
        return new ParseException(message, peek(), errorCode, suggestions);
    }
}
