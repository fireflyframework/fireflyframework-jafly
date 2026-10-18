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

package org.fireflyframework.eventsourcing.upcasting;

import org.fireflyframework.eventsourcing.domain.Event;

/**
 * Interface for event upcasting - transforming old event versions to new versions.
 * Upcasters handle event schema evolution and migration.
 * 
 * <p>Example usage:
 * <pre>{@code
 * public class AccountCreatedV1ToV2Upcaster implements EventUpcaster {
 *     public boolean canUpcast(String eventType, int eventVersion) {
 *         return "account.created".equals(eventType) && eventVersion == 1;
 *     }
 *     
 *     public Event upcast(Event event) {
 *         // Transform V1 to V2
 *         AccountCreatedEventV2 v2 = new AccountCreatedEventV2();
 *         v2.setAccountId(event.getAggregateId());
 *         // ... map fields ...
 *         return v2;
 *     }
 * }
 * }
 */
public interface EventUpcaster {

    /**
     * Check if this upcaster can handle the given event type and version.
     *
     * @param eventType the event type
     * @param eventVersion the event version
     * @return true if this upcaster can upcast this event
     */
    boolean canUpcast(String eventType, int eventVersion);

    /**
     * Upcast the event to a newer version.
     *
     * @param event the event to upcast
     * @return the upcasted event
     */
    Event upcast(Event event);

    /**
     * Get the target version this upcaster produces.
     *
     * @return the target event version
     */
    default int getTargetVersion() {
        return 2; // Default to version 2
    }

    /**
     * Get the priority of this upcaster (higher = runs first).
     * Useful when multiple upcasters can handle the same event.
     *
     * @return the priority (default: 0)
     */
    default int getPriority() {
        return 0;
    }
}

