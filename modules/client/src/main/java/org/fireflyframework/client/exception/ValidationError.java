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
package org.fireflyframework.client.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a single validation error.
 * 
 * <p>This class captures detailed information about a validation failure
 * including the field name, error message, error code, and rejected value.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {
    
    /**
     * The field that failed validation.
     */
    private String field;
    
    /**
     * The validation error message.
     */
    private String message;
    
    /**
     * The validation error code (e.g., "NotNull", "Size", "Pattern").
     */
    private String code;
    
    /**
     * The value that was rejected.
     */
    private Object rejectedValue;
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ValidationError{");
        
        if (field != null) {
            sb.append("field='").append(field).append("'");
        }
        
        if (message != null) {
            sb.append(", message='").append(message).append("'");
        }
        
        if (code != null) {
            sb.append(", code='").append(code).append("'");
        }
        
        if (rejectedValue != null) {
            sb.append(", rejectedValue=").append(rejectedValue);
        }
        
        sb.append('}');
        return sb.toString();
    }
}

