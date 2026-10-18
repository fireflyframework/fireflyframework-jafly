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

import org.fireflyframework.eventsourcing.store.r2dbc.EventOutboxEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC Repository for Event Outbox operations.
 * <p>
 * This repository provides reactive database access to the event_outbox table,
 * supporting the Transactional Outbox Pattern for reliable event publishing.
 * <p>
 * Key operations:
 * - Query pending and failed entries for processing
 * - Update entry status (PENDING -> PROCESSING -> COMPLETED/FAILED)
 * - Support priority-based and partition-based processing
 * - Cleanup completed entries
 * - Query statistics and monitoring data
 */
public interface EventOutboxRepository extends R2dbcRepository<EventOutboxEntity, UUID> {

    /**
     * Find all pending outbox entries ordered by priority and creation time.
     *
     * @param limit maximum number of entries to return
     * @return flux of pending entries
     */
    @Query("""
            SELECT * FROM event_outbox 
            WHERE status = 'PENDING' 
            ORDER BY priority DESC, created_at ASC 
            LIMIT :limit
            """)
    Flux<EventOutboxEntity> findPendingEntries(@Param("limit") int limit);

    /**
     * Find failed entries that are ready for retry.
     *
     * @param now current timestamp
     * @param limit maximum number of entries to return
     * @return flux of entries ready for retry
     */
    @Query("""
            SELECT * FROM event_outbox 
            WHERE status = 'FAILED' 
              AND next_retry_at <= :now 
              AND retry_count < max_retries
            ORDER BY priority DESC, next_retry_at ASC 
            LIMIT :limit
            """)
    Flux<EventOutboxEntity> findEntriesReadyForRetry(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * Find entries by status.
     *
     * @param status the status to filter by
     * @return flux of entries with the specified status
     */
    Flux<EventOutboxEntity> findByStatus(String status);

    /**
     * Find entries by aggregate ID.
     *
     * @param aggregateId the aggregate identifier
     * @return flux of entries for the aggregate
     */
    Flux<EventOutboxEntity> findByAggregateId(UUID aggregateId);

    /**
     * Find entries by tenant ID.
     *
     * @param tenantId the tenant identifier
     * @return flux of entries for the tenant
     */
    Flux<EventOutboxEntity> findByTenantId(String tenantId);

    /**
     * Find entries by correlation ID.
     *
     * @param correlationId the correlation identifier
     * @return flux of entries with the correlation ID
     */
    Flux<EventOutboxEntity> findByCorrelationId(String correlationId);

    /**
     * Find entries by partition key.
     *
     * @param partitionKey the partition key
     * @return flux of entries with the partition key
     */
    Flux<EventOutboxEntity> findByPartitionKey(String partitionKey);

    /**
     * Count entries by status.
     *
     * @param status the status to count
     * @return mono with the count
     */
    Mono<Long> countByStatus(String status);

    /**
     * Find failed entries that have exceeded max retries (dead letter queue).
     *
     * @return flux of permanently failed entries
     */
    @Query("""
            SELECT * FROM event_outbox 
            WHERE status = 'FAILED' 
              AND retry_count >= max_retries
            ORDER BY created_at ASC
            """)
    Flux<EventOutboxEntity> findDeadLetterEntries();

    /**
     * Delete completed entries older than the specified timestamp.
     *
     * @param olderThan cutoff timestamp
     * @return mono with the number of deleted entries
     */
    @Query("""
            DELETE FROM event_outbox 
            WHERE status = 'COMPLETED' 
              AND processed_at < :olderThan
            """)
    Mono<Long> deleteCompletedEntriesOlderThan(@Param("olderThan") Instant olderThan);

    /**
     * Find entries created within a time range.
     *
     * @param from start time (inclusive)
     * @param to end time (inclusive)
     * @return flux of entries within the time range
     */
    Flux<EventOutboxEntity> findByCreatedAtBetween(Instant from, Instant to);

    /**
     * Find entries by event type.
     *
     * @param eventType the event type
     * @return flux of entries with the event type
     */
    Flux<EventOutboxEntity> findByEventType(String eventType);

    /**
     * Find entries by aggregate type.
     *
     * @param aggregateType the aggregate type
     * @return flux of entries with the aggregate type
     */
    Flux<EventOutboxEntity> findByAggregateType(String aggregateType);

    /**
     * Find entries by status and tenant ID.
     *
     * @param status the status
     * @param tenantId the tenant identifier
     * @return flux of entries matching both criteria
     */
    Flux<EventOutboxEntity> findByStatusAndTenantId(String status, String tenantId);

    /**
     * Find entries by status and partition key.
     *
     * @param status the status
     * @param partitionKey the partition key
     * @return flux of entries matching both criteria
     */
    Flux<EventOutboxEntity> findByStatusAndPartitionKey(String status, String partitionKey);
}

