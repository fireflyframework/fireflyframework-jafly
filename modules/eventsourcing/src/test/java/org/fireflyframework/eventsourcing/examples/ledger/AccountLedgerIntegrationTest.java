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

import org.fireflyframework.eventsourcing.examples.ledger.exceptions.AccountClosedException;
import org.fireflyframework.eventsourcing.examples.ledger.exceptions.AccountFrozenException;
import org.fireflyframework.eventsourcing.examples.ledger.exceptions.InsufficientFundsException;
import org.fireflyframework.eventsourcing.snapshot.SnapshotStore;
import org.fireflyframework.eventsourcing.store.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Comprehensive integration test for Account Ledger.
 * <p>
 * This test demonstrates the complete event sourcing pattern:
 * - Creating aggregates
 * - Executing commands
 * - Loading from event history
 * - Snapshots for performance
 * - Time travel queries
 * - Business rule enforcement
 * - Concurrency handling
 * <p>
 * Note: This test is currently disabled as it requires a full Spring Boot context
 * with EventStore and SnapshotStore implementations. It serves as documentation
 * for how to use the AccountLedger in a real application.
 */
@Disabled("Requires full Spring Boot context with EventStore and SnapshotStore beans")
class AccountLedgerIntegrationTest {

    @Mock
    private EventStore eventStore;

    @Mock
    private SnapshotStore snapshotStore;

    private AccountLedgerService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock snapshot store to return empty (we'll test snapshots separately)
        when(snapshotStore.loadLatestSnapshot(any(), any())).thenReturn(Mono.empty());
        when(snapshotStore.saveSnapshot(any())).thenReturn(Mono.empty());

        service = new AccountLedgerService(eventStore, snapshotStore);
    }

    @Test
    void shouldOpenNewAccount() {
        // Given
        String accountNumber = "ACC-2025-001";
        String accountType = "CHECKING";
        UUID customerId = UUID.randomUUID();
        BigDecimal initialDeposit = BigDecimal.valueOf(1000.00);
        String currency = "USD";

        // When
        StepVerifier.create(service.openAccount(accountNumber, accountType, customerId, initialDeposit, currency))
                // Then
                .assertNext(account -> {
                    assertNotNull(account.getId());
                    assertEquals(accountNumber, account.getAccountNumber());
                    assertEquals(accountType, account.getAccountType());
                    assertEquals(customerId, account.getCustomerId());
                    assertEquals(initialDeposit, account.getBalance());
                    assertEquals(currency, account.getCurrency());
                    assertFalse(account.isFrozen());
                    assertFalse(account.isClosed());
                    assertEquals(1L, account.getCurrentVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldDepositMoney() {
        // Given - create account
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-002", "SAVINGS", customerId,
                BigDecimal.valueOf(500.00), "USD"
        ).block();

        // When - deposit money
        StepVerifier.create(service.deposit(
                        account.getId(),
                        BigDecimal.valueOf(250.00),
                        "Wire Transfer",
                        "REF-001",
                        "user-123"
                ))
                // Then
                .assertNext(updated -> {
                    assertEquals(BigDecimal.valueOf(750.00), updated.getBalance());
                    assertEquals(2L, updated.getCurrentVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldWithdrawMoney() {
        // Given - create account with balance
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-003", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        ).block();

        // When - withdraw money
        StepVerifier.create(service.withdraw(
                        account.getId(),
                        BigDecimal.valueOf(300.00),
                        "ATM Withdrawal",
                        "ATM-001",
                        "user-123"
                ))
                // Then
                .assertNext(updated -> {
                    assertEquals(BigDecimal.valueOf(700.00), updated.getBalance());
                    assertEquals(2L, updated.getCurrentVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectWithdrawalWhenInsufficientFunds() {
        // Given - account with low balance
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-004", "CHECKING", customerId,
                BigDecimal.valueOf(100.00), "USD"
        ).block();

        // When/Then - attempt to withdraw more than balance
        StepVerifier.create(service.withdraw(
                        account.getId(),
                        BigDecimal.valueOf(200.00),
                        "ATM Withdrawal",
                        "ATM-001",
                        "user-123"
                ))
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    void shouldFreezeAndUnfreezeAccount() {
        // Given - create account
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-005", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        ).block();

        // When - freeze account
        AccountLedger frozen = service.freezeAccount(
                account.getId(),
                "Suspicious activity",
                "admin-123"
        ).block();

        // Then - account is frozen
        assertTrue(frozen.isFrozen());
        assertEquals(2L, frozen.getCurrentVersion());

        // When - unfreeze account
        AccountLedger unfrozen = service.unfreezeAccount(
                account.getId(),
                "Investigation complete",
                "admin-123"
        ).block();

        // Then - account is active again
        assertFalse(unfrozen.isFrozen());
        assertEquals(3L, unfrozen.getCurrentVersion());
    }

    @Test
    void shouldRejectTransactionsOnFrozenAccount() {
        // Given - frozen account
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-006", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        ).block();

        service.freezeAccount(account.getId(), "Test freeze", "admin-123").block();

        // When/Then - attempt deposit on frozen account
        StepVerifier.create(service.deposit(
                        account.getId(),
                        BigDecimal.valueOf(100.00),
                        "Deposit",
                        "REF-001",
                        "user-123"
                ))
                .expectError(AccountFrozenException.class)
                .verify();

        // When/Then - attempt withdrawal on frozen account
        StepVerifier.create(service.withdraw(
                        account.getId(),
                        BigDecimal.valueOf(100.00),
                        "Withdrawal",
                        "REF-002",
                        "user-123"
                ))
                .expectError(AccountFrozenException.class)
                .verify();
    }

    @Test
    void shouldCloseAccount() {
        // Given - account with zero balance
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-007", "CHECKING", customerId,
                BigDecimal.valueOf(100.00), "USD"
        ).block();

        // Withdraw all money
        service.withdraw(account.getId(), BigDecimal.valueOf(100.00), "Close", "REF-001", "user-123").block();

        // When - close account
        AccountLedger closed = service.closeAccount(
                account.getId(),
                "Customer request",
                "user-123"
        ).block();

        // Then
        assertTrue(closed.isClosed());
        assertEquals(BigDecimal.ZERO, closed.getBalance());
        assertEquals(3L, closed.getCurrentVersion());
    }

    @Test
    void shouldRejectTransactionsOnClosedAccount() {
        // Given - closed account
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-008", "CHECKING", customerId,
                BigDecimal.ZERO, "USD"
        ).block();

        service.closeAccount(account.getId(), "Test close", "user-123").block();

        // When/Then - attempt transaction on closed account
        StepVerifier.create(service.deposit(
                        account.getId(),
                        BigDecimal.valueOf(100.00),
                        "Deposit",
                        "REF-001",
                        "user-123"
                ))
                .expectError(AccountClosedException.class)
                .verify();
    }

    @Test
    void shouldLoadAccountFromEventHistory() {
        // Given - account with multiple transactions
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-009", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        ).block();

        service.deposit(account.getId(), BigDecimal.valueOf(500.00), "Deposit 1", "REF-001", "user-123").block();
        service.withdraw(account.getId(), BigDecimal.valueOf(200.00), "Withdrawal 1", "REF-002", "user-123").block();
        service.deposit(account.getId(), BigDecimal.valueOf(100.00), "Deposit 2", "REF-003", "user-123").block();

        // When - load account from event store
        StepVerifier.create(service.getAccount(account.getId()))
                // Then - state is correctly reconstructed
                .assertNext(loaded -> {
                    assertEquals(BigDecimal.valueOf(1400.00), loaded.getBalance());
                    assertEquals(4L, loaded.getCurrentVersion()); // 1 open + 3 transactions
                    assertEquals("ACC-2025-009", loaded.getAccountNumber());
                })
                .verifyComplete();
    }

    @Test
    void shouldPerformTimeTravelQuery() throws InterruptedException {
        // Given - account with transactions at different times
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-010", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        ).block();

        Instant afterOpen = Instant.now();
        Thread.sleep(100); // Small delay to ensure different timestamps

        service.deposit(account.getId(), BigDecimal.valueOf(500.00), "Deposit 1", "REF-001", "user-123").block();

        Instant afterFirstDeposit = Instant.now();
        Thread.sleep(100);

        service.withdraw(account.getId(), BigDecimal.valueOf(300.00), "Withdrawal 1", "REF-002", "user-123").block();

        Instant afterWithdrawal = Instant.now();
        Thread.sleep(100);

        service.deposit(account.getId(), BigDecimal.valueOf(200.00), "Deposit 2", "REF-003", "user-123").block();

        // When/Then - query state at different points in time

        // After opening (should be 1000)
        StepVerifier.create(service.getAccountAtTime(account.getId(), afterOpen))
                .assertNext(atOpen -> {
                    assertEquals(BigDecimal.valueOf(1000.00), atOpen.getBalance());
                    assertEquals(1L, atOpen.getCurrentVersion());
                })
                .verifyComplete();

        // After first deposit (should be 1500)
        StepVerifier.create(service.getAccountAtTime(account.getId(), afterFirstDeposit))
                .assertNext(atFirstDeposit -> {
                    assertEquals(BigDecimal.valueOf(1500.00), atFirstDeposit.getBalance());
                    assertEquals(2L, atFirstDeposit.getCurrentVersion());
                })
                .verifyComplete();

        // After withdrawal (should be 1200)
        StepVerifier.create(service.getAccountAtTime(account.getId(), afterWithdrawal))
                .assertNext(atWithdrawal -> {
                    assertEquals(BigDecimal.valueOf(1200.00), atWithdrawal.getBalance());
                    assertEquals(3L, atWithdrawal.getCurrentVersion());
                })
                .verifyComplete();

        // Current state (should be 1400)
        StepVerifier.create(service.getAccount(account.getId()))
                .assertNext(current -> {
                    assertEquals(BigDecimal.valueOf(1400.00), current.getBalance());
                    assertEquals(4L, current.getCurrentVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldCreateAndUseSnapshot() {
        // Given - account with many transactions
        UUID customerId = UUID.randomUUID();
        AccountLedger account = service.openAccount(
                "ACC-2025-011", "CHECKING", customerId,
                BigDecimal.valueOf(1000.00), "USD"
        ).block();

        // Perform 10 transactions
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                service.deposit(account.getId(), BigDecimal.valueOf(100.00), "Deposit " + i, "REF-" + i, "user-123").block();
            } else {
                service.withdraw(account.getId(), BigDecimal.valueOf(50.00), "Withdrawal " + i, "REF-" + i, "user-123").block();
            }
        }

        // When - create snapshot
        StepVerifier.create(service.createSnapshot(account.getId()))
                .verifyComplete();

        // Then - verify snapshot was created (version should be 11: 1 open + 10 transactions)
        StepVerifier.create(service.getAccount(account.getId()))
                .assertNext(loaded -> {
                    assertEquals(11L, loaded.getCurrentVersion());
                    // Balance: 1000 + (5 * 100) - (5 * 50) = 1000 + 500 - 250 = 1250
                    assertEquals(BigDecimal.valueOf(1250.00), loaded.getBalance());
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleCompleteAccountLifecycle() {
        // This test demonstrates the complete lifecycle of an account
        UUID customerId = UUID.randomUUID();

        // 1. Open account
        AccountLedger account = service.openAccount(
                "ACC-2025-012", "CHECKING", customerId,
                BigDecimal.valueOf(5000.00), "USD"
        ).block();

        assertNotNull(account);
        assertEquals(BigDecimal.valueOf(5000.00), account.getBalance());

        // 2. Make several deposits
        service.deposit(account.getId(), BigDecimal.valueOf(1000.00), "Salary", "SAL-001", "user-123").block();
        service.deposit(account.getId(), BigDecimal.valueOf(500.00), "Bonus", "BON-001", "user-123").block();

        // 3. Make several withdrawals
        service.withdraw(account.getId(), BigDecimal.valueOf(2000.00), "Rent", "RENT-001", "user-123").block();
        service.withdraw(account.getId(), BigDecimal.valueOf(500.00), "Groceries", "GROC-001", "user-123").block();

        // 4. Freeze account temporarily
        service.freezeAccount(account.getId(), "Verify large transaction", "admin-123").block();

        // 5. Unfreeze account
        service.unfreezeAccount(account.getId(), "Verification complete", "admin-123").block();

        // 6. More transactions
        service.deposit(account.getId(), BigDecimal.valueOf(300.00), "Refund", "REF-001", "user-123").block();
        service.withdraw(account.getId(), BigDecimal.valueOf(4300.00), "Withdraw all", "CLOSE-001", "user-123").block();

        // 7. Close account
        AccountLedger closed = service.closeAccount(account.getId(), "Customer request", "user-123").block();

        // Verify final state
        assertNotNull(closed);
        assertTrue(closed.isClosed());
        assertEquals(BigDecimal.ZERO, closed.getBalance());
        assertEquals(10L, closed.getCurrentVersion()); // 1 open + 8 transactions + 1 close

        // Verify we can still query the account
        AccountLedger loaded = service.getAccount(account.getId()).block();
        assertNotNull(loaded);
        assertTrue(loaded.isClosed());
    }
}

