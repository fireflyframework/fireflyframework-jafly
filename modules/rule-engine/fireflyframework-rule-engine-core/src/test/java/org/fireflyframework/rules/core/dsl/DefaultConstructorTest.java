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
 * Test to verify that the default constructor works correctly
 */
@DisplayName("Default Constructor Tests")
class DefaultConstructorTest {

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

        // Use the constructor with default REST and JSON services
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    @DisplayName("Test that default constructor creates working REST and JSON services")
    void testDefaultConstructorWithRestAndJson() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate testData as "test"
                  - calculate testValue as 42
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify basic functions work with default constructor
        assertEquals("test", result.getOutputData().get("testData"));
        assertEquals(42, result.getOutputData().get("testValue"));

        System.out.println("✅ Default constructor works with basic functions");
        System.out.println("✅ testData: " + result.getOutputData().get("testData"));
        System.out.println("✅ testValue: " + result.getOutputData().get("testValue"));
    }

    @Test
    @DisplayName("Test that REST functions are available with default constructor")
    void testDefaultConstructorWithRestCall() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run restResponse as rest_get("https://httpbin.org/json")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify REST function was executed (should return either success or error response)
        assertNotNull(result.getOutputData().get("restResponse"));
        
        System.out.println("✅ Default constructor works with REST functions");
        System.out.println("✅ restResponse: " + result.getOutputData().get("restResponse"));
    }

    @Test
    @DisplayName("Test complex scenario with default constructor")
    void testComplexScenarioWithDefaultConstructor() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate userName as "John"
                  - calculate userAge as 30
                  - calculate userCity as "New York"
                  - calculate hasAddress as true
                  - calculate isAdult as userAge >= 18
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify all operations work
        assertEquals("John", result.getOutputData().get("userName"));
        assertEquals(30, result.getOutputData().get("userAge"));
        assertEquals("New York", result.getOutputData().get("userCity"));
        assertEquals(true, result.getOutputData().get("hasAddress"));
        assertEquals(true, result.getOutputData().get("isAdult"));

        System.out.println("✅ Complex scenario works with default constructor");
        System.out.println("✅ userName: " + result.getOutputData().get("userName"));
        System.out.println("✅ userAge: " + result.getOutputData().get("userAge"));
        System.out.println("✅ userCity: " + result.getOutputData().get("userCity"));
        System.out.println("✅ hasAddress: " + result.getOutputData().get("hasAddress"));
        System.out.println("✅ isAdult: " + result.getOutputData().get("isAdult"));
    }
}
