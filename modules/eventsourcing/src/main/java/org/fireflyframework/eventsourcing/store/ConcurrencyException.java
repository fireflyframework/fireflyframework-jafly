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

package org.fireflyframework.eventsourcing.store;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when optimistic concurrency control fails.
 * <p>
 * This exception occurs when trying to append events to an aggregate
 * with an expected version that doesn't match the current version
 * in the event store. This indicates that another process has
 * modified the aggregate between the time it was loaded and
 * the time the events are being appended.
 * <p>
 * Applications should handle this exception by:
 * 1. Reloading the aggregate from the event store
 * 2. Re-applying the business logic
 * 3. Attempting to save again
 */
@Getter
public class ConcurrencyException extends EventStoreException {

    private final UUID aggregateId;
    private final String aggregateType;
    private final long expectedVersion;
    private final long actualVersion;

    public ConcurrencyException(UUID aggregateId, 
                               String aggregateType,
                               long expectedVersion, 
                               long actualVersion) {
        super(String.format(
            "Concurrency conflict for aggregate %s (type: %s). Expected version: %d, actual version: %d",
            aggregateId, aggregateType, expectedVersion, actualVersion
        ));
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public ConcurrencyException(UUID aggregateId, 
                               String aggregateType,
                               long expectedVersion, 
                               long actualVersion,
                               Throwable cause) {
        super(String.format(
            "Concurrency conflict for aggregate %s (type: %s). Expected version: %d, actual version: %d",
            aggregateId, aggregateType, expectedVersion, actualVersion
        ), cause);
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }
}