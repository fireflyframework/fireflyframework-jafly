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
import org.fireflyframework.eventsourcing.outbox.EventOutboxService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;

/**
 * Health indicator for the event outbox.
 * Reports pending and failed entry counts, with configurable thresholds.
 */
@Slf4j
public class OutboxHealthIndicator implements HealthIndicator {

    private final EventOutboxService outboxService;
    private final Duration timeout;
    private final long pendingWarningThreshold;
    private final long failedDownThreshold;

    public OutboxHealthIndicator(EventOutboxService outboxService) {
        this(outboxService, Duration.ofSeconds(5), 1000L, 100L);
    }

    public OutboxHealthIndicator(EventOutboxService outboxService, Duration timeout,
                                  long pendingWarningThreshold, long failedDownThreshold) {
        this.outboxService = outboxService;
        this.timeout = timeout;
        this.pendingWarningThreshold = pendingWarningThreshold;
        this.failedDownThreshold = failedDownThreshold;
    }

    @Override
    public Health health() {
        try {
            var stats = outboxService.getStatistics()
                    .timeout(timeout)
                    .block();

            if (stats == null) {
                return Health.unknown()
                        .withDetail("error", "Could not retrieve outbox statistics")
                        .build();
            }

            Health.Builder builder = Health.up()
                    .withDetail("pendingEntries", stats.pendingCount())
                    .withDetail("failedEntries", stats.failedCount())
                    .withDetail("completedEntries", stats.completedCount())
                    .withDetail("deadLetterEntries", stats.deadLetterCount());

            if (stats.failedCount() > failedDownThreshold) {
                return builder.down()
                        .withDetail("warning", "Failed entries (" + stats.failedCount()
                                + ") exceed threshold (" + failedDownThreshold + ")")
                        .build();
            }

            if (stats.pendingCount() > pendingWarningThreshold) {
                builder.withDetail("warning", "Pending entries (" + stats.pendingCount()
                        + ") exceed threshold (" + pendingWarningThreshold + ")");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Outbox health check failed", e);
            return Health.down()
                    .withDetail("error", "Health check failed: " + e.getMessage())
                    .build();
        }
    }
}
