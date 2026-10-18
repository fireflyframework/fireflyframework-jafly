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

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope that wraps domain events with additional metadata for storage and processing.
 * <p>
 * The StoredEventEnvelope separates concerns between:
 * - Domain event data (the actual business event)
 * - Storage metadata (sequence numbers, persistence timestamps, etc.)
 * - Processing metadata (correlation IDs, causation information, etc.)
 * <p>
 * This pattern allows the domain events to remain pure and focused on business
 * concepts while providing the technical metadata needed for event sourcing
 * infrastructure operations.
 */
@Data
@Builder
@Jacksonized
public class StoredEventEnvelope {

    /**
     * The unique identifier for this event envelope.
     * This is different from the event's aggregate ID and is used
     * for global event ordering and deduplication.
     */
    private final UUID eventId;

    /**
     * The domain event wrapped by this envelope.
     */
    private final Event event;

    /**
     * The aggregate identifier this event belongs to.
     * Redundant with event.getAggregateId() but denormalized for query performance.
     */
    private final UUID aggregateId;

    /**
     * The type of aggregate this event belongs to (e.g., "Account", "Order").
     */
    private final String aggregateType;

    /**
     * The version/sequence number of this event within the aggregate's event stream.
     * This ensures ordering and enables optimistic concurrency control.
     */
    private final long aggregateVersion;

    /**
     * The global sequence number for this event across all aggregates.
     * Used for global event ordering and efficient querying.
     */
    private final long globalSequence;

    /**
     * The type of the domain event for deserialization purposes.
     */
    private final String eventType;

    /**
     * When this event was persisted to the event store.
     * This is different from the event's business timestamp.
     */
    private final Instant createdAt;

    /**
     * Additional metadata for correlation, causation, and tracing.
     * This can include:
     * - correlationId: for tracking distributed operations
     * - causationId: the command or event that caused this event
     * - userId: who initiated the action
     * - source: the system/service that produced the event
     */
    @Builder.Default
    private final Map<String, Object> metadata = Map.of();

    /**
     * Creates an StoredEventEnvelope for a new domain event.
     *
     * @param event the domain event to wrap
     * @param aggregateType the type of aggregate
     * @param aggregateVersion the version within the aggregate stream
     * @param globalSequence the global sequence number
     * @param metadata additional metadata
     * @return a new StoredEventEnvelope
     */
    public static StoredEventEnvelope of(Event event, String aggregateType, long aggregateVersion, 
                                   long globalSequence, Map<String, Object> metadata) {
        return StoredEventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .event(event)
                .aggregateId(event.getAggregateId())
                .aggregateType(aggregateType)
                .aggregateVersion(aggregateVersion)
                .globalSequence(globalSequence)
                .eventType(event.getEventType())
                .createdAt(Instant.now())
                .metadata(metadata != null ? metadata : Map.of())
                .build();
    }

    /**
     * Creates an StoredEventEnvelope for a new domain event with empty metadata.
     *
     * @param event the domain event to wrap
     * @param aggregateType the type of aggregate
     * @param aggregateVersion the version within the aggregate stream
     * @param globalSequence the global sequence number
     * @return a new StoredEventEnvelope
     */
    public static StoredEventEnvelope of(Event event, String aggregateType, long aggregateVersion, 
                                   long globalSequence) {
        return of(event, aggregateType, aggregateVersion, globalSequence, Map.of());
    }

    /**
     * Gets metadata value by key.
     *
     * @param key the metadata key
     * @return the metadata value, or null if not present
     */
    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }

    /**
     * Gets metadata value by key with type casting.
     *
     * @param key the metadata key
     * @param type the expected type
     * @param <T> the type parameter
     * @return the metadata value cast to the expected type, or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * Gets the correlation ID from metadata.
     *
     * @return the correlation ID, or null if not present
     */
    public String getCorrelationId() {
        return getMetadataValue("correlationId", String.class);
    }

    /**
     * Gets the causation ID from metadata.
     *
     * @return the causation ID, or null if not present
     */
    public String getCausationId() {
        return getMetadataValue("causationId", String.class);
    }

    /**
     * Gets the user ID from metadata.
     *
     * @return the user ID, or null if not present
     */
    public String getUserId() {
        return getMetadataValue("userId", String.class);
    }
}