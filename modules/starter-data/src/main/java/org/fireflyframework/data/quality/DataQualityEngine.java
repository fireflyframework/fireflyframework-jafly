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

package org.fireflyframework.data.quality;

import org.fireflyframework.data.event.DataQualityEvent;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine that evaluates a set of {@link DataQualityRule}s against a data object
 * and produces a {@link QualityReport}.
 *
 * <p>Supports two evaluation strategies via {@link QualityStrategy}:</p>
 * <ul>
 *   <li>{@link QualityStrategy#FAIL_FAST} - stops on the first CRITICAL failure</li>
 *   <li>{@link QualityStrategy#COLLECT_ALL} - evaluates every rule regardless of failures</li>
 * </ul>
 *
 * <p>When an {@link ApplicationEventPublisher} is provided, a {@link DataQualityEvent}
 * is published after each evaluation for observability.</p>
 */
public class DataQualityEngine {

    private final List<DataQualityRule<?>> rules;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates an engine with the given rules and no event publishing.
     *
     * @param rules the quality rules to evaluate
     */
    public DataQualityEngine(List<DataQualityRule<?>> rules) {
        this(rules, null);
    }

    /**
     * Creates an engine with the given rules and optional event publisher.
     *
     * @param rules          the quality rules to evaluate
     * @param eventPublisher the event publisher, or {@code null} to disable event publishing
     */
    public DataQualityEngine(List<DataQualityRule<?>> rules, ApplicationEventPublisher eventPublisher) {
        this.rules = rules;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Evaluates all applicable rules against the given data using the specified strategy.
     *
     * @param data     the data to validate
     * @param strategy the evaluation strategy
     * @param <T>      the type of data
     * @return a {@link Mono} emitting the {@link QualityReport}
     */
    @SuppressWarnings("unchecked")
    public <T> Mono<QualityReport> evaluate(T data, QualityStrategy strategy) {
        return Mono.fromCallable(() -> {
            List<QualityResult> results = new ArrayList<>();

            for (DataQualityRule<?> rule : rules) {
                DataQualityRule<T> typedRule = (DataQualityRule<T>) rule;
                QualityResult result = typedRule.evaluate(data);
                results.add(result);

                if (strategy == QualityStrategy.FAIL_FAST
                        && !result.isPassed()
                        && result.getSeverity() == QualitySeverity.CRITICAL) {
                    break;
                }
            }

            return buildReport(results);
        }).doOnNext(this::publishEvent);
    }

    private QualityReport buildReport(List<QualityResult> results) {
        int passedCount = 0;
        int failedCount = 0;
        boolean hasCriticalFailure = false;

        for (QualityResult result : results) {
            if (result.isPassed()) {
                passedCount++;
            } else {
                failedCount++;
                if (result.getSeverity() == QualitySeverity.CRITICAL) {
                    hasCriticalFailure = true;
                }
            }
        }

        return QualityReport.builder()
                .passed(!hasCriticalFailure)
                .totalRules(results.size())
                .passedRules(passedCount)
                .failedRules(failedCount)
                .results(results)
                .timestamp(Instant.now())
                .build();
    }

    private void publishEvent(QualityReport report) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DataQualityEvent(report));
        }
    }
}
