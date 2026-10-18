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

import org.fireflyframework.eventsourcing.annotation.EventSourcingTransactional;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.domain.EventStream;
import org.fireflyframework.eventsourcing.examples.ledger.snapshot.AccountLedgerSnapshot;
import org.fireflyframework.eventsourcing.logging.EventSourcingLoggingContext;
import org.fireflyframework.eventsourcing.snapshot.SnapshotStore;
import org.fireflyframework.eventsourcing.store.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing Account Ledger aggregates.
 * <p>
 * This service demonstrates the complete event sourcing pattern:
 * - Creating new aggregates and persisting events
 * - Loading aggregates from event history
 * - Executing commands and saving new events
 * - Using snapshots for performance optimization
 * - Handling concurrency conflicts with optimistic locking
 * - Automatic event publishing via @EventSourcingTransactional
 * <p>
 * Example usage:
 * <pre>
 * // Create new account
 * AccountLedger account = accountLedgerService.openAccount(
 *     "ACC-2025-001", "CHECKING", customerId,
 *     BigDecimal.valueOf(1000.00), "USD"
 * ).block();
 *
 * // Make deposit
 * accountLedgerService.deposit(
 *     accountId, BigDecimal.valueOf(500.00),
 *     "Wire Transfer", "REF-001", "user-123"
 * ).block();
 *
 * // Get current state
 * AccountLedger current = accountLedgerService.getAccount(accountId).block();
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLedgerService {
    
    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;
    
    /**
     * Opens a new account ledger.
     * <p>
     * This creates a new aggregate and persists the AccountOpenedEvent.
     * The @EventSourcingTransactional annotation ensures:
     * - Events are saved atomically
     * - Events are published to the event bus
     * - Transaction is committed before publishing
     *
     * @param accountNumber the account number
     * @param accountType the account type (CHECKING, SAVINGS, etc.)
     * @param customerId the customer who owns this account
     * @param initialDeposit the initial deposit amount
     * @param currency the currency code (USD, EUR, etc.)
     * @return the created account ledger
     */
    @EventSourcingTransactional
    public Mono<AccountLedger> openAccount(String accountNumber, String accountType,
                                          UUID customerId, BigDecimal initialDeposit,
                                          String currency) {
        UUID accountId = UUID.randomUUID();
        
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setUserId(customerId.toString());
        
        log.info("Opening new account: accountNumber={}, type={}, customerId={}, initialDeposit={}, currency={}",
                accountNumber, accountType, customerId, initialDeposit, currency);
        
        return Mono.fromCallable(() -> new AccountLedger(
                    accountId, accountNumber, accountType, customerId, initialDeposit, currency
                ))
                .flatMap(account -> eventStore.appendEvents(
                        accountId,
                        "AccountLedger",
                        account.getUncommittedEvents(),
                        0L
                    )
                    .doOnSuccess(stream -> {
                        account.markEventsAsCommitted();
                        log.info("Account opened successfully: accountId={}, version={}", accountId, stream.getCurrentVersion());
                    })
                    .thenReturn(account)
                );
    }
    
    /**
     * Deposits money into an account.
     * <p>
     * This loads the account from event history, executes the deposit command,
     * and saves the new MoneyDepositedEvent.
     *
     * @param accountId the account identifier
     * @param amount the amount to deposit
     * @param description transaction description
     * @param reference external reference
     * @param userId user making the deposit
     * @return the updated account ledger
     */
    @EventSourcingTransactional(retryOnConcurrencyConflict = true, maxRetries = 3)
    public Mono<AccountLedger> deposit(UUID accountId, BigDecimal amount,
                                      String description, String reference, String userId) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setUserId(userId);
        EventSourcingLoggingContext.setOperation("deposit");
        
        log.info("Processing deposit: accountId={}, amount={}, reference={}", accountId, amount, reference);
        
        return loadAccount(accountId)
                .doOnNext(account -> account.deposit(amount, description, reference, userId))
                .flatMap(this::saveAccount);
    }
    
    /**
     * Withdraws money from an account.
     *
     * @param accountId the account identifier
     * @param amount the amount to withdraw
     * @param description transaction description
     * @param reference external reference
     * @param userId user making the withdrawal
     * @return the updated account ledger
     */
    @EventSourcingTransactional(retryOnConcurrencyConflict = true, maxRetries = 3)
    public Mono<AccountLedger> withdraw(UUID accountId, BigDecimal amount,
                                       String description, String reference, String userId) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setUserId(userId);
        EventSourcingLoggingContext.setOperation("withdraw");
        
        log.info("Processing withdrawal: accountId={}, amount={}, reference={}", accountId, amount, reference);
        
        return loadAccount(accountId)
                .doOnNext(account -> account.withdraw(amount, description, reference, userId))
                .flatMap(this::saveAccount);
    }
    
    /**
     * Freezes an account.
     *
     * @param accountId the account identifier
     * @param reason reason for freezing
     * @param userId user freezing the account
     * @return the updated account ledger
     */
    @EventSourcingTransactional
    public Mono<AccountLedger> freezeAccount(UUID accountId, String reason, String userId) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setUserId(userId);
        EventSourcingLoggingContext.setOperation("freeze");
        
        log.info("Freezing account: accountId={}, reason={}", accountId, reason);
        
        return loadAccount(accountId)
                .doOnNext(account -> account.freeze(reason, userId))
                .flatMap(this::saveAccount);
    }
    
    /**
     * Unfreezes an account.
     *
     * @param accountId the account identifier
     * @param reason reason for unfreezing
     * @param userId user unfreezing the account
     * @return the updated account ledger
     */
    @EventSourcingTransactional
    public Mono<AccountLedger> unfreezeAccount(UUID accountId, String reason, String userId) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setUserId(userId);
        EventSourcingLoggingContext.setOperation("unfreeze");
        
        log.info("Unfreezing account: accountId={}, reason={}", accountId, reason);
        
        return loadAccount(accountId)
                .doOnNext(account -> account.unfreeze(reason, userId))
                .flatMap(this::saveAccount);
    }
    
    /**
     * Closes an account.
     *
     * @param accountId the account identifier
     * @param reason reason for closing
     * @param userId user closing the account
     * @return the updated account ledger
     */
    @EventSourcingTransactional
    public Mono<AccountLedger> closeAccount(UUID accountId, String reason, String userId) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setUserId(userId);
        EventSourcingLoggingContext.setOperation("close");
        
        log.info("Closing account: accountId={}, reason={}", accountId, reason);
        
        return loadAccount(accountId)
                .doOnNext(account -> account.close(reason, userId))
                .flatMap(this::saveAccount);
    }
    
    /**
     * Gets the current state of an account.
     *
     * @param accountId the account identifier
     * @return the account ledger
     */
    public Mono<AccountLedger> getAccount(UUID accountId) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        return loadAccount(accountId);
    }
    
    /**
     * Gets the state of an account at a specific point in time (time travel).
     *
     * @param accountId the account identifier
     * @param pointInTime the point in time
     * @return the account ledger at that point in time
     */
    public Mono<AccountLedger> getAccountAtTime(UUID accountId, Instant pointInTime) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setOperation("time-travel");

        log.info("Loading account at point in time: accountId={}, pointInTime={}", accountId, pointInTime);

        return eventStore.loadEventStream(accountId, "AccountLedger")
                .map(stream -> {
                    AccountLedger account = new AccountLedger(accountId);
                    // Filter events up to the point in time
                    List<StoredEventEnvelope> filteredEvents = stream.getEvents().stream()
                            .filter(envelope -> !envelope.getCreatedAt().isAfter(pointInTime))
                            .collect(java.util.stream.Collectors.toList());
                    account.loadFromHistory(filteredEvents);
                    return account;
                });
    }
    
    /**
     * Creates a snapshot of an account for performance optimization.
     *
     * @param accountId the account identifier
     * @return completion signal
     */
    public Mono<Void> createSnapshot(UUID accountId) {
        EventSourcingLoggingContext.setAggregateContext(accountId, "AccountLedger");
        EventSourcingLoggingContext.setOperation("create-snapshot");
        
        log.info("Creating snapshot for account: accountId={}", accountId);
        
        return loadAccount(accountId)
                .flatMap(account -> {
                    AccountLedgerSnapshot snapshot = account.createSnapshot();
                    return snapshotStore.saveSnapshot(snapshot)
                            .doOnSuccess(v -> log.info("Snapshot created: accountId={}, version={}", 
                                    accountId, snapshot.getVersion()));
                });
    }
    
    // Private helper methods
    
    private Mono<AccountLedger> loadAccount(UUID accountId) {
        // Try to load from snapshot first for better performance
        return snapshotStore.loadLatestSnapshot(accountId, "AccountLedger")
                .cast(AccountLedgerSnapshot.class)
                .flatMap(snapshot -> loadAccountFromSnapshot(accountId, snapshot))
                .switchIfEmpty(loadAccountFromEvents(accountId));
    }
    
    private Mono<AccountLedger> loadAccountFromSnapshot(UUID accountId, AccountLedgerSnapshot snapshot) {
        log.debug("Loading account from snapshot: accountId={}, snapshotVersion={}", accountId, snapshot.getVersion());

        return eventStore.loadEventStream(accountId, "AccountLedger", snapshot.getVersion())
                .map(stream -> {
                    AccountLedger account = AccountLedger.fromSnapshot(snapshot);
                    account.loadFromHistory(stream.getEvents());
                    log.debug("Account loaded from snapshot: accountId={}, currentVersion={}, eventsReplayed={}",
                            accountId, account.getCurrentVersion(), stream.getEvents().size());
                    return account;
                });
    }

    private Mono<AccountLedger> loadAccountFromEvents(UUID accountId) {
        log.debug("Loading account from events: accountId={}", accountId);

        return eventStore.loadEventStream(accountId, "AccountLedger")
                .map(stream -> {
                    AccountLedger account = new AccountLedger(accountId);
                    account.loadFromHistory(stream.getEvents());
                    log.debug("Account loaded from events: accountId={}, version={}, eventsReplayed={}",
                            accountId, account.getCurrentVersion(), stream.getEvents().size());
                    return account;
                });
    }

    private Mono<AccountLedger> saveAccount(AccountLedger account) {
        return eventStore.appendEvents(
                    account.getId(),
                    "AccountLedger",
                    account.getUncommittedEvents(),
                    account.getCurrentVersion() - account.getUncommittedEventCount()
                )
                .doOnSuccess(stream -> {
                    account.markEventsAsCommitted();
                    log.debug("Account saved: accountId={}, newVersion={}", account.getId(), stream.getCurrentVersion());
                })
                .thenReturn(account);
    }
}

