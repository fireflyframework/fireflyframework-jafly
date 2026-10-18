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

package org.fireflyframework.eventsourcing.snapshot;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Statistics and metrics about the snapshot store.
 * <p>
 * This class provides insights into snapshot usage and performance,
 * which can be used for monitoring, optimization, and capacity planning.
 */
@Data
@Builder
public class SnapshotStatistics {

    /**
     * Total number of snapshots stored.
     */
    private final long totalSnapshots;

    /**
     * Total number of unique aggregates with snapshots.
     */
    private final long totalAggregatesWithSnapshots;

    /**
     * Timestamp when these statistics were collected.
     */
    @Builder.Default
    private final Instant collectedAt = Instant.now();

    /**
     * Total storage size in bytes (if available).
     */
    private final Long totalStorageSizeBytes;

    /**
     * Average snapshot size in bytes.
     */
    public Double getAverageSnapshotSizeBytes() {
        if (totalStorageSizeBytes == null || totalSnapshots == 0) {
            return null;
        }
        return (double) totalStorageSizeBytes / totalSnapshots;
    }

    /**
     * Average snapshots per aggregate.
     */
    public double getAverageSnapshotsPerAggregate() {
        return totalAggregatesWithSnapshots > 0 ? 
               (double) totalSnapshots / totalAggregatesWithSnapshots : 0.0;
    }

    /**
     * Breakdown of snapshots by type.
     */
    private final Map<String, Long> snapshotsByType;

    /**
     * Age distribution of snapshots.
     */
    private final AgeDistribution ageDistribution;

    /**
     * Performance metrics (if available).
     */
    private final PerformanceMetrics performance;

    @Data
    @Builder
    public static class AgeDistribution {
        /**
         * Snapshots created in the last hour.
         */
        private final long lastHour;

        /**
         * Snapshots created in the last day.
         */
        private final long lastDay;

        /**
         * Snapshots created in the last week.
         */
        private final long lastWeek;

        /**
         * Snapshots created in the last month.
         */
        private final long lastMonth;

        /**
         * Snapshots older than a month.
         */
        private final long olderThanMonth;
    }

    @Data
    @Builder
    public static class PerformanceMetrics {
        /**
         * Average save operation time in milliseconds.
         */
        private final Double averageSaveTimeMs;

        /**
         * Average load operation time in milliseconds.
         */
        private final Double averageLoadTimeMs;

        /**
         * Cache hit rate (if caching is enabled).
         */
        private final Double cacheHitRate;

        /**
         * Compression ratio (if compression is enabled).
         */
        private final Double compressionRatio;
    }
}