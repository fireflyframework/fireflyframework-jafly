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
 * Represents a forEach loop action that iterates over a list and executes actions for each element.
 * 
 * Examples:
 *   Simple syntax:
 *     - forEach item in items: set total to total + item
 *     - forEach customer in customers: set customer.status to "ACTIVE"
 *     - forEach item, index in items: set processedItems[index] to item * 2
 *   
 *   Complex syntax:
 *     - forEach:
 *         variable: item
 *         in: items
 *         do:
 *           - set total to total + item
 *           - calculate itemScore as item * multiplier
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForEachAction extends Action {
    
    /**
     * Name of the iteration variable (e.g., "item" in "forEach item in items")
     */
    private String iterationVariable;
    
    /**
     * Optional name of the index variable (e.g., "index" in "forEach item, index in items")
     */
    private String indexVariable;
    
    /**
     * Expression that evaluates to the list to iterate over
     */
    private Expression listExpression;
    
    /**
     * Actions to execute for each iteration
     */
    private List<Action> bodyActions;
    
    public ForEachAction(SourceLocation location, String iterationVariable, Expression listExpression, List<Action> bodyActions) {
        super(location);
        this.iterationVariable = iterationVariable;
        this.listExpression = listExpression;
        this.bodyActions = bodyActions;
        this.indexVariable = null;
    }
    
    public ForEachAction(SourceLocation location, String iterationVariable, String indexVariable, Expression listExpression, List<Action> bodyActions) {
        super(location);
        this.iterationVariable = iterationVariable;
        this.indexVariable = indexVariable;
        this.listExpression = listExpression;
        this.bodyActions = bodyActions;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitForEachAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        boolean listHasVars = listExpression.hasVariableReferences();
        boolean bodyHasVars = bodyActions != null && bodyActions.stream().anyMatch(Action::hasVariableReferences);
        return listHasVars || bodyHasVars;
    }
    
    @Override
    public int getComplexity() {
        int complexity = 2; // Base complexity for loop structure

        // Expressions don't have getComplexity(), so we estimate based on type
        complexity += 1;

        if (bodyActions != null) {
            complexity += bodyActions.stream().mapToInt(Action::getComplexity).sum();
        }

        return complexity;
    }
    
    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("forEach ").append(iterationVariable);
        
        if (indexVariable != null) {
            sb.append(", ").append(indexVariable);
        }
        
        sb.append(" in ").append(listExpression.toDebugString());
        
        if (bodyActions != null && !bodyActions.isEmpty()) {
            String bodyStr = bodyActions.stream()
                    .map(Action::toDebugString)
                    .collect(Collectors.joining("; "));
            sb.append(": ").append(bodyStr);
        }
        
        return sb.toString();
    }
    
    /**
     * Check if this forEach has an index variable
     */
    public boolean hasIndexVariable() {
        return indexVariable != null && !indexVariable.isEmpty();
    }
}

