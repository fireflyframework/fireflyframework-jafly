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

import org.fireflyframework.eventsourcing.aggregate.AggregateRoot;
import org.fireflyframework.eventsourcing.examples.ledger.events.*;
import org.fireflyframework.eventsourcing.examples.ledger.exceptions.*;
import org.fireflyframework.eventsourcing.examples.ledger.snapshot.AccountLedgerSnapshot;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Account Ledger Aggregate Root.
 * <p>
 * This aggregate represents a bank account ledger that tracks all financial transactions.
 * It enforces business rules and maintains the current state derived from events.
 * <p>
 * Key responsibilities:
 * - Validate business rules before generating events
 * - Maintain account balance and status
 * - Prevent invalid operations (overdrafts, frozen account transactions, etc.)
 * - Generate domain events for all state changes
 * <p>
 * Example usage:
 * <pre>
 * // Create new account
 * AccountLedger account = new AccountLedger(
 *     accountId, "ACC-2025-001", "CHECKING", customerId,
 *     BigDecimal.valueOf(1000.00), "USD"
 * );
 *
 * // Make transactions
 * account.deposit(BigDecimal.valueOf(500.00), "Wire Transfer", "REF-001", "user-123");
 * account.withdraw(BigDecimal.valueOf(200.00), "ATM", "ATM-001", "user-123");
 *
 * // Get current state
 * BigDecimal balance = account.getBalance(); // 1300.00
 * long version = account.getVersion(); // 3
 * </pre>
 */
@Getter
public class AccountLedger extends AggregateRoot {
    
    // Current state (derived from events)
    private String accountNumber;
    private String accountType;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private boolean frozen;
    private boolean closed;
    private Instant openedAt;
    private Instant lastTransactionAt;
    
    /**
     * Constructor for reconstruction from events.
     * Used when loading an existing account from the event store.
     *
     * @param id the aggregate identifier
     */
    public AccountLedger(UUID id) {
        super(id, "AccountLedger");
        this.balance = BigDecimal.ZERO;
    }
    
    /**
     * Constructor for creating a new account (command).
     * Validates business rules and generates AccountOpenedEvent.
     *
     * @param id the aggregate identifier
     * @param accountNumber the account number
     * @param accountType the account type (CHECKING, SAVINGS, etc.)
     * @param customerId the customer who owns this account
     * @param initialDeposit the initial deposit amount
     * @param currency the currency code (USD, EUR, etc.)
     */
    public AccountLedger(UUID id, String accountNumber, String accountType,
                        UUID customerId, BigDecimal initialDeposit, String currency) {
        super(id, "AccountLedger");
        
        // Validate business rules
        validateAccountOpening(accountNumber, accountType, customerId, initialDeposit, currency);
        
        // Generate event
        applyChange(AccountOpenedEvent.builder()
                .aggregateId(id)
                .accountNumber(accountNumber)
                .accountType(accountType)
                .customerId(customerId)
                .initialDeposit(initialDeposit)
                .currency(currency)
                .build());
    }
    
    /**
     * Deposits money into the account.
     *
     * @param amount the amount to deposit
     * @param source the source of the deposit
     * @param reference external reference number
     * @param depositedBy user who made the deposit
     * @throws AccountClosedException if the account is closed
     * @throws InvalidAmountException if the amount is not positive
     */
    public void deposit(BigDecimal amount, String source, String reference, String depositedBy) {
        // Validate business rules
        if (closed) {
            throw new AccountClosedException("Cannot deposit to closed account: " + accountNumber);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Deposit amount must be positive: " + amount);
        }
        
        // Generate event
        applyChange(MoneyDepositedEvent.builder()
                .aggregateId(getId())
                .amount(amount)
                .source(source)
                .reference(reference)
                .depositedBy(depositedBy)
                .build());
    }
    
    /**
     * Withdraws money from the account.
     *
     * @param amount the amount to withdraw
     * @param destination the destination of the withdrawal
     * @param reference external reference number
     * @param withdrawnBy user who made the withdrawal
     * @throws AccountClosedException if the account is closed
     * @throws AccountFrozenException if the account is frozen
     * @throws InvalidAmountException if the amount is not positive
     * @throws InsufficientFundsException if the balance is insufficient
     */
    public void withdraw(BigDecimal amount, String destination, String reference, String withdrawnBy) {
        // Validate business rules
        if (closed) {
            throw new AccountClosedException("Cannot withdraw from closed account: " + accountNumber);
        }
        if (frozen) {
            throw new AccountFrozenException("Cannot withdraw from frozen account: " + accountNumber);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Withdrawal amount must be positive: " + amount);
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: balance=" + balance + ", requested=" + amount);
        }
        
        // Generate event
        applyChange(MoneyWithdrawnEvent.builder()
                .aggregateId(getId())
                .amount(amount)
                .destination(destination)
                .reference(reference)
                .withdrawnBy(withdrawnBy)
                .build());
    }
    
    /**
     * Freezes the account.
     *
     * @param reason the reason for freezing
     * @param frozenBy user who froze the account
     * @throws AccountClosedException if the account is already closed
     * @throws IllegalStateException if the account is already frozen
     */
    public void freeze(String reason, String frozenBy) {
        if (closed) {
            throw new AccountClosedException("Cannot freeze closed account: " + accountNumber);
        }
        if (frozen) {
            throw new IllegalStateException("Account is already frozen: " + accountNumber);
        }
        
        applyChange(AccountFrozenEvent.builder()
                .aggregateId(getId())
                .reason(reason)
                .frozenBy(frozenBy)
                .build());
    }
    
    /**
     * Unfreezes the account.
     *
     * @param reason the reason for unfreezing
     * @param unfrozenBy user who unfroze the account
     * @throws IllegalStateException if the account is not frozen
     */
    public void unfreeze(String reason, String unfrozenBy) {
        if (!frozen) {
            throw new IllegalStateException("Account is not frozen: " + accountNumber);
        }
        
        applyChange(AccountUnfrozenEvent.builder()
                .aggregateId(getId())
                .reason(reason)
                .unfrozenBy(unfrozenBy)
                .build());
    }
    
    /**
     * Closes the account.
     *
     * @param reason the reason for closing
     * @param closedBy user who closed the account
     * @throws IllegalStateException if the account is already closed
     */
    public void close(String reason, String closedBy) {
        if (closed) {
            throw new IllegalStateException("Account is already closed: " + accountNumber);
        }
        
        applyChange(AccountClosedEvent.builder()
                .aggregateId(getId())
                .reason(reason)
                .finalBalance(balance)
                .closedBy(closedBy)
                .build());
    }
    
    // Event handlers (how state changes)
    
    private void on(AccountOpenedEvent event) {
        this.accountNumber = event.getAccountNumber();
        this.accountType = event.getAccountType();
        this.customerId = event.getCustomerId();
        this.balance = event.getInitialDeposit();
        this.currency = event.getCurrency();
        this.frozen = false;
        this.closed = false;
        this.openedAt = event.getEventTimestamp();
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.getAmount());
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
        this.lastTransactionAt = event.getEventTimestamp();
    }
    
    private void on(AccountFrozenEvent event) {
        this.frozen = true;
    }
    
    private void on(AccountUnfrozenEvent event) {
        this.frozen = false;
    }
    
    private void on(AccountClosedEvent event) {
        this.closed = true;
    }
    
    // Validation methods
    
    private void validateAccountOpening(String accountNumber, String accountType,
                                       UUID customerId, BigDecimal initialDeposit, String currency) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        if (accountType == null || accountType.isBlank()) {
            throw new IllegalArgumentException("Account type is required");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (initialDeposit == null || initialDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }
    
    // Snapshot support
    
    /**
     * Creates a snapshot of the current state.
     *
     * @return snapshot containing all current state
     */
    public AccountLedgerSnapshot createSnapshot() {
        return new AccountLedgerSnapshot(
                getId(),
                getCurrentVersion(),
                Instant.now(),
                accountNumber,
                accountType,
                customerId,
                balance,
                currency,
                frozen,
                closed,
                openedAt,
                lastTransactionAt
        );
    }

    /**
     * Restores an account from a snapshot.
     * Note: After restoring from snapshot, you should call loadFromHistory()
     * with events after the snapshot version to get the current state.
     *
     * @param snapshot the snapshot to restore from
     * @return account with state from snapshot
     */
    public static AccountLedger fromSnapshot(AccountLedgerSnapshot snapshot) {
        AccountLedger account = new AccountLedger(snapshot.getAggregateId());
        account.accountNumber = snapshot.getAccountNumber();
        account.accountType = snapshot.getAccountType();
        account.customerId = snapshot.getCustomerId();
        account.balance = snapshot.getBalance();
        account.currency = snapshot.getCurrency();
        account.frozen = snapshot.isFrozen();
        account.closed = snapshot.isClosed();
        account.openedAt = snapshot.getOpenedAt();
        account.lastTransactionAt = snapshot.getLastTransactionAt();
        // Set version using protected method
        account.setCurrentVersion(snapshot.getVersion());
        return account;
    }
}

