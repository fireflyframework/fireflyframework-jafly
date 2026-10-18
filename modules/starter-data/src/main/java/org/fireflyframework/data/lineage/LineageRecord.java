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

package org.fireflyframework.data.lineage;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record capturing a single step in the provenance chain of a data entity.
 *
 * <p>Each lineage record tracks the transformation or enrichment applied to a business
 * entity, including input/output hashes for change detection and an OpenTelemetry
 * trace ID for distributed tracing correlation.</p>
 */
@Data
@Builder
public class LineageRecord {

    /**
     * Unique identifier for this lineage record (UUID).
     */
    private final String recordId;

    /**
     * Business entity identifier whose provenance is being tracked.
     */
    private final String entityId;

    /**
     * Name of the source system or provider that produced this record.
     */
    private final String sourceSystem;

    /**
     * Type of operation performed (e.g., ENRICHMENT, TRANSFORMATION, JOB_COLLECTION).
     */
    private final String operation;

    /**
     * Identifier of the operator that performed the operation (enricher name or job name).
     */
    private final String operatorId;

    /**
     * Timestamp when this lineage record was created.
     */
    private final Instant timestamp;

    /**
     * Hash of the input data before the operation was applied.
     */
    private final String inputHash;

    /**
     * Hash of the output data after the operation was applied.
     */
    private final String outputHash;

    /**
     * OpenTelemetry trace ID for distributed tracing correlation.
     */
    private final String traceId;

    /**
     * Additional metadata associated with this lineage record.
     */
    private final Map<String, Object> metadata;
}
