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

import org.fireflyframework.rules.core.dsl.action.*;
import org.fireflyframework.rules.core.dsl.condition.ComparisonConditionImpl;
import org.fireflyframework.rules.core.dsl.condition.ExpressionConditionImpl;
import org.fireflyframework.rules.core.dsl.condition.LogicalConditionImpl;
import org.fireflyframework.rules.core.dsl.expression.*;
import org.fireflyframework.rules.core.dsl.action.WhileActionImpl;
import org.fireflyframework.rules.core.dsl.action.DoWhileActionImpl;

/**
 * Visitor interface for traversing and operating on AST nodes.
 * Implements the visitor pattern to allow different operations on AST nodes
 * without modifying the node classes themselves.
 *
 * @param <T> the return type of visitor operations
 */
public interface ASTVisitor<T> {
    
    // Expression visitors
    T visitBinaryExpression(BinaryExpressionImpl node);
    T visitUnaryExpression(UnaryExpressionImpl node);
    T visitVariableExpression(VariableExpressionImpl node);
    T visitLiteralExpression(LiteralExpressionImpl node);
    T visitFunctionCallExpression(FunctionCallExpressionImpl node);

    // Condition visitors
    T visitComparisonCondition(ComparisonConditionImpl node);
    T visitLogicalCondition(LogicalConditionImpl node);
    T visitExpressionCondition(ExpressionConditionImpl node);

    // Action visitors
    T visitFunctionCallAction(FunctionCallActionImpl node);
    T visitConditionalAction(ConditionalActionImpl node);
    T visitCalculateAction(CalculateActionImpl node);
    T visitRunAction(RunActionImpl node);
    T visitSetAction(SetActionImpl node);
    T visitArithmeticAction(ArithmeticActionImpl node);
    T visitListAction(ListActionImpl node);
    T visitCircuitBreakerAction(CircuitBreakerActionImpl node);
    T visitForEachAction(ForEachActionImpl node);
    T visitWhileAction(WhileActionImpl node);
    T visitDoWhileAction(DoWhileActionImpl node);

    // Default implementation for unknown nodes
    default T visitUnknown(ASTNode node) {
        throw new UnsupportedOperationException(
            "Visitor does not support node type: " + node.getClass().getSimpleName());
    }
}
