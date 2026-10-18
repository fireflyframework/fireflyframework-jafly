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
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexical analyzer for the DSL.
 * Converts input text into a stream of tokens for parsing.
 */
@Slf4j
public class Lexer {
    
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 1;
    
    public Lexer(String source) {
        this.source = source != null ? source : "";
    }
    
    /**
     * Tokenize the entire source and return the list of tokens
     */
    public List<Token> tokenize() {
        log.debug("Starting lexical analysis of source: {}", source);
        
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme
            start = current;
            scanToken();
        }
        
        // Add EOF token
        tokens.add(Token.of(TokenType.EOF, "", getCurrentLocation()));
        
        log.debug("Lexical analysis complete. Generated {} tokens", tokens.size());
        return tokens;
    }
    
    /**
     * Scan a single token
     */
    private void scanToken() {
        char c = advance();
        
        switch (c) {
            case ' ', '\r', '\t' -> {
                // Ignore whitespace
            }
            case '\n' -> {
                line++;
                column = 1;
            }
            case '(' -> addToken(TokenType.LPAREN);
            case ')' -> addToken(TokenType.RPAREN);
            case '[' -> addToken(TokenType.LBRACKET);
            case ']' -> addToken(TokenType.RBRACKET);
            case ',' -> addToken(TokenType.COMMA);
            case '.' -> addToken(TokenType.DOT);
            case ':' -> addToken(TokenType.COLON);
            case ';' -> addToken(TokenType.SEMICOLON);
            case '+' -> addToken(TokenType.PLUS);
            case '-' -> addToken(TokenType.MINUS);
            case '%' -> addToken(TokenType.MODULO);
            case '/' -> addToken(TokenType.DIVIDE);
            case '*' -> {
                if (match('*')) {
                    addToken(TokenType.POWER);
                } else {
                    addToken(TokenType.MULTIPLY);
                }
            }
            case '=' -> {
                if (match('=')) {
                    addToken(TokenType.EQUALS);
                } else {
                    addToken(TokenType.ASSIGN);
                }
            }
            case '!' -> {
                if (match('=')) {
                    addToken(TokenType.NOT_EQUALS);
                } else {
                    addToken(TokenType.NOT);
                }
            }
            case '>' -> {
                if (match('=')) {
                    addToken(TokenType.GREATER_EQUAL);
                } else {
                    addToken(TokenType.GREATER_THAN);
                }
            }
            case '<' -> {
                if (match('=')) {
                    addToken(TokenType.LESS_EQUAL);
                } else {
                    addToken(TokenType.LESS_THAN);
                }
            }
            case '"' -> string();
            case '\'' -> singleQuotedString();
            default -> {
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    throw new LexerException(
                        "Unexpected character: " + c,
                        getCurrentLocation(),
                        "LEX_001"
                    );
                }
            }
        }
    }
    
    /**
     * Scan an identifier or keyword
     */
    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        
        // Check for multi-word operators and keywords
        String text = source.substring(start, current);
        
        // Look ahead for multi-word tokens
        if (isMultiWordToken(text)) {
            scanMultiWordToken(text);
            return;
        }
        
        // Check if it's a keyword or operator
        TokenType type = TokenType.getKeyword(text);
        if (type == null) {
            type = TokenType.getOperator(text);
        }
        if (type == null) {
            // Check for boolean literals
            if ("true".equals(text) || "false".equals(text)) {
                addToken(TokenType.BOOLEAN, Boolean.parseBoolean(text));
            } else if ("null".equals(text)) {
                addToken(TokenType.NULL, null);
            } else {
                type = TokenType.IDENTIFIER;
                addToken(type);
            }
        } else {
            addToken(type);
        }
    }
    
    /**
     * Check if the current identifier might be part of a multi-word token
     */
    private boolean isMultiWordToken(String text) {
        // Check for tokens that might have underscores or multiple words
        return text.equals("not") || text.equals("is") || text.equals("in") || 
               text.equals("starts") || text.equals("ends") || text.equals("at");
    }
    
    /**
     * Scan multi-word tokens like "not_equals", "is_null", "in_list", etc.
     */
    private void scanMultiWordToken(String firstWord) {
        // Save current position
        int savedCurrent = current;
        int savedColumn = column;
        
        // Skip whitespace and underscores
        while (peek() == ' ' || peek() == '_') {
            advance();
        }
        
        // Read the next word
        int wordStart = current;
        while (isAlphaNumeric(peek())) {
            advance();
        }
        
        if (current > wordStart) {
            String secondWord = source.substring(wordStart, current);
            String combined = firstWord + "_" + secondWord;
            
            // Check if the combined token is a valid operator
            TokenType type = TokenType.getOperator(combined);
            if (type != null) {
                // Update the token span to include both words
                start = savedCurrent - firstWord.length();
                addToken(type);
                return;
            }
        }
        
        // If not a valid multi-word token, backtrack
        current = savedCurrent;
        column = savedColumn;
        
        // Process as single word
        TokenType type = TokenType.getKeyword(firstWord);
        if (type == null) {
            type = TokenType.getOperator(firstWord);
        }
        if (type == null) {
            type = TokenType.IDENTIFIER;
        }
        addToken(type);
    }
    
    /**
     * Scan a number literal
     */
    private void number() {
        while (isDigit(peek())) {
            advance();
        }
        
        // Look for a fractional part
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();
            
            while (isDigit(peek())) {
                advance();
            }
        }
        
        String text = source.substring(start, current);
        try {
            if (text.contains(".")) {
                addToken(TokenType.NUMBER, Double.parseDouble(text));
            } else {
                long longValue = Long.parseLong(text);
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    addToken(TokenType.NUMBER, (int) longValue);
                } else {
                    addToken(TokenType.NUMBER, longValue);
                }
            }
        } catch (NumberFormatException e) {
            throw new LexerException(
                "Invalid number format: " + text,
                getCurrentLocation(),
                "LEX_002",
                e
            );
        }
    }
    
    /**
     * Scan a double-quoted string literal
     */
    private void string() {
        StringBuilder value = new StringBuilder();

        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\\') {
                // Handle escape sequences
                advance(); // consume the backslash
                if (isAtEnd()) {
                    throw new LexerException(
                        "Unterminated string escape sequence",
                        getCurrentLocation(),
                        "LEX_004"
                    );
                }

                char escaped = advance();
                switch (escaped) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'r' -> value.append('\r');
                    case '\\' -> value.append('\\');
                    case '"' -> value.append('"');
                    case '\'' -> value.append('\'');
                    default -> {
                        // For other characters, include them as-is (like \+ for regex)
                        value.append('\\').append(escaped);
                    }
                }
            } else {
                if (peek() == '\n') {
                    line++;
                    column = 1;
                }
                value.append(advance());
            }
        }

        if (isAtEnd()) {
            throw new LexerException(
                "Unterminated string",
                getCurrentLocation(),
                "LEX_003"
            );
        }

        // The closing "
        advance();

        addToken(TokenType.STRING, value.toString());
    }
    
    /**
     * Scan a single-quoted string literal
     */
    private void singleQuotedString() {
        while (peek() != '\'' && !isAtEnd()) {
            if (peek() == '\n') {
                line++;
                column = 1;
            }
            advance();
        }
        
        if (isAtEnd()) {
            throw new LexerException(
                "Unterminated string",
                getCurrentLocation(),
                "LEX_003"
            );
        }
        
        // The closing '
        advance();
        
        // Trim the surrounding quotes
        String value = source.substring(start + 1, current - 1);
        addToken(TokenType.STRING, value);
    }
    
    /**
     * Check if we've reached the end of the source
     */
    private boolean isAtEnd() {
        return current >= source.length();
    }
    
    /**
     * Advance to the next character and return the current one
     */
    private char advance() {
        column++;
        return source.charAt(current++);
    }
    
    /**
     * Check if the current character matches the expected one
     */
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        
        current++;
        column++;
        return true;
    }
    
    /**
     * Look at the current character without consuming it
     */
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }
    
    /**
     * Look at the next character without consuming it
     */
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }
    
    /**
     * Check if a character is a digit
     */
    private boolean isDigit(char c) {
        return Character.isDigit(c);
    }

    /**
     * Check if a character is alphabetic
     */
    private boolean isAlpha(char c) {
        return Character.isLetter(c) || c == '_';
    }

    /**
     * Check if a character is alphanumeric
     */
    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
    
    /**
     * Add a token without a literal value
     */
    private void addToken(TokenType type) {
        addToken(type, null);
    }
    
    /**
     * Add a token with a literal value
     */
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(Token.of(type, text, literal, getCurrentLocation()));
    }
    
    /**
     * Get the current source location
     */
    private SourceLocation getCurrentLocation() {
        return SourceLocation.range(line, column - (current - start), start, current, source);
    }
}
