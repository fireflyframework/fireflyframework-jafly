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
import java.util.stream.Collectors;

/**
 * Represents a function call expression.
 * Examples: max(a, b), substring(text, 0, 5), calculateRisk(customer)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FunctionCallExpression extends Expression {
    
    /**
     * Name of the function to call
     */
    private String functionName;
    
    /**
     * Arguments passed to the function
     */
    private List<Expression> arguments;

    public FunctionCallExpression(SourceLocation location, String functionName, List<Expression> arguments) {
        super(location);
        this.functionName = functionName;
        this.arguments = arguments != null ? arguments : new ArrayList<>();
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitFunctionCallExpression(this);
    }
    
    @Override
    public ExpressionType getExpressionType() {
        // Function return type depends on the specific function
        // This would be determined by a function registry in a real implementation
        return ExpressionType.ANY;
    }
    
    @Override
    public boolean isConstant() {
        // Function calls are constant only if all arguments are constant
        // and the function is pure (no side effects)
        return arguments != null && arguments.stream().allMatch(Expression::isConstant);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return arguments != null && arguments.stream().anyMatch(Expression::hasVariableReferences);
    }
    
    @Override
    public String toDebugString() {
        if (arguments == null || arguments.isEmpty()) {
            return functionName + "()";
        }
        
        String args = arguments.stream()
                .map(Expression::toDebugString)
                .collect(Collectors.joining(", "));
        
        return functionName + "(" + args + ")";
    }
    
    /**
     * Get the number of arguments
     */
    public int getArgumentCount() {
        return arguments != null ? arguments.size() : 0;
    }
    
    /**
     * Get a specific argument by index
     */
    public Expression getArgument(int index) {
        if (arguments == null || index < 0 || index >= arguments.size()) {
            throw new IndexOutOfBoundsException("Argument index out of bounds: " + index);
        }
        return arguments.get(index);
    }
    
    /**
     * Check if this function call has any arguments
     */
    public boolean hasArguments() {
        return arguments != null && !arguments.isEmpty();
    }
}
