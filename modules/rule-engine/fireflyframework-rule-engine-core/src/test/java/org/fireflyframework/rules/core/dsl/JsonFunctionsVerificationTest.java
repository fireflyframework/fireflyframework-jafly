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
import org.fireflyframework.rules.core.services.JsonPathService;
import org.fireflyframework.rules.core.services.impl.JsonPathServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification test for JSON functions to ensure documentation accuracy
 */
@DisplayName("JSON Functions Verification Tests")
class JsonFunctionsVerificationTest {

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

        // Create JSON service
        JsonPathService jsonPathService = new JsonPathServiceImpl();

        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, null, jsonPathService);
    }

    @Test
    @DisplayName("Test json_get function with simple properties")
    void testJsonGetSimpleProperties() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run userName as json_get(userData, "name")
                  - run userAge as json_get(userData, "age")
                  - run isActive as json_get(userData, "active")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "John Doe");
        userData.put("age", 30);
        userData.put("active", true);
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals("John Doe", result.getOutputData().get("userName"));
        assertEquals(30, result.getOutputData().get("userAge"));
        assertEquals(true, result.getOutputData().get("isActive"));
    }

    @Test
    @DisplayName("Test json_get function with nested objects")
    void testJsonGetNestedObjects() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run city as json_get(userData, "address.city")
                  - run state as json_get(userData, "address.state")
                  - run zipCode as json_get(userData, "address.zipCode")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        Map<String, Object> address = new HashMap<>();
        address.put("city", "New York");
        address.put("state", "NY");
        address.put("zipCode", "10001");
        userData.put("address", address);
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals("New York", result.getOutputData().get("city"));
        assertEquals("NY", result.getOutputData().get("state"));
        assertEquals("10001", result.getOutputData().get("zipCode"));
    }

    @Test
    @DisplayName("Test json_get function with arrays")
    void testJsonGetArrays() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run firstHobby as json_get(userData, "hobbies[0]")
                  - run secondHobby as json_get(userData, "hobbies[1]")
                  - run firstSkillName as json_get(userData, "skills[0].name")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("hobbies", Arrays.asList("reading", "swimming", "coding"));
        
        Map<String, Object> skill1 = new HashMap<>();
        skill1.put("name", "Java");
        skill1.put("level", "Expert");
        Map<String, Object> skill2 = new HashMap<>();
        skill2.put("name", "Python");
        skill2.put("level", "Intermediate");
        userData.put("skills", Arrays.asList(skill1, skill2));
        
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals("reading", result.getOutputData().get("firstHobby"));
        assertEquals("swimming", result.getOutputData().get("secondHobby"));
        assertEquals("Java", result.getOutputData().get("firstSkillName"));
    }

    @Test
    @DisplayName("Test json_exists function")
    void testJsonExists() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run hasEmail as json_exists(userData, "email")
                  - run hasPhone as json_exists(userData, "phone")
                  - run hasAddress as json_exists(userData, "address")
                  - run hasAddressCity as json_exists(userData, "address.city")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", "john@example.com");
        Map<String, Object> address = new HashMap<>();
        address.put("city", "New York");
        userData.put("address", address);
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(true, result.getOutputData().get("hasEmail"));
        assertEquals(false, result.getOutputData().get("hasPhone"));
        assertEquals(true, result.getOutputData().get("hasAddress"));
        assertEquals(true, result.getOutputData().get("hasAddressCity"));
    }

    @Test
    @DisplayName("Test json_size function")
    void testJsonSize() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run hobbiesCount as json_size(userData, "hobbies")
                  - run skillsCount as json_size(userData, "skills")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("hobbies", Arrays.asList("reading", "swimming", "coding"));
        userData.put("skills", Arrays.asList("Java", "Python"));
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getOutputData().get("hobbiesCount"));
        assertEquals(2, result.getOutputData().get("skillsCount"));
    }

    @Test
    @DisplayName("Test json_type function")
    void testJsonType() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run nameType as json_type(userData, "name")
                  - run ageType as json_type(userData, "age")
                  - run activeType as json_type(userData, "active")
                  - run hobbiesType as json_type(userData, "hobbies")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "John Doe");
        userData.put("age", 30);
        userData.put("active", true);
        userData.put("hobbies", Arrays.asList("reading", "swimming"));
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals("String", result.getOutputData().get("nameType"));
        assertEquals("Integer", result.getOutputData().get("ageType"));
        assertEquals("Boolean", result.getOutputData().get("activeType"));
        assertEquals("ArrayList", result.getOutputData().get("hobbiesType"));
    }

    @Test
    @DisplayName("Test json_path alias for json_get")
    void testJsonPathAlias() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run userName as json_path(userData, "name")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "John Doe");
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals("John Doe", result.getOutputData().get("userName"));
    }
}
