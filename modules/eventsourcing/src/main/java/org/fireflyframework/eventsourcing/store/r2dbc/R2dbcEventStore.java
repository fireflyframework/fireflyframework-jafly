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

package org.fireflyframework.eventsourcing.store.r2dbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.eventsourcing.config.EventSourcingProperties;
import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.StoredEventEnvelope;
import org.fireflyframework.eventsourcing.domain.EventStream;
import org.fireflyframework.eventsourcing.logging.EventSourcingLoggingContext;
import org.fireflyframework.eventsourcing.outbox.EventOutboxService;
import org.fireflyframework.eventsourcing.store.*;
import org.fireflyframework.eventsourcing.transaction.EventSourcingTransactionalAspect;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R2DBC-based implementation of EventStore.
 * <p>
 * This implementation uses R2DBC for reactive database access, providing
 * non-blocking event persistence and retrieval. It supports:
 * - PostgreSQL, MySQL, H2, and other R2DBC-compatible databases
 * - Atomic event appending with optimistic concurrency control
 * - Efficient event streaming and querying
 * - JSON serialization of events and metadata
 * <p>
 * Database schema requirements:
 * <pre>
 * CREATE TABLE events (
 *     event_id UUID PRIMARY KEY,
 *     aggregate_id UUID NOT NULL,
 *     aggregate_type VARCHAR(255) NOT NULL,
 *     aggregate_version BIGINT NOT NULL,
 *     global_sequence BIGSERIAL UNIQUE,
 *     event_type VARCHAR(255) NOT NULL,
 *     event_data JSONB NOT NULL,
 *     metadata JSONB,
 *     created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
 *     UNIQUE(aggregate_id, aggregate_version)
 * );
 * 
 * CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);
 * CREATE INDEX idx_events_global_sequence ON events(global_sequence);
 * CREATE INDEX idx_events_type ON events(event_type);
 * CREATE INDEX idx_events_created_at ON events(created_at);
 * </pre>
 */
@Slf4j
public class R2dbcEventStore implements EventStore {

    private final DatabaseClient databaseClient;
    private final R2dbcEntityTemplate entityTemplate;
    private final ObjectMapper objectMapper;
    private final EventSourcingProperties properties;
    private final ReactiveTransactionManager transactionManager;
    private final TransactionalOperator transactionalOperator;
    private final ConnectionFactory connectionFactory;

    // Optional: Event Outbox Service for reliable event publishing
    @Autowired(required = false)
    private EventOutboxService outboxService;

    public R2dbcEventStore(DatabaseClient databaseClient,
                          R2dbcEntityTemplate entityTemplate,
                          ObjectMapper objectMapper,
                          EventSourcingProperties properties,
                          ReactiveTransactionManager transactionManager,
                          TransactionalOperator transactionalOperator,
                          ConnectionFactory connectionFactory) {
        this.databaseClient = databaseClient;
        this.entityTemplate = entityTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactionManager = transactionManager;
        this.transactionalOperator = transactionalOperator;
        this.connectionFactory = connectionFactory;

        log.debug("Creating R2DBC EventStore with database: {}", connectionFactory.getMetadata().getName());
    }

    @Override
    public Mono<EventStream> appendEvents(UUID aggregateId,
                                         String aggregateType,
                                         List<Event> events,
                                         long expectedVersion,
                                         Map<String, Object> metadata) {

        if (events == null || events.isEmpty()) {
            log.warn("Attempted to append null or empty events list for aggregate {}", aggregateId);
            return Mono.error(new IllegalArgumentException("Events list cannot be null or empty"));
        }

        long startTime = System.currentTimeMillis();
        EventSourcingLoggingContext.setAggregateContext(aggregateId, aggregateType);
        EventSourcingLoggingContext.setOperation("appendEvents");

        log.info("Appending {} events to aggregate {} (type: {}), expected version: {}",
                 events.size(), aggregateId, aggregateType, expectedVersion);

        if (log.isDebugEnabled()) {
            events.forEach(event -> log.debug("Event to append: type={}, eventType={}",
                    event.getClass().getSimpleName(), event.getEventType()));
        }

        return transactionalOperator.transactional(
                checkConcurrency(aggregateId, aggregateType, expectedVersion)
                        .flatMap(currentVersion -> {
                            log.debug("Concurrency check passed. Current version: {}, creating envelopes", currentVersion);
                            List<StoredEventEnvelope> envelopes = createEventEnvelopes(
                                    events, aggregateType, currentVersion, metadata);

                            log.debug("Created {} event envelopes, inserting into database", envelopes.size());
                            return insertEvents(envelopes)
                                    .then(saveToOutboxIfEnabled(envelopes))
                                    .thenReturn(EventStream.of(aggregateId, aggregateType, envelopes))
                                    .transform(mono -> EventSourcingTransactionalAspect.addPendingEvents(mono, envelopes));
                        })
        )
        .doOnSuccess(stream -> {
            long duration = System.currentTimeMillis() - startTime;
            EventSourcingLoggingContext.setDuration(duration);
            log.info("Successfully appended {} events to aggregate {} in {}ms",
                    events.size(), aggregateId, duration);
            EventSourcingLoggingContext.clearAll();
        })
        .doOnError(error -> {
            long duration = System.currentTimeMillis() - startTime;
            EventSourcingLoggingContext.setDuration(duration);

            if (error instanceof ConcurrencyException) {
                log.warn("Concurrency conflict when appending events to aggregate {}: {}",
                        aggregateId, error.getMessage());
            } else {
                log.error("Failed to append events to aggregate {} after {}ms: {}",
                        aggregateId, duration, error.getMessage(), error);
            }
            EventSourcingLoggingContext.clearAll();
        });
    }

    @Override
    public Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType) {
        return loadEventStream(aggregateId, aggregateType, 0L);
    }

    @Override
    public Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType, long fromVersion) {
        long startTime = System.currentTimeMillis();
        EventSourcingLoggingContext.setAggregateContext(aggregateId, aggregateType);
        EventSourcingLoggingContext.setOperation("loadEventStream");

        log.info("Loading event stream for aggregate {} (type: {}), from version: {}",
                aggregateId, aggregateType, fromVersion);

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events
                WHERE aggregate_id = :aggregateId
                  AND aggregate_type = :aggregateType
                  AND aggregate_version >= :fromVersion
                ORDER BY aggregate_version ASC
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", aggregateType)
                .bind("fromVersion", fromVersion)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all()
                .collectList()
                .map(envelopes -> EventStream.of(aggregateId, aggregateType, envelopes))
                .doOnSuccess(stream -> {
                    long duration = System.currentTimeMillis() - startTime;
                    EventSourcingLoggingContext.setDuration(duration);
                    log.info("Loaded {} events for aggregate {} in {}ms",
                            stream.size(), aggregateId, duration);
                    EventSourcingLoggingContext.clearAll();
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("Failed to load event stream for aggregate {} after {}ms: {}",
                            aggregateId, duration, error.getMessage(), error);
                    EventSourcingLoggingContext.clearAll();
                });
    }

    @Override
    public Mono<EventStream> loadEventStream(UUID aggregateId, String aggregateType, 
                                           long fromVersion, long toVersion) {
        log.debug("Loading event stream for aggregate {}, versions {} to {}", 
                 aggregateId, fromVersion, toVersion);

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE aggregate_id = :aggregateId 
                  AND aggregate_type = :aggregateType 
                  AND aggregate_version >= :fromVersion 
                  AND aggregate_version <= :toVersion
                ORDER BY aggregate_version ASC
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", aggregateType)
                .bind("fromVersion", fromVersion)
                .bind("toVersion", toVersion)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all()
                .collectList()
                .map(envelopes -> EventStream.of(aggregateId, aggregateType, envelopes));
    }

    @Override
    public Mono<Long> getAggregateVersion(UUID aggregateId, String aggregateType) {
        String sql = """
                SELECT COALESCE(MAX(aggregate_version), -1) as version
                FROM events 
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", aggregateType)
                .map(row -> row.get("version", Long.class))
                .one()
                .defaultIfEmpty(-1L);
    }

    @Override
    public Mono<Boolean> aggregateExists(UUID aggregateId, String aggregateType) {
        return getAggregateVersion(aggregateId, aggregateType)
                .map(version -> version >= 0);
    }

    @Override
    public Flux<StoredEventEnvelope> streamAllEvents() {
        return streamAllEvents(0L);
    }

    @Override
    public Flux<StoredEventEnvelope> streamAllEvents(long fromSequence) {
        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE global_sequence >= :fromSequence
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("fromSequence", fromSequence)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Flux<StoredEventEnvelope> streamEventsByType(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return Flux.empty();
        }

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE event_type = ANY(:eventTypes)
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("eventTypes", eventTypes.toArray(new String[0]))
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Flux<StoredEventEnvelope> streamEventsByAggregateType(List<String> aggregateTypes) {
        if (aggregateTypes == null || aggregateTypes.isEmpty()) {
            return Flux.empty();
        }

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE aggregate_type = ANY(:aggregateTypes)
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("aggregateTypes", aggregateTypes.toArray(new String[0]))
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Flux<StoredEventEnvelope> streamEventsByTimeRange(Instant from, Instant to) {
        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events 
                WHERE created_at >= :from AND created_at <= :to
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Flux<StoredEventEnvelope> streamEventsByMetadata(Map<String, Object> metadataCriteria) {
        if (metadataCriteria == null || metadataCriteria.isEmpty()) {
            return streamAllEvents();
        }

        // Build a JSONB containment query: metadata @> '{"key":"value"}'::jsonb
        // This uses PostgreSQL's JSONB containment operator for efficient indexed lookups.
        String criteriaJson;
        try {
            criteriaJson = objectMapper.writeValueAsString(metadataCriteria);
        } catch (JsonProcessingException e) {
            return Flux.error(new EventStoreException("Failed to serialize metadata criteria", e));
        }

        String sql = """
                SELECT event_id, aggregate_id, aggregate_type, aggregate_version, global_sequence,
                       event_type, event_data, metadata, created_at
                FROM events
                WHERE metadata::jsonb @> :criteriaJson::jsonb
                ORDER BY global_sequence ASC
                """;

        return databaseClient.sql(sql)
                .bind("criteriaJson", criteriaJson)
                .map((row, metadata) -> mapToEventEnvelope(row, metadata))
                .all();
    }

    @Override
    public Mono<Long> getCurrentGlobalSequence() {
        String sql = "SELECT COALESCE(MAX(global_sequence), 0) as max_sequence FROM events";
        
        return databaseClient.sql(sql)
                .map(row -> row.get("max_sequence", Long.class))
                .one()
                .defaultIfEmpty(-1L);
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return databaseClient.sql("SELECT 1")
                .fetch()
                .first()
                .map(row -> true)
                .onErrorReturn(false);
    }

    @Override
    public Mono<EventStoreStatistics> getStatistics() {
        String sql = """
                SELECT 
                    COUNT(*) as total_events,
                    COUNT(DISTINCT aggregate_id) as total_aggregates,
                    MAX(global_sequence) as current_global_sequence
                FROM events
                """;

        return databaseClient.sql(sql)
                .map(row -> EventStoreStatistics.builder()
                        .totalEvents(row.get("total_events", Long.class))
                        .totalAggregates(row.get("total_aggregates", Long.class))
                        .currentGlobalSequence(row.get("current_global_sequence", Long.class))
                        .build())
                .one();
    }

    // Private helper methods

    private Mono<Long> checkConcurrency(UUID aggregateId, String aggregateType, long expectedVersion) {
        return getAggregateVersion(aggregateId, aggregateType)
                .flatMap(currentVersion -> {
                    if (currentVersion != expectedVersion) {
                        return Mono.error(new ConcurrencyException(
                                aggregateId, aggregateType, expectedVersion, currentVersion));
                    }
                    return Mono.just(currentVersion);
                });
    }

    private List<StoredEventEnvelope> createEventEnvelopes(List<Event> events, String aggregateType,
                                                    long baseVersion, Map<String, Object> metadata) {
        return java.util.stream.IntStream.range(0, events.size())
                .mapToObj(i -> {
                    long version = baseVersion + i + 1;
                    // globalSequence is 0 here — the DB assigns the real value via BIGSERIAL
                    return StoredEventEnvelope.of(events.get(i), aggregateType, version, 0L, metadata);
                })
                .toList();
    }
    
    /**
     * Converts StoredEventEnvelope to EventEntity for database persistence using R2DBC.
     */
    private EventEntity toEventEntity(StoredEventEnvelope envelope) {
        try {
            EventEntity entity = new EventEntity();
            entity.setEventId(envelope.getEventId());
            entity.setAggregateId(envelope.getAggregateId());
            entity.setAggregateType(envelope.getAggregateType());
            entity.setAggregateVersion(envelope.getAggregateVersion());
            entity.setGlobalSequence(envelope.getGlobalSequence());
            entity.setEventType(envelope.getEventType());
            entity.setEventData(serializeEvent(envelope.getEvent()));
            entity.setMetadata(serializeMetadata(envelope.getMetadata()));
            entity.setCreatedAt(envelope.getCreatedAt());

            // Set production fields from MDC context if available
            entity.setCorrelationId(org.slf4j.MDC.get(EventSourcingLoggingContext.CORRELATION_ID));
            entity.setTenantId(org.slf4j.MDC.get(EventSourcingLoggingContext.TENANT_ID));
            entity.setCreatedBy(org.slf4j.MDC.get(EventSourcingLoggingContext.USER_ID));

            return entity;
        } catch (JsonProcessingException e) {
            throw new EventStoreException("Failed to serialize event for database persistence", e);
        }
    }
    
    private Mono<Void> insertEvents(List<StoredEventEnvelope> envelopes) {
        String sql = """
                INSERT INTO events (event_id, aggregate_id, aggregate_type, aggregate_version,
                                  event_type, event_data, metadata, created_at)
                VALUES (:eventId, :aggregateId, :aggregateType, :aggregateVersion,
                       :eventType, :eventData, :metadata, :createdAt)
                """;

        return Flux.fromIterable(envelopes)
                .flatMap(envelope -> {
                    try {
                        String serializedMetadata = serializeMetadata(envelope.getMetadata());
                        String eventDataJson = serializeEvent(envelope.getEvent());

                        var spec = databaseClient.sql(sql)
                                .bind("eventId", envelope.getEventId())
                                .bind("aggregateId", envelope.getAggregateId())
                                .bind("aggregateType", envelope.getAggregateType())
                                .bind("aggregateVersion", envelope.getAggregateVersion())
                                .bind("eventType", envelope.getEventType())
                                .bind("createdAt", envelope.getCreatedAt());
                        
                        // Use helper method to bind JSON data
                        spec = bindJsonData(spec, "eventData", eventDataJson);
                        spec = bindJsonData(spec, "metadata", serializedMetadata);
                        
                        return spec.fetch().rowsUpdated();
                    } catch (Exception e) {
                        return Mono.error(new EventStoreException("Failed to serialize event", e));
                    }
                })
                .then();
    }

    private StoredEventEnvelope mapToEventEnvelope(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        try {
            UUID eventId = row.get("event_id", UUID.class);
            UUID aggregateId = row.get("aggregate_id", UUID.class);
            String aggregateType = row.get("aggregate_type", String.class);
            Long aggregateVersion = row.get("aggregate_version", Long.class);
            Long globalSequence = row.get("global_sequence", Long.class);
            String eventType = row.get("event_type", String.class);
            // Read TEXT columns directly (database-agnostic)
            String eventData = row.get("event_data", String.class);
            String metadataJson = row.get("metadata", String.class);
            Instant createdAt = row.get("created_at", Instant.class);

            Event event = deserializeEvent(eventData, eventType);
            Map<String, Object> eventMetadata = deserializeMetadata(metadataJson);

            return StoredEventEnvelope.builder()
                    .eventId(eventId)
                    .event(event)
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .aggregateVersion(aggregateVersion)
                    .globalSequence(globalSequence)
                    .eventType(eventType)
                    .createdAt(createdAt)
                    .metadata(eventMetadata)
                    .build();

        } catch (Exception e) {
            throw new EventStoreException("Failed to map database row to StoredEventEnvelope", e);
        }
    }

    private String serializeEvent(Event event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }

    private String serializeMetadata(Map<String, Object> metadata) throws JsonProcessingException {
        return metadata != null && !metadata.isEmpty() ? 
               objectMapper.writeValueAsString(metadata) : null;
    }

    private Event deserializeEvent(String eventData, String eventType) throws JsonProcessingException {
        try {
            // First try to deserialize as is - this works if the event type is properly registered
            return objectMapper.readValue(eventData, Event.class);
        } catch (Exception e) {
            // If that fails, try to create a generic event wrapper
            log.warn("Failed to deserialize event of type '{}', using generic wrapper: {}", eventType, e.getMessage());
            return new GenericEvent(eventType, eventData);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeMetadata(String metadataJson) throws JsonProcessingException {
        if (metadataJson == null || metadataJson.trim().isEmpty()) {
            return Map.of();
        }
        return objectMapper.readValue(metadataJson, Map.class);
    }
    
    /**
     * Binds JSON data to the database client spec as TEXT.
     * Database-agnostic approach using TEXT columns for JSON data.
     */
    private DatabaseClient.GenericExecuteSpec bindJsonData(DatabaseClient.GenericExecuteSpec spec,
                                                          String paramName, String jsonData) {
        if (jsonData == null) {
            return spec.bindNull(paramName, String.class);
        }
        return spec.bind(paramName, jsonData);
    }

    /**
     * Saves event envelopes to the outbox if the outbox service is enabled.
     * This is called within the same transaction as the event store write,
     * ensuring atomicity between event persistence and outbox entry creation.
     *
     * @param envelopes the event envelopes to save to outbox
     * @return mono that completes when all envelopes are saved to outbox
     */
    private Mono<Void> saveToOutboxIfEnabled(List<StoredEventEnvelope> envelopes) {
        if (outboxService == null) {
            log.debug("Outbox service not configured, skipping outbox write");
            return Mono.empty();
        }

        log.debug("Saving {} events to outbox", envelopes.size());
        return Flux.fromIterable(envelopes)
                .flatMap(envelope -> outboxService.saveToOutbox(envelope))
                .then()
                .doOnSuccess(v -> log.debug("Successfully saved {} events to outbox", envelopes.size()))
                .doOnError(error -> log.error("Failed to save events to outbox", error));
    }
}
