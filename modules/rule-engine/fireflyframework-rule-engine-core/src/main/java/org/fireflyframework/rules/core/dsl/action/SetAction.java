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
import org.fireflyframework.rules.core.dsl.expression.Expression;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a set action that assigns a value to a variable.
 * Examples: set status to "approved", set result to calculateScore()
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SetAction extends Action {
    
    /**
     * Name of the variable to set
     */
    private String variableName;
    
    /**
     * Value to assign to the variable
     */
    private Expression value;
    
    public SetAction(SourceLocation location, String variableName, Expression value) {
        super(location);
        this.variableName = variableName;
        this.value = value;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitSetAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return value.hasVariableReferences();
    }
    
    @Override
    public String toDebugString() {
        return String.format("set %s to %s", variableName, value.toDebugString());
    }
}
