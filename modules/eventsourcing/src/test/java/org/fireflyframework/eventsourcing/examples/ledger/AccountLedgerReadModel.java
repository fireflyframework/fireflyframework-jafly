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

package org.fireflyframework.eventsourcing.examples.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for Account Ledger.
 * <p>
 * This is a denormalized view optimized for queries.
 * It's built from events by the AccountLedgerProjectionService.
 * <p>
 * This demonstrates read/write separation in event sourcing:
 * - Writes go through AccountLedgerService → AccountLedger aggregate → Events
 * - Reads use this read model for fast lookups (no event replay needed)
 * <p>
 * Benefits:
 * - Fast queries without replaying events
 * - Optimized for specific query patterns
 * - Can have multiple read models for different use cases
 * - Eventual consistency is acceptable for reads
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("account_ledger_read_model")
public class AccountLedgerReadModel {
    
    @Id
    private UUID accountId;
    
    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private boolean frozen;
    private boolean closed;
    
    private Instant openedAt;
    private Instant lastTransactionAt;
    private Instant closedAt;
    
    private long version;
    private Instant lastUpdated;
    
    // Audit fields
    private String lastUpdatedBy;
    private String status; // ACTIVE, FROZEN, CLOSED
}

