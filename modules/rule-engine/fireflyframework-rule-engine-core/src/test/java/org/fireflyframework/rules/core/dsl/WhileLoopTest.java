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

import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.services.ConstantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test while and do-while loop functionality
 */
@DisplayName("While and Do-While Loop Tests")
public class WhileLoopTest {

    private ASTRulesEvaluationEngine evaluationEngine;
    private ConstantService constantService;

    @BeforeEach
    void setUp() {
        constantService = Mockito.mock(ConstantService.class);
        Mockito.lenient().when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    @DisplayName("Simple while loop - count to 10")
    void testSimpleWhileLoop() {
        String yaml = """
            name: Simple While Loop
            inputs:
              - start
            when:
              - "true"
            then:
              - set counter to start
              - set total to 0
              - while counter less_than 10: calculate total as total + counter; add 1 to counter
            output:
              counter: number
              total: number
            """;

        Map<String, Object> inputData = Map.of("start", 0);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(10, ((Number) result.getOutputData().get("counter")).intValue());
        assertEquals(45, ((Number) result.getOutputData().get("total")).doubleValue(), 0.001); // 0+1+2+...+9 = 45
    }

    @Test
    @DisplayName("Do-while loop - execute at least once")
    void testDoWhileLoop() {
        String yaml = """
            name: Do-While Loop
            inputs:
              - start
            when:
              - "true"
            then:
              - set counter to start
              - set total to 0
              - do: calculate total as total + counter; add 1 to counter while counter less_than 5
            output:
              counter: number
              total: number
            """;

        Map<String, Object> inputData = Map.of("start", 0);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(5, ((Number) result.getOutputData().get("counter")).intValue());
        assertEquals(10, ((Number) result.getOutputData().get("total")).doubleValue(), 0.001); // 0+1+2+3+4 = 10
    }

    @Test
    @DisplayName("Do-while executes at least once even when condition is false")
    void testDoWhileExecutesAtLeastOnce() {
        String yaml = """
            name: Do-While At Least Once
            inputs:
              - value
            when:
              - "true"
            then:
              - set counter to 0
              - do: add 1 to counter while value greater_than 100
            output:
              counter: number
            """;

        Map<String, Object> inputData = Map.of("value", 5);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(1, ((Number) result.getOutputData().get("counter")).intValue()); // Executes once even though condition is false
    }

    @Test
    @DisplayName("While loop with complex condition")
    void testWhileLoopWithComplexCondition() {
        String yaml = """
            name: While Loop Complex Condition
            inputs:
              - maxValue
            when:
              - "true"
            then:
              - set counter to 0
              - set sum to 0
              - while counter less_than maxValue and sum less_than 100: calculate sum as sum + counter; add 1 to counter
            output:
              counter: number
              sum: number
            """;

        Map<String, Object> inputData = Map.of("maxValue", 50);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        // Loop should stop when sum >= 100, which happens at counter = 14 (sum = 91) -> counter = 15 (sum = 106)
        assertEquals(15, ((Number) result.getOutputData().get("counter")).intValue());
        assertTrue(((Number) result.getOutputData().get("sum")).doubleValue() >= 100);
    }

    @Test
    @DisplayName("While loop - condition false from start")
    void testWhileLoopConditionFalseFromStart() {
        String yaml = """
            name: While Loop No Execution
            inputs:
              - value
            when:
              - "true"
            then:
              - set counter to 0
              - while value greater_than 100: add 1 to counter
            output:
              counter: number
            """;

        Map<String, Object> inputData = Map.of("value", 5);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(0, ((Number) result.getOutputData().get("counter")).intValue()); // Never executes
    }

    @Test
    @DisplayName("While loop with multiple iterations")
    void testWhileLoopMultipleIterations() {
        String yaml = """
            name: While Loop Multiple Iterations
            when:
              - "true"
            then:
              - set counter to 0
              - set total to 0
              - while counter less_than 5: calculate total as total + counter; add 1 to counter
            output:
              counter: number
              total: number
            """;

        Map<String, Object> inputData = Map.of();

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(5, ((Number) result.getOutputData().get("counter")).intValue());
        assertEquals(10, ((Number) result.getOutputData().get("total")).doubleValue(), 0.001); // 0+1+2+3+4 = 10
    }
}

