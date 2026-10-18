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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a function call action.
 * Examples: call sendNotification with [customer, "approved"], 
 *           call logEvent with ["rule_executed", ruleName]
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FunctionCallAction extends Action {
    
    /**
     * Name of the function to call
     */
    private String functionName;
    
    /**
     * Arguments to pass to the function
     */
    private List<Expression> arguments;
    
    /**
     * Optional variable to store the function result
     */
    private String resultVariable;
    
    public FunctionCallAction(SourceLocation location, String functionName, List<Expression> arguments) {
        super(location);
        this.functionName = functionName;
        this.arguments = arguments;
    }
    
    public FunctionCallAction(SourceLocation location, String functionName, List<Expression> arguments, String resultVariable) {
        super(location);
        this.functionName = functionName;
        this.arguments = arguments;
        this.resultVariable = resultVariable;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitFunctionCallAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return arguments != null && arguments.stream().anyMatch(Expression::hasVariableReferences);
    }
    
    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("call ").append(functionName);
        
        if (arguments != null && !arguments.isEmpty()) {
            String args = arguments.stream()
                    .map(Expression::toDebugString)
                    .collect(Collectors.joining(", "));
            sb.append(" with [").append(args).append("]");
        }
        
        if (resultVariable != null) {
            sb.append(" -> ").append(resultVariable);
        }
        
        return sb.toString();
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
    
    /**
     * Check if this function call stores its result in a variable
     */
    public boolean hasResultVariable() {
        return resultVariable != null && !resultVariable.trim().isEmpty();
    }
}
