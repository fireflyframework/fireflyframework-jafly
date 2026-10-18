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
import org.fireflyframework.rules.core.dsl.SourceLocation;

/**
 * Base class for all action AST nodes.
 * Actions represent operations that can be performed, such as variable assignments,
 * function calls, or conditional actions.
 */
public abstract class Action extends ASTNode {
    
    public Action(SourceLocation location) {
        super(location);
    }
    
    /**
     * Check if this action has side effects (modifies state)
     */
    public boolean hasSideEffects() {
        return true; // Most actions have side effects by default
    }
    
    /**
     * Check if this action references any variables
     */
    public abstract boolean hasVariableReferences();
    
    /**
     * Get the complexity level of this action (for optimization purposes)
     */
    public int getComplexity() {
        return 1;
    }
}
