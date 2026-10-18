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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the engine is safe under concurrent evaluation of the same rule against
 * different inputs. Each evaluation owns its own {@link org.fireflyframework.rules.core.dsl.visitor.EvaluationContext},
 * the parser caches a shared (immutable) AST, and custom functions are stateless lookups
 * -- but the property is only meaningful if exercised, so this test fires hundreds of
 * concurrent evaluations and asserts no cross-talk.
 */
class ConcurrentEvaluationTest {

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

    /** A non-trivial rule that mutates several local variables in a loop. */
    private static final String LOOP_RULE = """
            inputs:
              factor: "number"
              count: "number"
            then:
              - set accumulator to 0
              - set iterations to 0
              - forEach _, idx in [1,2,3,4,5,6,7,8,9,10]:
                  add factor to accumulator
              - set iterations to count
            output:
              accumulator: accumulator
              iterations: iterations
            """;

    @Test
    @DisplayName("Hundreds of concurrent evaluations of the same rule produce independent, correct results")
    void concurrentEvaluationProducesIndependentResults() throws InterruptedException, ExecutionException {
        int evaluations = 500;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<CompletableFuture<ASTRulesEvaluationResult>> futures = new ArrayList<>(evaluations);
            for (int i = 0; i < evaluations; i++) {
                final int factor = i;
                futures.add(CompletableFuture.supplyAsync(
                        () -> engine.evaluateRules(LOOP_RULE, Map.of("factor", factor, "count", factor)),
                        pool));
            }

            for (int i = 0; i < evaluations; i++) {
                ASTRulesEvaluationResult result = futures.get(i).get(30, TimeUnit.SECONDS);
                assertThat(result.isSuccess())
                        .as("Evaluation #%d should succeed", i)
                        .isTrue();
                BigDecimal accumulator = new BigDecimal(result.getOutputData().get("accumulator").toString());
                BigDecimal iterations = new BigDecimal(result.getOutputData().get("iterations").toString());

                // Each evaluation accumulates factor 10 times, so accumulator = factor * 10
                assertThat(accumulator)
                        .as("Evaluation #%d: accumulator should equal factor*10", i)
                        .isEqualByComparingTo(BigDecimal.valueOf(i * 10L));
                // iterations is set to the count input, which is the iteration index
                assertThat(iterations)
                        .as("Evaluation #%d: iterations should equal count input", i)
                        .isEqualByComparingTo(BigDecimal.valueOf(i));
            }
        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Evaluation timed out -- possible deadlock under concurrency", e);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS))
                    .as("Thread pool should terminate cleanly")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Custom function called concurrently from many evaluations sees independent argument lists")
    void customFunctionConcurrencyIsolation() throws Exception {
        // The custom function records every argument list it sees in a thread-safe counter.
        // We assert that every recorded argument matches a real evaluation -- i.e. there's
        // no torn-read across evaluations sharing state.
        AtomicInteger callCount = new AtomicInteger();
        registry.register("score_input", args -> {
            callCount.incrementAndGet();
            // Each call receives the integer passed in; doubling it lets us correlate
            // input -> output across the concurrent fan-out.
            return ((Number) args[0]).intValue() * 2;
        });

        String yaml = """
                inputs:
                  payload: "number"
                then:
                  - run scored as score_input(payload)
                output:
                  scored: scored
                """;

        int evaluations = 200;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<Integer>> futures = IntStream.range(0, evaluations)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("payload", i));
                        assertThat(result.isSuccess()).isTrue();
                        return ((Number) result.getOutputData().get("scored")).intValue();
                    }, pool))
                    .toList();

            for (int i = 0; i < evaluations; i++) {
                int actual = futures.get(i).get(15, TimeUnit.SECONDS);
                assertThat(actual)
                        .as("Eval #%d should see its own argument (no cross-talk)", i)
                        .isEqualTo(i * 2);
            }
            assertThat(callCount.get()).isEqualTo(evaluations);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
