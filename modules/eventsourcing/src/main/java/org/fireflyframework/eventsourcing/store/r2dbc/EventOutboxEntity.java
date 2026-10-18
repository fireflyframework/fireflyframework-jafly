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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity class representing an event outbox entry in the database for R2DBC operations.
 * This entity maps to the "event_outbox" table and implements the Transactional Outbox Pattern.
 * 
 * <p>The Transactional Outbox Pattern ensures reliable event publishing by:
 * <ul>
 *   <li>Storing events in the same transaction as the aggregate changes</li>
 *   <li>Processing events asynchronously with retry logic</li>
 *   <li>Guaranteeing at-least-once delivery semantics</li>
 *   <li>Supporting priority-based processing</li>
 * </ul>
 * 
 * <p>Production-ready entity with full support for:
 * <ul>
 *   <li>Audit trails (updated_at)</li>
 *   <li>Multi-tenancy (tenant_id)</li>
 *   <li>Distributed tracing (correlation_id)</li>
 *   <li>Priority processing (priority)</li>
 *   <li>Retry management (retry_count, max_retries, next_retry_at)</li>
 *   <li>Partitioning (partition_key)</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("event_outbox")
public class EventOutboxEntity implements org.springframework.data.domain.Persistable<java.util.UUID> {

    // Core outbox fields
    @Id
    @Column("outbox_id")
    private UUID outboxId;
    
    @Column("aggregate_id")
    private UUID aggregateId;
    
    @Column("aggregate_type")
    private String aggregateType;
    
    @Column("event_type")
    private String eventType;
    
    @Column("event_data")
    private String eventData;
    
    @Column("metadata")
    private String metadata;
    
    @Column("status")
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    
    @Column("created_at")
    private Instant createdAt;
    
    @Column("processed_at")
    private Instant processedAt;
    
    @Column("retry_count")
    private Integer retryCount;
    
    @Column("last_error")
    private String lastError;
    
    @Column("next_retry_at")
    private Instant nextRetryAt;
    
    // Production enhancements (V6 migration)
    @Column("updated_at")
    private Instant updatedAt;
    
    @Column("tenant_id")
    private String tenantId;
    
    @Column("correlation_id")
    private String correlationId;
    
    @Column("priority")
    private Integer priority; // 1-10, higher is more important
    
    @Column("max_retries")
    private Integer maxRetries;
    
    @Column("partition_key")
    private String partitionKey;
    
    /**
     * Outbox entry status enumeration.
     */
    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    @Override
    public UUID getId() {
        return outboxId;
    }

    @Override
    public boolean isNew() {
        // Entity is new if it doesn't have a processed_at timestamp
        return processedAt == null;
    }
}
