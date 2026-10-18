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

package org.fireflyframework.rules.core.dsl.visitor;

import org.fireflyframework.rules.core.dsl.SourceLocation;
import lombok.Data;

/**
 * Represents a validation error found during AST validation.
 */
@Data
public class ValidationError {
    
    private final String message;
    private final SourceLocation location;
    private final String errorCode;
    
    public ValidationError(String message, SourceLocation location, String errorCode) {
        this.message = message;
        this.location = location;
        this.errorCode = errorCode;
    }
    
    public ValidationError(String message, SourceLocation location) {
        this(message, location, "VAL_GENERIC");
    }
    
    /**
     * Get a detailed error message with location information
     */
    public String getDetailedMessage() {
        if (location != null) {
            return String.format("[%s] %s at line %d, column %d", 
                errorCode, message, location.getLine(), location.getColumn());
        } else {
            return String.format("[%s] %s", errorCode, message);
        }
    }
    
    /**
     * Get error message with context
     */
    public String getMessageWithContext() {
        if (location != null && location.hasContext()) {
            StringBuilder sb = new StringBuilder();
            sb.append(getDetailedMessage()).append("\n");
            sb.append("Context:\n");
            sb.append(location.getContextText());
            return sb.toString();
        } else {
            return getDetailedMessage();
        }
    }
    
    @Override
    public String toString() {
        return getDetailedMessage();
    }
}
