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

import org.fireflyframework.rules.core.dsl.ASTNode;
import org.fireflyframework.rules.core.dsl.SourceLocation;

/**
 * Base class for all condition AST nodes.
 * Conditions represent boolean expressions that can be evaluated to true or false.
 */
public abstract class Condition extends ASTNode {
    
    public Condition(SourceLocation location) {
        super(location);
    }
    
    /**
     * Check if this condition is a constant value that can be evaluated at parse time
     */
    public boolean isConstant() {
        return false;
    }
    
    /**
     * Check if this condition references any variables
     */
    public abstract boolean hasVariableReferences();
    
    /**
     * Get the complexity level of this condition (for optimization purposes)
     */
    public int getComplexity() {
        return 1;
    }
}
