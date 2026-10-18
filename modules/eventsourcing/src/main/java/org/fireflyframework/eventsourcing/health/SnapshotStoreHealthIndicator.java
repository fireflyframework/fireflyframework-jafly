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

package org.fireflyframework.eventsourcing.health;

import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.eventsourcing.snapshot.SnapshotStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;

/**
 * Health indicator for the snapshot store.
 * Reports connectivity and basic statistics.
 */
@Slf4j
public class SnapshotStoreHealthIndicator implements HealthIndicator {

    private final SnapshotStore snapshotStore;
    private final Duration timeout;

    public SnapshotStoreHealthIndicator(SnapshotStore snapshotStore) {
        this(snapshotStore, Duration.ofSeconds(5));
    }

    public SnapshotStoreHealthIndicator(SnapshotStore snapshotStore, Duration timeout) {
        this.snapshotStore = snapshotStore;
        this.timeout = timeout;
    }

    @Override
    public Health health() {
        try {
            Boolean healthy = snapshotStore.isHealthy()
                    .timeout(timeout)
                    .block();

            if (Boolean.TRUE.equals(healthy)) {
                Health.Builder builder = Health.up();

                try {
                    var stats = snapshotStore.getStatistics()
                            .timeout(timeout)
                            .block();
                    if (stats != null) {
                        builder.withDetail("totalSnapshots", stats.getTotalSnapshots())
                               .withDetail("totalAggregatesWithSnapshots", stats.getTotalAggregatesWithSnapshots());
                        if (stats.getTotalStorageSizeBytes() != null) {
                            builder.withDetail("totalStorageBytes", stats.getTotalStorageSizeBytes());
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch snapshot statistics for health: {}", e.getMessage());
                }

                return builder.build();
            }

            return Health.down()
                    .withDetail("error", "Snapshot store health check returned false")
                    .build();
        } catch (Exception e) {
            log.error("Snapshot store health check failed", e);
            return Health.down()
                    .withDetail("error", "Health check failed: " + e.getMessage())
                    .build();
        }
    }
}
