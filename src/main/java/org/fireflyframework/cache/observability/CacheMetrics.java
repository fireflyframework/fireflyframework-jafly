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

package org.fireflyframework.cache.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.fireflyframework.observability.metrics.MetricTags;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Observability instrumentation for cache operations.
 * <p>
 * Records:
 * <ul>
 *     <li>{@code firefly.cache.gets} — total cache get attempts, tagged by {@code result=hit|miss} and {@code adapter}</li>
 *     <li>{@code firefly.cache.puts} — total cache put operations, tagged by {@code adapter}</li>
 *     <li>{@code firefly.cache.evictions} — total cache evictions, tagged by {@code adapter}</li>
 *     <li>{@code firefly.cache.errors} — failed cache operations, tagged by {@code operation} and {@code error.type}</li>
 *     <li>{@code firefly.cache.operation.duration} — timer for get/put/evict latency, tagged by {@code operation}</li>
 * </ul>
 * <p>
 * When the {@link MeterRegistry} is unavailable, all methods become no-ops with zero overhead.
 */
public class CacheMetrics extends FireflyMetricsSupport {

    private static final String TAG_RESULT = "result";
    private static final String TAG_ADAPTER = "adapter";
    private static final String TAG_OPERATION = "operation";
    private static final String RESULT_HIT = "hit";
    private static final String RESULT_MISS = "miss";

    public CacheMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "cache");
    }

    public void recordHit(String adapter) {
        counter("gets", TAG_RESULT, RESULT_HIT, TAG_ADAPTER, adapter).increment();
    }

    public void recordMiss(String adapter) {
        counter("gets", TAG_RESULT, RESULT_MISS, TAG_ADAPTER, adapter).increment();
    }

    public void recordPut(String adapter) {
        counter("puts", TAG_ADAPTER, adapter).increment();
    }

    public void recordEviction(String adapter) {
        counter("evictions", TAG_ADAPTER, adapter).increment();
    }

    public void recordError(String operation, Throwable error) {
        recordFailure("errors", error, TAG_OPERATION, operation);
    }

    /**
     * Wraps a cache get operation with timing and hit/miss accounting.
     */
    public <V> Mono<Optional<V>> timedGet(String adapter, Mono<Optional<V>> operation) {
        return timed("operation.duration", operation, TAG_OPERATION, "get", TAG_ADAPTER, adapter)
                .doOnNext(opt -> {
                    if (opt.isPresent()) {
                        recordHit(adapter);
                    } else {
                        recordMiss(adapter);
                    }
                })
                .doOnError(e -> recordError("get", e));
    }

    /**
     * Wraps a cache put operation with timing.
     */
    public <T> Mono<T> timedPut(String adapter, Mono<T> operation) {
        return timed("operation.duration", operation, TAG_OPERATION, "put", TAG_ADAPTER, adapter)
                .doOnSuccess(v -> recordPut(adapter))
                .doOnError(e -> recordError("put", e));
    }

    /**
     * Wraps a cache evict operation with timing.
     */
    public <T> Mono<T> timedEvict(String adapter, Mono<T> operation) {
        return timed("operation.duration", operation, TAG_OPERATION, "evict", TAG_ADAPTER, adapter)
                .doOnSuccess(v -> recordEviction(adapter))
                .doOnError(e -> recordError("evict", e));
    }
}
