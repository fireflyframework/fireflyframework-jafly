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

package org.fireflyframework.rules.core.dsl.action;

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a circuit breaker action that stops rule execution with a message.
 * Examples: circuit_breaker "Credit score too low", circuit_breaker "Insufficient funds"
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CircuitBreakerAction extends Action {
    
    /**
     * Message to include with the circuit breaker
     */
    private String message;
    
    /**
     * Optional error code
     */
    private String errorCode;
    
    public CircuitBreakerAction(SourceLocation location, String message) {
        super(location);
        this.message = message;
    }
    
    public CircuitBreakerAction(SourceLocation location, String message, String errorCode) {
        super(location);
        this.message = message;
        this.errorCode = errorCode;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitCircuitBreakerAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return false; // Circuit breaker actions don't reference variables
    }
    
    @Override
    public boolean hasSideEffects() {
        return true; // Circuit breaker actions have significant side effects (stop execution)
    }
    
    @Override
    public int getComplexity() {
        return 3; // Circuit breaker actions are complex as they affect control flow
    }

    @Override
    public String toDebugString() {
        return String.format("CircuitBreakerAction{message='%s', errorCode='%s'}",
                message, errorCode);
    }
}
