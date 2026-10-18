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

package org.fireflyframework.eventsourcing.aggregate;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AggregateRoot base class.
 */
class AggregateRootTest {

    @Test
    void testAggregateCreation() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId, "12345", BigDecimal.valueOf(1000));
        
        assertEquals(accountId, account.getId());
        assertEquals("Account", account.getAggregateType());
        assertEquals(0L, account.getVersion()); // One event applied: version -1 → 0
        assertEquals(1, account.getUncommittedEventCount());
        assertTrue(account.hasUncommittedEvents());
        assertEquals("12345", account.getAccountNumber());
        assertEquals(BigDecimal.valueOf(1000), account.getBalance());
    }

    @Test
    void testEventApplication() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId, "12345", BigDecimal.valueOf(1000));
        
        // Clear initial creation event
        account.markEventsAsCommitted();
        assertEquals(0, account.getUncommittedEventCount());
        
        // Apply business operations
        account.withdraw(BigDecimal.valueOf(100));
        account.deposit(BigDecimal.valueOf(50));
        
        assertEquals(BigDecimal.valueOf(950), account.getBalance());
        assertEquals(2L, account.getVersion()); // 3 events applied: version -1 → 2
        assertEquals(2, account.getUncommittedEventCount()); // Only withdraw and deposit
        
        List<Event> uncommittedEvents = account.getUncommittedEvents();
        assertEquals(2, uncommittedEvents.size());
        assertTrue(uncommittedEvents.get(0) instanceof MoneyWithdrawnEvent);
        assertTrue(uncommittedEvents.get(1) instanceof MoneyDepositedEvent);
    }

    @Test
    void testEventCommitting() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId, "12345", BigDecimal.valueOf(1000));
        
        account.withdraw(BigDecimal.valueOf(100));
        assertTrue(account.hasUncommittedEvents());
        assertEquals(2, account.getUncommittedEventCount());
        
        account.markEventsAsCommitted();
        assertFalse(account.hasUncommittedEvents());
        assertEquals(0, account.getUncommittedEventCount());
        assertEquals(1L, account.getVersion()); // Version remains the same after commit
    }

    @Test
    void testLoadFromHistory() {
        UUID accountId = UUID.randomUUID();
        
        // Create historical events
        AccountCreatedEvent createdEvent = new AccountCreatedEvent(accountId, "12345", BigDecimal.valueOf(1000));
        MoneyWithdrawnEvent withdrawEvent = new MoneyWithdrawnEvent(accountId, BigDecimal.valueOf(100));
        MoneyDepositedEvent depositEvent = new MoneyDepositedEvent(accountId, BigDecimal.valueOf(50));
        
        // Create event envelopes
        List<StoredEventEnvelope> history = List.of(
            StoredEventEnvelope.of(createdEvent, "Account", 1L, 100L),
            StoredEventEnvelope.of(withdrawEvent, "Account", 2L, 101L),
            StoredEventEnvelope.of(depositEvent, "Account", 3L, 102L)
        );
        
        // Reconstruct aggregate from history
        TestAccount account = new TestAccount(accountId);
        account.loadFromHistory(history);
        
        assertEquals(BigDecimal.valueOf(950), account.getBalance());
        assertEquals("12345", account.getAccountNumber());
        assertEquals(3L, account.getVersion());
        assertFalse(account.hasUncommittedEvents());
    }

    @Test
    void testInvalidEventApplication() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId, "12345", BigDecimal.valueOf(1000));
        
        // Try to apply event with different aggregate ID
        UUID differentId = UUID.randomUUID();
        AccountCreatedEvent invalidEvent = new AccountCreatedEvent(differentId, "67890", BigDecimal.valueOf(500));
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.applyChange(invalidEvent);
        });
    }

    @Test
    void testNullEventApplication() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId, "12345", BigDecimal.valueOf(1000));
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.applyChange(null);
        });
    }

    @Test
    void testInvalidHistoryLoading() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId);
        
        // Event with different aggregate ID
        UUID differentId = UUID.randomUUID();
        AccountCreatedEvent invalidEvent = new AccountCreatedEvent(differentId, "12345", BigDecimal.valueOf(1000));
        StoredEventEnvelope invalidEnvelope = StoredEventEnvelope.of(invalidEvent, "Account", 1L, 100L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.loadFromHistory(List.of(invalidEnvelope));
        });
    }

    @Test
    void testInvalidAggregateTypeInHistory() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId);
        
        AccountCreatedEvent event = new AccountCreatedEvent(accountId, "12345", BigDecimal.valueOf(1000));
        StoredEventEnvelope invalidEnvelope = StoredEventEnvelope.of(event, "DifferentType", 1L, 100L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.loadFromHistory(List.of(invalidEnvelope));
        });
    }

    @Test
    void testBusinessLogicValidation() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId, "12345", BigDecimal.valueOf(100));
        
        // Try to withdraw more than available balance
        assertThrows(IllegalArgumentException.class, () -> {
            account.withdraw(BigDecimal.valueOf(200));
        });
        
        // Try to deposit negative amount
        assertThrows(IllegalArgumentException.class, () -> {
            account.deposit(BigDecimal.valueOf(-50));
        });
    }

    @Test
    void testMissingEventHandler() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId);
        
        // Apply event that doesn't have a handler
        UnhandledEvent unhandledEvent = new UnhandledEvent(accountId);
        
        assertThrows(EventHandlerException.class, () -> {
            account.applyChange(unhandledEvent);
        });
    }

    @Test
    void testAggregateEquality() {
        UUID accountId = UUID.randomUUID();
        TestAccount account1 = new TestAccount(accountId, "12345", BigDecimal.valueOf(1000));
        TestAccount account2 = new TestAccount(accountId, "67890", BigDecimal.valueOf(500));
        TestAccount account3 = new TestAccount(UUID.randomUUID(), "12345", BigDecimal.valueOf(1000));
        
        assertEquals(account1, account2); // Same ID
        assertNotEquals(account1, account3); // Different ID
        assertEquals(account1.hashCode(), account2.hashCode());
    }

    @Test
    void testAggregateToString() {
        UUID accountId = UUID.randomUUID();
        TestAccount account = new TestAccount(accountId, "12345", BigDecimal.valueOf(1000));
        
        String toString = account.toString();
        assertTrue(toString.contains("TestAccount"));
        assertTrue(toString.contains(accountId.toString()));
        assertTrue(toString.contains("version=0"));
        assertTrue(toString.contains("uncommittedEvents=1"));
    }

    @Test
    void testInvalidAggregateConstruction() {
        // Null ID
        assertThrows(IllegalArgumentException.class, () -> {
            new TestAccount(null, "12345", BigDecimal.valueOf(1000));
        });
        
        // Null aggregate type
        assertThrows(IllegalArgumentException.class, () -> {
        new TestAccount(UUID.randomUUID(), null, BigDecimal.valueOf(1000), true);
        });
        
        // Empty aggregate type
        assertThrows(IllegalArgumentException.class, () -> {
        new TestAccount(UUID.randomUUID(), "", BigDecimal.valueOf(1000), true);
        });
    }

    // Test aggregate implementation
    public static class TestAccount extends AggregateRoot {
        private String accountNumber;
        private BigDecimal balance;
        
        // Constructor for new aggregates
        public TestAccount(UUID id, String accountNumber, BigDecimal initialBalance) {
            super(id, "Account");
            applyChange(new AccountCreatedEvent(id, accountNumber, initialBalance));
        }
        
        // Constructor for reconstruction
        public TestAccount(UUID id) {
            super(id, "Account");
        }
        
        // Test constructor with custom aggregate type
        public TestAccount(UUID id, String aggregateType, BigDecimal balance, boolean testConstructor) {
            super(id, aggregateType);
            this.balance = balance;
        }
        
        public void withdraw(BigDecimal amount) {
            if (balance.compareTo(amount) < 0) {
                throw new IllegalArgumentException("Insufficient funds");
            }
            applyChange(new MoneyWithdrawnEvent(getId(), amount));
        }
        
        public void deposit(BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            applyChange(new MoneyDepositedEvent(getId(), amount));
        }
        
        // Event handlers
        private void on(AccountCreatedEvent event) {
            this.accountNumber = event.getAccountNumber();
            this.balance = event.getInitialBalance();
        }
        
        private void on(MoneyWithdrawnEvent event) {
            this.balance = this.balance.subtract(event.getAmount());
        }
        
        private void on(MoneyDepositedEvent event) {
            this.balance = this.balance.add(event.getAmount());
        }
        
        // Getters
        public String getAccountNumber() { return accountNumber; }
        public BigDecimal getBalance() { return balance; }
    }
    
    // Test event implementations
    @JsonTypeName("account.created")
    public record AccountCreatedEvent(
            UUID aggregateId,
            String accountNumber,
            BigDecimal initialBalance
    ) implements Event {
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        @Override
        public String getEventType() {
            return "account.created";
        }
        
        public String getAccountNumber() {
            return accountNumber;
        }
        
        public BigDecimal getInitialBalance() {
            return initialBalance;
        }
    }
    
    @JsonTypeName("money.withdrawn")
    public record MoneyWithdrawnEvent(
            UUID aggregateId,
            BigDecimal amount
    ) implements Event {
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        @Override
        public String getEventType() {
            return "money.withdrawn";
        }
        
        public BigDecimal getAmount() {
            return amount;
        }
    }
    
    @JsonTypeName("money.deposited")
    public record MoneyDepositedEvent(
            UUID aggregateId,
            BigDecimal amount
    ) implements Event {
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        @Override
        public String getEventType() {
            return "money.deposited";
        }
        
        public BigDecimal getAmount() {
            return amount;
        }
    }
    
    @JsonTypeName("unhandled.event")
    public record UnhandledEvent(UUID aggregateId) implements Event {
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        @Override
        public String getEventType() {
            return "unhandled.event";
        }
    }
}