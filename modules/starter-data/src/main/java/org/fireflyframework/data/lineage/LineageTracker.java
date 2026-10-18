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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port interface for tracking data lineage and provenance.
 *
 * <p>Implementations of this interface record the provenance chain of data entities
 * as they flow through enrichments, transformations, and job collections. This enables
 * full auditability of how data was produced and modified.</p>
 */
public interface LineageTracker {

    /**
     * Records a lineage entry for a data operation.
     *
     * @param record the lineage record to persist
     * @return a Mono that completes when the record has been stored
     */
    Mono<Void> record(LineageRecord record);

    /**
     * Retrieves the complete lineage chain for a business entity.
     *
     * @param entityId the business entity identifier
     * @return a Flux emitting all lineage records for the entity
     */
    Flux<LineageRecord> getLineage(String entityId);

    /**
     * Retrieves all lineage records produced by a specific operator.
     *
     * @param operatorId the operator identifier (enricher name or job name)
     * @return a Flux emitting all lineage records from the operator
     */
    Flux<LineageRecord> getLineageByOperator(String operatorId);
}
