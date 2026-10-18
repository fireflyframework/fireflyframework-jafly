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
import org.fireflyframework.rules.core.dsl.visitor.ValidationError;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception thrown when AST validation fails.
 * Contains multiple validation errors with detailed diagnostics.
 */
public class ValidationException extends ASTException {
    
    private final List<ValidationError> validationErrors;
    
    public ValidationException(List<ValidationError> validationErrors) {
        super(
            formatValidationMessage(validationErrors),
            getFirstLocation(validationErrors),
            "VAL_MULTIPLE",
            getValidationSuggestions(validationErrors)
        );
        this.validationErrors = validationErrors;
    }
    
    public ValidationException(ValidationError error) {
        this(List.of(error));
    }
    
    public ValidationException(String message, SourceLocation location, String errorCode, List<String> suggestions) {
        super(message, location, errorCode, suggestions);
        this.validationErrors = List.of();
    }
    
    public ValidationException(String message, SourceLocation location, String errorCode) {
        super(message, location, errorCode);
        this.validationErrors = List.of();
    }
    
    public ValidationException(String message, SourceLocation location) {
        super(message, location, "VAL_GENERIC");
        this.validationErrors = List.of();
    }
    
    /**
     * Get all validation errors
     */
    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }
    
    /**
     * Check if this exception contains multiple validation errors
     */
    public boolean hasMultipleErrors() {
        return validationErrors.size() > 1;
    }
    
    /**
     * Get the number of validation errors
     */
    public int getErrorCount() {
        return validationErrors.size();
    }
    
    @Override
    public String getDetailedMessage() {
        if (validationErrors.isEmpty()) {
            return super.getDetailedMessage();
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Validation failed with ").append(validationErrors.size()).append(" error(s):\n\n");
        
        for (int i = 0; i < validationErrors.size(); i++) {
            ValidationError error = validationErrors.get(i);
            sb.append(i + 1).append(". ").append(error.getMessageWithContext());
            if (i < validationErrors.size() - 1) {
                sb.append("\n\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Get validation errors grouped by error code
     */
    public List<List<ValidationError>> getErrorsByCode() {
        return validationErrors.stream()
                .collect(Collectors.groupingBy(ValidationError::getErrorCode))
                .values()
                .stream()
                .toList();
    }
    
    /**
     * Get validation errors for a specific location
     */
    public List<ValidationError> getErrorsAtLocation(SourceLocation location) {
        return validationErrors.stream()
                .filter(error -> error.getLocation() != null && error.getLocation().equals(location))
                .toList();
    }
    
    // Helper methods for constructor
    
    private static String formatValidationMessage(List<ValidationError> errors) {
        if (errors.isEmpty()) {
            return "Validation failed";
        }
        
        if (errors.size() == 1) {
            return errors.get(0).getMessage();
        }
        
        return String.format("Validation failed with %d errors", errors.size());
    }
    
    private static SourceLocation getFirstLocation(List<ValidationError> errors) {
        return errors.stream()
                .map(ValidationError::getLocation)
                .filter(location -> location != null)
                .findFirst()
                .orElse(null);
    }
    
    private static List<String> getValidationSuggestions(List<ValidationError> errors) {
        return List.of(
            "Fix all validation errors before proceeding",
            "Check the DSL syntax documentation",
            "Verify variable and function names are correct"
        );
    }
    
    // Common validation error factory methods
    
    public static ValidationException typeIncompatibility(String operation, String leftType, String rightType, SourceLocation location) {
        return new ValidationException(
            String.format("Type incompatibility in %s operation: %s and %s", operation, leftType, rightType),
            location,
            "VAL_TYPE_001",
            List.of(
                "Ensure operands are of compatible types",
                "Use type conversion if necessary",
                "Check the operation documentation for supported types"
            )
        );
    }
    
    public static ValidationException undefinedReference(String type, String name, SourceLocation location) {
        return new ValidationException(
            String.format("Undefined %s: %s", type, name),
            location,
            "VAL_REF_001",
            List.of(
                "Check if the " + type + " name is spelled correctly",
                "Ensure the " + type + " is defined before use",
                "Verify the " + type + " is available in the current scope"
            )
        );
    }
    
    public static ValidationException invalidOperandCount(String operation, int expected, int actual, SourceLocation location) {
        return new ValidationException(
            String.format("Invalid operand count for %s: expected %d, got %d", operation, expected, actual),
            location,
            "VAL_OPERAND_001",
            List.of(
                "Check the operation documentation for required operand count",
                "Add or remove operands as needed",
                "Verify the expression syntax is correct"
            )
        );
    }
    
    public static ValidationException invalidSyntax(String construct, String details, SourceLocation location) {
        return new ValidationException(
            String.format("Invalid %s syntax: %s", construct, details),
            location,
            "VAL_SYNTAX_001",
            List.of(
                "Check the DSL syntax documentation",
                "Verify the " + construct + " structure is correct",
                "Look for missing or extra tokens"
            )
        );
    }
}
