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

import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.domain.EventStream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main interface for event storage and retrieval in the event sourcing system.
 * <p>
 * The EventStore is the central component that provides:
 * - Persistence of domain events
 * - Retrieval of event streams for aggregate reconstruction
 * - Global event querying capabilities
 * - Optimistic concurrency control through versioning
 * - Event ordering guarantees
 * <p>
 * Implementations should ensure:
 * - ACID properties for event persistence
 * - Ordering within aggregate streams
 * - Optimistic concurrency control
 * - High performance for both reads and writes
 * - Scalability for large event volumes
 * <p>
 * The interface is designed to be reactive, using Project Reactor types
 * to support non-blocking operations and backpressure handling.
 */
public interface EventStore {

    /**
     * Appends new events to an aggregate's event stream.
     * <p>
     * This operation should be atomic - either all events are saved or none.
     * It should also enforce optimistic concurrency control by checking
     * the expected version against the current aggregate version.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @param events the events to append
     * @param expectedVersion the expected current version of the aggregate
     * @param metadata additional metadata to attach to all events
     * @return a Mono that emits the updated event stream
     * @throws ConcurrencyException if the expected version doesn't match
     * @throws EventStoreException if the operation fails
     */
    Mono<EventStream> appendEvents(UUID aggregateId, 
                                   String aggregateType,
                                   List<Event> events, 
                                   long expectedVersion,
                                   Map<String, Object> metadata);

    /**
     * Appends events with empty metadata.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @param events the events to append
     * @param expectedVersion the expected current version of the aggregate
     * @return a Mono that emits the updated event stream
     */
    default Mono<EventStream> appendEvents(UUID aggregateId, 
                                           String aggregateType,
                                           List<Event> events, 
                                           long expectedVersion) {
        return appendEvents(aggregateId, aggregateType, events, expectedVersion, Map.of());
    }

    /**
     * Loads the complete event stream for an aggregate.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @return a Mono that emits the event stream, or empty if aggregate doesn't exist
     */
    Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType);

    /**
     * Loads events for an aggregate starting from a specific version.
     * This is useful for loading events after a snapshot.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @param fromVersion the version to start from (inclusive)
     * @return a Mono that emits the event stream from the specified version
     */
    Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType, long fromVersion);

    /**
     * Loads events for an aggregate in a specific version range.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @param fromVersion the starting version (inclusive)
     * @param toVersion the ending version (inclusive)
     * @return a Mono that emits the event stream in the specified range
     */
    Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType, long fromVersion, long toVersion);

    /**
     * Gets the current version of an aggregate without loading all events.
     * This is useful for optimistic concurrency control checks.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @return a Mono that emits the current version, or 0 if aggregate doesn't exist
     */
    Mono<Long> getAggregateVersion(UUID aggregateId, String aggregateType);

    /**
     * Checks if an aggregate exists in the event store.
     *
     * @param aggregateId the aggregate identifier
     * @param aggregateType the aggregate type
     * @return a Mono that emits true if the aggregate exists
     */
    Mono<Boolean> aggregateExists(UUID aggregateId, String aggregateType);

    /**
     * Streams all events from the event store in global order.
     * This is useful for projections and read model updates.
     *
     * @return a Flux of event envelopes in global sequence order
     */
    Flux<StoredEventEnvelope> streamAllEvents();

    /**
     * Streams events from a specific global sequence number.
     * This is useful for incremental projection updates.
     *
     * @param fromSequence the global sequence number to start from (inclusive)
     * @return a Flux of event envelopes from the specified sequence
     */
    Flux<StoredEventEnvelope> streamAllEvents(long fromSequence);

    /**
     * Streams events of specific types.
     * This is useful for building specialized projections.
     *
     * @param eventTypes the event types to include
     * @return a Flux of event envelopes matching the specified types
     */
    Flux<StoredEventEnvelope> streamEventsByType(List<String> eventTypes);

    /**
     * Streams events for specific aggregate types.
     *
     * @param aggregateTypes the aggregate types to include
     * @return a Flux of event envelopes for the specified aggregate types
     */
    Flux<StoredEventEnvelope> streamEventsByAggregateType(List<String> aggregateTypes);

    /**
     * Streams events within a time range.
     *
     * @param from the start time (inclusive)
     * @param to the end time (inclusive)
     * @return a Flux of event envelopes within the time range
     */
    Flux<StoredEventEnvelope> streamEventsByTimeRange(Instant from, Instant to);

    /**
     * Streams events with specific metadata criteria.
     *
     * @param metadataCriteria the metadata key-value pairs to match
     * @return a Flux of event envelopes matching the metadata criteria
     */
    Flux<StoredEventEnvelope> streamEventsByMetadata(Map<String, Object> metadataCriteria);

    /**
     * Gets the current global sequence number.
     * This represents the last assigned sequence number in the event store.
     *
     * @return a Mono that emits the current global sequence number
     */
    Mono<Long> getCurrentGlobalSequence();

    /**
     * Performs health check on the event store.
     *
     * @return a Mono that emits true if the event store is healthy
     */
    Mono<Boolean> isHealthy();

    /**
     * Gets statistics about the event store.
     *
     * @return a Mono that emits event store statistics
     */
    Mono<EventStoreStatistics> getStatistics();
}