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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Test JSON path functions in the DSL
 */
@ExtendWith(MockitoExtension.class)
class JsonPathFunctionsTest {

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
    void testJsonGetSimpleProperty() {
        String yaml = """
                when:
                  - "json_get(userData, 'name') == 'John Doe'"
                then:
                  - run userName as json_get(userData, "name")
                """;

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "John Doe");
        userData.put("age", 30);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals("John Doe", result.getOutputData().get("userName"));
    }

    @Test
    void testJsonGetNestedProperty() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run userCity as json_get(userData, "address.city")
                """;

        // Create JSON string as input data (as users would provide)
        String userDataJson = """
                {
                    "name": "John Doe",
                    "address": {
                        "street": "123 Main St",
                        "city": "New York",
                        "zipCode": "10001"
                    }
                }
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userDataJson);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals("New York", result.getOutputData().get("userCity"));
    }

    @Test
    void testJsonGetArrayElement() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run firstHobby as json_get(userData, "hobbies[0]")
                """;

        // Create JSON string as input data (as users would provide)
        String userDataJson = """
                {
                    "name": "John Doe",
                    "hobbies": ["reading", "swimming", "coding"]
                }
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userDataJson);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals("reading", result.getOutputData().get("firstHobby"));
    }

    @Test
    void testJsonGetComplexNestedStructure() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run expensiveItem as json_get(userData, "orders[0].items[1].name")
                """;

        // Create JSON string as input data (as users would provide)
        String userDataJson = """
                {
                    "name": "John Doe",
                    "orders": [
                        {
                            "id": "ORD-001",
                            "items": [
                                {
                                    "name": "Book",
                                    "price": 25.99
                                },
                                {
                                    "name": "Laptop",
                                    "price": 999.99
                                }
                            ]
                        }
                    ]
                }
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userDataJson);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals("Laptop", result.getOutputData().get("expensiveItem"));
    }

    @Test
    void testJsonExistsFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - run hasPhone as json_exists(userData, "address.phone")
                """;

        // Create JSON string instead of Map for JSON path functions
        String userDataJson = """
                {
                    "name": "John Doe",
                    "address": {
                        "street": "123 Main St",
                        "city": "New York",
                        "phone": "555-1234"
                    }
                }
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userDataJson);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals(true, result.getOutputData().get("hasPhone"));
    }

    @Test
    void testJsonSizeFunction() {
        String yaml = """
                when:
                  - 'json_size(userData, "hobbies") >= 3'
                then:
                  - run hobbyCount as json_size(userData, "hobbies")
                """;

        // Create JSON string instead of Map for JSON path functions
        String userDataJson = """
                {
                    "name": "John Doe",
                    "hobbies": ["reading", "swimming", "coding", "gaming"]
                }
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userDataJson);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals(4, result.getOutputData().get("hobbyCount"));
    }

    @Test
    void testJsonTypeFunction() {
        String yaml = """
                when:
                  - 'json_type(userData, "age") == "Integer"'
                then:
                  - run ageType as json_type(userData, "age")
                """;

        // Create JSON string instead of Map for JSON path functions
        String userDataJson = """
                {
                    "name": "John Doe",
                    "age": 30
                }
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userDataJson);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals("Integer", result.getOutputData().get("ageType"));
    }

    @Test
    void testJsonPathWithNonExistentPath() {
        String yaml = """
                when:
                  - 'json_get(userData, "nonexistent.path") == null'
                then:
                  - set pathExists to false
                """;

        // Create JSON string instead of Map for JSON path functions
        String userDataJson = """
                {
                    "name": "John Doe"
                }
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userDataJson);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals(false, result.getOutputData().get("pathExists"));
    }

    @Test
    void testJsonPathInComplexSyntax() {
        String yaml = """
                when:
                  - 'json_get(userData, "profile.preferences.notifications") == true'
                then:
                  - run notificationEmail as json_get(userData, "profile.email")
                """;

        // Create JSON string instead of Map for JSON path functions
        String userDataJson = """
                {
                    "name": "John Doe",
                    "profile": {
                        "email": "john@example.com",
                        "preferences": {
                            "notifications": true,
                            "theme": "dark"
                        }
                    }
                }
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("userData", userDataJson);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.isConditionResult());
        assertEquals("john@example.com", result.getOutputData().get("notificationEmail"));
    }
}
