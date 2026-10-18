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

package org.fireflyframework.rules.core.dsl.exception;

import org.fireflyframework.rules.core.dsl.SourceLocation;

import java.util.List;

/**
 * Exception thrown during AST evaluation.
 * Provides context about evaluation errors with precise location information.
 */
public class EvaluationException extends ASTException {
    
    public EvaluationException(String message, SourceLocation location, String errorCode, List<String> suggestions, Throwable cause) {
        super(message, location, errorCode, suggestions, cause);
    }
    
    public EvaluationException(String message, SourceLocation location, String errorCode, List<String> suggestions) {
        super(message, location, errorCode, suggestions);
    }
    
    public EvaluationException(String message, SourceLocation location, String errorCode) {
        super(message, location, errorCode);
    }
    
    public EvaluationException(String message, SourceLocation location) {
        super(message, location, "EVAL_GENERIC");
    }
    
    // Common evaluation error factory methods
    
    public static EvaluationException divisionByZero(SourceLocation location) {
        return new EvaluationException(
            "Division by zero",
            location,
            "EVAL_001",
            List.of("Ensure the divisor is not zero", "Add a condition to check for zero before division")
        );
    }
    
    public static EvaluationException undefinedVariable(String variableName, SourceLocation location) {
        return new EvaluationException(
            "Undefined variable: " + variableName,
            location,
            "EVAL_002",
            List.of(
                "Check if the variable name is spelled correctly",
                "Ensure the variable is defined before use",
                "Verify the variable is in scope"
            )
        );
    }
    
    public static EvaluationException undefinedFunction(String functionName, SourceLocation location) {
        return new EvaluationException(
            "Undefined function: " + functionName,
            location,
            "EVAL_003",
            List.of(
                "Check if the function name is spelled correctly",
                "Ensure the function is registered with the rule engine",
                "Verify the function is available in the current context"
            )
        );
    }
    
    public static EvaluationException typeError(String expectedType, String actualType, SourceLocation location) {
        return new EvaluationException(
            String.format("Type error: expected %s but got %s", expectedType, actualType),
            location,
            "EVAL_004",
            List.of(
                "Check the types of operands in the expression",
                "Use type conversion functions if needed",
                "Verify the expression logic is correct"
            )
        );
    }
    
    public static EvaluationException indexOutOfBounds(int index, int size, SourceLocation location) {
        return new EvaluationException(
            String.format("Index %d is out of bounds for list of size %d", index, size),
            location,
            "EVAL_005",
            List.of(
                "Check that the index is within the valid range [0, " + (size - 1) + "]",
                "Add bounds checking before accessing list elements",
                "Verify the list is not empty before indexing"
            )
        );
    }
    
    public static EvaluationException invalidRegex(String pattern, SourceLocation location, Throwable cause) {
        return new EvaluationException(
            "Invalid regular expression pattern: " + pattern,
            location,
            "EVAL_006",
            List.of(
                "Check the regex pattern syntax",
                "Escape special characters if needed",
                "Test the regex pattern with a regex validator"
            ),
            cause
        );
    }
    
    public static EvaluationException functionCallError(String functionName, String error, SourceLocation location, Throwable cause) {
        return new EvaluationException(
            String.format("Error calling function '%s': %s", functionName, error),
            location,
            "EVAL_007",
            List.of(
                "Check the function arguments are correct",
                "Verify the function implementation",
                "Ensure all required parameters are provided"
            ),
            cause
        );
    }
    
    public static EvaluationException propertyAccessError(String propertyPath, SourceLocation location) {
        return new EvaluationException(
            "Cannot access property: " + propertyPath,
            location,
            "EVAL_008",
            List.of(
                "Check if the property exists on the object",
                "Verify the object is not null",
                "Ensure the property path is correct"
            )
        );
    }
}
