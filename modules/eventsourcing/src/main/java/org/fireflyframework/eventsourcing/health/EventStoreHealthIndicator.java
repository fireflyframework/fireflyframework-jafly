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
import org.fireflyframework.eventsourcing.store.EventStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;

/**
 * Health indicator for the event store.
 * Reports R2DBC connectivity, event count, and latest global sequence.
 */
@Slf4j
public class EventStoreHealthIndicator implements HealthIndicator {

    private final EventStore eventStore;
    private final Duration timeout;

    public EventStoreHealthIndicator(EventStore eventStore) {
        this(eventStore, Duration.ofSeconds(5));
    }

    public EventStoreHealthIndicator(EventStore eventStore, Duration timeout) {
        this.eventStore = eventStore;
        this.timeout = timeout;
    }

    @Override
    public Health health() {
        try {
            Boolean healthy = eventStore.isHealthy()
                    .timeout(timeout)
                    .block();

            if (Boolean.TRUE.equals(healthy)) {
                Health.Builder builder = Health.up();

                // Attempt to fetch statistics (non-critical)
                try {
                    var stats = eventStore.getStatistics()
                            .timeout(timeout)
                            .block();
                    if (stats != null) {
                        builder.withDetail("totalEvents", stats.getTotalEvents())
                               .withDetail("totalAggregates", stats.getTotalAggregates())
                               .withDetail("currentGlobalSequence", stats.getCurrentGlobalSequence());
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch event store statistics for health: {}", e.getMessage());
                }

                return builder.build();
            }

            return Health.down()
                    .withDetail("error", "Event store health check returned false")
                    .build();
        } catch (Exception e) {
            log.error("Event store health check failed", e);
            return Health.down()
                    .withDetail("error", "Health check failed: " + e.getMessage())
                    .build();
        }
    }
}
