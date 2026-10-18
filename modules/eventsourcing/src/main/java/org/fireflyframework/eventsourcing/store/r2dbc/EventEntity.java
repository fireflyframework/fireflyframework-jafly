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
 * Entity class representing an event in the database for R2DBC operations.
 * This entity maps to the "events" table and is used with fireflyframework-r2dbc utilities.
 *
 * <p>Production-ready entity with full support for:
 * <ul>
 *   <li>Audit trails (created_by, updated_at)</li>
 *   <li>Multi-tenancy (tenant_id)</li>
 *   <li>Distributed tracing (correlation_id, causation_id)</li>
 *   <li>Data integrity (checksum, event_size_bytes)</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("events")
public class EventEntity {

    // Core event fields
    @Id
    @Column("event_id")
    private UUID eventId;

    @Column("aggregate_id")
    private UUID aggregateId;

    @Column("aggregate_type")
    private String aggregateType;

    @Column("aggregate_version")
    private Long aggregateVersion;

    @Column("global_sequence")
    private Long globalSequence;

    @Column("event_type")
    private String eventType;

    @Column("event_data")
    private String eventData;

    @Column("metadata")
    private String metadata;

    @Column("created_at")
    private Instant createdAt;

    // Production enhancements (V4 migration)
    @Column("updated_at")
    private Instant updatedAt;

    @Column("created_by")
    private String createdBy;

    @Column("tenant_id")
    private String tenantId;

    @Column("correlation_id")
    private String correlationId;

    @Column("causation_id")
    private String causationId;

    @Column("event_size_bytes")
    private Integer eventSizeBytes;

    @Column("checksum")
    private String checksum;
}