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
 * Represents a calculate action that evaluates an expression and stores the result.
 * Examples: calculate totalScore as creditScore + incomeScore,
 *           calculate riskLevel as assessRisk(customer, account)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CalculateAction extends Action {
    
    /**
     * Name of the variable to store the calculated result
     */
    private String resultVariable;
    
    /**
     * Expression to calculate
     */
    private Expression expression;
    
    public CalculateAction(SourceLocation location, String resultVariable, Expression expression) {
        super(location);
        this.resultVariable = resultVariable;
        this.expression = expression;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitCalculateAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return expression.hasVariableReferences();
    }
    
    @Override
    public String toDebugString() {
        return String.format("calculate %s as %s", resultVariable, expression.toDebugString());
    }
}
