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

package org.fireflyframework.eda.error.impl;

import org.fireflyframework.eda.error.CustomErrorHandler;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Custom error handler that records error metrics via {@link FireflyMetricsSupport}.
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "firefly.eda.error.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class MetricsErrorHandler extends FireflyMetricsSupport implements CustomErrorHandler {

    public MetricsErrorHandler(MeterRegistry meterRegistry) {
        super(meterRegistry, "eda");
    }

    @Override
    public Mono<Void> handleError(Object event, Map<String, Object> headers,
                                 Throwable error, String listenerMethod) {
        return Mono.fromRunnable(() -> recordErrorMetrics(event, headers, error, listenerMethod));
    }

    @Override
    public String getHandlerName() {
        return "metrics-error-handler";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    private void recordErrorMetrics(Object event, Map<String, Object> headers, Throwable error, String listenerMethod) {
        String errorType = error.getClass().getSimpleName();
        String eventType = event.getClass().getSimpleName();

        counter("listener.errors",
                "listener.method", listenerMethod,
                "error.type", errorType,
                "event.type", eventType)
                .increment();

        Object startTime = headers.get("processing.start.time");
        if (startTime instanceof Long) {
            long duration = System.currentTimeMillis() - (Long) startTime;
            timer("listener.error.duration",
                    "listener.method", listenerMethod,
                    "error.type", errorType)
                    .record(duration, TimeUnit.MILLISECONDS);
        }

        log.debug("Recorded error metrics for {} in {}: {}",
                errorType, listenerMethod, error.getMessage());
    }
}
