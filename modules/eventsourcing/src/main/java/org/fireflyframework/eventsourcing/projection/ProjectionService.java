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

package org.fireflyframework.eventsourcing.projection;

import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for implementing read model projections in event sourced applications.
 * <p>
 * This abstract class provides common functionality for building and maintaining
 * read model projections from event streams. Implementations should extend this
 * class and provide specific projection logic for their domain.
 * <p>
 * Key features:
 * - Event handling with error recovery
 * - Projection position tracking for resume capability
 * - Batch processing for performance
 * - Monitoring and health checks
 * - Cache invalidation hooks
 * 
 * @param <T> The projection entity type
 */
@Slf4j
public abstract class ProjectionService<T> {
    
    private final ProjectionMetrics metrics;
    private volatile long latestGlobalSequence = 0L;
    
    /**
     * Constructor that initializes metrics.
     *
     * @param meterRegistry the meter registry for metrics collection
     */
    protected ProjectionService(MeterRegistry meterRegistry) {
        this.metrics = new ProjectionMetrics(getProjectionName(), meterRegistry);
    }
    
    /**
     * Processes a single event to update the projection.
     * Implementations should handle the specific event types they care about
     * and update the read model accordingly.
     *
     * @param envelope the event envelope containing the event and metadata
     * @return a Mono that completes when the projection is updated
     */
    public abstract Mono<Void> handleEvent(StoredEventEnvelope envelope);
    
    /**
     * Gets the current position of this projection.
     * Used for resuming projection processing from the correct point.
     *
     * @return a Mono containing the current global sequence position
     */
    public abstract Mono<Long> getCurrentPosition();
    
    /**
     * Updates the projection position after successfully processing events.
     *
     * @param position the new position to save
     * @return a Mono that completes when the position is saved
     */
    public abstract Mono<Void> updatePosition(long position);
    
    /**
     * Gets the name of this projection for monitoring and logging.
     *
     * @return the projection name
     */
    public abstract String getProjectionName();
    
    /**
     * Processes a batch of events, updating the projection position atomically.
     * This method provides transactional behavior for batch processing.
     *
     * @param events the events to process
     * @return a Mono that completes when all events are processed
     */
    public Mono<Void> processBatch(Flux<StoredEventEnvelope> events) {
        return events
            .collectList()
            .flatMap(eventList -> {
                if (eventList.isEmpty()) {
                    return Mono.empty();
                }
                
                log.debug("Processing batch of {} events for projection {}", 
                         eventList.size(), getProjectionName());
                
                Timer.Sample timerSample = metrics.startEventProcessingTimer();
                
                return Flux.fromIterable(eventList)
                    .concatMap(this::handleEvent)
                    .then(updatePosition(eventList.stream()
                                       .mapToLong(StoredEventEnvelope::getGlobalSequence)
                                       .max()
                                       .orElse(0L)))
                    .doOnSuccess(v -> {
                        timerSample.stop(metrics.getEventProcessingTimer());
                        eventList.forEach(event -> metrics.recordEventProcessed());
                        log.debug("Completed batch processing for projection {}", getProjectionName());
                    })
                    .doOnError(error -> {
                        timerSample.stop(metrics.getEventProcessingTimer());
                        eventList.forEach(event -> metrics.recordEventFailed());
                        log.error("Failed batch processing for projection {}: {}", 
                                 getProjectionName(), error.getMessage());
                    });
            });
    }
    
    /**
     * Processes events one by one with individual position updates.
     * Use this for scenarios where you need immediate consistency but can sacrifice some performance.
     *
     * @param events the events to process
     * @return a Mono that completes when all events are processed
     */
    public Mono<Void> processIndividually(Flux<StoredEventEnvelope> events) {
        return events
            .flatMap(envelope -> {
                Timer.Sample timerSample = metrics.startEventProcessingTimer();
                
                return handleEvent(envelope)
                    .then(updatePosition(envelope.getGlobalSequence()))
                    .doOnSuccess(v -> {
                        timerSample.stop(metrics.getEventProcessingTimer());
                        metrics.recordEventProcessed();
                        log.trace("Processed event {} for projection {}", 
                                 envelope.getEventId(), getProjectionName());
                    })
                    .doOnError(error -> {
                        timerSample.stop(metrics.getEventProcessingTimer());
                        metrics.recordEventFailed();
                        log.error("Failed to process event {} for projection {}: {}", 
                                 envelope.getEventId(), getProjectionName(), error.getMessage());
                    });
            })
            .then();
    }
    
    /**
     * Resets the projection to a clean state and rebuilds from the beginning.
     * This is useful for projection schema changes or corruption recovery.
     *
     * @return a Mono that completes when the projection is reset
     */
    public Mono<Void> resetProjection() {
        log.info("Resetting projection: {}", getProjectionName());
        
        return clearProjectionData()
            .then(updatePosition(0L))
            .then(onProjectionReset())
            .doOnSuccess(v -> {
                metrics.recordProjectionReset();
                log.info("Successfully reset projection: {}", getProjectionName());
            })
            .doOnError(error -> log.error("Failed to reset projection {}: {}", 
                                        getProjectionName(), error.getMessage()));
    }
    
    /**
     * Gets health information about this projection.
     * Includes position, lag, error counts, and other monitoring data.
     *
     * @param latestGlobalSequence the latest global sequence in the event store
     * @return projection health information
     */
    public Mono<ProjectionHealth> getHealth(long latestGlobalSequence) {
        this.latestGlobalSequence = latestGlobalSequence;
        
        return getCurrentPosition()
            .map(currentPosition -> {
                long lag = latestGlobalSequence - currentPosition;
                boolean isHealthy = lag < getMaxAllowedLag();
                
                metrics.updatePosition(currentPosition, latestGlobalSequence);
                
                return ProjectionHealth.builder()
                    .projectionName(getProjectionName())
                    .currentPosition(currentPosition)
                    .latestGlobalSequence(latestGlobalSequence)
                    .lag(lag)
                    .isHealthy(isHealthy)
                    .lastUpdated(Instant.now())
                    .processingRate(metrics.getProcessingRate())
                    .build();
            })
            .onErrorReturn(ProjectionHealth.builder()
                    .projectionName(getProjectionName())
                    .currentPosition(null)
                    .latestGlobalSequence(latestGlobalSequence)
                    .lag(null)
                    .isHealthy(false)
                    .lastUpdated(Instant.now())
                    .errorMessage("Failed to retrieve health information")
                    .build());
    }
    
    /**
     * Clears all projection data. Called during projection reset.
     * Implementations should remove all read model data for this projection.
     *
     * @return a Mono that completes when data is cleared
     */
    protected abstract Mono<Void> clearProjectionData();
    
    /**
     * Called after a projection reset is complete.
     * Can be used for cleanup, cache invalidation, or notifications.
     *
     * @return a Mono that completes when post-reset actions are done
     */
    protected Mono<Void> onProjectionReset() {
        return Mono.empty();
    }
    
    /**
     * Gets the maximum allowed lag for this projection to be considered healthy.
     * Default is 1000 events, but can be overridden based on projection requirements.
     *
     * @return the maximum allowed lag in number of events
     */
    protected long getMaxAllowedLag() {
        return 1000L;
    }
    
    /**
     * Handles specific event types that this projection cares about.
     * This is a helper method to simplify event type filtering in implementations.
     *
     * @param envelope the event envelope
     * @param eventType the event type to match
     * @param handler the handler function for this event type
     * @return a Mono that processes the event if it matches, or empty if it doesn't
     */
    protected Mono<Void> handleEventType(StoredEventEnvelope envelope, String eventType, 
                                        java.util.function.Function<StoredEventEnvelope, Mono<Void>> handler) {
        if (eventType.equals(envelope.getEventType())) {
            return handler.apply(envelope);
        }
        return Mono.empty();
    }
    
    /**
     * Extracts metadata from an event envelope for projection use.
     *
     * @param envelope the event envelope
     * @return metadata map
     */
    protected Map<String, Object> extractMetadata(StoredEventEnvelope envelope) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("eventId", envelope.getEventId().toString());
        metadata.put("aggregateId", envelope.getAggregateId().toString());
        metadata.put("aggregateType", envelope.getAggregateType());
        metadata.put("eventType", envelope.getEventType());
        metadata.put("globalSequence", envelope.getGlobalSequence());
        metadata.put("createdAt", envelope.getCreatedAt().toString());
        
        if (envelope.getMetadata() != null) {
            metadata.putAll(envelope.getMetadata());
        }
        
        return metadata;
    }
    
    /**
     * Gets the metrics for this projection.
     *
     * @return the projection metrics instance
     */
    protected ProjectionMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Performs a comprehensive health check of the projection.
     *
     * @return a Mono containing the health information
     */
    public Mono<ProjectionHealth> checkHealth() {
        return getLatestGlobalSequenceFromEventStore()
                .flatMap(this::getHealth)
                .onErrorReturn(ProjectionHealth.builder()
                        .projectionName(getProjectionName())
                        .currentPosition(null)
                        .latestGlobalSequence(null)
                        .lag(null)
                        .isHealthy(false)
                        .lastUpdated(Instant.now())
                        .errorMessage("Failed to retrieve latest global sequence")
                        .build());
    }
    
    /**
     * Gets the latest global sequence from the event store.
     * Implementations should provide this to enable proper health monitoring.
     *
     * @return a Mono containing the latest global sequence
     */
    protected abstract Mono<Long> getLatestGlobalSequenceFromEventStore();
}
