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

package org.fireflyframework.eventsourcing.outbox;

import org.fireflyframework.eventsourcing.logging.EventSourcingLoggingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Background processor for Event Outbox entries.
 * <p>
 * This component runs scheduled tasks to:
 * - Poll the outbox table for pending entries
 * - Publish events to external systems
 * - Retry failed entries with exponential backoff
 * - Clean up old completed entries
 * <p>
 * The processor can be enabled/disabled via configuration:
 * <pre>
 * eventsourcing.outbox.processor.enabled=true
 * eventsourcing.outbox.processor.batch-size=100
 * eventsourcing.outbox.processor.cleanup-days=7
 * </pre>
 * <p>
 * Processing intervals:
 * - Pending entries: every 5 seconds
 * - Retry entries: every 30 seconds
 * - Cleanup: every hour
 * <p>
 * The processor uses Spring's @Scheduled annotation for simplicity.
 * For production systems with multiple instances, consider using:
 * - ShedLock for distributed locking
 * - Quartz for more advanced scheduling
 * - Dedicated outbox processor service
 */
@RequiredArgsConstructor
@Slf4j
public class EventOutboxProcessor {

    private final EventOutboxService outboxService;
    
    // Configuration defaults
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_CLEANUP_DAYS = 7;

    /**
     * Processes pending outbox entries.
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void processPendingEntries() {
        EventSourcingLoggingContext.setOperation("outbox-process-pending");
        log.debug("Starting scheduled processing of pending outbox entries");

        long startTime = System.currentTimeMillis();
        outboxService.processPendingEntries(DEFAULT_BATCH_SIZE)
                .doOnSuccess(count -> {
                    EventSourcingLoggingContext.setDuration(System.currentTimeMillis() - startTime);
                    if (count > 0) {
                        log.info("Processed {} pending outbox entries", count);
                    }
                })
                .doOnError(error -> log.error("Error in scheduled processing of pending entries", error))
                .doFinally(signalType -> EventSourcingLoggingContext.clearOperationContext())
                .subscribe();
    }

    /**
     * Processes failed entries that are ready for retry.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 20000)
    public void processRetryEntries() {
        EventSourcingLoggingContext.setOperation("outbox-process-retry");
        log.debug("Starting scheduled processing of retry outbox entries");

        long startTime = System.currentTimeMillis();
        outboxService.processRetryEntries(DEFAULT_BATCH_SIZE)
                .doOnSuccess(count -> {
                    EventSourcingLoggingContext.setDuration(System.currentTimeMillis() - startTime);
                    if (count > 0) {
                        log.info("Processed {} retry outbox entries", count);
                    }
                })
                .doOnError(error -> log.error("Error in scheduled processing of retry entries", error))
                .doFinally(signalType -> EventSourcingLoggingContext.clearOperationContext())
                .subscribe();
    }

    /**
     * Cleans up old completed entries.
     * Runs every hour.
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void cleanupCompletedEntries() {
        EventSourcingLoggingContext.setOperation("outbox-cleanup");
        log.debug("Starting scheduled cleanup of completed outbox entries");

        long startTime = System.currentTimeMillis();
        outboxService.cleanupCompletedEntries(DEFAULT_CLEANUP_DAYS)
                .doOnSuccess(count -> {
                    EventSourcingLoggingContext.setDuration(System.currentTimeMillis() - startTime);
                    if (count > 0) {
                        log.info("Cleaned up {} completed outbox entries", count);
                    }
                })
                .doOnError(error -> log.error("Error in scheduled cleanup of completed entries", error))
                .doFinally(signalType -> EventSourcingLoggingContext.clearOperationContext())
                .subscribe();
    }

    /**
     * Logs outbox statistics.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void logStatistics() {
        EventSourcingLoggingContext.setOperation("outbox-statistics");

        outboxService.getStatistics()
                .doOnSuccess(stats -> {
                    if (stats.pendingCount() > 0 || stats.failedCount() > 0 || stats.deadLetterCount() > 0) {
                        log.info("Outbox statistics - Pending: {}, Processing: {}, Completed: {}, Failed: {}, Dead Letter: {}",
                                stats.pendingCount(), stats.processingCount(), stats.completedCount(),
                                stats.failedCount(), stats.deadLetterCount());

                        if (stats.deadLetterCount() > 0) {
                            log.warn("WARNING: {} entries in dead letter queue (exceeded max retries)",
                                    stats.deadLetterCount());
                        }
                    }
                })
                .doOnError(error -> log.error("Error retrieving outbox statistics", error))
                .doFinally(signalType -> EventSourcingLoggingContext.clearOperationContext())
                .subscribe();
    }
}

