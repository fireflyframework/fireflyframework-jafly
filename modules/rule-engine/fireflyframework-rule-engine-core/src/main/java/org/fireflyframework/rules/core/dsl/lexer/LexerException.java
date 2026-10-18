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

import org.fireflyframework.kernel.exception.FireflyException;
import org.fireflyframework.rules.core.dsl.SourceLocation;

/**
 * Exception thrown when lexical analysis fails.
 * Provides detailed error information including source location.
 */
public class LexerException extends FireflyException {
    
    private final SourceLocation location;
    private final String errorCode;
    
    public LexerException(String message, SourceLocation location) {
        super(message);
        this.location = location;
        this.errorCode = "LEX_ERROR";
    }
    
    public LexerException(String message, SourceLocation location, String errorCode) {
        super(message);
        this.location = location;
        this.errorCode = errorCode;
    }
    
    public LexerException(String message, SourceLocation location, Throwable cause) {
        super(message, cause);
        this.location = location;
        this.errorCode = "LEX_ERROR";
    }
    
    public LexerException(String message, SourceLocation location, String errorCode, Throwable cause) {
        super(message, cause);
        this.location = location;
        this.errorCode = errorCode;
    }
    
    public SourceLocation getLocation() {
        return location;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Get a detailed error message with location information
     */
    public String getDetailedMessage() {
        if (location != null) {
            return String.format("[%s] %s at %s", errorCode, getMessage(), location.toString());
        } else {
            return String.format("[%s] %s", errorCode, getMessage());
        }
    }
    
    /**
     * Get contextual error message with source code excerpt
     */
    public String getContextualMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(getDetailedMessage()).append("\n");
        
        if (location != null) {
            String context = location.getContextualText(2);
            if (!context.isEmpty()) {
                sb.append("\nSource context:\n").append(context);
            }
        }
        
        return sb.toString();
    }
}
