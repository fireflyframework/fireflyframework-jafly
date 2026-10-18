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

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a logical condition combining multiple conditions.
 * Examples: (age > 18) AND (income > 50000), NOT (status equals "inactive")
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogicalCondition extends Condition {
    
    /**
     * Logical operator
     */
    private LogicalOperator operator;
    
    /**
     * Operand conditions
     */
    private List<Condition> operands;
    
    public LogicalCondition(SourceLocation location, LogicalOperator operator, List<Condition> operands) {
        super(location);
        this.operator = operator;
        this.operands = operands;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitLogicalCondition(this);
    }
    
    @Override
    public boolean isConstant() {
        return operands != null && operands.stream().allMatch(Condition::isConstant);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return operands != null && operands.stream().anyMatch(Condition::hasVariableReferences);
    }
    
    @Override
    public int getComplexity() {
        if (operands == null || operands.isEmpty()) {
            return 1;
        }
        return 1 + operands.stream().mapToInt(Condition::getComplexity).sum();
    }
    
    @Override
    public String toDebugString() {
        if (operands == null || operands.isEmpty()) {
            return operator.getSymbol() + "()";
        }
        
        if (operator == LogicalOperator.NOT && operands.size() == 1) {
            return String.format("(%s %s)", operator.getSymbol(), operands.get(0).toDebugString());
        }
        
        String conditions = operands.stream()
                .map(Condition::toDebugString)
                .collect(Collectors.joining(" " + operator.getSymbol() + " "));
        
        return "(" + conditions + ")";
    }
    
    /**
     * Get the number of operands
     */
    public int getOperandCount() {
        return operands != null ? operands.size() : 0;
    }
    
    /**
     * Get a specific operand by index
     */
    public Condition getOperand(int index) {
        if (operands == null || index < 0 || index >= operands.size()) {
            throw new IndexOutOfBoundsException("Operand index out of bounds: " + index);
        }
        return operands.get(index);
    }
    
    /**
     * Validate that the logical condition has the correct number of operands
     */
    public void validate() {
        int operandCount = getOperandCount();
        
        switch (operator) {
            case NOT:
                if (operandCount != 1) {
                    throw new IllegalStateException("NOT operator requires exactly 1 operand, got: " + operandCount);
                }
                break;
            case AND, OR:
                if (operandCount < 2) {
                    throw new IllegalStateException(operator + " operator requires at least 2 operands, got: " + operandCount);
                }
                break;
        }
    }
}



