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

package org.fireflyframework.eda.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for EDA library.
 * <p>
 * Extends {@link FireflyMetricsSupport} for consistent metric naming ({@code firefly.eda.*})
 * and tag conventions (lowercase.dots). All metric creation, caching, and null-safety
 * is handled by the base class.
 * <p>
 * This component provides comprehensive metrics for:
 * <ul>
 *   <li>Event publishing - success/failure counts, latency, message sizes</li>
 *   <li>Event consumption - processing times, failure rates, throughput</li>
 *   <li>Publisher health and availability</li>
 *   <li>Consumer health and processing statistics</li>
 *   <li>Circuit breaker and resilience metrics</li>
 * </ul>
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "firefly.eda", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EdaMetrics extends FireflyMetricsSupport {

    private final ConcurrentMap<String, AtomicLong> publisherHealthGauges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> consumerHealthGauges = new ConcurrentHashMap<>();

    public EdaMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "eda");
    }

    // Publishing metrics

    /**
     * Records a successful event publication.
     */
    public void recordPublishSuccess(String publisherType, String destination, String eventType,
                                     Duration duration, long messageSize) {
        timer("publish.duration",
                "publisher.type", publisherType,
                "destination", destination,
                "event.type", eventType,
                "status", "success")
                .record(duration);

        counter("publish.count",
                "publisher.type", publisherType,
                "destination", destination,
                "status", "success")
                .increment();

        distributionSummary("publish.message.size",
                "publisher.type", publisherType,
                "destination", destination)
                .record(messageSize);
    }

    /**
     * Records a failed event publication.
     */
    public void recordPublishFailure(String publisherType, String destination, String eventType,
                                     Duration duration, String errorType) {
        timer("publish.duration",
                "publisher.type", publisherType,
                "destination", destination,
                "event.type", eventType,
                "status", "failure")
                .record(duration);

        counter("publish.count",
                "publisher.type", publisherType,
                "destination", destination,
                "error.type", errorType,
                "status", "failure")
                .increment();
    }

    // Consumption metrics

    /**
     * Records a successful event consumption.
     */
    public void recordConsumeSuccess(String consumerType, String source, String eventType,
                                     Duration processingDuration) {
        timer("consume.duration",
                "consumer.type", consumerType,
                "source", source,
                "event.type", eventType,
                "status", "success")
                .record(processingDuration);

        counter("consume.count",
                "consumer.type", consumerType,
                "source", source,
                "status", "success")
                .increment();
    }

    /**
     * Records a failed event consumption.
     */
    public void recordConsumeFailure(String consumerType, String source, String eventType,
                                     Duration processingDuration, String errorType) {
        timer("consume.duration",
                "consumer.type", consumerType,
                "source", source,
                "event.type", eventType,
                "status", "failure")
                .record(processingDuration);

        counter("consume.count",
                "consumer.type", consumerType,
                "source", source,
                "error.type", errorType,
                "status", "failure")
                .increment();
    }

    // Health metrics

    /**
     * Updates publisher health status.
     */
    public void updatePublisherHealth(String publisherType, String connectionId, boolean isHealthy) {
        String gaugeKey = publisherType + ":" + connectionId;
        AtomicLong gaugeValue = publisherHealthGauges.computeIfAbsent(gaugeKey, key -> {
            AtomicLong ref = new AtomicLong(0);
            gauge("publisher.health", ref, AtomicLong::get,
                    "publisher.type", publisherType,
                    "connection.id", connectionId);
            return ref;
        });
        gaugeValue.set(isHealthy ? 1 : 0);
    }

    /**
     * Updates consumer health status.
     */
    public void updateConsumerHealth(String consumerType, boolean isHealthy) {
        AtomicLong gaugeValue = consumerHealthGauges.computeIfAbsent(consumerType, key -> {
            AtomicLong ref = new AtomicLong(0);
            gauge("consumer.health", ref, AtomicLong::get,
                    "consumer.type", consumerType);
            return ref;
        });
        gaugeValue.set(isHealthy ? 1 : 0);
    }

    // Circuit breaker metrics

    /**
     * Records circuit breaker state change.
     */
    public void recordCircuitBreakerStateChange(String publisherType, String state) {
        counter("circuit.breaker.state.change",
                "publisher.type", publisherType,
                "state", state)
                .increment();
    }

    /**
     * Records retry attempt.
     */
    public void recordRetryAttempt(String publisherType, int attempt, boolean successful) {
        counter("retry.attempt",
                "publisher.type", publisherType,
                "attempt", String.valueOf(attempt),
                "status", successful ? "success" : "failure")
                .increment();
    }

    /**
     * Records rate limiter rejection.
     */
    public void recordRateLimiterRejection(String publisherType) {
        counter("rate.limiter.rejection",
                "publisher.type", publisherType)
                .increment();
    }
}
