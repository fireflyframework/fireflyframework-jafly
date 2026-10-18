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
 * Represents a unary expression with one operand and an operator.
 * Examples: -x, not condition, exists variable
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UnaryExpression extends Expression {
    
    /**
     * Unary operator
     */
    private UnaryOperator operator;
    
    /**
     * Operand of the unary expression
     */
    private Expression operand;

    public UnaryExpression(SourceLocation location, UnaryOperator operator, Expression operand) {
        super(location);
        this.operator = operator;
        this.operand = operand;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitUnaryExpression(this);
    }
    
    @Override
    public ExpressionType getExpressionType() {
        return operator.getResultType();
    }
    
    @Override
    public boolean isConstant() {
        return operand.isConstant();
    }
    
    @Override
    public boolean hasVariableReferences() {
        return operand.hasVariableReferences();
    }
    
    @Override
    public String toDebugString() {
        return String.format("(%s %s)", operator.getSymbol(), operand.toDebugString());
    }
}



