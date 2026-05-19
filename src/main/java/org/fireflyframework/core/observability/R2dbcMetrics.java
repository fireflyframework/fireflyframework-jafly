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

package org.fireflyframework.core.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Observability instrumentation for reactive R2DBC operations.
 * <p>
 * Records:
 * <ul>
 *     <li>{@code firefly.r2dbc.query.duration} — query execution latency, tagged by {@code operation} (select/insert/update/delete)</li>
 *     <li>{@code firefly.r2dbc.queries} — total queries executed, tagged by {@code operation} and {@code status}</li>
 *     <li>{@code firefly.r2dbc.errors} — query failures, tagged by {@code error.type}</li>
 * </ul>
 * <p>
 * Use the {@link #timedQuery(String, Mono)} and {@link #timedQueryFlux(String, Flux)} wrappers to
 * instrument repository methods. When the {@link MeterRegistry} is unavailable, all methods
 * become no-ops with zero overhead.
 */
public class R2dbcMetrics extends FireflyMetricsSupport {

    private static final String TAG_OPERATION = "operation";

    public R2dbcMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "r2dbc");
    }

    /**
     * Wraps a reactive R2DBC operation (e.g. select/insert/update/delete returning a single row)
     * with a query duration timer and success/failure counters.
     */
    public <T> Mono<T> timedQuery(String operation, Mono<T> query) {
        return timed("query.duration", query, TAG_OPERATION, operation)
                .doOnSuccess(v -> recordSuccess("queries", TAG_OPERATION, operation))
                .doOnError(e -> recordFailure("queries", e, TAG_OPERATION, operation));
    }

    /**
     * Wraps a reactive R2DBC streaming query with a query duration timer and counters.
     */
    public <T> Flux<T> timedQueryFlux(String operation, Flux<T> query) {
        return timed("query.duration", query, TAG_OPERATION, operation)
                .doOnComplete(() -> recordSuccess("queries", TAG_OPERATION, operation))
                .doOnError(e -> recordFailure("queries", e, TAG_OPERATION, operation));
    }
}
