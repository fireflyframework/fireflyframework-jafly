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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for new DSL function primitives added to round out the built-in catalog:
 * {@code coalesce}, {@code if_else}, {@code is_in_range}, plus the documented-but-
 * previously-missing {@code calculate_age}, {@code format_date}, {@code validate_email},
 * {@code validate_phone}.
 */
class DslPrimitivesTest {

    private ASTRulesEvaluationEngine engine;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);
        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList())).thenReturn(Flux.empty());
        engine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    @DisplayName("coalesce returns the first non-null argument")
    void coalesceReturnsFirstNonNull() {
        String yaml = """
                then:
                  - run preferred as coalesce(nickname, full_name, "Anonymous")
                output:
                  preferred: preferred
                """;
        Map<String, Object> input = new HashMap<>();
        input.put("nickname", null);
        input.put("full_name", "Jane Doe");

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, input);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("preferred", "Jane Doe");
    }

    @Test
    @DisplayName("coalesce falls all the way through to a literal default")
    void coalesceFallsThroughToDefault() {
        String yaml = """
                then:
                  - run preferred as coalesce(nickname, full_name, "Anonymous")
                output:
                  preferred: preferred
                """;
        Map<String, Object> input = new HashMap<>();
        input.put("nickname", null);
        input.put("full_name", null);

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, input);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("preferred", "Anonymous");
    }

    @Test
    @DisplayName("if_else picks the then-branch when the condition is truthy")
    void ifElseTruthyPicksThen() {
        String yaml = """
                then:
                  - run category as if_else(age >= 18, "adult", "minor")
                output:
                  category: category
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("age", 21));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("category", "adult");
    }

    @Test
    @DisplayName("if_else picks the else-branch when the condition is falsey")
    void ifElseFalseyPicksElse() {
        String yaml = """
                then:
                  - run category as if_else(age >= 18, "adult", "minor")
                output:
                  category: category
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("age", 12));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("category", "minor");
    }

    @Test
    @DisplayName("is_in_range returns true within bounds, false outside (inclusive on both ends)")
    void isInRangeBoundary() {
        String yaml = """
                then:
                  - run lowOk as is_in_range(50, 50, 100)
                  - run highOk as is_in_range(100, 50, 100)
                  - run below as is_in_range(49, 50, 100)
                  - run above as is_in_range(101, 50, 100)
                output:
                  lowOk: lowOk
                  highOk: highOk
                  below: below
                  above: above
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("lowOk", true);
        assertThat(result.getOutputData()).containsEntry("highOk", true);
        assertThat(result.getOutputData()).containsEntry("below", false);
        assertThat(result.getOutputData()).containsEntry("above", false);
    }

    @Test
    @DisplayName("calculate_age returns whole years between birth date and reference date")
    void calculateAgeWithExplicitReferenceDate() {
        String yaml = """
                then:
                  - run age as calculate_age("1990-06-15", "2024-06-14")
                  - run ageAtBirthday as calculate_age("1990-06-15", "2024-06-15")
                output:
                  age: age
                  ageAtBirthday: ageAtBirthday
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("age", new BigDecimal("33"));
        assertThat(result.getOutputData()).containsEntry("ageAtBirthday", new BigDecimal("34"));
    }

    @Test
    @DisplayName("format_date applies a DateTimeFormatter pattern")
    void formatDateWithPattern() {
        String yaml = """
                then:
                  - run iso as format_date("2024-06-15")
                  - run pretty as format_date("2024-06-15", "dd MMM yyyy")
                output:
                  iso: iso
                  pretty: pretty
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("iso", "2024-06-15");
        // Locale-dependent month abbreviation; just check the structure
        assertThat((String) result.getOutputData().get("pretty")).matches("15 \\w{3} 2024");
    }

    @Test
    @DisplayName("validate_email function form matches the is_email operator")
    void validateEmailFunctionForm() {
        String yaml = """
                then:
                  - run good as validate_email("alice@example.com")
                  - run bad as validate_email("not-an-email")
                output:
                  good: good
                  bad: bad
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("good", true);
        assertThat(result.getOutputData()).containsEntry("bad", false);
    }

    @Test
    @DisplayName("is_valid with unknown validation type surfaces a clear error")
    void isValidUnknownTypeThrows() {
        String yaml = """
                then:
                  - run check as is_valid(value, "no_such_type")
                output:
                  check: check
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("value", "x"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("no_such_type");
    }
}
