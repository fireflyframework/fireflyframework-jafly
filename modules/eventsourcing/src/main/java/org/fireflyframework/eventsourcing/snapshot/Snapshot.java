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

import java.time.Instant;
import java.util.UUID;

/**
 * Interface representing a snapshot of aggregate state.
 * <p>
 * Snapshots are used to optimize aggregate reconstruction by capturing
 * the state at a specific point in time, reducing the number of events
 * that need to be replayed from the event store.
 * <p>
 * Snapshots contain:
 * - The complete aggregate state at a specific version
 * - Metadata about when and why the snapshot was taken
 * - Version information for consistency checking
 * <p>
 * Implementations should be:
 * - Serializable (support JSON/binary serialization)
 * - Immutable (state should not change after creation)
 * - Self-contained (include all necessary state information)
 * <p>
 * <b>Recommended Approach:</b>
 * <p>
 * Instead of implementing this interface directly, extend {@link AbstractSnapshot}
 * which provides common functionality and reduces boilerplate:
 * <pre>
 * {@code
 * @Getter
 * public class AccountLedgerSnapshot extends AbstractSnapshot {
 *     private final String accountNumber;
 *     private final BigDecimal balance;
 *     private final String currency;
 *
 *     public AccountLedgerSnapshot(UUID aggregateId, long version, Instant createdAt,
 *                                 String accountNumber, BigDecimal balance, String currency) {
 *         super(aggregateId, version, createdAt);
 *         this.accountNumber = accountNumber;
 *         this.balance = balance;
 *         this.currency = currency;
 *     }
 *
 *     @Override
 *     public String getSnapshotType() {
 *         return "AccountLedger";
 *     }
 * }
 * }
 * </pre>
 *
 * @see AbstractSnapshot
 */
public interface Snapshot {

    /**
     * Gets the unique identifier of the aggregate this snapshot belongs to.
     *
     * @return the aggregate identifier, never null
     */
    UUID getAggregateId();

    /**
     * Gets the type of aggregate this snapshot represents.
     * This should match the aggregate type in the event stream.
     *
     * @return the snapshot type, never null or empty
     */
    String getSnapshotType();

    /**
     * Gets the version of the aggregate at the time this snapshot was taken.
     * This corresponds to the last event version included in the snapshot.
     *
     * @return the aggregate version at snapshot time
     */
    long getVersion();

    /**
     * Gets the timestamp when this snapshot was created.
     *
     * @return the creation timestamp, never null
     */
    Instant getCreatedAt();

    /**
     * Gets the version of the snapshot schema.
     * This is useful for snapshot evolution and migration when
     * the aggregate structure changes over time.
     *
     * @return the snapshot version, defaults to 1
     */
    default int getSnapshotVersion() {
        return 1;
    }

    /**
     * Gets the reason this snapshot was created.
     * This can be helpful for debugging and monitoring.
     *
     * @return the snapshot reason, may be null
     */
    default String getReason() {
        return null;
    }

    /**
     * Gets the size of this snapshot in bytes (if known).
     * This can be used for storage optimization and monitoring.
     *
     * @return the snapshot size in bytes, or null if unknown
     */
    default Long getSizeBytes() {
        return null;
    }

    /**
     * Checks if this snapshot is older than the specified number of days.
     *
     * @param days the number of days
     * @return true if the snapshot is older than the specified days
     */
    default boolean isOlderThan(int days) {
        return getCreatedAt().isBefore(Instant.now().minusSeconds(days * 24 * 60 * 60));
    }

    /**
     * Checks if this snapshot is for a specific aggregate version.
     *
     * @param version the version to check
     * @return true if the snapshot is for the specified version
     */
    default boolean isForVersion(long version) {
        return getVersion() == version;
    }

    /**
     * Checks if this snapshot is newer than the specified version.
     *
     * @param version the version to compare against
     * @return true if the snapshot version is greater than the specified version
     */
    default boolean isNewerThan(long version) {
        return getVersion() > version;
    }
}