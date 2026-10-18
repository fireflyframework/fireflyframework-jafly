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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the new 'run' command functionality.
 * Tests that 'run' works for function calls, REST calls, and JSON operations.
 */
@DisplayName("Run Action Tests")
class RunActionTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);

        // Mock constant service to return empty flux (no constants from database)
        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, null, null);
    }

    @Test
    @DisplayName("Test run command with mathematical function")
    void testRunWithMathFunction() {
        String yaml = """
                name: "Run Math Function Test"
                description: "Test run command with mathematical functions"
                
                inputs:
                  - value1
                  - value2
                  - value3
                
                when:
                  - "true"
                
                then:
                  - run maximum as max(value1, value2, value3)
                  - run minimum as min(value1, value2, value3)
                  - run absolute as abs(value2)
                
                output:
                  maximum: number
                  minimum: number
                  absolute: number
                """;

        Map<String, Object> inputData = Map.of(
            "value1", 10,
            "value2", -5,
            "value3", 7
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(10.0, ((Number) result.getOutputData().get("maximum")).doubleValue());
        assertEquals(-5.0, ((Number) result.getOutputData().get("minimum")).doubleValue());
        assertEquals(5.0, ((Number) result.getOutputData().get("absolute")).doubleValue());
    }

    @Test
    @DisplayName("Test run command with string functions")
    void testRunWithStringFunctions() {
        String yaml = """
                name: "Run String Function Test"
                description: "Test run command with string functions"

                inputs:
                  - firstName
                  - lastName

                when:
                  - "true"

                then:
                  - run upperFirst as upper(firstName)
                  - run lowerLast as lower(lastName)
                  - run trimmedFirst as trim(firstName)

                output:
                  upperFirst: text
                  lowerLast: text
                  trimmedFirst: text
                """;

        Map<String, Object> inputData = Map.of(
            "firstName", "John",
            "lastName", "DOE"
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals("JOHN", result.getOutputData().get("upperFirst"));
        assertEquals("doe", result.getOutputData().get("lowerLast"));
        assertEquals("John", result.getOutputData().get("trimmedFirst"));
    }

    @Test
    @DisplayName("Test run command with JSON operations")
    void testRunWithJsonOperations() {
        String yaml = """
                name: "Run JSON Operations Test"
                description: "Test run command with JSON operations"
                
                inputs:
                  - userData
                
                when:
                  - "true"
                
                then:
                  - run userName as json_get(userData, "name")
                  - run userAge as json_get(userData, "age")
                  - run hasEmail as json_exists(userData, "email")
                
                output:
                  userName: text
                  userAge: number
                  hasEmail: boolean
                """;

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "Alice");
        userData.put("age", 30);
        userData.put("email", "alice@example.com");

        Map<String, Object> inputData = Map.of("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals("Alice", result.getOutputData().get("userName"));
        assertEquals(30, result.getOutputData().get("userAge"));
        assertEquals(true, result.getOutputData().get("hasEmail"));
    }

    @Test
    @DisplayName("Test calculate command still works for mathematical operations")
    void testCalculateForMathOperations() {
        String yaml = """
                name: "Calculate Math Test"
                description: "Test that calculate still works for math"
                
                inputs:
                  - principal
                  - rate
                  - time
                
                when:
                  - "true"
                
                then:
                  - calculate simple_interest as principal * rate * time
                  - calculate total_amount as principal + simple_interest
                
                output:
                  simple_interest: number
                  total_amount: number
                """;

        Map<String, Object> inputData = Map.of(
            "principal", 10000,
            "rate", 0.05,
            "time", 2
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(1000.0, ((Number) result.getOutputData().get("simple_interest")).doubleValue());
        assertEquals(11000.0, ((Number) result.getOutputData().get("total_amount")).doubleValue());
    }

    @Test
    @DisplayName("Test mixing calculate and run commands")
    void testMixingCalculateAndRun() {
        String yaml = """
                name: "Mixed Calculate and Run Test"
                description: "Test using both calculate and run in the same rule"
                
                inputs:
                  - amount
                  - taxRate
                
                when:
                  - "true"
                
                then:
                  - calculate tax as amount * taxRate
                  - calculate total as amount + tax
                  - run formatted_total as format_currency(total)
                
                output:
                  tax: number
                  total: number
                  formatted_total: text
                """;

        Map<String, Object> inputData = Map.of(
            "amount", 100,
            "taxRate", 0.15
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(15.0, ((Number) result.getOutputData().get("tax")).doubleValue());
        assertEquals(115.0, ((Number) result.getOutputData().get("total")).doubleValue());
        assertNotNull(result.getOutputData().get("formatted_total"));
    }
}

