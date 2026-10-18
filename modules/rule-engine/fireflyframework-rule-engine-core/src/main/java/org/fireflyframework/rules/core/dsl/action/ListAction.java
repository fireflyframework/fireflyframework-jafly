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

/**
 * Represents a list operation action.
 * Examples: append item to list, prepend item to list, remove item from list
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListAction extends Action {
    
    /**
     * The list operation to perform
     */
    private ListOperationType operation;
    
    /**
     * Value to add/remove from the list
     */
    private Expression value;
    
    /**
     * Name of the list variable to modify
     */
    private String listVariable;
    
    public ListAction(SourceLocation location, ListOperationType operation, Expression value, String listVariable) {
        super(location);
        this.operation = operation;
        this.value = value;
        this.listVariable = listVariable;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitListAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return value.hasVariableReferences();
    }
    
    @Override
    public int getComplexity() {
        return 2; // List operations are moderately complex
    }

    @Override
    public String toDebugString() {
        return String.format("ListAction{%s %s %s %s}",
                operation.getKeyword(), value.toDebugString(),
                operation.getPreposition(), listVariable);
    }
    
    /**
     * Enumeration of list operation types
     */
    public enum ListOperationType {
        APPEND("append", "to"),
        PREPEND("prepend", "to"),
        REMOVE("remove", "from");
        
        private final String keyword;
        private final String preposition;
        
        ListOperationType(String keyword, String preposition) {
            this.keyword = keyword;
            this.preposition = preposition;
        }
        
        public String getKeyword() {
            return keyword;
        }
        
        public String getPreposition() {
            return preposition;
        }
        
        public static ListOperationType fromKeyword(String keyword) {
            for (ListOperationType op : values()) {
                if (op.keyword.equals(keyword)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown list operation: " + keyword);
        }
    }
}
