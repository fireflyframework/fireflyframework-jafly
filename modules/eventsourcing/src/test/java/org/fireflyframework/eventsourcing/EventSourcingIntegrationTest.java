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

package org.fireflyframework.eventsourcing;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.fireflyframework.eventsourcing.aggregate.AggregateRoot;
import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating the event sourcing library functionality.
 */
class EventSourcingIntegrationTest {

    @Test
    void testBasicEventSourcingFlow() {
        // Create a new account aggregate
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "123456", BigDecimal.valueOf(1000.00));
        
        // Perform some business operations
        account.withdraw(BigDecimal.valueOf(100.00));
        account.deposit(BigDecimal.valueOf(50.00));
        
        // Verify state changes
        assertEquals(BigDecimal.valueOf(950.00), account.getBalance());
        assertEquals(3, account.getUncommittedEventCount()); // 1 for creation + 2 business operations
        assertTrue(account.hasUncommittedEvents());
        
        // Verify events were generated
        var uncommittedEvents = account.getUncommittedEvents();
        assertEquals(3, uncommittedEvents.size());
        
        assertTrue(uncommittedEvents.get(0) instanceof AccountCreatedEvent);
        assertTrue(uncommittedEvents.get(1) instanceof MoneyWithdrawnEvent);
        assertTrue(uncommittedEvents.get(2) instanceof MoneyDepositedEvent);
        
        MoneyWithdrawnEvent withdrawEvent = (MoneyWithdrawnEvent) uncommittedEvents.get(1);
        assertEquals(BigDecimal.valueOf(100.00), withdrawEvent.getAmount());
        
        MoneyDepositedEvent depositEvent = (MoneyDepositedEvent) uncommittedEvents.get(2);
        assertEquals(BigDecimal.valueOf(50.00), depositEvent.getAmount());
    }

    @Test
    void testAggregateReconstruction() {
        UUID accountId = UUID.randomUUID();
        
        // Create events that represent the history
        var accountCreatedEvent = new AccountCreatedEvent(accountId, "123456", BigDecimal.valueOf(1000.00));
        var withdrawEvent = new MoneyWithdrawnEvent(accountId, BigDecimal.valueOf(100.00));
        var depositEvent = new MoneyDepositedEvent(accountId, BigDecimal.valueOf(50.00));
        
        // Reconstruct aggregate from events would typically involve EventEnvelopes
        // This is a simplified test showing the concept
        Account account = new Account(accountId, "123456", BigDecimal.ZERO);
        
        // Create event envelopes for loading from history
        var envelopes = List.of(
            StoredEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(accountId)
                .aggregateType("Account")
                .aggregateVersion(1L)
                .globalSequence(1L)
                .event(accountCreatedEvent)
                .eventType(accountCreatedEvent.getEventType())
                .createdAt(accountCreatedEvent.getEventTimestamp())
                .build(),
            StoredEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(accountId)
                .aggregateType("Account")
                .aggregateVersion(2L)
                .globalSequence(2L)
                .event(withdrawEvent)
                .eventType(withdrawEvent.getEventType())
                .createdAt(withdrawEvent.getEventTimestamp())
                .build(),
            StoredEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(accountId)
                .aggregateType("Account")
                .aggregateVersion(3L)
                .globalSequence(3L)
                .event(depositEvent)
                .eventType(depositEvent.getEventType())
                .createdAt(depositEvent.getEventTimestamp())
                .build()
        );
        
        // Load from history instead of manual apply
        account.loadFromHistory(envelopes);
        
        // Verify final state (loadFromHistory already clears uncommitted events)
        assertEquals(BigDecimal.valueOf(950.00), account.getBalance());
        assertEquals("123456", account.getAccountNumber());
        assertFalse(account.hasUncommittedEvents());
    }

    // Example aggregate implementation
    public static class Account extends AggregateRoot {
        private String accountNumber;
        private BigDecimal balance;
        
        // Constructor for new aggregates
        public Account(UUID id, String accountNumber, BigDecimal initialBalance) {
            super(id, "Account");
            applyChange(new AccountCreatedEvent(id, accountNumber, initialBalance));
        }
        
        // Constructor for reconstruction
        public Account(UUID id) {
            super(id, "Account");
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
    
    // Example event implementations
    @JsonTypeName("account.created")
    public record AccountCreatedEvent(
            UUID aggregateId,
            String accountNumber,
            BigDecimal initialBalance
    ) implements Event {
        @Override
        public String getEventType() {
            return "account.created";
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        public String getAccountNumber() {
            return accountNumber;
        }
        
        public BigDecimal getInitialBalance() {
            return initialBalance;
        }
        
        @Override
        public Map<String, Object> getMetadata() {
            return Map.of("source", "test", "version", "1.0");
        }
    }
    
    @JsonTypeName("money.withdrawn")
    public record MoneyWithdrawnEvent(
            UUID aggregateId,
            BigDecimal amount
    ) implements Event {
        @Override
        public String getEventType() {
            return "money.withdrawn";
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        public BigDecimal getAmount() {
            return amount;
        }
        
        @Override
        public Instant getEventTimestamp() {
            return Instant.now();
        }
    }
    
    @JsonTypeName("money.deposited")
    public record MoneyDepositedEvent(
            UUID aggregateId,
            BigDecimal amount
    ) implements Event {
        @Override
        public String getEventType() {
            return "money.deposited";
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
        
        public BigDecimal getAmount() {
            return amount;
        }
    }
}