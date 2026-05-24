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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closes two specific test gaps identified by the phase-4 DSL gap audit:
 *
 * <ol>
 *   <li>{@code do-while} loop semantics (the existing test suite covered {@code forEach}
 *       and {@code while} but not {@code do-while}).</li>
 *   <li>{@code CustomFunctionRegistry} usage from <em>condition expressions</em>, not
 *       just actions -- to confirm extension functions are reachable through the
 *       evaluator from both code paths.</li>
 * </ol>
 */
class DoWhileAndConditionFunctionTest {

    private ASTRulesEvaluationEngine engine;
    private CustomFunctionRegistry registry;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constants = Mockito.mock(ConstantService.class);
        Mockito.when(constants.getConstantsByCodes(Mockito.anyList())).thenReturn(Flux.empty());
        registry = new CustomFunctionRegistry();
        engine = new ASTRulesEvaluationEngine(parser, constants, null, null, registry);
    }

    @Test
    @DisplayName("do-while executes body once then re-checks the condition each iteration")
    void doWhileExecutesAtLeastOnce() {
        String yaml = """
                inputs:
                  startValue: "number"
                then:
                  - set counter to startValue
                  - do: add 1 to counter while counter < 5
                output:
                  counter: counter
                """;

        // Start below cap: should loop until counter == 5
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("startValue", 2));
        assertThat(result.isSuccess()).isTrue();
        assertThat(new BigDecimal(result.getOutputData().get("counter").toString()))
                .isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("do-while runs the body at least once even when the condition is already false")
    void doWhileRunsBodyAtLeastOnceWhenConditionInitiallyFalse() {
        String yaml = """
                inputs:
                  startValue: "number"
                then:
                  - set counter to startValue
                  - do: add 1 to counter while counter < 5
                output:
                  counter: counter
                """;

        // Start at 5: condition is false from the start, but do-while always runs the body once
        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("startValue", 5));
        assertThat(result.isSuccess()).isTrue();
        assertThat(new BigDecimal(result.getOutputData().get("counter").toString()))
                .isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    @DisplayName("Custom functions are callable from condition expressions, not just from actions")
    void customFunctionReachableFromCondition() {
        // Register a custom function that flags VIP customers
        registry.register("is_vip", args -> {
            String id = String.valueOf(args[0]);
            return id.startsWith("VIP-");
        });

        String yaml = """
                inputs:
                  customerId: "string"
                when:
                  - is_vip(customerId) equals true
                then:
                  - set tier to "PREMIUM"
                else:
                  - set tier to "STANDARD"
                output:
                  tier: tier
                """;

        ASTRulesEvaluationResult vipResult = engine.evaluateRules(yaml, Map.of("customerId", "VIP-001"));
        assertThat(vipResult.isSuccess()).isTrue();
        assertThat(vipResult.getOutputData()).containsEntry("tier", "PREMIUM");

        ASTRulesEvaluationResult standardResult = engine.evaluateRules(yaml, Map.of("customerId", "STD-001"));
        assertThat(standardResult.isSuccess()).isTrue();
        assertThat(standardResult.getOutputData()).containsEntry("tier", "STANDARD");
    }
}
