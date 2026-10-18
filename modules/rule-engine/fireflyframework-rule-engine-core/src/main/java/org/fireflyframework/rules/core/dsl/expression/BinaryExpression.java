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

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a binary expression with two operands and an operator.
 * Examples: a + b, x > y, name contains "test"
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BinaryExpression extends Expression {
    
    /**
     * Left operand of the binary expression
     */
    private Expression left;
    
    /**
     * Binary operator
     */
    private BinaryOperator operator;
    
    /**
     * Right operand of the binary expression
     */
    private Expression right;
    
    public BinaryExpression(SourceLocation location, Expression left, BinaryOperator operator, Expression right) {
        super(location);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitBinaryExpression(this);
    }
    
    @Override
    public ExpressionType getExpressionType() {
        return operator.getResultType();
    }
    
    @Override
    public boolean isConstant() {
        return left.isConstant() && right.isConstant();
    }
    
    @Override
    public boolean hasVariableReferences() {
        return left.hasVariableReferences() || right.hasVariableReferences();
    }
    
    @Override
    public String toDebugString() {
        return String.format("(%s %s %s)", 
            left.toDebugString(), 
            operator.getSymbol(), 
            right.toDebugString());
    }
}



