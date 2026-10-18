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
import org.fireflyframework.rules.core.dsl.exception.ASTException;
import org.fireflyframework.rules.core.dsl.lexer.Token;

import java.util.List;

/**
 * Exception thrown during parsing with detailed error information.
 * Extends ASTException to provide consistent error handling across the AST system.
 */
public class ParseException extends ASTException {

    private final Token token;

    public ParseException(String message, Token token, String errorCode, List<String> suggestions) {
        super(message, token != null ? token.getLocation() : null, errorCode, suggestions);
        this.token = token;
    }

    public ParseException(String message, Token token, List<String> suggestions) {
        this(message, token, "PARSE_GENERIC", suggestions);
    }

    public ParseException(String message, Token token) {
        this(message, token, "PARSE_GENERIC", List.of());
    }

    public ParseException(String message, SourceLocation location, String errorCode) {
        super(message, location, errorCode);
        this.token = null;
    }

    public ParseException(String message, SourceLocation location, String errorCode, List<String> suggestions) {
        super(message, location, errorCode, suggestions);
        this.token = null;
    }

    public Token getToken() {
        return token;
    }

    // Common parsing error factory methods

    public static ParseException unexpectedToken(Token expected, Token actual) {
        return new ParseException(
            String.format("Expected %s but found %s", expected.getType(), actual.getType()),
            actual,
            "PARSE_001",
            List.of(
                "Check the syntax around this location",
                "Ensure all required tokens are present",
                "Verify parentheses and brackets are balanced"
            )
        );
    }

    public static ParseException unexpectedEndOfInput(SourceLocation location) {
        return new ParseException(
            "Unexpected end of input",
            location,
            "PARSE_002"
        );
    }

    public static ParseException invalidExpression(String details, SourceLocation location) {
        return new ParseException(
            "Invalid expression: " + details,
            location,
            "PARSE_003"
        );
    }

    public static ParseException missingOperand(String operator, SourceLocation location) {
        return new ParseException(
            "Missing operand for operator: " + operator,
            location,
            "PARSE_004",
            List.of(
                "Add the missing operand",
                "Check the operator precedence",
                "Verify the expression structure"
            )
        );
    }

    public static ParseException invalidOperator(String operator, SourceLocation location) {
        return new ParseException(
            "Invalid operator: " + operator,
            location,
            "PARSE_005",
            List.of(
                "Check the operator spelling",
                "Verify the operator is supported",
                "Consult the DSL documentation"
            )
        );
    }
}
