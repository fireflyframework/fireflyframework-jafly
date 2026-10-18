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

import org.fireflyframework.kernel.exception.FireflyException;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import lombok.Getter;

import java.util.List;

/**
 * Base exception for all AST-related errors.
 * Provides enhanced error reporting with source location and suggestions.
 */
@Getter
public class ASTException extends FireflyException {
    
    private final SourceLocation location;
    private final String errorCode;
    private final List<String> suggestions;
    
    public ASTException(String message, SourceLocation location, String errorCode, List<String> suggestions, Throwable cause) {
        super(message, cause);
        this.location = location;
        this.errorCode = errorCode;
        this.suggestions = suggestions != null ? suggestions : List.of();
    }
    
    public ASTException(String message, SourceLocation location, String errorCode, List<String> suggestions) {
        this(message, location, errorCode, suggestions, null);
    }
    
    public ASTException(String message, SourceLocation location, String errorCode) {
        this(message, location, errorCode, List.of(), null);
    }
    
    public ASTException(String message, SourceLocation location) {
        this(message, location, "AST_GENERIC", List.of(), null);
    }

    public ASTException(String message) {
        this(message, null, "AST_GENERIC", List.of(), null);
    }

    public ASTException(String message, Throwable cause) {
        this(message, null, "AST_GENERIC", List.of(), cause);
    }
    
    /**
     * Get a detailed error message with location and suggestions
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        
        // Error code and message
        sb.append("[").append(errorCode).append("] ").append(getMessage());
        
        // Location information
        if (location != null) {
            sb.append(" at line ").append(location.getLine())
              .append(", column ").append(location.getColumn());
        }
        
        // Context if available
        if (location != null && location.hasContext()) {
            sb.append("\n\nContext:\n").append(location.getContextText());
        }
        
        // Suggestions
        if (!suggestions.isEmpty()) {
            sb.append("\n\nSuggestions:");
            for (String suggestion : suggestions) {
                sb.append("\n  - ").append(suggestion);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Check if this exception has location information
     */
    public boolean hasLocation() {
        return location != null;
    }
    
    /**
     * Check if this exception has suggestions
     */
    public boolean hasSuggestions() {
        return !suggestions.isEmpty();
    }
    
    /**
     * Get the error category based on error code prefix
     */
    public String getErrorCategory() {
        if (errorCode == null) return "UNKNOWN";
        
        String[] parts = errorCode.split("_");
        return parts.length > 0 ? parts[0] : "UNKNOWN";
    }
    
    @Override
    public String toString() {
        return getDetailedMessage();
    }
}
