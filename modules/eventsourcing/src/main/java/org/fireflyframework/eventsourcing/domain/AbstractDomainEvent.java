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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.fireflyframework.eventsourcing.annotation.DomainEvent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for domain events that provides common functionality
 * and improves developer experience with Spring-aligned annotations.
 * <p>
 * This class provides:
 * - Default implementations for common event methods
 * - Builder pattern support via Lombok's @SuperBuilder
 * - Metadata management with fluent API
 * - Timestamp handling with sensible defaults
 * - Event versioning support
 * - Automatic event type resolution from @DomainEvent annotation
 * <p>
 * <b>Usage Example (Recommended):</b>
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
 *     // No need to override getEventType() - it's read from @DomainEvent!
 * }
 *
 * // Creating events with builder pattern and fluent metadata API
 * AccountCreatedEvent event = AccountCreatedEvent.builder()
 *     .aggregateId(accountId)
 *     .accountNumber("12345")
 *     .accountType("CHECKING")
 *     .initialBalance(BigDecimal.valueOf(1000))
 *     .userId("user-123")
 *     .source("mobile-app")
 *     .correlationId("corr-456")
 *     .build();
 * }
 * </pre>
 * <p>
 * <b>Benefits:</b>
 * <ul>
 *   <li>Less boilerplate code - no need to implement getAggregateId(), getEventTimestamp(), etc.</li>
 *   <li>Builder pattern support for clean event construction</li>
 *   <li>Fluent metadata API for adding contextual information</li>
 *   <li>Automatic timestamp generation</li>
 *   <li>Event versioning support for schema evolution</li>
 * </ul>
 * <p>
 * <b>Best Practices:</b>
 * <ul>
 *   <li>Always use @JsonTypeName annotation with a stable event type identifier</li>
 *   <li>Keep events immutable - use final fields or Lombok's @Value</li>
 *   <li>Include all necessary business data in the event</li>
 *   <li>Use meaningful, past-tense event names (e.g., "AccountCreated", not "CreateAccount")</li>
 *   <li>Add metadata for correlation IDs, user context, and tracing information</li>
 * </ul>
 *
 * @see Event
 * @see StoredEventEnvelope
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractDomainEvent implements Event {

    /**
     * The unique identifier of the aggregate that produced this event.
     * This is required and must be set when creating the event.
     */
    private UUID aggregateId;

    /**
     * The timestamp when this event occurred in the business domain.
     * Defaults to the current time if not explicitly set.
     */
    private Instant eventTimestamp;

    /**
     * Additional metadata for this event (correlation IDs, user context, etc.).
     * Initialized as an empty map if not provided.
     */
    private Map<String, Object> metadata;

    /**
     * The version of the event schema.
     * Defaults to 1. Increment this when changing the event structure.
     */
    private int eventVersion;

    /**
     * Gets the event timestamp, defaulting to current time if not set.
     *
     * @return the event timestamp, never null
     */
    @Override
    public Instant getEventTimestamp() {
        if (eventTimestamp == null) {
            eventTimestamp = Instant.now();
        }
        return eventTimestamp;
    }

    /**
     * Gets the metadata map, initializing it if necessary.
     *
     * @return the metadata map, never null
     */
    @Override
    public Map<String, Object> getMetadata() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        return metadata;
    }

    /**
     * Gets the event version, defaulting to 1 if not set.
     *
     * @return the event version
     */
    @Override
    public int getEventVersion() {
        return eventVersion > 0 ? eventVersion : 1;
    }

    /**
     * Adds a metadata entry to this event.
     * <p>
     * This is a convenience method for adding metadata in a fluent style.
     * Note: This method is primarily useful during event construction.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this event instance for method chaining
     */
    @JsonIgnore
    public AbstractDomainEvent addMetadata(String key, Object value) {
        getMetadata().put(key, value);
        return this;
    }

    /**
     * Adds multiple metadata entries to this event.
     *
     * @param additionalMetadata the metadata entries to add
     * @return this event instance for method chaining
     */
    @JsonIgnore
    public AbstractDomainEvent addMetadata(Map<String, Object> additionalMetadata) {
        if (additionalMetadata != null) {
            getMetadata().putAll(additionalMetadata);
        }
        return this;
    }

    /**
     * Builder class that supports fluent metadata addition.
     * This is automatically generated by Lombok's @SuperBuilder.
     */
    public abstract static class AbstractDomainEventBuilder<C extends AbstractDomainEvent, 
                                                            B extends AbstractDomainEventBuilder<C, B>> {
        
        /**
         * Adds a metadata entry during event construction.
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this builder for method chaining
         */
        public B addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return self();
        }

        /**
         * Adds multiple metadata entries during event construction.
         *
         * @param additionalMetadata the metadata entries to add
         * @return this builder for method chaining
         */
        public B addMetadata(Map<String, Object> additionalMetadata) {
            if (additionalMetadata != null) {
                if (this.metadata == null) {
                    this.metadata = new HashMap<>();
                }
                this.metadata.putAll(additionalMetadata);
            }
            return self();
        }

        /**
         * Sets the correlation ID in metadata.
         * This is a convenience method for distributed tracing.
         *
         * @param correlationId the correlation ID
         * @return this builder for method chaining
         */
        public B correlationId(String correlationId) {
            return addMetadata("correlationId", correlationId);
        }

        /**
         * Sets the causation ID in metadata.
         * This represents the command or event that caused this event.
         *
         * @param causationId the causation ID
         * @return this builder for method chaining
         */
        public B causationId(String causationId) {
            return addMetadata("causationId", causationId);
        }

        /**
         * Sets the user ID in metadata.
         * This represents the user who triggered this event.
         *
         * @param userId the user ID
         * @return this builder for method chaining
         */
        public B userId(String userId) {
            return addMetadata("userId", userId);
        }

        /**
         * Sets the source system in metadata.
         * This represents the system or service that generated this event.
         *
         * @param source the source system identifier
         * @return this builder for method chaining
         */
        public B source(String source) {
            return addMetadata("source", source);
        }
    }

    @Override
    public String toString() {
        return String.format("%s{aggregateId=%s, eventType=%s, timestamp=%s, version=%d}", 
            getClass().getSimpleName(), 
            aggregateId, 
            getEventType(), 
            getEventTimestamp(), 
            getEventVersion());
    }
}

