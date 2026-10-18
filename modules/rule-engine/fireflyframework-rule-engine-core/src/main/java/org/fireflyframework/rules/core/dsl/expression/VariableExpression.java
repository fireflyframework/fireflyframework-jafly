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

package org.fireflyframework.rules.core.dsl.expression;

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a variable reference in an expression.
 * Examples: customerAge, account.balance, items[0].name
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VariableExpression extends Expression {
    
    /**
     * The variable name or path
     */
    private String variableName;
    
    /**
     * Property access path for nested properties (e.g., ["account", "balance"])
     */
    private List<String> propertyPath;
    
    /**
     * Array/list index access (if applicable)
     */
    private Expression indexExpression;

    public VariableExpression(SourceLocation location, String variableName, List<String> propertyPath) {
        super(location);
        this.variableName = variableName;
        this.propertyPath = propertyPath != null ? propertyPath : new ArrayList<>();
        this.indexExpression = null;
    }
    
    public VariableExpression(SourceLocation location, String variableName) {
        super(location);
        this.variableName = variableName;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitVariableExpression(this);
    }
    
    @Override
    public ExpressionType getExpressionType() {
        // Variable type is unknown at parse time
        return ExpressionType.ANY;
    }
    
    @Override
    public boolean isConstant() {
        return false; // Variables are never constant
    }
    
    @Override
    public boolean hasVariableReferences() {
        return true;
    }
    
    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder(variableName);
        
        if (propertyPath != null && !propertyPath.isEmpty()) {
            for (String property : propertyPath) {
                sb.append(".").append(property);
            }
        }
        
        if (indexExpression != null) {
            sb.append("[").append(indexExpression.toDebugString()).append("]");
        }
        
        return sb.toString();
    }
    
    /**
     * Get the full variable path as a dot-separated string
     */
    public String getFullPath() {
        if (propertyPath == null || propertyPath.isEmpty()) {
            return variableName;
        }
        
        StringBuilder sb = new StringBuilder(variableName);
        for (String property : propertyPath) {
            sb.append(".").append(property);
        }
        return sb.toString();
    }
    
    /**
     * Check if this is a simple variable reference (no property access or indexing)
     */
    public boolean isSimpleVariable() {
        return (propertyPath == null || propertyPath.isEmpty()) && indexExpression == null;
    }
    
    /**
     * Check if this variable has property access
     */
    public boolean hasPropertyAccess() {
        return propertyPath != null && !propertyPath.isEmpty();
    }
    
    /**
     * Check if this variable has index access
     */
    public boolean hasIndexAccess() {
        return indexExpression != null;
    }
}
