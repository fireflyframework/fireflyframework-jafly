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

import org.fireflyframework.rules.core.dsl.ASTNode;
import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Represents a while loop action that executes body actions while a condition is true.
 * Examples: 
 *   while counter < 10: add 1 to counter
 *   while balance > 0: calculate balance as balance - payment
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WhileAction extends Action {
    
    /**
     * The condition to evaluate before each iteration
     */
    private Condition condition;
    
    /**
     * The actions to execute in each iteration
     */
    private List<Action> bodyActions;
    
    /**
     * Maximum number of iterations to prevent infinite loops (default: 1000)
     */
    private int maxIterations = 1000;
    
    public WhileAction(SourceLocation location, Condition condition, List<Action> bodyActions) {
        super(location);
        this.condition = condition;
        this.bodyActions = bodyActions;
    }
    
    public WhileAction(SourceLocation location, Condition condition, List<Action> bodyActions, int maxIterations) {
        super(location);
        this.condition = condition;
        this.bodyActions = bodyActions;
        this.maxIterations = maxIterations;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitWhileAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        // Check if condition or body actions have variable references
        if (condition != null && condition.hasVariableReferences()) {
            return true;
        }
        if (bodyActions != null) {
            for (Action action : bodyActions) {
                if (action.hasVariableReferences()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean hasSideEffects() {
        return true; // While loops always have side effects
    }
    
    @Override
    public int getComplexity() {
        int complexity = 5; // Base complexity for while loop
        if (bodyActions != null) {
            for (Action action : bodyActions) {
                complexity += action.getComplexity();
            }
        }
        return complexity;
    }

    @Override
    public String toDebugString() {
        return String.format("WhileAction{condition=%s, bodyActions=%d, maxIterations=%d}",
                condition != null ? condition.toDebugString() : "null",
                bodyActions != null ? bodyActions.size() : 0,
                maxIterations);
    }
}

