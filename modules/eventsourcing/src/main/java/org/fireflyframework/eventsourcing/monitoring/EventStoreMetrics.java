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

package org.fireflyframework.eventsourcing.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.springframework.boot.actuate.health.Health;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics and monitoring for Event Store operations.
 *
 * This component provides comprehensive monitoring of event store performance,
 * including operation timers, counters, and health indicators.
 *
 * Metrics exposed:
 * - firefly.eventsourcing.operations.duration (Timer)
 * - firefly.eventsourcing.events.appended (Counter)
 * - firefly.eventsourcing.events.loaded (Counter)
 * - firefly.eventsourcing.errors (Counter)
 * - firefly.eventsourcing.concurrency.conflicts (Counter)
 * - firefly.eventsourcing.connection.pool.active (Gauge)
 * - firefly.eventsourcing.batch.size (DistributionSummary)
 */
@Slf4j
public class EventStoreMetrics extends FireflyMetricsSupport {

    // Gauges
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalAggregates = new AtomicLong(0);
    private final AtomicLong totalEvents = new AtomicLong(0);

    // Health tracking
    private volatile boolean isHealthy = true;
    private volatile Instant lastHealthCheck = Instant.now();
    private volatile String lastError;

    public EventStoreMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "eventsourcing");

        gauge("connection.pool.active", activeConnections, AtomicLong::get);
        gauge("aggregates.total", totalAggregates, AtomicLong::get);
        gauge("events.total", totalEvents, AtomicLong::get);
    }

    /**
     * Record an append operation with timing and event count.
     *
     * @param duration the duration of the append operation
     * @param eventCount the number of events appended
     */
    public void recordAppendOperation(Duration duration, int eventCount) {
        timer("operations.duration", "operation", "append").record(duration);
        counter("events.appended").increment(eventCount);
        distributionSummary("batch.size").record(eventCount);

        log.debug("Recorded append operation: {} events in {}", eventCount, duration);
    }

    /**
     * Record a load operation with timing and event count.
     *
     * @param duration the duration of the load operation
     * @param eventCount the number of events loaded
     */
    public void recordLoadOperation(Duration duration, int eventCount) {
        timer("operations.duration", "operation", "load").record(duration);
        counter("events.loaded").increment(eventCount);

        log.debug("Recorded load operation: {} events in {}", eventCount, duration);
    }

    /**
     * Record a query operation with timing.
     *
     * @param duration the duration of the query operation
     */
    public void recordQueryOperation(Duration duration) {
        timer("operations.duration", "operation", "query").record(duration);

        log.debug("Recorded query operation in {}", duration);
    }

    /**
     * Record an error by type.
     *
     * @param errorType the type/category of the error
     * @param error the throwable that occurred
     */
    public void recordError(String errorType, Throwable error) {
        counter("errors", "type", errorType).increment();

        this.lastError = error.getMessage();
        this.isHealthy = false;

        log.warn("Recorded event store error of type {}: {}", errorType, error.getMessage());
    }

    /**
     * Record a concurrency conflict.
     */
    public void recordConcurrencyConflict() {
        counter("concurrency.conflicts").increment();
        log.debug("Recorded concurrency conflict");
    }

    /**
     * Start timing an operation.
     *
     * @return a timer sample to stop later
     */
    public Timer.Sample startTimer() {
        MeterRegistry reg = registry();
        return reg != null ? Timer.start(reg) : null;
    }

    /**
     * Update active connection count.
     *
     * @param count the number of active connections
     */
    public void setActiveConnections(long count) {
        activeConnections.set(count);
    }

    /**
     * Update total aggregate count.
     *
     * @param count the total number of aggregates
     */
    public void setTotalAggregates(long count) {
        totalAggregates.set(count);
    }

    /**
     * Update total event count.
     *
     * @param count the total number of events
     */
    public void setTotalEvents(long count) {
        totalEvents.set(count);
    }

    /**
     * Mark the event store as healthy.
     */
    public void markHealthy() {
        this.isHealthy = true;
        this.lastHealthCheck = Instant.now();
        this.lastError = null;
    }

    /**
     * Mark the event store as unhealthy.
     */
    public void markUnhealthy(String reason) {
        this.isHealthy = false;
        this.lastError = reason;
        this.lastHealthCheck = Instant.now();
    }

    /**
     * Builds health details for use by health indicators.
     *
     * @param builder the Health builder to add details to
     */
    public void contributeHealthDetails(Health.Builder builder) {
        if (!isHealthy) {
            builder.down();
        }
        builder.withDetail("last.health.check", lastHealthCheck)
               .withDetail("total.events", totalEvents.get())
               .withDetail("total.aggregates", totalAggregates.get())
               .withDetail("active.connections", activeConnections.get())
               .withDetail("last.error", lastError);
    }

    /**
     * Get performance summary for monitoring dashboards.
     *
     * @return performance summary with all metrics
     */
    public PerformanceSummary getPerformanceSummary() {
        return PerformanceSummary.builder()
                .eventsAppended(counter("events.appended").count())
                .eventsLoaded(counter("events.loaded").count())
                .totalErrors(counter("errors", "type", "general").count())
                .concurrencyConflicts(counter("concurrency.conflicts").count())
                .averageAppendTime(timer("operations.duration", "operation", "append")
                        .mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .averageLoadTime(timer("operations.duration", "operation", "load")
                        .mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .averageBatchSize(distributionSummary("batch.size").mean())
                .activeConnections(activeConnections.get())
                .totalEvents(totalEvents.get())
                .totalAggregates(totalAggregates.get())
                .isHealthy(isHealthy)
                .lastHealthCheck(lastHealthCheck)
                .build();
    }

    /**
     * Performance summary data transfer object.
     */
    public static class PerformanceSummary {
        private final double eventsAppended;
        private final double eventsLoaded;
        private final double totalErrors;
        private final double concurrencyConflicts;
        private final double averageAppendTime;
        private final double averageLoadTime;
        private final double averageBatchSize;
        private final long activeConnections;
        private final long totalEvents;
        private final long totalAggregates;
        private final boolean isHealthy;
        private final Instant lastHealthCheck;

        private PerformanceSummary(Builder builder) {
            this.eventsAppended = builder.eventsAppended;
            this.eventsLoaded = builder.eventsLoaded;
            this.totalErrors = builder.totalErrors;
            this.concurrencyConflicts = builder.concurrencyConflicts;
            this.averageAppendTime = builder.averageAppendTime;
            this.averageLoadTime = builder.averageLoadTime;
            this.averageBatchSize = builder.averageBatchSize;
            this.activeConnections = builder.activeConnections;
            this.totalEvents = builder.totalEvents;
            this.totalAggregates = builder.totalAggregates;
            this.isHealthy = builder.isHealthy;
            this.lastHealthCheck = builder.lastHealthCheck;
        }

        public static Builder builder() {
            return new Builder();
        }

        public double getEventsAppended() { return eventsAppended; }
        public double getEventsLoaded() { return eventsLoaded; }
        public double getTotalErrors() { return totalErrors; }
        public double getConcurrencyConflicts() { return concurrencyConflicts; }
        public double getAverageAppendTime() { return averageAppendTime; }
        public double getAverageLoadTime() { return averageLoadTime; }
        public double getAverageBatchSize() { return averageBatchSize; }
        public long getActiveConnections() { return activeConnections; }
        public long getTotalEvents() { return totalEvents; }
        public long getTotalAggregates() { return totalAggregates; }
        public boolean isHealthy() { return isHealthy; }
        public Instant getLastHealthCheck() { return lastHealthCheck; }

        public static class Builder {
            private double eventsAppended;
            private double eventsLoaded;
            private double totalErrors;
            private double concurrencyConflicts;
            private double averageAppendTime;
            private double averageLoadTime;
            private double averageBatchSize;
            private long activeConnections;
            private long totalEvents;
            private long totalAggregates;
            private boolean isHealthy;
            private Instant lastHealthCheck;

            public Builder eventsAppended(double eventsAppended) { this.eventsAppended = eventsAppended; return this; }
            public Builder eventsLoaded(double eventsLoaded) { this.eventsLoaded = eventsLoaded; return this; }
            public Builder totalErrors(double totalErrors) { this.totalErrors = totalErrors; return this; }
            public Builder concurrencyConflicts(double concurrencyConflicts) { this.concurrencyConflicts = concurrencyConflicts; return this; }
            public Builder averageAppendTime(double averageAppendTime) { this.averageAppendTime = averageAppendTime; return this; }
            public Builder averageLoadTime(double averageLoadTime) { this.averageLoadTime = averageLoadTime; return this; }
            public Builder averageBatchSize(double averageBatchSize) { this.averageBatchSize = averageBatchSize; return this; }
            public Builder activeConnections(long activeConnections) { this.activeConnections = activeConnections; return this; }
            public Builder totalEvents(long totalEvents) { this.totalEvents = totalEvents; return this; }
            public Builder totalAggregates(long totalAggregates) { this.totalAggregates = totalAggregates; return this; }
            public Builder isHealthy(boolean isHealthy) { this.isHealthy = isHealthy; return this; }
            public Builder lastHealthCheck(Instant lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; return this; }

            public PerformanceSummary build() {
                return new PerformanceSummary(this);
            }
        }
    }
}
