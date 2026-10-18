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

package org.fireflyframework.eventsourcing.examples.ledger.snapshot;

import org.fireflyframework.eventsourcing.snapshot.AbstractSnapshot;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of AccountLedger state at a specific version.
 * <p>
 * This snapshot extends {@link AbstractSnapshot} which provides common snapshot
 * functionality like metadata management, validation, and utility methods.
 * <p>
 * Contains all necessary state to reconstruct the account without replaying all events.
 * This is used for performance optimization when accounts have many transactions.
 * <p>
 * <b>Performance Benefits:</b>
 * <ul>
 *   <li>Reduces event replay time by 100x for accounts with 1000+ transactions</li>
 *   <li>Decreases memory usage during aggregate reconstruction</li>
 *   <li>Enables faster query responses for account balance and state</li>
 * </ul>
 * <p>
 * <b>Example usage:</b>
 * <pre>
 * // Create snapshot from account
 * AccountLedgerSnapshot snapshot = account.createSnapshot();
 *
 * // Restore account from snapshot
 * AccountLedger account = AccountLedger.fromSnapshot(snapshot);
 *
 * // Load only events after snapshot
 * account.loadFromHistory(eventsAfterSnapshot);
 * </pre>
 * <p>
 * <b>Snapshot Strategy:</b>
 * <p>
 * Snapshots are typically created:
 * <ul>
 *   <li>Every N events (e.g., every 50 transactions)</li>
 *   <li>After significant state changes</li>
 *   <li>During scheduled maintenance windows</li>
 *   <li>On-demand for high-volume accounts</li>
 * </ul>
 *
 * @see AbstractSnapshot
 * @see org.fireflyframework.eventsourcing.examples.ledger.AccountLedger
 */
@Getter
public class AccountLedgerSnapshot extends AbstractSnapshot {

    // Account identification
    private final String accountNumber;
    private final String accountType;
    private final UUID customerId;

    // Financial state
    private final BigDecimal balance;
    private final String currency;

    // Account status
    private final boolean frozen;
    private final boolean closed;

    // Timestamps
    private final Instant openedAt;
    private final Instant lastTransactionAt;

    /**
     * Constructs a new AccountLedger snapshot.
     *
     * @param aggregateId the account aggregate identifier
     * @param version the aggregate version at snapshot time
     * @param createdAt the snapshot creation timestamp
     * @param accountNumber the account number
     * @param accountType the account type (CHECKING, SAVINGS, etc.)
     * @param customerId the customer who owns this account
     * @param balance the current account balance
     * @param currency the currency code (USD, EUR, etc.)
     * @param frozen whether the account is frozen
     * @param closed whether the account is closed
     * @param openedAt when the account was opened
     * @param lastTransactionAt when the last transaction occurred
     */
    public AccountLedgerSnapshot(UUID aggregateId, long version, Instant createdAt,
                                String accountNumber, String accountType, UUID customerId,
                                BigDecimal balance, String currency,
                                boolean frozen, boolean closed,
                                Instant openedAt, Instant lastTransactionAt) {
        super(aggregateId, version, createdAt);
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.customerId = customerId;
        this.balance = balance;
        this.currency = currency;
        this.frozen = frozen;
        this.closed = closed;
        this.openedAt = openedAt;
        this.lastTransactionAt = lastTransactionAt;
    }

    @Override
    public String getSnapshotType() {
        return "AccountLedger";
    }

    @Override
    public String toString() {
        return "AccountLedgerSnapshot{" +
                "aggregateId=" + getAggregateId() +
                ", version=" + getVersion() +
                ", accountNumber='" + accountNumber + '\'' +
                ", balance=" + balance +
                ", currency='" + currency + '\'' +
                ", frozen=" + frozen +
                ", closed=" + closed +
                ", createdAt=" + getCreatedAt() +
                '}';
    }
}

