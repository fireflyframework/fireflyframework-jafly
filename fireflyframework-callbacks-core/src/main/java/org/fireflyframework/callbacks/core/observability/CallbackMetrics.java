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

package org.fireflyframework.callbacks.core.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import reactor.core.publisher.Mono;

/**
 * Observability instrumentation for the callbacks dispatcher.
 * <p>
 * Records:
 * <ul>
 *     <li>{@code firefly.callbacks.deliveries} — counter tagged by {@code target.id} and
 *         {@code status} (success/failure/retry/cancelled)</li>
 *     <li>{@code firefly.callbacks.delivery.duration} — timer of dispatch latency,
 *         tagged by {@code target.id}</li>
 *     <li>{@code firefly.callbacks.retries} — counter of retry attempts,
 *         tagged by {@code target.id}, {@code attempt}</li>
 *     <li>{@code firefly.callbacks.circuit.opened} — counter of circuit-breaker openings,
 *         tagged by {@code target.id}</li>
 *     <li>{@code firefly.callbacks.errors} — counter of delivery errors,
 *         tagged by {@code target.id}, {@code error.type}</li>
 * </ul>
 */
public class CallbackMetrics extends FireflyMetricsSupport {

    private static final String TAG_TARGET = "target.id";
    private static final String TAG_ATTEMPT = "attempt";

    public CallbackMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "callbacks");
    }

    public <T> Mono<T> timedDelivery(String targetId, Mono<T> delivery) {
        return timed("delivery.duration", delivery, TAG_TARGET, targetId)
                .doOnSuccess(v -> recordSuccess("deliveries", TAG_TARGET, targetId))
                .doOnError(e -> {
                    recordFailure("deliveries", e, TAG_TARGET, targetId);
                    recordFailure("errors", e, TAG_TARGET, targetId);
                });
    }

    public void recordRetry(String targetId, int attempt) {
        counter("retries", TAG_TARGET, targetId, TAG_ATTEMPT, String.valueOf(attempt)).increment();
    }

    public void recordCircuitOpened(String targetId) {
        counter("circuit.opened", TAG_TARGET, targetId).increment();
    }
}
