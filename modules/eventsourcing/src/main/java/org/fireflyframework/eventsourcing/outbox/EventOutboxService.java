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

package org.fireflyframework.eventsourcing.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.logging.EventSourcingLoggingContext;
import org.fireflyframework.eventsourcing.publisher.EventSourcingPublisher;
import org.fireflyframework.eventsourcing.store.r2dbc.EventOutboxEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing Event Outbox operations.
 * <p>
 * This service implements the Transactional Outbox Pattern, ensuring reliable
 * event publishing with at-least-once delivery guarantees.
 * <p>
 * Key responsibilities:
 * - Save events to the outbox table in the same transaction as aggregate changes
 * - Process pending outbox entries and publish them to external systems
 * - Handle retry logic with exponential backoff
 * - Manage dead letter queue for permanently failed entries
 * - Provide monitoring and statistics
 * <p>
 * The outbox pattern solves the dual-write problem by:
 * 1. Writing events to the outbox table in the same DB transaction as the aggregate
 * 2. A separate process polls the outbox and publishes events asynchronously
 * 3. Retries failed publications with exponential backoff
 * 4. Marks entries as completed after successful publication
 */
@RequiredArgsConstructor
@Slf4j
public class EventOutboxService {

    private final EventOutboxRepository outboxRepository;
    private final EventSourcingPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Saves an event envelope to the outbox.
     * This should be called within the same transaction as the event store write.
     *
     * @param envelope the event envelope to save
     * @return mono that completes when the entry is saved
     */
    public Mono<EventOutboxEntity> saveToOutbox(StoredEventEnvelope envelope) {
        return saveToOutbox(envelope, 5, 3); // Default priority=5, max_retries=3
    }

    /**
     * Saves an event envelope to the outbox with custom priority and retry settings.
     *
     * @param envelope the event envelope to save
     * @param priority processing priority (1=highest, 10=lowest)
     * @param maxRetries maximum number of retry attempts
     * @return mono that completes when the entry is saved
     */
    public Mono<EventOutboxEntity> saveToOutbox(StoredEventEnvelope envelope, int priority, int maxRetries) {
        return Mono.fromCallable(() -> {
            EventOutboxEntity entity = new EventOutboxEntity();
            entity.setOutboxId(UUID.randomUUID());
            entity.setAggregateId(envelope.getAggregateId());
            entity.setAggregateType(envelope.getAggregateType());
            entity.setEventType(envelope.getEventType());
            entity.setEventData(serializeEvent(envelope.getEvent()));
            entity.setMetadata(serializeMetadata(envelope.getMetadata()));
            entity.setStatus(EventOutboxEntity.Status.PENDING.name());
            entity.setCreatedAt(Instant.now());
            entity.setRetryCount(0);
            entity.setPriority(priority);
            entity.setMaxRetries(maxRetries);
            
            // Set context from MDC
            entity.setTenantId(MDC.get(EventSourcingLoggingContext.TENANT_ID));
            entity.setCorrelationId(envelope.getCorrelationId());
            
            // Set partition key for ordered processing (use aggregate ID)
            entity.setPartitionKey(envelope.getAggregateId().toString());
            
            return entity;
        })
        .flatMap(outboxRepository::save)
        .doOnSuccess(saved -> log.debug("Saved event to outbox: outboxId={}, eventType={}, aggregateId={}", 
                saved.getOutboxId(), saved.getEventType(), saved.getAggregateId()))
        .doOnError(error -> log.error("Failed to save event to outbox: eventType={}, aggregateId={}", 
                envelope.getEventType(), envelope.getAggregateId(), error));
    }

    /**
     * Processes pending outbox entries by publishing them to external systems.
     *
     * @param batchSize maximum number of entries to process
     * @return mono with the number of successfully processed entries
     */
    public Mono<Long> processPendingEntries(int batchSize) {
        log.info("Processing pending outbox entries, batch size: {}", batchSize);
        
        return outboxRepository.findPendingEntries(batchSize)
                .concatMap(this::processEntry)
                .filter(success -> success)
                .count()
                .doOnSuccess(count -> log.info("Successfully processed {} outbox entries", count))
                .doOnError(error -> log.error("Error processing outbox entries", error));
    }

    /**
     * Processes failed entries that are ready for retry.
     *
     * @param batchSize maximum number of entries to process
     * @return mono with the number of successfully processed entries
     */
    public Mono<Long> processRetryEntries(int batchSize) {
        log.info("Processing retry outbox entries, batch size: {}", batchSize);
        
        return outboxRepository.findEntriesReadyForRetry(Instant.now(), batchSize)
                .concatMap(this::processEntry)
                .filter(success -> success)
                .count()
                .doOnSuccess(count -> log.info("Successfully processed {} retry entries", count))
                .doOnError(error -> log.error("Error processing retry entries", error));
    }

    /**
     * Processes a single outbox entry.
     *
     * @param entry the outbox entry to process
     * @return mono that emits true if processing succeeded, false otherwise
     */
    private Mono<Boolean> processEntry(EventOutboxEntity entry) {
        // Set MDC context for structured JSON logging
        EventSourcingLoggingContext.setOutboxId(entry.getOutboxId());
        EventSourcingLoggingContext.setAggregateContext(entry.getAggregateId(), entry.getAggregateType());
        EventSourcingLoggingContext.setEventType(entry.getEventType());
        EventSourcingLoggingContext.setStatus(entry.getStatus());
        EventSourcingLoggingContext.setRetryCount(entry.getRetryCount());
        EventSourcingLoggingContext.setPriority(entry.getPriority());
        if (entry.getTenantId() != null) {
            EventSourcingLoggingContext.setTenantId(entry.getTenantId());
        }
        if (entry.getCorrelationId() != null) {
            EventSourcingLoggingContext.setCorrelationId(entry.getCorrelationId());
        }

        log.debug("Processing outbox entry: outboxId={}, eventType={}, retryCount={}",
                entry.getOutboxId(), entry.getEventType(), entry.getRetryCount());

        return markAsProcessing(entry)
                .then(publishEntry(entry))
                .flatMap(success -> {
                    if (success) {
                        return markAsCompleted(entry).thenReturn(true);
                    } else {
                        return markAsFailed(entry, "Publication failed").thenReturn(false);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error processing outbox entry: outboxId={}", entry.getOutboxId(), error);
                    return markAsFailed(entry, error.getMessage()).thenReturn(false);
                })
                .doFinally(signalType -> {
                    // Clear MDC context
                    EventSourcingLoggingContext.clearOutboxContext();
                    EventSourcingLoggingContext.clearAggregateContext();
                    EventSourcingLoggingContext.clearEventType();
                });
    }

    /**
     * Publishes an outbox entry to external systems.
     *
     * @param entry the outbox entry to publish
     * @return mono that emits true if publication succeeded
     */
    private Mono<Boolean> publishEntry(EventOutboxEntity entry) {
        return Mono.fromCallable(() -> deserializeEvent(entry.getEventData()))
                .flatMap(event -> {
                    Map<String, Object> metadata = deserializeMetadata(entry.getMetadata());
                    
                    // Set logging context
                    EventSourcingLoggingContext.setAggregateContext(entry.getAggregateId(), entry.getAggregateType());
                    EventSourcingLoggingContext.setEventType(entry.getEventType());
                    if (entry.getCorrelationId() != null) {
                        EventSourcingLoggingContext.setCorrelationId(entry.getCorrelationId());
                    }
                    if (entry.getTenantId() != null) {
                        EventSourcingLoggingContext.setTenantId(entry.getTenantId());
                    }
                    
                    return eventPublisher.publishDomainEvent(event, metadata)
                            .thenReturn(true)
                            .doFinally(signal -> EventSourcingLoggingContext.clearAll());
                })
                .onErrorReturn(false);
    }

    /**
     * Marks an outbox entry as processing.
     */
    private Mono<EventOutboxEntity> markAsProcessing(EventOutboxEntity entry) {
        entry.setStatus(EventOutboxEntity.Status.PROCESSING.name());
        entry.setUpdatedAt(Instant.now());
        return outboxRepository.save(entry);
    }

    /**
     * Marks an outbox entry as completed.
     */
    private Mono<EventOutboxEntity> markAsCompleted(EventOutboxEntity entry) {
        entry.setStatus(EventOutboxEntity.Status.COMPLETED.name());
        entry.setProcessedAt(Instant.now());
        entry.setUpdatedAt(Instant.now());
        return outboxRepository.save(entry)
                .doOnSuccess(e -> log.info("Marked outbox entry as completed: outboxId={}", e.getOutboxId()));
    }

    /**
     * Marks an outbox entry as failed.
     */
    private Mono<EventOutboxEntity> markAsFailed(EventOutboxEntity entry, String errorMessage) {
        entry.setStatus(EventOutboxEntity.Status.FAILED.name());
        entry.setRetryCount(entry.getRetryCount() + 1);
        entry.setLastError(errorMessage);
        entry.setUpdatedAt(Instant.now());
        
        // Calculate next retry time with exponential backoff
        if (entry.getRetryCount() < entry.getMaxRetries()) {
            long backoffMinutes = (long) Math.pow(2, entry.getRetryCount());
            entry.setNextRetryAt(Instant.now().plusSeconds(backoffMinutes * 60));
        }
        
        return outboxRepository.save(entry)
                .doOnSuccess(e -> log.warn("Marked outbox entry as failed: outboxId={}, retryCount={}/{}, nextRetry={}", 
                        e.getOutboxId(), e.getRetryCount(), e.getMaxRetries(), e.getNextRetryAt()));
    }

    /**
     * Gets dead letter queue entries (permanently failed).
     */
    public Flux<EventOutboxEntity> getDeadLetterEntries() {
        return outboxRepository.findDeadLetterEntries();
    }

    /**
     * Cleans up completed entries older than the specified number of days.
     */
    public Mono<Long> cleanupCompletedEntries(int olderThanDays) {
        Instant cutoff = Instant.now().minusSeconds(olderThanDays * 24 * 60 * 60L);
        return outboxRepository.deleteCompletedEntriesOlderThan(cutoff)
                .doOnSuccess(count -> log.info("Cleaned up {} completed outbox entries older than {} days", 
                        count, olderThanDays));
    }

    /**
     * Gets statistics about outbox entries.
     */
    public Mono<OutboxStatistics> getStatistics() {
        return Mono.zip(
                outboxRepository.countByStatus("PENDING"),
                outboxRepository.countByStatus("PROCESSING"),
                outboxRepository.countByStatus("COMPLETED"),
                outboxRepository.countByStatus("FAILED"),
                outboxRepository.findDeadLetterEntries().count()
        ).map(tuple -> new OutboxStatistics(
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT3(),
                tuple.getT4(),
                tuple.getT5()
        ));
    }

    private String serializeEvent(Event event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize metadata", e);
        }
    }

    private Event deserializeEvent(String eventData) {
        try {
            return objectMapper.readValue(eventData, Event.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeMetadata(String metadata) {
        try {
            return objectMapper.readValue(metadata, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /**
     * Statistics about outbox entries.
     */
    public record OutboxStatistics(
            long pendingCount,
            long processingCount,
            long completedCount,
            long failedCount,
            long deadLetterCount
    ) {}
}

