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

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for snapshots that provides common functionality.
 * <p>
 * This class implements the {@link Snapshot} interface and provides default
 * implementations for common snapshot metadata. Subclasses only need to:
 * <ol>
 *   <li>Define their aggregate-specific state fields</li>
 *   <li>Implement {@link #getSnapshotType()} to return the aggregate type</li>
 *   <li>Optionally override {@link #getSnapshotVersion()} for schema versioning</li>
 * </ol>
 * <p>
 * <b>Benefits of using AbstractSnapshot:</b>
 * <ul>
 *   <li>Reduces boilerplate code in snapshot implementations</li>
 *   <li>Ensures consistent snapshot metadata handling</li>
 *   <li>Provides built-in validation and utility methods</li>
 *   <li>Supports snapshot evolution through schema versioning</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>
 * {@code
 * @Getter
 * public class AccountLedgerSnapshot extends AbstractSnapshot {
 *     // Aggregate-specific state
 *     private final String accountNumber;
 *     private final BigDecimal balance;
 *     private final String currency;
 *     private final boolean frozen;
 *     private final boolean closed;
 *     
 *     public AccountLedgerSnapshot(UUID aggregateId, long version, Instant createdAt,
 *                                 String accountNumber, BigDecimal balance, String currency,
 *                                 boolean frozen, boolean closed) {
 *         super(aggregateId, version, createdAt);
 *         this.accountNumber = accountNumber;
 *         this.balance = balance;
 *         this.currency = currency;
 *         this.frozen = frozen;
 *         this.closed = closed;
 *     }
 *     
 *     @Override
 *     public String getSnapshotType() {
 *         return "AccountLedger";
 *     }
 * }
 * }
 * </pre>
 * <p>
 * <b>Snapshot Evolution:</b>
 * <p>
 * When your aggregate structure changes, you can version your snapshots:
 * <pre>
 * {@code
 * @Override
 * public int getSnapshotVersion() {
 *     return 2; // Increment when schema changes
 * }
 * }
 * </pre>
 * <p>
 * This allows you to handle migration from old snapshot versions to new ones.
 *
 * @see Snapshot
 * @see org.fireflyframework.eventsourcing.aggregate.AggregateRoot
 */
@Getter
public abstract class AbstractSnapshot implements Snapshot {
    
    /**
     * The unique identifier of the aggregate this snapshot belongs to.
     */
    private final UUID aggregateId;
    
    /**
     * The version of the aggregate at the time this snapshot was taken.
     * This corresponds to the last event version included in the snapshot.
     */
    private final long version;
    
    /**
     * The timestamp when this snapshot was created.
     */
    private final Instant createdAt;
    
    /**
     * Optional reason for creating this snapshot.
     * Can be used for debugging and monitoring.
     */
    private final String reason;
    
    /**
     * Optional size of the snapshot in bytes.
     * Can be used for storage optimization and monitoring.
     */
    private final Long sizeBytes;
    
    /**
     * Constructs a new snapshot with the specified metadata.
     * <p>
     * This is the primary constructor that should be used by subclasses.
     *
     * @param aggregateId the aggregate identifier, must not be null
     * @param version the aggregate version at snapshot time, must be >= 0
     * @param createdAt the snapshot creation timestamp, must not be null
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    protected AbstractSnapshot(UUID aggregateId, long version, Instant createdAt) {
        this(aggregateId, version, createdAt, null, null);
    }
    
    /**
     * Constructs a new snapshot with the specified metadata and optional fields.
     *
     * @param aggregateId the aggregate identifier, must not be null
     * @param version the aggregate version at snapshot time, must be >= 0
     * @param createdAt the snapshot creation timestamp, must not be null
     * @param reason optional reason for creating this snapshot
     * @param sizeBytes optional size of the snapshot in bytes
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    protected AbstractSnapshot(UUID aggregateId, long version, Instant createdAt, 
                              String reason, Long sizeBytes) {
        if (aggregateId == null) {
            throw new IllegalArgumentException("Aggregate ID cannot be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version cannot be negative: " + version);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created timestamp cannot be null");
        }
        
        this.aggregateId = aggregateId;
        this.version = version;
        this.createdAt = createdAt;
        this.reason = reason;
        this.sizeBytes = sizeBytes;
    }
    
    /**
     * Gets the type of aggregate this snapshot represents.
     * <p>
     * Subclasses must implement this method to return the aggregate type name.
     * This should match the aggregate type used in the event stream.
     * <p>
     * Example:
     * <pre>
     * {@code
     * @Override
     * public String getSnapshotType() {
     *     return "AccountLedger";
     * }
     * }
     * </pre>
     *
     * @return the snapshot type, never null or empty
     */
    @Override
    public abstract String getSnapshotType();
    
    /**
     * Gets the version of the snapshot schema.
     * <p>
     * Override this method when you need to version your snapshot schema
     * for evolution and migration purposes.
     * <p>
     * Default implementation returns 1.
     *
     * @return the snapshot schema version, defaults to 1
     */
    @Override
    public int getSnapshotVersion() {
        return 1;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractSnapshot)) return false;
        AbstractSnapshot that = (AbstractSnapshot) o;
        return version == that.version &&
                Objects.equals(aggregateId, that.aggregateId) &&
                Objects.equals(getSnapshotType(), that.getSnapshotType());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(aggregateId, version, getSnapshotType());
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "aggregateId=" + aggregateId +
                ", type='" + getSnapshotType() + '\'' +
                ", version=" + version +
                ", createdAt=" + createdAt +
                (reason != null ? ", reason='" + reason + '\'' : "") +
                (sizeBytes != null ? ", sizeBytes=" + sizeBytes : "") +
                '}';
    }
}

