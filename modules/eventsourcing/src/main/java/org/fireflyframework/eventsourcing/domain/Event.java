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

package org.fireflyframework.eventsourcing.domain;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.fireflyframework.eventsourcing.annotation.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base interface for all domain events in the event sourcing system.
 * <p>
 * Events represent facts that have happened in the past and are immutable.
 * They capture state changes in aggregates and form the single source of truth
 * for the system's state.
 * <p>
 * Events should be:
 * - Immutable (all fields final)
 * - Serializable (support JSON serialization)
 * - Self-contained (include all necessary information)
 * - Domain-focused (represent business concepts, not technical operations)
 * <p>
 * <b>Recommended Approach - Using @DomainEvent annotation:</b>
 * <pre>
 * {@code
 * @DomainEvent("account.created")
 * @SuperBuilder
 * @Getter
 * @NoArgsConstructor
 * @AllArgsConstructor
 * public class AccountCreatedEvent extends AbstractDomainEvent {
 *     private String accountNumber;
 *     private String accountType;
 *     private BigDecimal initialBalance;
 *     // No need to override getEventType() - it's read from the annotation!
 * }
 * }
 * </pre>
 * <p>
 * <b>Alternative - Using Java Records:</b>
 * <pre>
 * {@code
 * @DomainEvent("account.created")
 * public record AccountCreatedEvent(
 *     UUID aggregateId,
 *     String accountNumber,
 *     String accountType,
 *     BigDecimal initialBalance
 * ) implements Event {
 *     // getEventType() is automatically provided by the default method
 * }
 * }
 * </pre>
 *
 * @see DomainEvent
 * @see AbstractDomainEvent
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType"
)
public interface Event {

    /**
     * Gets the unique identifier of the aggregate that produced this event.
     * <p>
     * This is used to group events by aggregate and ensure ordering within
     * an aggregate's event stream.
     *
     * @return the aggregate identifier, never null
     */
    UUID getAggregateId();

    /**
     * Gets the event type identifier.
     * <p>
     * This should be a stable, unique identifier for this type of event,
     * typically in dot notation (e.g., "account.created", "payment.processed").
     * This value is used for event serialization and deserialization.
     * <p>
     * <b>Default Implementation:</b>
     * The default implementation reads the event type from the {@link DomainEvent}
     * annotation. If the annotation is not present, it throws an exception.
     * <p>
     * You can override this method if you need custom logic, but using the
     * {@link DomainEvent} annotation is the recommended approach.
     *
     * @return the event type, never null or empty
     * @throws IllegalStateException if @DomainEvent annotation is not present
     */
    default String getEventType() {
        DomainEvent annotation = this.getClass().getAnnotation(DomainEvent.class);
        if (annotation == null) {
            throw new IllegalStateException(
                "Event class " + this.getClass().getSimpleName() +
                " must be annotated with @DomainEvent or override getEventType() method"
            );
        }
        return annotation.value();
    }

    /**
     * Gets additional metadata for this event.
     * <p>
     * Metadata can include:
     * - Causation ID (command that caused this event)
     * - Correlation ID (for distributed tracing)
     * - User ID (who triggered the event)
     * - Source system information
     * - Custom business metadata
     *
     * @return metadata map, may be empty but never null
     */
    default Map<String, Object> getMetadata() {
        return Map.of();
    }

    /**
     * Gets the timestamp when this event occurred.
     * <p>
     * This should represent the business time when the event happened,
     * not when it was persisted to the event store.
     *
     * @return the event timestamp, defaults to current time if not specified
     */
    default Instant getEventTimestamp() {
        return Instant.now();
    }

    /**
     * Gets the version of the event schema.
     * <p>
     * This is useful for event evolution and migration. When changing
     * the structure of an event, increment this version to maintain
     * backward compatibility.
     *
     * @return the event version, defaults to 1
     */
    default int getEventVersion() {
        return 1;
    }
}