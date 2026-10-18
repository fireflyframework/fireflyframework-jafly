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

package org.fireflyframework.eventsourcing.projection;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer-based metrics tracking for event sourcing projections.
 * Provides counters, timers, and gauges for monitoring projection performance and health.
 */
@Slf4j
public class ProjectionMetrics extends FireflyMetricsSupport {

    private final String projectionName;

    private final AtomicLong currentPosition = new AtomicLong(0L);
    private final AtomicLong lagAmount = new AtomicLong(0L);
    private final AtomicLong lastProcessedAt = new AtomicLong(System.currentTimeMillis());

    public ProjectionMetrics(String projectionName, MeterRegistry meterRegistry) {
        super(meterRegistry, "eventsourcing");
        this.projectionName = projectionName;

        gauge("projection.position.current", currentPosition, AtomicLong::get,
                "projection", projectionName);
        gauge("projection.lag", lagAmount, AtomicLong::get,
                "projection", projectionName);
        gauge("projection.last.processed.seconds.ago", this,
                metrics -> (System.currentTimeMillis() - metrics.lastProcessedAt.get()) / 1000.0,
                "projection", projectionName);
    }

    /**
     * Records successful event processing.
     */
    public void recordEventProcessed() {
        counter("projection.events.processed", "projection", projectionName).increment();
        lastProcessedAt.set(System.currentTimeMillis());
    }

    /**
     * Records failed event processing.
     */
    public void recordEventFailed() {
        counter("projection.events.failed", "projection", projectionName).increment();
    }

    /**
     * Records projection reset.
     */
    public void recordProjectionReset() {
        counter("projection.resets", "projection", projectionName).increment();
        currentPosition.set(0L);
        lagAmount.set(0L);
    }

    /**
     * Records time taken to process an event.
     */
    public Timer.Sample startEventProcessingTimer() {
        MeterRegistry reg = registry();
        return reg != null ? Timer.start(reg) : null;
    }

    /**
     * Updates current position and lag metrics.
     */
    public void updatePosition(long position, long globalSequence) {
        currentPosition.set(position);
        lagAmount.set(Math.max(0, globalSequence - position));
    }

    /**
     * Gets a timer for recording event processing duration.
     */
    public Timer getEventProcessingTimer() {
        return timer("projection.event.processing.duration", "projection", projectionName);
    }

    /**
     * Gets current processing rate (events per second) over the last minute.
     */
    public double getProcessingRate() {
        return counter("projection.events.processed", "projection", projectionName).count() / 60.0;
    }

    /**
     * Gets current position.
     */
    public long getCurrentPosition() {
        return currentPosition.get();
    }

    /**
     * Gets current lag amount.
     */
    public long getLagAmount() {
        return lagAmount.get();
    }

    /**
     * Gets the projection name.
     */
    public String getProjectionName() {
        return projectionName;
    }
}
