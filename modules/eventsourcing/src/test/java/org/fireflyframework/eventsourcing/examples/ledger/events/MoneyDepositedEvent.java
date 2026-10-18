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

/**
 * Domain Event: Money was deposited into the account.
 * <p>
 * This event represents a credit transaction that increases the account balance.
 * <p>
 * Example usage:
 * <pre>
 * MoneyDepositedEvent event = MoneyDepositedEvent.builder()
 *     .aggregateId(accountId)
 *     .amount(BigDecimal.valueOf(500.00))
 *     .source("Wire Transfer")
 *     .reference("REF-789")
 *     .depositedBy("user-456")
 *     .userId("user-456")
 *     .correlationId("corr-123")
 *     .build();
 * </pre>
 */
@DomainEvent("money.deposited")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyDepositedEvent extends AbstractDomainEvent {
    
    /**
     * The amount deposited.
     */
    private BigDecimal amount;
    
    /**
     * The source of the deposit (e.g., "Wire Transfer", "Check Deposit", "Cash Deposit").
     */
    private String source;
    
    /**
     * External reference number for the deposit.
     */
    private String reference;
    
    /**
     * User ID of the person who made the deposit.
     */
    private String depositedBy;
}

