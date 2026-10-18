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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Interface for snapshot storage and retrieval operations.
 * <p>
 * The SnapshotStore provides persistence capabilities for aggregate snapshots,
 * enabling performance optimization of event sourcing systems by reducing
 * the number of events that need to be replayed during aggregate reconstruction.
 * <p>
 * Key responsibilities:
 * - Save and retrieve snapshots by aggregate ID and type
 * - Manage snapshot lifecycle (creation, cleanup, retention)
 * - Support snapshot versioning and evolution
 * - Provide query capabilities for snapshot management
 * <p>
 * The interface is designed to be reactive, using Project Reactor types
 * for non-blocking operations and scalability.
 */
public interface SnapshotStore {

    /**
     * Saves a snapshot to the store.
     * <p>
     * If a snapshot already exists for the same aggregate and version,
     * the existing snapshot should be replaced.
     *
     * @param snapshot the snapshot to save
     * @return a Mono that completes when the snapshot is saved
     * @throws SnapshotException if the save operation fails
     */
    Mono<Void> saveSnapshot(Snapshot snapshot);

    /**
     * Loads the latest snapshot for an aggregate.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @return a Mono that emits the latest snapshot, or empty if none exists
     */
    Mono<Snapshot> loadLatestSnapshot(UUID aggregateId, String snapshotType);

    /**
     * Loads a snapshot for a specific version or the latest one before that version.
     * <p>
     * This is useful when reconstructing an aggregate to a specific point in time.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @param maxVersion the maximum version to consider
     * @return a Mono that emits the snapshot, or empty if none exists
     */
    Mono<Snapshot> loadSnapshotAtOrBeforeVersion(UUID aggregateId, String snapshotType, long maxVersion);

    /**
     * Loads a snapshot for an exact version.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @param version the exact version
     * @return a Mono that emits the snapshot, or empty if none exists for that version
     */
    Mono<Snapshot> loadSnapshotAtVersion(UUID aggregateId, String snapshotType, long version);

    /**
     * Checks if a snapshot exists for an aggregate.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @return a Mono that emits true if a snapshot exists
     */
    Mono<Boolean> snapshotExists(UUID aggregateId, String snapshotType);

    /**
     * Gets the version of the latest snapshot for an aggregate.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @return a Mono that emits the latest snapshot version, or 0 if no snapshot exists
     */
    Mono<Long> getLatestSnapshotVersion(UUID aggregateId, String snapshotType);

    /**
     * Deletes a specific snapshot.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @param version the snapshot version to delete
     * @return a Mono that completes when the snapshot is deleted
     */
    Mono<Void> deleteSnapshot(UUID aggregateId, String snapshotType, long version);

    /**
     * Deletes all snapshots for an aggregate.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @return a Mono that completes when all snapshots are deleted
     */
    Mono<Void> deleteAllSnapshots(UUID aggregateId, String snapshotType);

    /**
     * Deletes snapshots older than the specified timestamp.
     * <p>
     * This is useful for snapshot cleanup and storage optimization.
     *
     * @param olderThan the cutoff timestamp
     * @return a Mono that emits the number of deleted snapshots
     */
    Mono<Long> deleteSnapshotsOlderThan(Instant olderThan);

    /**
     * Deletes snapshots older than the specified number of days.
     *
     * @param days the number of days
     * @return a Mono that emits the number of deleted snapshots
     */
    default Mono<Long> deleteSnapshotsOlderThan(int days) {
        Instant cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L);
        return deleteSnapshotsOlderThan(cutoff);
    }

    /**
     * Keeps only the specified number of latest snapshots per aggregate.
     * <p>
     * This helps manage storage by keeping only recent snapshots.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @param keepCount the number of snapshots to keep
     * @return a Mono that emits the number of deleted snapshots
     */
    Mono<Long> keepLatestSnapshots(UUID aggregateId, String snapshotType, int keepCount);

    /**
     * Lists all snapshots for an aggregate, ordered by version descending.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @return a Flux of snapshots ordered by version (newest first)
     */
    Flux<Snapshot> listSnapshots(UUID aggregateId, String snapshotType);

    /**
     * Lists snapshots within a version range.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @param fromVersion the minimum version (inclusive)
     * @param toVersion the maximum version (inclusive)
     * @return a Flux of snapshots within the version range
     */
    Flux<Snapshot> listSnapshots(UUID aggregateId, String snapshotType, long fromVersion, long toVersion);

    /**
     * Counts the number of snapshots for an aggregate.
     *
     * @param aggregateId the aggregate identifier
     * @param snapshotType the snapshot type
     * @return a Mono that emits the snapshot count
     */
    Mono<Long> countSnapshots(UUID aggregateId, String snapshotType);

    /**
     * Gets statistics about the snapshot store.
     *
     * @return a Mono that emits snapshot store statistics
     */
    Mono<SnapshotStatistics> getStatistics();

    /**
     * Performs a health check on the snapshot store.
     *
     * @return a Mono that emits true if the snapshot store is healthy
     */
    Mono<Boolean> isHealthy();

    /**
     * Optimizes the snapshot store (e.g., compaction, indexing).
     * <p>
     * The specific optimization depends on the implementation.
     *
     * @return a Mono that completes when optimization is finished
     */
    Mono<Void> optimize();
}