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
import org.fireflyframework.rules.core.dsl.function.CustomFunctionRegistry;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.services.ConstantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for the function primitives added in 26.05.08 to round out the built-in
 * catalog: list operations (filter/map/reduce/find/sort/reverse/distinct),
 * statistical aggregates (median/stddev/variance), date field extractors
 * (year_of/month_of/day_of_month/day_of_week/current_iso), and string formatting
 * (format with {0}-style placeholders, concat).
 */
class NewBuiltinFunctionsTest {

    private ASTRulesEvaluationEngine engine;
    private CustomFunctionRegistry registry;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);
        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList())).thenReturn(Flux.empty());
        registry = new CustomFunctionRegistry();
        engine = new ASTRulesEvaluationEngine(parser, constantService, null, null, registry);
    }

    // ---------------------------------------------------------------------------------
    // Functional list operations
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("filter(list, function_name) keeps only items where the named predicate is truthy")
    void filterKeepsTruthy() {
        registry.register("greater_than_50", args -> ((Number) args[0]).intValue() > 50);

        String yaml = """
                inputs:
                  numbers: "list"
                then:
                  - run filtered as filter(numbers, "greater_than_50")
                output:
                  filtered: list
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml,
                Map.of("numbers", List.of(10, 60, 30, 80, 20, 100)));

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Integer> filtered = (List<Integer>) result.getOutputData().get("filtered");
        assertThat(filtered).containsExactly(60, 80, 100);
    }

    @Test
    @DisplayName("map(list, function_name) transforms every item via the named function")
    void mapTransformsAllItems() {
        registry.register("double_it", args -> ((Number) args[0]).intValue() * 2);

        String yaml = """
                inputs:
                  values: "list"
                then:
                  - run doubled as map(values, "double_it")
                output:
                  doubled: list
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("values", List.of(1, 2, 3, 4)));

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Integer> doubled = (List<Integer>) result.getOutputData().get("doubled");
        assertThat(doubled).containsExactly(2, 4, 6, 8);
    }

    @Test
    @DisplayName("reduce(list, initial, function_name) accumulates left-to-right")
    void reduceAccumulates() {
        registry.register("add_two", args -> ((Number) args[0]).intValue() + ((Number) args[1]).intValue());

        String yaml = """
                inputs:
                  values: "list"
                then:
                  - run total as reduce(values, 0, "add_two")
                output:
                  total: number
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("values", List.of(1, 2, 3, 4, 5)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.getOutputData().get("total")).intValue()).isEqualTo(15);
    }

    @Test
    @DisplayName("find(list, function_name) returns the first matching item, or null if none")
    void findReturnsFirstMatch() {
        registry.register("is_positive_pred", args -> ((Number) args[0]).intValue() > 0);

        String yaml = """
                inputs:
                  values: "list"
                then:
                  - run firstPositive as find(values, "is_positive_pred")
                output:
                  firstPositive: number
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("values", List.of(-3, -1, 5, 2)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.getOutputData().get("firstPositive")).intValue()).isEqualTo(5);
    }

    @Test
    @DisplayName("sort, reverse, distinct work on simple lists")
    void sortReverseDistinct() {
        String yaml = """
                inputs:
                  nums: "list"
                then:
                  - run sorted as sort(nums)
                  - run reversed as reverse(nums)
                  - run unique as distinct(nums)
                output:
                  sorted: list
                  reversed: list
                  unique: list
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("nums", List.of(3, 1, 4, 1, 5, 9, 2, 6)));

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked") List<Integer> sorted = (List<Integer>) result.getOutputData().get("sorted");
        @SuppressWarnings("unchecked") List<Integer> reversed = (List<Integer>) result.getOutputData().get("reversed");
        @SuppressWarnings("unchecked") List<Integer> unique = (List<Integer>) result.getOutputData().get("unique");

        assertThat(sorted).containsExactly(1, 1, 2, 3, 4, 5, 6, 9);
        assertThat(reversed).containsExactly(6, 2, 9, 5, 1, 4, 1, 3);
        assertThat(unique).containsExactly(3, 1, 4, 5, 9, 2, 6);
    }

    // ---------------------------------------------------------------------------------
    // Statistical aggregates
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("median of an odd-length list returns the middle element")
    void medianOddLength() {
        String yaml = """
                inputs:
                  data: "list"
                then:
                  - run m as median(data)
                output:
                  m: number
                """;
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("data", List.of(1, 3, 2, 5, 4)));
        assertThat(result.isSuccess()).isTrue();
        assertThat(new BigDecimal(result.getOutputData().get("m").toString())).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("median of an even-length list returns the mean of the two middle elements")
    void medianEvenLength() {
        String yaml = """
                inputs:
                  data: "list"
                then:
                  - run m as median(data)
                output:
                  m: number
                """;
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("data", List.of(1, 2, 3, 4)));
        assertThat(result.isSuccess()).isTrue();
        // Mean of 2 and 3 = 2.5
        assertThat(new BigDecimal(result.getOutputData().get("m").toString())).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("stddev and variance for a known sequence match the expected sample-statistic values")
    void stddevAndVariance() {
        String yaml = """
                inputs:
                  data: "list"
                then:
                  - run v as variance(data)
                  - run s as stddev(data)
                output:
                  v: number
                  s: number
                """;
        // Sample variance of [2, 4, 4, 4, 5, 5, 7, 9] = 32/7 ≈ 4.571 (n-1 denominator).
        // Sample stddev = sqrt(32/7) ≈ 2.138.
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml,
                Map.of("data", List.of(2, 4, 4, 4, 5, 5, 7, 9)));
        assertThat(result.isSuccess()).isTrue();
        BigDecimal variance = new BigDecimal(result.getOutputData().get("v").toString());
        BigDecimal stddev = new BigDecimal(result.getOutputData().get("s").toString());
        assertThat(variance.doubleValue()).isCloseTo(32.0 / 7.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(stddev.doubleValue()).isCloseTo(Math.sqrt(32.0 / 7.0), org.assertj.core.data.Offset.offset(0.0001));
    }

    // ---------------------------------------------------------------------------------
    // Date field extractors
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("year_of / month_of / day_of_month / day_of_week extract the right ISO field")
    void dateFieldExtractors() {
        // 2026-05-24 -- Sunday (ISO day-of-week 7)
        String yaml = """
                inputs:
                  d: "string"
                then:
                  - run y as year_of(d)
                  - run mo as month_of(d)
                  - run dom as day_of_month(d)
                  - run dow as day_of_week(d)
                output:
                  y: number
                  mo: number
                  dom: number
                  dow: number
                """;
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("d", "2026-05-24"));
        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> out = result.getOutputData();
        assertThat(new BigDecimal(out.get("y").toString())).isEqualByComparingTo("2026");
        assertThat(new BigDecimal(out.get("mo").toString())).isEqualByComparingTo("5");
        assertThat(new BigDecimal(out.get("dom").toString())).isEqualByComparingTo("24");
        assertThat(new BigDecimal(out.get("dow").toString())).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("year_of on an unparseable date surfaces a clean error message")
    void dateExtractorOnBadInputFails() {
        String yaml = """
                inputs:
                  d: "string"
                then:
                  - run y as year_of(d)
                output:
                  y: number
                """;
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("d", "not-a-date"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("year_of");
    }

    // ---------------------------------------------------------------------------------
    // String formatting
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("format(template, args...) substitutes {0}, {1}, ... placeholders")
    void formatPlaceholders() {
        String yaml = """
                inputs:
                  name: "string"
                  score: "number"
                then:
                  - run greeting as format("Hello, {0}! Your score is {1}.", name, score)
                output:
                  greeting: text
                """;
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml,
                Map.of("name", "Alice", "score", 92));
        assertThat(result.isSuccess()).isTrue();
        assertThat((String) result.getOutputData().get("greeting"))
                .isEqualTo("Hello, Alice! Your score is 92.");
    }

    @Test
    @DisplayName("format raises a clear error when the template references a missing placeholder")
    void formatMissingPlaceholderFails() {
        String yaml = """
                then:
                  - run msg as format("Need {0} and {1}", "only-one")
                output:
                  msg: text
                """;
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("format").contains("{1}");
    }

    @Test
    @DisplayName("concat(...args) joins all argument string representations")
    void concatJoinsArgs() {
        String yaml = """
                then:
                  - run joined as concat("a", "-", "b", "-", "c")
                output:
                  joined: text
                """;
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());
        assertThat(result.isSuccess()).isTrue();
        assertThat((String) result.getOutputData().get("joined")).isEqualTo("a-b-c");
    }

    // ---------------------------------------------------------------------------------
    // Real-world usage: combine list ops + statistical + formatting in one rule
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("End-to-end: filter, map, statistics, and format compose for a transaction summary")
    void endToEndComposed() {
        registry.register("is_above_100", args -> ((Number) args[0]).intValue() > 100);
        registry.register("with_fee", args -> ((Number) args[0]).intValue() + 5);

        // The format() template contains colons, so the entire action line must be wrapped
        // in YAML single-quotes; otherwise YAML interprets the colon as a key/value separator.
        String yaml = """
                inputs:
                  amounts: "list"
                then:
                  - run large_txns as filter(amounts, "is_above_100")
                  - run with_fees as map(large_txns, "with_fee")
                  - run total as sum(with_fees)
                  - run avg_amount as avg(with_fees)
                  - 'run summary as format("Large txns - {0}, total - {1}, avg - {2}", count(large_txns), total, avg_amount)'
                output:
                  large_txns: list
                  total: number
                  avg_amount: number
                  summary: text
                """;
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml,
                Map.of("amounts", List.of(50, 150, 75, 200, 300, 80, 110)));

        assertThat(result.isSuccess()).isTrue();
        // large_txns = [150, 200, 300, 110]; with_fees = [155, 205, 305, 115]; sum = 780; avg = 195
        @SuppressWarnings("unchecked")
        List<Integer> large = (List<Integer>) result.getOutputData().get("large_txns");
        assertThat(large).containsExactly(150, 200, 300, 110);
        assertThat(((Number) result.getOutputData().get("total")).intValue()).isEqualTo(780);
        assertThat(((String) result.getOutputData().get("summary")))
                .startsWith("Large txns - 4")
                .contains("total - 780");
    }
}
