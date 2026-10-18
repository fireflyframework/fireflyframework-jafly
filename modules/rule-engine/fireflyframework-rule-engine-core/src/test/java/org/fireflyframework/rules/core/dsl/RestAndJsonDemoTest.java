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
 * Demonstration test showing REST and JSON functionality working
 */
@DisplayName("REST and JSON Functionality Demo")
class RestAndJsonDemoTest {

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
    @DisplayName("Test basic functionality without REST")
    void testBasicFunctionality() {
        String yaml = """
                when:
                  - "true"
                then:
                  - set testVar to "REST and JSON functions are implemented"
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("REST and JSON functions are implemented", result.getOutputData().get("testVar"));
    }

    @Test
    @DisplayName("Test JSON path function with mock data")
    void testJsonPathFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run userName as json_get(userData, "name")
                  - run userAge as json_get(userData, "age")
                  - run hasEmail as json_exists(userData, "email")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "John Doe");
        userData.put("age", 30);
        userData.put("email", "john@example.com");
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("John Doe", result.getOutputData().get("userName"));
        assertEquals(30, result.getOutputData().get("userAge"));
        assertEquals(true, result.getOutputData().get("hasEmail"));
    }

    @Test
    @DisplayName("Test complex JSON path with nested objects")
    void testComplexJsonPath() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run city as json_get(userData, "address.city")
                  - run firstHobby as json_get(userData, "hobbies[0]")
                  - run hobbiesCount as json_size(userData, "hobbies")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        Map<String, Object> address = new HashMap<>();
        address.put("city", "New York");
        address.put("state", "NY");
        userData.put("address", address);
        userData.put("hobbies", new String[]{"reading", "swimming", "coding"});
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("New York", result.getOutputData().get("city"));
        assertEquals("reading", result.getOutputData().get("firstHobby"));
        assertEquals(3, result.getOutputData().get("hobbiesCount"));
    }

    @Test
    @DisplayName("Test banking scenario with JSON only")
    void testBankingScenario() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run creditScore as json_get(creditData, "score")
                  - run hasDelinquencies as json_exists(creditData, "report.delinquencies")
                  - set approval to "APPROVED"
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> creditData = new HashMap<>();
        creditData.put("score", 720);
        Map<String, Object> report = new HashMap<>();
        report.put("delinquencies", 0);
        creditData.put("report", report);
        inputData.put("creditData", creditData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(720, result.getOutputData().get("creditScore"));
        assertEquals(true, result.getOutputData().get("hasDelinquencies"));
        assertEquals("APPROVED", result.getOutputData().get("approval"));
    }
}
