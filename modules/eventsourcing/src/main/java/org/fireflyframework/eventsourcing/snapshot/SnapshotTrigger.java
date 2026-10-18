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

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Determines when snapshots should be created and triggers their creation asynchronously.
 *
 * <p>After events are appended to an aggregate, the trigger checks whether the new version
 * crosses the configured frequency threshold (e.g., every 100 events). If so, a snapshot
 * save is initiated on a bounded-elastic scheduler to avoid blocking the main event-append path.</p>
 *
 * <p>Snapshot creation is fire-and-forget from the caller's perspective — failures are logged
 * but never propagate to the event-append operation.</p>
 */
@Slf4j
public class SnapshotTrigger {

    private final SnapshotStore snapshotStore;
    private final int frequency;

    /**
     * @param snapshotStore the store to save snapshots to
     * @param frequency     create a snapshot every N events (e.g., 100)
     */
    public SnapshotTrigger(SnapshotStore snapshotStore, int frequency) {
        this.snapshotStore = snapshotStore;
        this.frequency = frequency;
        log.info("SnapshotTrigger initialized with frequency: {} events", frequency);
    }

    /**
     * Checks whether a snapshot should be taken at the given aggregate version and,
     * if so, saves it asynchronously.
     *
     * @param snapshot the snapshot to potentially save
     * @return true if the snapshot was triggered, false if skipped
     */
    public boolean maybeTrigger(Snapshot snapshot) {
        if (snapshot.getVersion() > 0 && snapshot.getVersion() % frequency == 0) {
            log.info("Snapshot trigger fired for aggregate {} at version {} (frequency={})",
                    snapshot.getAggregateId(), snapshot.getVersion(), frequency);

            snapshotStore.saveSnapshot(snapshot)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(e -> log.error("Async snapshot save failed for aggregate {}: {}",
                            snapshot.getAggregateId(), e.getMessage()))
                    .subscribe();
            return true;
        }
        return false;
    }

    /**
     * Reactive variant — returns a Mono that completes after the save (or empty if no trigger).
     * Useful when callers want to chain snapshot creation into a reactive pipeline.
     */
    public Mono<Void> maybeTriggerReactive(Snapshot snapshot) {
        if (snapshot.getVersion() > 0 && snapshot.getVersion() % frequency == 0) {
            log.info("Snapshot trigger fired for aggregate {} at version {}",
                    snapshot.getAggregateId(), snapshot.getVersion());
            return snapshotStore.saveSnapshot(snapshot)
                    .doOnError(e -> log.error("Snapshot save failed for aggregate {}: {}",
                            snapshot.getAggregateId(), e.getMessage()))
                    .onErrorResume(e -> Mono.empty());
        }
        return Mono.empty();
    }

    public int getFrequency() {
        return frequency;
    }
}
