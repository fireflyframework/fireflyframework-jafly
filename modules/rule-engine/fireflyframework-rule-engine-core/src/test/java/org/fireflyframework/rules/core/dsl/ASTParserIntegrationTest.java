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

import org.fireflyframework.rules.core.dsl.action.Action;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.exception.ASTException;
import org.fireflyframework.rules.core.dsl.expression.Expression;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.dsl.visitor.ActionExecutor;
import org.fireflyframework.rules.core.dsl.visitor.EvaluationContext;
import org.fireflyframework.rules.core.dsl.visitor.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for the AST-based parser.
 * Tests parsing, evaluation, and execution of DSL constructs.
 */
@DisplayName("AST Parser Integration Tests")
class ASTParserIntegrationTest {
    
    private DSLParser dslParser;
    private EvaluationContext context;
    
    @BeforeEach
    void setUp() {
        dslParser = new DSLParser();
        context = new EvaluationContext("test-operation");
        
        // Set up test variables
        context.setVariable("age", 25);
        context.setVariable("income", 50000);
        context.setVariable("status", "ACTIVE");
        context.setVariable("score", 750);
        context.setVariable("name", "John Doe");
        context.setConstant("MIN_AGE", 18);
        context.setConstant("MAX_SCORE", 850);
    }
    
    @Nested
    @DisplayName("Expression Parsing and Evaluation")
    class ExpressionTests {
        
        @Test
        @DisplayName("Should parse and evaluate simple arithmetic expressions")
        void testSimpleArithmetic() {
            Expression expr = dslParser.parseExpression("age + 5");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = expr.accept(evaluator);
            assertThat(result.toString()).isEqualTo("30.0");
        }
        
        @Test
        @DisplayName("Should parse and evaluate complex arithmetic expressions")
        void testComplexArithmetic() {
            Expression expr = dslParser.parseExpression("(income * 0.1) + (age * 100)");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = expr.accept(evaluator);
            assertThat(result.toString()).isEqualTo("7500.00");
        }
        
        @Test
        @DisplayName("Should parse and evaluate string operations")
        void testStringOperations() {
            Expression expr = dslParser.parseExpression("name + \" is \" + status");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = expr.accept(evaluator);
            assertThat(result).isEqualTo("John Doe is ACTIVE");
        }
        
        @Test
        @DisplayName("Should parse and evaluate function calls")
        void testFunctionCalls() {
            // Note: Function calls will need to be implemented
            assertThatCode(() -> {
                Expression expr = dslParser.parseExpression("max(age, MIN_AGE)");
                ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
                expr.accept(evaluator);
            }).doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("Should handle variable references with property access")
        void testVariablePropertyAccess() {
            // Set up a complex object
            Map<String, Object> person = new HashMap<>();
            person.put("firstName", "John");
            person.put("lastName", "Doe");
            context.setVariable("person", person);
            
            assertThatCode(() -> {
                Expression expr = dslParser.parseExpression("person.firstName");
                ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
                expr.accept(evaluator);
            }).doesNotThrowAnyException();
        }
    }
    
    @Nested
    @DisplayName("Condition Parsing and Evaluation")
    class ConditionTests {
        
        @Test
        @DisplayName("Should parse and evaluate simple comparison conditions")
        void testSimpleComparison() {
            Condition condition = dslParser.parseCondition("age >= MIN_AGE");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = condition.accept(evaluator);
            assertThat(result).isEqualTo(true);
        }
        
        @Test
        @DisplayName("Should parse and evaluate logical AND conditions")
        void testLogicalAnd() {
            Condition condition = dslParser.parseCondition("age >= MIN_AGE and income > 30000");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = condition.accept(evaluator);
            assertThat(result).isEqualTo(true);
        }
        
        @Test
        @DisplayName("Should parse and evaluate logical OR conditions")
        void testLogicalOr() {
            Condition condition = dslParser.parseCondition("age < MIN_AGE or income > 40000");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = condition.accept(evaluator);
            assertThat(result).isEqualTo(true);
        }
        
        @Test
        @DisplayName("Should parse and evaluate logical NOT conditions")
        void testLogicalNot() {
            Condition condition = dslParser.parseCondition("not (age < MIN_AGE)");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = condition.accept(evaluator);
            assertThat(result).isEqualTo(true);
        }
        
        @Test
        @DisplayName("Should parse and evaluate string comparison conditions")
        void testStringComparison() {
            Condition condition = dslParser.parseCondition("status equals \"ACTIVE\"");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = condition.accept(evaluator);
            assertThat(result).isEqualTo(true);
        }
        
        @Test
        @DisplayName("Should parse and evaluate range conditions")
        void testRangeCondition() {
            Condition condition = dslParser.parseCondition("score >= 700 and score <= MAX_SCORE");
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);

            Object result = condition.accept(evaluator);
            assertThat(result).isEqualTo(true);
        }
        
        @Test
        @DisplayName("Should parse and evaluate complex nested conditions")
        void testComplexNestedConditions() {
            Condition condition = dslParser.parseCondition(
                "(age >= MIN_AGE and income > 30000) or (score > 700 and status equals \"ACTIVE\")"
            );
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
            
            Object result = condition.accept(evaluator);
            assertThat(result).isEqualTo(true);
        }
    }
    
    @Nested
    @DisplayName("Action Parsing and Execution")
    class ActionTests {
        
        @Test
        @DisplayName("Should parse and execute SET actions")
        void testSetAction() {
            Action action = dslParser.parseAction("set result to \"approved\"");
            ActionExecutor executor = new ActionExecutor(context);
            
            action.accept(executor);
            
            assertThat(context.getVariable("result")).isEqualTo("approved");
        }
        
        @Test
        @DisplayName("Should parse and execute CALCULATE actions")
        void testCalculateAction() {
            Action action = dslParser.parseAction("calculate totalScore as score + age");
            ActionExecutor executor = new ActionExecutor(context);
            
            action.accept(executor);
            
            assertThat(context.getVariable("totalScore").toString()).isEqualTo("775.0");
        }
        
        @Test
        @DisplayName("Should parse and execute conditional actions")
        void testConditionalAction() {
            Action action = dslParser.parseAction(
                "if score > 700 then set category to \"premium\" else set category to \"standard\""
            );
            ActionExecutor executor = new ActionExecutor(context);
            
            action.accept(executor);
            
            assertThat(context.getVariable("category")).isEqualTo("premium");
        }
        
        @Test
        @DisplayName("Should parse and execute CALL actions for known built-in functions")
        void testCallActionWithBuiltin() {
            // `log` is a built-in action function; `validateCredit` (the previous test's name)
            // does not exist, and after the silent-null-on-unknown-function fix the executor
            // now throws -- intentional behaviour to surface typos.
            assertThatCode(() -> {
                Action action = dslParser.parseAction("call log with [\"score check\", \"INFO\"]");
                ActionExecutor executor = new ActionExecutor(context);
                action.accept(executor);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CALL action with unknown function name surfaces a typed error")
        void testCallActionUnknownFunctionThrows() {
            Action action = dslParser.parseAction("call validateCredit with [score, income]");
            ActionExecutor executor = new ActionExecutor(context);
            assertThatThrownBy(() -> action.accept(executor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("validateCredit");
        }
    }
    
    @Nested
    @DisplayName("Error Handling and Diagnostics")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should provide detailed error messages for invalid expressions")
        void testInvalidExpressionError() {
            assertThatThrownBy(() -> dslParser.parseExpression("age >= and income"))
                .isInstanceOf(ASTException.class)
                .hasMessageContaining("Expected expression");
        }
        
        @Test
        @DisplayName("Should provide detailed error messages for invalid conditions")
        void testInvalidConditionError() {
            assertThatThrownBy(() -> dslParser.parseCondition("age >= and income"))
                .isInstanceOf(ASTException.class)
                .hasMessageContaining("Expected");
        }
        
        @Test
        @DisplayName("Should provide detailed error messages for invalid actions")
        void testInvalidActionError() {
            assertThatThrownBy(() -> dslParser.parseAction("set to value"))
                .isInstanceOf(ASTException.class)
                .hasMessageContaining("Expected");
        }
        
        @Test
        @DisplayName("Should handle undefined variables gracefully")
        void testUndefinedVariable() {
            assertThatCode(() -> {
                Expression expr = dslParser.parseExpression("undefinedVar + 5");
                ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
                Object result = expr.accept(evaluator);
                // Should return a default value or handle gracefully
            }).doesNotThrowAnyException();
        }
    }
    
    @Nested
    @DisplayName("Performance and Edge Cases")
    class PerformanceTests {
        
        @Test
        @DisplayName("Should handle deeply nested expressions")
        void testDeeplyNestedExpressions() {
            String deepExpression = "((((age + 1) * 2) - 3) / 4) + 5";
            
            assertThatCode(() -> {
                Expression expr = dslParser.parseExpression(deepExpression);
                ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
                expr.accept(evaluator);
            }).doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("Should handle large numbers of conditions")
        void testManyConditions() {
            StringBuilder conditionBuilder = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                if (i > 0) conditionBuilder.append(" and ");
                conditionBuilder.append("age > ").append(i);
            }
            
            assertThatCode(() -> {
                Condition condition = dslParser.parseCondition(conditionBuilder.toString());
                ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
                condition.accept(evaluator);
            }).doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("Should parse and evaluate quickly")
        void testParsingPerformance() {
            String expression = "age * income + score - MIN_AGE";
            
            long startTime = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                Expression expr = dslParser.parseExpression(expression);
                ExpressionEvaluator evaluator = new ExpressionEvaluator(context);
                expr.accept(evaluator);
            }
            long endTime = System.nanoTime();
            
            long durationMs = (endTime - startTime) / 1_000_000;
            assertThat(durationMs).isLessThan(1000); // Should complete in less than 1 second
        }
    }
}
