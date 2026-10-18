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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for forEach loop functionality
 */
@DisplayName("ForEach Loop Tests")
@ExtendWith(MockitoExtension.class)
public class ForEachLoopTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @Mock
    private ConstantService constantService;

    @BeforeEach
    void setUp() {
        // Mock constant service to return empty flux (no constants from database)
        Mockito.lenient().when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Nested
    @DisplayName("Basic forEach Tests")
    class BasicForEachTests {

        @Test
        @DisplayName("Test simple forEach with sum")
        void testSimpleForEachSum() {
            String yaml = """
                name: "Simple forEach Sum"
                description: "Sum all numbers in a list"
                
                inputs:
                  - numbers
                
                when:
                  - "true"
                
                then:
                  - set total to 0
                  - forEach num in numbers: calculate total as total + num
                
                output:
                  total: number
                """;

            Map<String, Object> inputData = Map.of(
                "numbers", List.of(10, 20, 30, 40, 50)
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

            assertTrue(result.isSuccess());
            assertEquals(150.0, ((Number) result.getOutputData().get("total")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("Test forEach with string concatenation")
        void testForEachStringConcat() {
            String yaml = """
                name: "forEach String Concatenation"
                description: "Concatenate strings from a list"
                
                inputs:
                  - words
                
                when:
                  - "true"
                
                then:
                  - set sentence to ""
                  - forEach word in words: set sentence to sentence + word + " "
                
                output:
                  sentence: text
                """;

            Map<String, Object> inputData = Map.of(
                "words", List.of("Hello", "World", "from", "forEach")
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

            assertTrue(result.isSuccess());
            assertEquals("Hello World from forEach ", result.getOutputData().get("sentence"));
        }

        @Test
        @DisplayName("Test forEach with index variable")
        void testForEachWithIndex() {
            String yaml = """
                name: "forEach with Index"
                description: "Use index variable in forEach"
                
                inputs:
                  - items
                
                when:
                  - "true"
                
                then:
                  - set indexSum to 0
                  - forEach item, idx in items: calculate indexSum as indexSum + idx
                
                output:
                  indexSum: number
                """;

            Map<String, Object> inputData = Map.of(
                "items", List.of("a", "b", "c", "d")  // indices: 0, 1, 2, 3
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

            assertTrue(result.isSuccess());
            // Sum of indices: 0 + 1 + 2 + 3 = 6
            assertEquals(6.0, ((Number) result.getOutputData().get("indexSum")).doubleValue(), 0.001);
        }
    }

    @Nested
    @DisplayName("forEach with Conditions")
    class ForEachWithConditionsTests {

        @Test
        @DisplayName("Test forEach with conditional filtering")
        void testForEachWithConditionalFiltering() {
            String yaml = """
                name: "forEach with Filtering"
                description: "Filter items based on condition"
                
                inputs:
                  - numbers
                
                when:
                  - "true"
                
                then:
                  - set evenSum to 0
                  - forEach num in numbers: if num % 2 equals 0 then calculate evenSum as evenSum + num
                
                output:
                  evenSum: number
                """;

            Map<String, Object> inputData = Map.of(
                "numbers", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

            assertTrue(result.isSuccess());
            // Sum of even numbers: 2 + 4 + 6 + 8 + 10 = 30
            assertEquals(30.0, ((Number) result.getOutputData().get("evenSum")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("Test forEach with multiple conditions")
        void testForEachWithMultipleConditions() {
            String yaml = """
                name: "forEach with Multiple Conditions"
                description: "Count items based on multiple conditions"
                
                inputs:
                  - scores
                
                when:
                  - "true"
                
                then:
                  - set highCount to 0
                  - set mediumCount to 0
                  - set lowCount to 0
                  - forEach score in scores: if score >= 80 then calculate highCount as highCount + 1
                  - forEach score in scores: if score >= 50 AND score < 80 then calculate mediumCount as mediumCount + 1
                  - forEach score in scores: if score < 50 then calculate lowCount as lowCount + 1
                
                output:
                  highCount: number
                  mediumCount: number
                  lowCount: number
                """;

            Map<String, Object> inputData = Map.of(
                "scores", List.of(95, 75, 60, 45, 85, 30, 70, 90)
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

            assertTrue(result.isSuccess());
            assertEquals(3.0, ((Number) result.getOutputData().get("highCount")).doubleValue(), 0.001);  // 95, 85, 90
            assertEquals(3.0, ((Number) result.getOutputData().get("mediumCount")).doubleValue(), 0.001); // 75, 60, 70
            assertEquals(2.0, ((Number) result.getOutputData().get("lowCount")).doubleValue(), 0.001);    // 45, 30
        }
    }

    @Nested
    @DisplayName("forEach with List Operations")
    class ForEachWithListOperationsTests {

        @Test
        @DisplayName("Test forEach building a new list")
        void testForEachBuildingList() {
            String yaml = """
                name: "forEach Building List"
                description: "Build a new list from forEach"
                
                inputs:
                  - numbers
                
                when:
                  - "true"
                
                then:
                  - set doubled to []
                  - forEach num in numbers: calculate temp as num * 2
                  - forEach num in numbers: append num * 2 to doubled
                
                output:
                  doubled: list
                """;

            Map<String, Object> inputData = Map.of(
                "numbers", List.of(1, 2, 3, 4, 5)
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<Object> doubled = (List<Object>) result.getOutputData().get("doubled");
            assertNotNull(doubled);
            assertEquals(5, doubled.size());
        }
    }

    @Nested
    @DisplayName("forEach Edge Cases")
    class ForEachEdgeCasesTests {

        @Test
        @DisplayName("Test forEach with empty list")
        void testForEachWithEmptyList() {
            String yaml = """
                name: "forEach with Empty List"
                description: "Handle empty list gracefully"
                
                inputs:
                  - items
                
                when:
                  - "true"
                
                then:
                  - set count to 0
                  - forEach item in items: calculate count as count + 1
                
                output:
                  count: number
                """;

            Map<String, Object> inputData = Map.of(
                "items", List.of()
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

            assertTrue(result.isSuccess());
            assertEquals(0.0, ((Number) result.getOutputData().get("count")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("Test forEach with single item")
        void testForEachWithSingleItem() {
            String yaml = """
                name: "forEach with Single Item"
                description: "Handle single item list"
                
                inputs:
                  - items
                
                when:
                  - "true"
                
                then:
                  - set result to 0
                  - forEach item in items: set result to item
                
                output:
                  result: number
                """;

            Map<String, Object> inputData = Map.of(
                "items", List.of(42)
            );

            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

            assertTrue(result.isSuccess());
            assertEquals(42, result.getOutputData().get("result"));
        }
    }
}

