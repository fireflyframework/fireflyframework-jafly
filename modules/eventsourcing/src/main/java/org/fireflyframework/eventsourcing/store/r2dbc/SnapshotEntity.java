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
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity class representing an aggregate snapshot in the database for R2DBC operations.
 * This entity maps to the "snapshots" table and is used with fireflyframework-r2dbc utilities.
 * 
 * <p>Production-ready entity with full support for:
 * <ul>
 *   <li>Audit trails (created_by, updated_at)</li>
 *   <li>Multi-tenancy (tenant_id)</li>
 *   <li>Compression (compression_type, is_compressed)</li>
 *   <li>Data integrity (checksum, snapshot_size_bytes)</li>
 * </ul>
 * 
 * <p>Snapshots are used to optimize aggregate reconstruction by capturing
 * the complete state at a specific version, reducing the number of events
 * that need to be replayed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("snapshots")
public class SnapshotEntity {

    // Core snapshot fields
    @Column("aggregate_id")
    private UUID aggregateId;
    
    @Column("aggregate_type")
    private String aggregateType;
    
    @Column("aggregate_version")
    private Long aggregateVersion;
    
    @Column("snapshot_data")
    private String snapshotData;
    
    @Column("created_at")
    private Instant createdAt;
    
    // Production enhancements (V5 migration)
    @Column("updated_at")
    private Instant updatedAt;
    
    @Column("created_by")
    private String createdBy;
    
    @Column("tenant_id")
    private String tenantId;
    
    @Column("snapshot_size_bytes")
    private Integer snapshotSizeBytes;
    
    @Column("checksum")
    private String checksum;
    
    @Column("compression_type")
    private String compressionType;
    
    @Column("is_compressed")
    private Boolean isCompressed;
}

