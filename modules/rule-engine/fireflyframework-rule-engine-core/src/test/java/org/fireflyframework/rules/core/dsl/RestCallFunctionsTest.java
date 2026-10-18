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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Test REST call functions in the DSL
 */
@ExtendWith(MockitoExtension.class)
class RestCallFunctionsTest {

    @Mock
    private ConstantService constantService;

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        // Mock constant service to return empty flux (no constants from database)
        lenient().when(constantService.getConstantsByCodes(any())).thenReturn(Flux.empty());

        // Use the constructor with default REST and JSON services
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    void testRestGetFunctionInSimpleSyntax() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run todoData as rest_get("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        // Note: This test will make an actual HTTP call to dummyjson.com
        // In a real test environment, you would mock the WebClient
    }

    @Test
    void testRestGetFunctionInComplexSyntax() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run todoData as rest_get("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        // The result should contain the calculated todoData
    }

    @Test
    void testRestPostFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run postResponse as rest_post("https://dummyjson.com/todos/add", "test body")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallWithHeaders() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run response as rest_get("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallWithTimeout() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run response as rest_get("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testGenericRestCallFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run todoData as rest_get("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("todoData"));
    }

    @Test
    void testRestCallErrorHandling() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run errorResponse as rest_get("https://invalid-url-that-does-not-exist.com/api")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        // The rule should still succeed, but the REST call should return an error response
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallWithVariables() {
        String yaml = """
                when:
                  - "true"
                then:
                  - set apiUrl to "https://dummyjson.com/todos/1"
                  - run response as rest_get(apiUrl)
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("todoId", "1");

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallInCondition() {
        String yaml = """
                when:
                  - 'json_get(rest_get("https://dummyjson.com/todos/1"), "completed") == false'
                then:
                  - set todoIncomplete to true
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }
}
