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

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Repository for Account Ledger read model.
 * <p>
 * This provides fast query access to account data without replaying events.
 * It's updated by the AccountLedgerProjectionService as events are processed.
 */
@Repository
public interface AccountLedgerRepository extends R2dbcRepository<AccountLedgerReadModel, UUID> {
    
    /**
     * Finds an account by account number.
     */
    Mono<AccountLedgerReadModel> findByAccountNumber(String accountNumber);
    
    /**
     * Finds all accounts for a customer.
     */
    Flux<AccountLedgerReadModel> findByCustomerId(UUID customerId);
    
    /**
     * Finds all active accounts (not frozen or closed).
     */
    @Query("SELECT * FROM account_ledger_read_model WHERE status = 'ACTIVE'")
    Flux<AccountLedgerReadModel> findAllActive();
    
    /**
     * Finds all frozen accounts.
     */
    @Query("SELECT * FROM account_ledger_read_model WHERE status = 'FROZEN'")
    Flux<AccountLedgerReadModel> findAllFrozen();
    
    /**
     * Finds all closed accounts.
     */
    @Query("SELECT * FROM account_ledger_read_model WHERE status = 'CLOSED'")
    Flux<AccountLedgerReadModel> findAllClosed();
    
    /**
     * Finds accounts with balance greater than specified amount.
     */
    @Query("SELECT * FROM account_ledger_read_model WHERE balance > :minBalance AND status = 'ACTIVE'")
    Flux<AccountLedgerReadModel> findByBalanceGreaterThan(BigDecimal minBalance);
    
    /**
     * Finds accounts by type.
     */
    Flux<AccountLedgerReadModel> findByAccountType(String accountType);
    
    /**
     * Finds accounts by currency.
     */
    Flux<AccountLedgerReadModel> findByCurrency(String currency);
}

