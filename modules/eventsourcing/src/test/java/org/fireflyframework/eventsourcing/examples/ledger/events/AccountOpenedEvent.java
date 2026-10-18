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

package org.fireflyframework.eventsourcing.examples.ledger.events;

import org.fireflyframework.eventsourcing.annotation.DomainEvent;
import org.fireflyframework.eventsourcing.domain.AbstractDomainEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain Event: Account was opened with initial deposit.
 * <p>
 * This event represents the creation of a new account ledger.
 * It captures all essential information needed to initialize the account state.
 * <p>
 * Example usage:
 * <pre>
 * AccountOpenedEvent event = AccountOpenedEvent.builder()
 *     .aggregateId(accountId)
 *     .accountNumber("ACC-2025-001")
 *     .accountType("CHECKING")
 *     .customerId(customerId)
 *     .initialDeposit(BigDecimal.valueOf(1000.00))
 *     .currency("USD")
 *     .userId("user-123")
 *     .source("mobile-app")
 *     .build();
 * </pre>
 */
@DomainEvent("account.opened")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountOpenedEvent extends AbstractDomainEvent {
    
    /**
     * The account number (e.g., "ACC-2025-001").
     */
    private String accountNumber;
    
    /**
     * The type of account (e.g., "CHECKING", "SAVINGS", "MONEY_MARKET").
     */
    private String accountType;
    
    /**
     * The customer who owns this account.
     */
    private UUID customerId;
    
    /**
     * The initial deposit amount when opening the account.
     */
    private BigDecimal initialDeposit;
    
    /**
     * The currency code (e.g., "USD", "EUR", "GBP").
     */
    private String currency;
}

