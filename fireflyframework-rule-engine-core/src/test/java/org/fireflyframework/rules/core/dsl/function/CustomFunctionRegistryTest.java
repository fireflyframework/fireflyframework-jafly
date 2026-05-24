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

package org.fireflyframework.rules.core.dsl.function;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioural tests for {@link CustomFunctionRegistry} and its integration with the
 * evaluation engine. These cover the public extension contract: name resolution,
 * shadowing of built-ins, error semantics, and unknown-function diagnostics.
 */
class CustomFunctionRegistryTest {

    private CustomFunctionRegistry registry;
    private ASTRulesEvaluationEngine engine;

    @BeforeEach
    void setUp() {
        registry = new CustomFunctionRegistry();
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);
        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList())).thenReturn(Flux.empty());
        engine = new ASTRulesEvaluationEngine(parser, constantService, null, null, registry);
    }

    @Test
    @DisplayName("Registering a custom function makes it callable from `run`")
    void registeredFunctionCallableFromRun() {
        registry.register("triple", args -> ((Number) args[0]).intValue() * 3);

        String yaml = """
                when:
                  - amount at_least 0
                then:
                  - run result as triple(amount)
                output:
                  result: result
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("amount", 7));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("result", 21);
    }

    @Test
    @DisplayName("Custom function shadows built-in of the same name")
    void customFunctionShadowsBuiltin() {
        // The built-in `max` returns the largest argument. Shadow it with a no-op that returns 999.
        registry.register("max", args -> BigDecimal.valueOf(999));

        String yaml = """
                when:
                  - score at_least 0
                then:
                  - run winner as max(score, 1)
                output:
                  winner: winner
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("score", 5));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("winner", BigDecimal.valueOf(999));
    }

    @Test
    @DisplayName("Unknown function name surfaces as a structured evaluation failure (not silent null)")
    void unknownFunctionFailsEvaluation() {
        String yaml = """
                when:
                  - amount at_least 0
                then:
                  - run result as no_such_function(amount)
                output:
                  result: result
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("amount", 1));

        // The engine now fails the whole evaluation rather than silently swallowing the
        // unknown-function error and continuing past the broken action.
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("no_such_function");
    }

    @Test
    @DisplayName("Lookup is case-insensitive")
    void lookupIsCaseInsensitive() {
        registry.register("MyFunc", args -> "called");

        assertThat(registry.lookup("myfunc")).isPresent();
        assertThat(registry.lookup("MYFUNC")).isPresent();
        assertThat(registry.lookup("MyFunc")).isPresent();
    }

    @Test
    @DisplayName("Re-registering a name replaces the previous function")
    void reRegistrationReplaces() {
        registry.register("f", args -> "v1");
        registry.register("f", args -> "v2");

        assertThat(registry.lookup("f").orElseThrow().apply(new Object[0])).isEqualTo("v2");
    }

    @Test
    @DisplayName("Unregister removes the function")
    void unregisterRemoves() {
        registry.register("temp", args -> 1);
        assertThat(registry.contains("temp")).isTrue();
        assertThat(registry.unregister("temp")).isTrue();
        assertThat(registry.contains("temp")).isFalse();
        assertThat(registry.unregister("temp")).isFalse();
    }

    @Test
    @DisplayName("Blank name and null function are rejected at registration time")
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> registry.register("", args -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(null, args -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register("ok", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
