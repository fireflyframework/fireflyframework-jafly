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
 * Represents an arithmetic action that modifies a variable using arithmetic operations.
 * Examples: add 10 to balance, subtract fee from amount, multiply score by 1.5, divide total by count
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArithmeticAction extends Action {
    
    /**
     * The arithmetic operation to perform
     */
    private ArithmeticOperationType operation;
    
    /**
     * Name of the variable to modify
     */
    private String variableName;
    
    /**
     * Value to use in the arithmetic operation
     */
    private Expression value;
    
    public ArithmeticAction(SourceLocation location, ArithmeticOperationType operation, String variableName, Expression value) {
        super(location);
        this.operation = operation;
        this.variableName = variableName;
        this.value = value;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitArithmeticAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return value.hasVariableReferences();
    }
    
    @Override
    public int getComplexity() {
        return 2; // Arithmetic actions are slightly more complex than simple assignments
    }

    @Override
    public String toDebugString() {
        return String.format("ArithmeticAction{%s %s %s %s}",
                operation.getKeyword(), value.toDebugString(),
                operation.getPreposition(), variableName);
    }
    
    /**
     * Enumeration of arithmetic operation types for actions
     */
    public enum ArithmeticOperationType {
        ADD("add", "to"),
        SUBTRACT("subtract", "from"),
        MULTIPLY("multiply", "by"),
        DIVIDE("divide", "by");
        
        private final String keyword;
        private final String preposition;
        
        ArithmeticOperationType(String keyword, String preposition) {
            this.keyword = keyword;
            this.preposition = preposition;
        }
        
        public String getKeyword() {
            return keyword;
        }
        
        public String getPreposition() {
            return preposition;
        }
        
        public static ArithmeticOperationType fromKeyword(String keyword) {
            for (ArithmeticOperationType op : values()) {
                if (op.keyword.equals(keyword)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown arithmetic operation: " + keyword);
        }
    }
}
