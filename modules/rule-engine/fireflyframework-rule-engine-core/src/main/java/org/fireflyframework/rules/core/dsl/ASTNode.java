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

package org.fireflyframework.rules.core.dsl;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Base class for all Abstract Syntax Tree nodes in the rule DSL.
 * Provides common functionality for source location tracking and visitor pattern support.
 */
@Data
@AllArgsConstructor
public abstract class ASTNode {
    
    /**
     * Source location information for error reporting and debugging
     */
    private SourceLocation location;
    
    /**
     * Accept method for the visitor pattern.
     * Allows different operations to be performed on AST nodes without modifying the node classes.
     *
     * @param visitor the visitor to accept
     * @param <T> the return type of the visitor
     * @return the result of the visitor operation
     */
    public abstract <T> T accept(ASTVisitor<T> visitor);
    
    /**
     * Get a string representation of this node for debugging
     */
    public abstract String toDebugString();
    
    /**
     * Get the type name of this AST node
     */
    public String getNodeType() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * Check if this node has location information
     */
    public boolean hasLocation() {
        return location != null;
    }
    
    /**
     * Get location information as a string, or "unknown" if not available
     */
    public String getLocationString() {
        return hasLocation() ? location.toString() : "unknown location";
    }
}
