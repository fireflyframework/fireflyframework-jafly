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
import static org.mockito.Mockito.when;

/**
 * Verification test for REST functions to ensure documentation accuracy
 */
@DisplayName("REST Functions Verification Tests")
class RestFunctionsVerificationTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);

        // Mock constant service to return empty flux (no constants from database)
        when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        // Use the constructor with default REST and JSON services
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    @DisplayName("Test rest_get function is recognized and executed")
    void testRestGetFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run apiResponse as rest_get("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());

        // The REST call should succeed and return actual data from the API
        Map<String, Object> apiResponse = (Map<String, Object>) result.getOutputData().get("apiResponse");
        assertNotNull(apiResponse);
        assertEquals(1, apiResponse.get("id"));
        assertEquals(false, apiResponse.get("completed"));
        assertNotNull(apiResponse.get("todo"));
    }

    @Test
    @DisplayName("Test rest_post function is recognized and executed")
    void testRestPostFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run apiResponse as rest_post("https://dummyjson.com/todos/add", "test body")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());

        // Verify the function was recognized and executed (returns error response)
        Map<String, Object> apiResponse = (Map<String, Object>) result.getOutputData().get("apiResponse");
        assertNotNull(apiResponse);
        assertEquals(false, apiResponse.get("success"));
    }

    @Test
    @DisplayName("Test rest_put function is recognized and executed")
    void testRestPutFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run apiResponse as rest_put("https://dummyjson.com/todos/1", "updated body")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());

        // Verify the function was recognized and executed
        Map<String, Object> apiResponse = (Map<String, Object>) result.getOutputData().get("apiResponse");
        assertNotNull(apiResponse);
        assertEquals(false, apiResponse.get("success"));
    }

    @Test
    @DisplayName("Test rest_delete function is recognized and executed")
    void testRestDeleteFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run apiResponse as rest_delete("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test rest_patch function is recognized and executed")
    void testRestPatchFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run apiResponse as rest_patch("https://dummyjson.com/todos/1", "patch body")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test rest_call generic function is recognized and executed")
    void testRestCallFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run apiResponse as rest_call("GET", "https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test REST and JSON integration")
    void testRestAndJsonIntegration() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run apiResponse as rest_get("https://dummyjson.com/todos/1")
                  - run todoId as json_get(apiResponse, "id")
                  - run todoText as json_get(apiResponse, "todo")
                  - run isCompleted as json_get(apiResponse, "completed")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
        assertNotNull(result.getOutputData().get("todoId"));
        assertNotNull(result.getOutputData().get("todoText"));
        assertNotNull(result.getOutputData().get("isCompleted"));
    }

    @Test
    @DisplayName("Test banking scenario with REST and JSON")
    void testBankingScenarioWithRestAndJson() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run todoResponse as rest_get("https://dummyjson.com/todos/1")
                  - run todoId as json_get(todoResponse, "id")
                  - run hasUserId as json_exists(todoResponse, "userId")
                  - calculate status as "VALID"
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("todoResponse"));
        assertNotNull(result.getOutputData().get("todoId"));
        assertNotNull(result.getOutputData().get("hasUserId"));
        assertNotNull(result.getOutputData().get("status"));
    }
}
