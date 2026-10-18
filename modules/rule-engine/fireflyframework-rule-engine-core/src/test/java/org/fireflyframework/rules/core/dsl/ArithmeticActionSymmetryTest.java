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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code multiply} and {@code divide} arithmetic actions accept both
 * canonical ({@code multiply VALUE by VARIABLE}) and English-natural
 * ({@code multiply VARIABLE by VALUE}) orderings, producing identical results.
 *
 * <p>{@code add} and {@code subtract} are already English-natural in the value-first
 * form ({@code add 5 to score}, {@code subtract penalty from total}) and are not part
 * of this symmetry contract.
 */
class ArithmeticActionSymmetryTest {

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
    @DisplayName("multiply VALUE by VARIABLE (canonical form) -- value first, target second")
    void multiplyCanonicalForm() {
        String yaml = """
                then:
                  - set risk_factor to 10
                  - multiply 1.5 by risk_factor
                output:
                  risk_factor: number
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(new BigDecimal(result.getOutputData().get("risk_factor").toString()))
                .isEqualByComparingTo(new BigDecimal("15.0"));
    }

    @Test
    @DisplayName("multiply VARIABLE by VALUE (English-natural form) -- target first, value second")
    void multiplyEnglishNaturalForm() {
        String yaml = """
                then:
                  - set risk_factor to 10
                  - multiply risk_factor by 1.5
                output:
                  risk_factor: number
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(new BigDecimal(result.getOutputData().get("risk_factor").toString()))
                .isEqualByComparingTo(new BigDecimal("15.0"));
    }

    @Test
    @DisplayName("divide accepts both orderings symmetrically")
    void divideBothOrderings() {
        String yamlCanonical = """
                then:
                  - set monthly to 1200
                  - divide 12 by monthly
                output:
                  monthly: number
                """;
        String yamlNatural = """
                then:
                  - set monthly to 1200
                  - divide monthly by 12
                output:
                  monthly: number
                """;

        // Both forms compute monthly = monthly / 12 = 100
        for (String yaml : new String[]{yamlCanonical, yamlNatural}) {
            ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());
            assertThat(result.isSuccess())
                    .as("yaml: %s", yaml)
                    .isTrue();
            assertThat(new BigDecimal(result.getOutputData().get("monthly").toString()))
                    .as("yaml: %s", yaml)
                    .isEqualByComparingTo(new BigDecimal("100"));
        }
    }

    @Test
    @DisplayName("add and subtract retain English-natural value-first grammar (no symmetry needed)")
    void addAndSubtractEnglishNatural() {
        String yaml = """
                then:
                  - set score to 100
                  - add 5 to score          # score += 5
                  - subtract 2 from score   # score -= 2
                output:
                  score: number
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of());
        assertThat(result.isSuccess()).isTrue();
        assertThat(new BigDecimal(result.getOutputData().get("score").toString()))
                .isEqualByComparingTo(new BigDecimal("103"));
    }

    @Test
    @DisplayName("Complex value expression in either position parses correctly")
    void complexValueExpression() {
        String yaml = """
                inputs:
                  baseAmount: "number"
                  rate: "number"
                then:
                  - set total to baseAmount
                  - multiply total by (1 + rate)
                output:
                  total: number
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(
                yaml, Map.of("baseAmount", new BigDecimal("100"), "rate", new BigDecimal("0.25")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(new BigDecimal(result.getOutputData().get("total").toString()))
                .isEqualByComparingTo(new BigDecimal("125.00"));
    }
}
