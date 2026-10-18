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

/**
 * Domain Event: Account was frozen.
 * <p>
 * This event represents the freezing of an account, typically due to:
 * - Suspicious activity
 * - Fraud investigation
 * - Legal hold
 * - Customer request
 * <p>
 * When an account is frozen, withdrawals are not allowed but deposits may still be accepted.
 */
@DomainEvent("account.frozen")
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountFrozenEvent extends AbstractDomainEvent {
    
    /**
     * The reason for freezing the account.
     */
    private String reason;
    
    /**
     * User ID of the person who froze the account.
     */
    private String frozenBy;
}

