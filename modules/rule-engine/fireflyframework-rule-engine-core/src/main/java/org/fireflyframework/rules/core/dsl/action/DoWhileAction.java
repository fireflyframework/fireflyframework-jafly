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
 * Represents a do-while loop action that executes body actions at least once, 
 * then continues while a condition is true.
 * Examples: 
 *   do: add 1 to counter while counter < 10
 *   do: calculate balance as balance - payment while balance > 0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DoWhileAction extends Action {
    
    /**
     * The actions to execute in each iteration
     */
    private List<Action> bodyActions;
    
    /**
     * The condition to evaluate after each iteration
     */
    private Condition condition;
    
    /**
     * Maximum number of iterations to prevent infinite loops (default: 1000)
     */
    private int maxIterations = 1000;
    
    public DoWhileAction(SourceLocation location, List<Action> bodyActions, Condition condition) {
        super(location);
        this.bodyActions = bodyActions;
        this.condition = condition;
    }
    
    public DoWhileAction(SourceLocation location, List<Action> bodyActions, Condition condition, int maxIterations) {
        super(location);
        this.bodyActions = bodyActions;
        this.condition = condition;
        this.maxIterations = maxIterations;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitDoWhileAction(this);
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
        return true; // Do-while loops always have side effects
    }
    
    @Override
    public int getComplexity() {
        int complexity = 5; // Base complexity for do-while loop
        if (bodyActions != null) {
            for (Action action : bodyActions) {
                complexity += action.getComplexity();
            }
        }
        return complexity;
    }

    @Override
    public String toDebugString() {
        return String.format("DoWhileAction{bodyActions=%d, condition=%s, maxIterations=%d}",
                bodyActions != null ? bodyActions.size() : 0,
                condition != null ? condition.toDebugString() : "null",
                maxIterations);
    }
}

