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

package org.fireflyframework.rules.core.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import reactor.core.publisher.Mono;

/**
 * Observability instrumentation for the Firefly rule engine.
 * <p>
 * Records:
 * <ul>
 *     <li>{@code firefly.ruleengine.evaluations} — total rule evaluations, tagged
 *         by {@code rule.id} and {@code result} (matched/unmatched/error)</li>
 *     <li>{@code firefly.ruleengine.evaluation.duration} — timer of evaluation latency,
 *         tagged by {@code rule.id}</li>
 *     <li>{@code firefly.ruleengine.conditions.matched} — counter of matched conditions,
 *         tagged by {@code rule.id}, {@code condition.id}</li>
 *     <li>{@code firefly.ruleengine.compilations} — counter of YAML→AST compilations,
 *         tagged by {@code status} (success/failure)</li>
 *     <li>{@code firefly.ruleengine.errors} — counter of evaluation errors,
 *         tagged by {@code rule.id}, {@code error.type}</li>
 * </ul>
 */
public class RuleEngineMetrics extends FireflyMetricsSupport {

    private static final String TAG_RULE_ID = "rule.id";
    private static final String TAG_CONDITION_ID = "condition.id";
    private static final String TAG_RESULT = "result";

    public RuleEngineMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "ruleengine");
    }

    public <T> Mono<T> timedEvaluation(String ruleId, Mono<T> evaluation) {
        return timed("evaluation.duration", evaluation, TAG_RULE_ID, ruleId)
                .doOnSuccess(v -> counter("evaluations",
                        TAG_RULE_ID, ruleId, TAG_RESULT, "matched").increment())
                .doOnError(e -> {
                    counter("evaluations", TAG_RULE_ID, ruleId, TAG_RESULT, "error").increment();
                    recordFailure("errors", e, TAG_RULE_ID, ruleId);
                });
    }

    public void recordUnmatched(String ruleId) {
        counter("evaluations", TAG_RULE_ID, ruleId, TAG_RESULT, "unmatched").increment();
    }

    public void recordConditionMatched(String ruleId, String conditionId) {
        counter("conditions.matched", TAG_RULE_ID, ruleId, TAG_CONDITION_ID, conditionId).increment();
    }

    public void recordCompilation(boolean success) {
        counter("compilations", "status", success ? "success" : "failure").increment();
    }
}
