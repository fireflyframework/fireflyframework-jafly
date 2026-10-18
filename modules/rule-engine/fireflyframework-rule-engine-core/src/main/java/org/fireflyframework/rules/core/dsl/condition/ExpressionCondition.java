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

package org.fireflyframework.rules.core.dsl.condition;

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import org.fireflyframework.rules.core.dsl.expression.Expression;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a condition that wraps an expression that should evaluate to a boolean.
 * Examples: isActive, hasPermission(user, "read"), calculateRisk() > threshold
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExpressionCondition extends Condition {
    
    /**
     * The expression that should evaluate to a boolean value
     */
    private Expression expression;
    
    public ExpressionCondition(SourceLocation location, Expression expression) {
        super(location);
        this.expression = expression;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitExpressionCondition(this);
    }
    
    @Override
    public boolean isConstant() {
        return expression.isConstant();
    }
    
    @Override
    public boolean hasVariableReferences() {
        return expression.hasVariableReferences();
    }
    
    @Override
    public String toDebugString() {
        return expression.toDebugString();
    }
}
