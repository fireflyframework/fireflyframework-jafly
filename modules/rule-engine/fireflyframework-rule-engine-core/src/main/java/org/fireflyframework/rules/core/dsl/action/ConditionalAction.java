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
import org.fireflyframework.rules.core.dsl.condition.Condition;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a conditional action (if-then-else within an action).
 * Examples: if score > 700 then set status to "approved" else set status to "review"
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConditionalAction extends Action {
    
    /**
     * Condition to evaluate
     */
    private Condition condition;
    
    /**
     * Actions to execute if condition is true
     */
    private List<Action> thenActions;
    
    /**
     * Actions to execute if condition is false (optional)
     */
    private List<Action> elseActions;
    
    public ConditionalAction(SourceLocation location, Condition condition, List<Action> thenActions) {
        super(location);
        this.condition = condition;
        this.thenActions = thenActions;
    }
    
    public ConditionalAction(SourceLocation location, Condition condition, List<Action> thenActions, List<Action> elseActions) {
        super(location);
        this.condition = condition;
        this.thenActions = thenActions;
        this.elseActions = elseActions;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitConditionalAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        boolean conditionHasVars = condition.hasVariableReferences();
        boolean thenHasVars = thenActions != null && thenActions.stream().anyMatch(Action::hasVariableReferences);
        boolean elseHasVars = elseActions != null && elseActions.stream().anyMatch(Action::hasVariableReferences);
        
        return conditionHasVars || thenHasVars || elseHasVars;
    }
    
    @Override
    public int getComplexity() {
        int complexity = 1 + condition.getComplexity();
        
        if (thenActions != null) {
            complexity += thenActions.stream().mapToInt(Action::getComplexity).sum();
        }
        
        if (elseActions != null) {
            complexity += elseActions.stream().mapToInt(Action::getComplexity).sum();
        }
        
        return complexity;
    }
    
    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("if ").append(condition.toDebugString());
        
        if (thenActions != null && !thenActions.isEmpty()) {
            String thenStr = thenActions.stream()
                    .map(Action::toDebugString)
                    .collect(Collectors.joining("; "));
            sb.append(" then ").append(thenStr);
        }
        
        if (elseActions != null && !elseActions.isEmpty()) {
            String elseStr = elseActions.stream()
                    .map(Action::toDebugString)
                    .collect(Collectors.joining("; "));
            sb.append(" else ").append(elseStr);
        }
        
        return sb.toString();
    }
    
    /**
     * Check if this conditional action has else actions
     */
    public boolean hasElseActions() {
        return elseActions != null && !elseActions.isEmpty();
    }
    
    /**
     * Get the number of then actions
     */
    public int getThenActionCount() {
        return thenActions != null ? thenActions.size() : 0;
    }
    
    /**
     * Get the number of else actions
     */
    public int getElseActionCount() {
        return elseActions != null ? elseActions.size() : 0;
    }
}
