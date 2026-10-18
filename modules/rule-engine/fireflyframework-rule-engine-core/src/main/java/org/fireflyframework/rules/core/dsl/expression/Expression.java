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

import org.fireflyframework.rules.core.dsl.ASTNode;
import org.fireflyframework.rules.core.dsl.SourceLocation;

/**
 * Base class for all expression AST nodes.
 * Expressions represent values that can be computed, such as variables,
 * literals, arithmetic operations, and function calls.
 */
public abstract class Expression extends ASTNode {
    
    public Expression(SourceLocation location) {
        super(location);
    }
    
    /**
     * Get the expected result type of this expression.
     * This is used for type checking and optimization.
     */
    public abstract ExpressionType getExpressionType();
    
    /**
     * Check if this expression is a constant value that can be evaluated at parse time
     */
    public boolean isConstant() {
        return false;
    }
    
    /**
     * Check if this expression references any variables
     */
    public abstract boolean hasVariableReferences();
}


