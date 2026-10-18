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
import org.fireflyframework.rules.core.dsl.expression.Expression;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a comparison condition between two expressions.
 * Examples: age > 18, name equals "John", balance between 100 and 1000
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ComparisonCondition extends Condition {
    
    /**
     * Left side of the comparison
     */
    private Expression left;
    
    /**
     * Comparison operator
     */
    private ComparisonOperator operator;
    
    /**
     * Right side of the comparison
     */
    private Expression right;
    
    /**
     * Additional operand for range operations (e.g., BETWEEN)
     */
    private Expression rangeEnd;
    
    public ComparisonCondition(SourceLocation location, Expression left, ComparisonOperator operator, Expression right) {
        super(location);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
    
    public ComparisonCondition(SourceLocation location, Expression left, ComparisonOperator operator, Expression right, Expression rangeEnd) {
        super(location);
        this.left = left;
        this.operator = operator;
        this.right = right;
        this.rangeEnd = rangeEnd;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitComparisonCondition(this);
    }
    
    @Override
    public boolean isConstant() {
        boolean baseConstant = left.isConstant() && right.isConstant();
        return rangeEnd != null ? baseConstant && rangeEnd.isConstant() : baseConstant;
    }
    
    @Override
    public boolean hasVariableReferences() {
        boolean leftHasVars = left.hasVariableReferences();
        boolean rightHasVars = right != null && right.hasVariableReferences();
        boolean baseHasVars = leftHasVars || rightHasVars;
        return rangeEnd != null ? baseHasVars || rangeEnd.hasVariableReferences() : baseHasVars;
    }
    
    @Override
    public String toDebugString() {
        if (rangeEnd != null) {
            return String.format("(%s %s %s and %s)",
                left.toDebugString(),
                operator.getSymbol(),
                right.toDebugString(),
                rangeEnd.toDebugString());
        } else if (right != null) {
            return String.format("(%s %s %s)",
                left.toDebugString(),
                operator.getSymbol(),
                right.toDebugString());
        } else {
            // Unary operator
            return String.format("(%s %s)",
                left.toDebugString(),
                operator.getKeyword());
        }
    }
    
    /**
     * Check if this is a range comparison (BETWEEN or NOT_BETWEEN)
     */
    public boolean isRangeComparison() {
        return operator == ComparisonOperator.BETWEEN || operator == ComparisonOperator.NOT_BETWEEN;
    }
    
    /**
     * Validate that range comparisons have the required rangeEnd operand
     */
    public void validate() {
        if (isRangeComparison() && rangeEnd == null) {
            throw new IllegalStateException("Range comparison requires rangeEnd operand: " + operator);
        }
        if (!isRangeComparison() && rangeEnd != null) {
            throw new IllegalStateException("Non-range comparison should not have rangeEnd operand: " + operator);
        }
    }
}



