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
 * Domain Event: Money was withdrawn from the account.
 * <p>
 * This event represents a debit transaction that decreases the account balance.
 * <p>
 * Example usage:
 * <pre>
 * MoneyWithdrawnEvent event = MoneyWithdrawnEvent.builder()
 *     .aggregateId(accountId)
 *     .amount(BigDecimal.valueOf(250.00))
 *     .destination("ATM Withdrawal")
 *     .reference("ATM-001")
 *     .withdrawnBy("user-123")
 *     .userId("user-123")
 *     .correlationId("corr-456")
 *     .build();
 * </pre>
 */
@DomainEvent("money.withdrawn")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyWithdrawnEvent extends AbstractDomainEvent {
    
    /**
     * The amount withdrawn.
     */
    private BigDecimal amount;
    
    /**
     * The destination of the withdrawal (e.g., "ATM Withdrawal", "Wire Transfer", "Check").
     */
    private String destination;
    
    /**
     * External reference number for the withdrawal.
     */
    private String reference;
    
    /**
     * User ID of the person who made the withdrawal.
     */
    private String withdrawnBy;
}

