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

import java.util.List;
import java.util.UUID;

/**
 * Represents a stream of events for a specific aggregate.
 * <p>
 * An EventStream contains all the events that belong to a single aggregate instance,
 * ordered by their aggregate version. This is the fundamental unit for:
 * - Aggregate reconstruction from events
 * - Optimistic concurrency control
 * - Event store queries
 * <p>
 * Event streams provide:
 * - Ordering guarantees within an aggregate
 * - Version information for concurrency control
 * - Aggregate identity and type information
 * - Snapshot integration for performance optimization
 */
@Data
@Builder
public class EventStream {

    /**
     * The unique identifier of the aggregate this stream belongs to.
     */
    private final UUID aggregateId;

    /**
     * The type of aggregate (e.g., "Account", "Order", "User").
     * This is used for deserialization and aggregate factory selection.
     */
    private final String aggregateType;

    /**
     * The current version of the aggregate after all events in this stream.
     * This represents the highest aggregate version in the events list.
     */
    private final long currentVersion;

    /**
     * The version from which events were loaded.
     * This is useful when loading events from a specific version onwards,
     * such as when reconstructing from a snapshot.
     */
    @Builder.Default
    private final long fromVersion = 0L;

    /**
     * The list of event envelopes in this stream, ordered by aggregate version.
     * Events should be in ascending version order.
     */
    private final List<StoredEventEnvelope> events;

    /**
     * Whether this stream is empty (contains no events).
     *
     * @return true if the stream has no events
     */
    public boolean isEmpty() {
        return events == null || events.isEmpty();
    }

    /**
     * Gets the number of events in this stream.
     *
     * @return the event count
     */
    public int size() {
        return events != null ? events.size() : 0;
    }

    /**
     * Gets the first event in the stream.
     *
     * @return the first event envelope, or null if stream is empty
     */
    public StoredEventEnvelope getFirstEvent() {
        return isEmpty() ? null : events.get(0);
    }

    /**
     * Gets the last event in the stream.
     *
     * @return the last event envelope, or null if stream is empty
     */
    public StoredEventEnvelope getLastEvent() {
        return isEmpty() ? null : events.get(events.size() - 1);
    }

    /**
     * Gets events starting from a specific version (inclusive).
     *
     * @param fromVersion the minimum version to include
     * @return a list of events from the specified version onwards
     */
    public List<StoredEventEnvelope> getEventsFromVersion(long fromVersion) {
        if (isEmpty()) {
            return List.of();
        }

        return events.stream()
                .filter(envelope -> envelope.getAggregateVersion() >= fromVersion)
                .toList();
    }

    /**
     * Gets events up to a specific version (inclusive).
     *
     * @param toVersion the maximum version to include
     * @return a list of events up to the specified version
     */
    public List<StoredEventEnvelope> getEventsToVersion(long toVersion) {
        if (isEmpty()) {
            return List.of();
        }

        return events.stream()
                .filter(envelope -> envelope.getAggregateVersion() <= toVersion)
                .toList();
    }

    /**
     * Gets events in a specific version range (both inclusive).
     *
     * @param fromVersion the minimum version to include
     * @param toVersion the maximum version to include
     * @return a list of events in the specified range
     */
    public List<StoredEventEnvelope> getEventsInRange(long fromVersion, long toVersion) {
        if (isEmpty()) {
            return List.of();
        }

        return events.stream()
                .filter(envelope -> {
                    long version = envelope.getAggregateVersion();
                    return version >= fromVersion && version <= toVersion;
                })
                .toList();
    }

    /**
     * Creates an empty event stream for an aggregate.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @return an empty event stream
     */
    public static EventStream empty(UUID aggregateId, String aggregateType) {
        return EventStream.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .currentVersion(0L)
                .fromVersion(0L)
                .events(List.of())
                .build();
    }

    /**
     * Creates an event stream from a list of event envelopes.
     * The events should already be ordered by aggregate version.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @param events the list of event envelopes
     * @return the event stream
     * @throws IllegalArgumentException if events belong to different aggregates or are not ordered
     */
    public static EventStream of(UUID aggregateId, String aggregateType, List<StoredEventEnvelope> events) {
        if (events == null || events.isEmpty()) {
            return empty(aggregateId, aggregateType);
        }

        // Validate all events belong to the same aggregate
        boolean allMatch = events.stream()
                .allMatch(envelope -> aggregateId.equals(envelope.getAggregateId()) &&
                                    aggregateType.equals(envelope.getAggregateType()));
        
        if (!allMatch) {
            throw new IllegalArgumentException("All events must belong to the same aggregate");
        }

        // Find version range
        long minVersion = events.stream()
                .mapToLong(StoredEventEnvelope::getAggregateVersion)
                .min()
                .orElse(0L);
        
        long maxVersion = events.stream()
                .mapToLong(StoredEventEnvelope::getAggregateVersion)
                .max()
                .orElse(0L);

        return EventStream.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .currentVersion(maxVersion)
                .fromVersion(minVersion)
                .events(List.copyOf(events))
                .build();
    }
}