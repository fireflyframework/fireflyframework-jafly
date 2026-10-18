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

package org.fireflyframework.eventsourcing.store;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Statistics and metrics about the event store.
 * <p>
 * This class provides insights into the event store's performance and usage,
 * which can be used for monitoring, alerting, and capacity planning.
 */
@Data
@Builder
public class EventStoreStatistics {

    /**
     * Total number of events stored.
     */
    private final long totalEvents;

    /**
     * Total number of unique aggregates.
     */
    private final long totalAggregates;

    /**
     * Current global sequence number.
     */
    private final long currentGlobalSequence;

    /**
     * Timestamp when these statistics were collected.
     */
    @Builder.Default
    private final Instant collectedAt = Instant.now();

    /**
     * Storage size in bytes (if available).
     */
    private final Long storageSizeBytes;

    /**
     * Average events per aggregate.
     */
    public double getAverageEventsPerAggregate() {
        return totalAggregates > 0 ? (double) totalEvents / totalAggregates : 0.0;
    }

    /**
     * Breakdown of events by type.
     */
    private final Map<String, Long> eventsByType;

    /**
     * Breakdown of aggregates by type.
     */
    private final Map<String, Long> aggregatesByType;

    /**
     * Performance metrics (if available).
     */
    private final PerformanceMetrics performance;

    @Data
    @Builder
    public static class PerformanceMetrics {
        /**
         * Average append operation time in milliseconds.
         */
        private final Double averageAppendTimeMs;

        /**
         * Average load operation time in milliseconds.
         */
        private final Double averageLoadTimeMs;

        /**
         * Operations per second.
         */
        private final Double operationsPerSecond;

        /**
         * Current active connections.
         */
        private final Integer activeConnections;
    }
}