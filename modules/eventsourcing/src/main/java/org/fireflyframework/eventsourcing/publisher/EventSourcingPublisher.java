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

package org.fireflyframework.eventsourcing.publisher;

import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.fireflyframework.eventsourcing.config.EventSourcingProperties;
import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.logging.EventSourcingLoggingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Publisher for broadcasting domain events through the EDA infrastructure.
 * <p>
 * This component integrates event sourcing with the EDA library to:
 * - Publish domain events to external systems
 * - Support multiple messaging backends (Kafka, RabbitMQ, etc.)
 * - Enable event-driven architecture patterns
 * - Provide publish-subscribe capabilities for projections and sagas
 * <p>
 * Events are published after successful persistence to the event store,
 * ensuring that external consumers only receive events that have been
 * durably stored.
 * <p>
 * The publisher supports:
 * - Automatic topic/destination selection based on event types
 * - Custom metadata propagation
 * - Resilience patterns (circuit breaker, retry, etc.)
 * - Multiple messaging formats (JSON, Avro, Protobuf)
 */
@RequiredArgsConstructor
@Slf4j
public class EventSourcingPublisher {

    private final EventPublisherFactory publisherFactory;
    private final EventSourcingProperties properties;

    /**
     * Publishes a single event envelope to the EDA infrastructure.
     *
     * @param envelope the event envelope to publish
     * @return a Mono that completes when the event is published
     */
    public Mono<Void> publishEvent(StoredEventEnvelope envelope) {
        long startTime = System.currentTimeMillis();

        return Mono.fromRunnable(() -> validateEnvelope(envelope))
                .then(getEventPublisher())
                .flatMap(publisher -> {
                    String destination = determineDestination(envelope);
                    Map<String, Object> metadata = enrichMetadata(envelope);

                    EventSourcingLoggingContext.setAggregateContext(envelope.getAggregateId(), envelope.getAggregateType());
                    EventSourcingLoggingContext.setEventType(envelope.getEventType());

                    log.info("Publishing event: eventId={}, type={}, aggregateId={}, destination={}",
                             envelope.getEventId(), envelope.getEventType(), envelope.getAggregateId(), destination);

                    return publisher.publish(envelope.getEvent(), destination)
                            .doOnSuccess(v -> {
                                long duration = System.currentTimeMillis() - startTime;
                                log.info("Event published successfully: eventId={}, type={}, destination={} in {}ms",
                                        envelope.getEventId(), envelope.getEventType(), destination, duration);
                                EventSourcingLoggingContext.clearAll();
                            })
                            .doOnError(error -> {
                                long duration = System.currentTimeMillis() - startTime;
                                log.error("Failed to publish event: eventId={}, type={}, destination={} after {}ms",
                                        envelope.getEventId(), envelope.getEventType(), destination, duration, error);
                                EventSourcingLoggingContext.clearAll();
                            });
                });
    }

    /**
     * Publishes multiple event envelopes as a batch.
     * <p>
     * This method attempts to publish all events but doesn't fail
     * if individual events fail to publish.
     *
     * @param envelopes the event envelopes to publish
     * @return a Mono that completes when all events have been processed
     */
    public Mono<Void> publishEvents(List<StoredEventEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            log.debug("No events to publish (empty or null list)");
            return Mono.empty();
        }

        long startTime = System.currentTimeMillis();
        log.info("Publishing batch of {} events", envelopes.size());

        return Flux.fromIterable(envelopes)
                .flatMap(envelope -> publishEvent(envelope)
                        .onErrorResume(error -> {
                            log.error("Failed to publish event in batch: eventId={}, aggregateId={}, eventType={}: {}",
                                    envelope.getEventId(), envelope.getAggregateId(),
                                    envelope.getEventType(), error.getMessage(), error);
                            return Mono.empty();
                        }))
                .then()
                .doOnSuccess(v -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Batch publishing completed for {} events in {}ms", envelopes.size(), duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("Batch publishing failed after {}ms for {} events", duration, envelopes.size(), error);
                });
    }

    /**
     * Publishes a raw domain event with metadata.
     *
     * @param event the domain event
     * @param metadata additional metadata
     * @return a Mono that completes when the event is published
     */
    public Mono<Void> publishDomainEvent(Event event, Map<String, Object> metadata) {
        return getEventPublisher()
                .flatMap(publisher -> {
                    String destination = determineDestination(event);
                    Map<String, Object> enrichedMetadata = enrichMetadata(event, metadata);
                    
                    log.debug("Publishing domain event: type={}, aggregateId={}, destination={}", 
                             event.getEventType(), event.getAggregateId(), destination);
                    
                    return publisher.publish(event, destination);
                });
    }

    /**
     * Gets the configured event publisher.
     *
     * @return a Mono that emits the event publisher
     */
    private Mono<EventPublisher> getEventPublisher() {
        return Mono.fromSupplier(() -> {
            EventPublisher publisher = publisherFactory.getPublisher(properties.getPublisher().getType());
            if (publisher == null) {
                throw new EventPublishingException("No event publisher available for type: " + 
                                                 properties.getPublisher().getType());
            }
            return publisher;
        });
    }

    /**
     * Determines the destination (topic/queue/exchange) for an event envelope.
     *
     * @param envelope the event envelope
     * @return the destination
     */
    private String determineDestination(StoredEventEnvelope envelope) {
        return determineDestination(envelope.getEvent());
    }

    /**
     * Determines the destination for a domain event.
     *
     * @param event the domain event
     * @return the destination
     */
    private String determineDestination(Event event) {
        // Check for custom destination mapping
        String customDestination = properties.getPublisher().getDestinationMappings()
                .get(event.getEventType());
        
        if (customDestination != null) {
            return customDestination;
        }

        // Use default pattern: {prefix}.{eventType}
        String prefix = properties.getPublisher().getDestinationPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + "." + event.getEventType();
        }

        // Fall back to just the event type
        return event.getEventType();
    }

    /**
     * Enriches event metadata for publishing.
     *
     * @param envelope the event envelope
     * @return enriched metadata
     */
    private Map<String, Object> enrichMetadata(StoredEventEnvelope envelope) {
        Map<String, Object> metadata = Map.of(
                "eventId", envelope.getEventId().toString(),
                "aggregateId", envelope.getAggregateId().toString(),
                "aggregateType", envelope.getAggregateType(),
                "aggregateVersion", envelope.getAggregateVersion(),
                "globalSequence", envelope.getGlobalSequence(),
                "eventType", envelope.getEventType(),
                "createdAt", envelope.getCreatedAt().toString()
        );

        // Add envelope metadata
        if (envelope.getMetadata() != null && !envelope.getMetadata().isEmpty()) {
            metadata = Map.of(
                    "eventId", envelope.getEventId().toString(),
                    "aggregateId", envelope.getAggregateId().toString(),
                    "aggregateType", envelope.getAggregateType(),
                    "aggregateVersion", envelope.getAggregateVersion(),
                    "globalSequence", envelope.getGlobalSequence(),
                    "eventType", envelope.getEventType(),
                    "createdAt", envelope.getCreatedAt().toString(),
                    "metadata", envelope.getMetadata()
            );
        }

        return metadata;
    }

    /**
     * Enriches domain event metadata for publishing.
     *
     * @param event the domain event
     * @param additionalMetadata additional metadata
     * @return enriched metadata
     */
    private Map<String, Object> enrichMetadata(Event event, Map<String, Object> additionalMetadata) {
        Map<String, Object> baseMetadata = Map.of(
                "aggregateId", event.getAggregateId().toString(),
                "eventType", event.getEventType(),
                "eventTimestamp", event.getEventTimestamp().toString(),
                "eventVersion", event.getEventVersion()
        );

        if (additionalMetadata == null || additionalMetadata.isEmpty()) {
            return baseMetadata;
        }

        // Merge additional metadata
        Map<String, Object> enrichedMetadata = new java.util.HashMap<>(baseMetadata);
        enrichedMetadata.putAll(additionalMetadata);
        return enrichedMetadata;
    }

    /**
     * Validates an event envelope before publishing.
     *
     * @param envelope the event envelope to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateEnvelope(StoredEventEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Event envelope cannot be null");
        }
        if (envelope.getEvent() == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        if (envelope.getEventId() == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (envelope.getAggregateId() == null) {
            throw new IllegalArgumentException("Aggregate ID cannot be null");
        }
        if (envelope.getEventType() == null || envelope.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type cannot be null or empty");
        }
    }

    /**
     * Checks if event publishing is enabled.
     *
     * @return true if publishing is enabled
     */
    public boolean isPublishingEnabled() {
        return properties.getPublisher().isEnabled();
    }
}