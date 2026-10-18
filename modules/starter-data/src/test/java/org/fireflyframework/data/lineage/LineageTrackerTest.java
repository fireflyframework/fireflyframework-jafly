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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InMemoryLineageTracker}.
 */
class LineageTrackerTest {

    private InMemoryLineageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryLineageTracker();
    }

    @Test
    void record_shouldStoreLineageRecord() {
        // Given
        LineageRecord record = LineageRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .entityId("entity-001")
                .sourceSystem("provider-alpha")
                .operation("ENRICHMENT")
                .operatorId("company-profile-enricher")
                .timestamp(Instant.now())
                .inputHash("abc123")
                .outputHash("def456")
                .traceId("trace-001")
                .metadata(Map.of("source", "api"))
                .build();

        // When & Then
        StepVerifier.create(tracker.record(record))
                .verifyComplete();

        StepVerifier.create(tracker.getLineage("entity-001"))
                .assertNext(retrieved -> {
                    assertThat(retrieved.getRecordId()).isEqualTo(record.getRecordId());
                    assertThat(retrieved.getEntityId()).isEqualTo("entity-001");
                    assertThat(retrieved.getSourceSystem()).isEqualTo("provider-alpha");
                    assertThat(retrieved.getOperation()).isEqualTo("ENRICHMENT");
                    assertThat(retrieved.getOperatorId()).isEqualTo("company-profile-enricher");
                    assertThat(retrieved.getInputHash()).isEqualTo("abc123");
                    assertThat(retrieved.getOutputHash()).isEqualTo("def456");
                    assertThat(retrieved.getTraceId()).isEqualTo("trace-001");
                    assertThat(retrieved.getMetadata()).containsEntry("source", "api");
                })
                .verifyComplete();
    }

    @Test
    void getLineage_shouldReturnAllRecordsForEntity() {
        // Given
        String entityId = "entity-002";

        LineageRecord first = LineageRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .entityId(entityId)
                .sourceSystem("provider-alpha")
                .operation("ENRICHMENT")
                .operatorId("enricher-a")
                .timestamp(Instant.now())
                .inputHash("hash-1")
                .outputHash("hash-2")
                .build();

        LineageRecord second = LineageRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .entityId(entityId)
                .sourceSystem("provider-beta")
                .operation("TRANSFORMATION")
                .operatorId("transformer-b")
                .timestamp(Instant.now())
                .inputHash("hash-2")
                .outputHash("hash-3")
                .build();

        LineageRecord third = LineageRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .entityId(entityId)
                .sourceSystem("provider-gamma")
                .operation("JOB_COLLECTION")
                .operatorId("collector-c")
                .timestamp(Instant.now())
                .inputHash("hash-3")
                .outputHash("hash-4")
                .build();

        // When
        StepVerifier.create(
                tracker.record(first)
                        .then(tracker.record(second))
                        .then(tracker.record(third))
        ).verifyComplete();

        // Then
        StepVerifier.create(tracker.getLineage(entityId))
                .assertNext(r -> assertThat(r.getOperatorId()).isEqualTo("enricher-a"))
                .assertNext(r -> assertThat(r.getOperatorId()).isEqualTo("transformer-b"))
                .assertNext(r -> assertThat(r.getOperatorId()).isEqualTo("collector-c"))
                .verifyComplete();
    }

    @Test
    void getLineageByOperator_shouldFilterByOperator() {
        // Given
        LineageRecord fromAlpha = LineageRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .entityId("entity-100")
                .sourceSystem("provider-alpha")
                .operation("ENRICHMENT")
                .operatorId("enricher-alpha")
                .timestamp(Instant.now())
                .inputHash("a1")
                .outputHash("a2")
                .build();

        LineageRecord fromBeta = LineageRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .entityId("entity-200")
                .sourceSystem("provider-beta")
                .operation("ENRICHMENT")
                .operatorId("enricher-beta")
                .timestamp(Instant.now())
                .inputHash("b1")
                .outputHash("b2")
                .build();

        LineageRecord fromAlphaAgain = LineageRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .entityId("entity-300")
                .sourceSystem("provider-alpha")
                .operation("TRANSFORMATION")
                .operatorId("enricher-alpha")
                .timestamp(Instant.now())
                .inputHash("c1")
                .outputHash("c2")
                .build();

        // When
        StepVerifier.create(
                tracker.record(fromAlpha)
                        .then(tracker.record(fromBeta))
                        .then(tracker.record(fromAlphaAgain))
        ).verifyComplete();

        // Then
        StepVerifier.create(tracker.getLineageByOperator("enricher-alpha").collectList())
                .assertNext(records -> {
                    assertThat(records).hasSize(2);
                    assertThat(records).allMatch(r -> "enricher-alpha".equals(r.getOperatorId()));
                    assertThat(records).extracting(LineageRecord::getEntityId)
                            .containsExactlyInAnyOrder("entity-100", "entity-300");
                })
                .verifyComplete();
    }

    @Test
    void getLineage_shouldReturnEmptyForUnknownEntity() {
        StepVerifier.create(tracker.getLineage("nonexistent-entity"))
                .verifyComplete();
    }
}
