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

package org.fireflyframework.data.integration;

import org.fireflyframework.data.enrichment.EnricherFallback;
import org.fireflyframework.data.enrichment.FallbackEnrichmentExecutor;
import org.fireflyframework.data.enrichment.FallbackStrategy;
import org.fireflyframework.data.lineage.InMemoryLineageTracker;
import org.fireflyframework.data.lineage.LineageRecord;
import org.fireflyframework.data.model.EnrichmentRequest;
import org.fireflyframework.data.model.EnrichmentResponse;
import org.fireflyframework.data.model.EnrichmentStrategy;
import org.fireflyframework.data.quality.DataQualityEngine;
import org.fireflyframework.data.quality.DataQualityRule;
import org.fireflyframework.data.quality.QualitySeverity;
import org.fireflyframework.data.quality.QualityStrategy;
import org.fireflyframework.data.quality.rules.NotNullRule;
import org.fireflyframework.data.quality.rules.PatternRule;
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataEnricherRegistry;
import org.fireflyframework.data.transform.TransformContext;
import org.fireflyframework.data.transform.TransformationChain;
import org.fireflyframework.data.transform.transformers.ComputedFieldTransformer;
import org.fireflyframework.data.transform.transformers.FieldMappingTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-stack integration test that exercises the entire data pipeline:
 * enrichment with fallback, quality gate validation, transformation chain,
 * and lineage recording.
 */
@ExtendWith(MockitoExtension.class)
class FullStackIntegrationTest {

    /**
     * Annotated primary enricher that falls back to "fallback-provider"
     * when the primary returns empty results (zero fields enriched).
     */
    @EnricherFallback(fallbackTo = "fallback-provider", strategy = FallbackStrategy.ON_EMPTY)
    abstract static class PrimaryEnricherWithEmptyFallback extends DataEnricher<Object, Object, Object> {
        PrimaryEnricherWithEmptyFallback() { super(null, null, null, Object.class); }
    }

    @Test
    void fullFlow_enrichWithFallback_qualityGate_transform_lineage() {
        // ---- Step 1: Enrichment with fallback ----

        // Primary enricher returns success but with 0 fields enriched (empty result)
        DataEnricher<?, ?, ?> primary = mock(PrimaryEnricherWithEmptyFallback.class);
        EnrichmentResponse emptyResponse = EnrichmentResponse.builder()
                .success(true)
                .providerName("primary-provider")
                .type("company-profile")
                .fieldsEnriched(0)
                .enrichedData(Map.of())
                .message("No data found")
                .build();
        when(primary.enrich(any())).thenReturn(Mono.just(emptyResponse));
        when(primary.getProviderName()).thenReturn("primary-provider");

        // Fallback enricher returns enriched data
        DataEnricher<?, ?, ?> fallback = mock(DataEnricher.class);
        Map<String, Object> enrichedData = new HashMap<>();
        enrichedData.put("first_name", "Alice");
        enrichedData.put("last_name", "Smith");
        enrichedData.put("email_address", "alice@example.com");

        EnrichmentResponse fallbackResponse = EnrichmentResponse.builder()
                .success(true)
                .providerName("fallback-provider")
                .type("company-profile")
                .fieldsEnriched(3)
                .enrichedData(enrichedData)
                .metadata(new HashMap<>())
                .message("Enrichment successful")
                .build();
        when(fallback.enrich(any())).thenReturn(Mono.just(fallbackResponse));

        // Registry returns fallback when asked
        DataEnricherRegistry registry = mock(DataEnricherRegistry.class);
        when(registry.getEnricherByProvider("fallback-provider")).thenReturn(Optional.of(fallback));

        FallbackEnrichmentExecutor executor = new FallbackEnrichmentExecutor(registry);
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", "C-12345"))
                .requestId("full-stack-test-001")
                .build();

        // ---- Step 2: Quality gate with real rules ----
        @SuppressWarnings("unchecked")
        DataQualityRule<Map<String, Object>> nameRule = new NotNullRule<>(
                "first_name",
                m -> m.get("first_name"),
                QualitySeverity.CRITICAL
        );

        @SuppressWarnings("unchecked")
        DataQualityRule<Map<String, Object>> emailRule = new PatternRule<>(
                "email_address",
                Pattern.compile(".+@.+\\..+"),
                m -> (String) m.get("email_address"),
                QualitySeverity.CRITICAL
        );

        DataQualityEngine qualityEngine = new DataQualityEngine(List.of(nameRule, emailRule));

        // ---- Step 3: Transformation chain ----
        TransformationChain<Map<String, Object>, Map<String, Object>> chain =
                TransformationChain.<Map<String, Object>, Map<String, Object>>create()
                        .then(new FieldMappingTransformer(Map.of(
                                "first_name", "firstName",
                                "last_name", "lastName",
                                "email_address", "email"
                        )))
                        .then(new ComputedFieldTransformer("fullName",
                                m -> m.get("firstName") + " " + m.get("lastName")));

        TransformContext transformContext = TransformContext.builder()
                .requestId("full-stack-test-001")
                .build();

        // ---- Step 4: Lineage tracker ----
        InMemoryLineageTracker lineageTracker = new InMemoryLineageTracker();

        // ---- Execute the full pipeline ----
        StepVerifier.create(
                executor.enrichWithFallback(primary, request)
                        .flatMap(response -> {
                            // Verify enrichment succeeded via fallback
                            assertThat(response.isSuccess()).isTrue();
                            assertThat(response.getProviderName()).isEqualTo("fallback-provider");
                            assertThat(response.getFieldsEnriched()).isEqualTo(3);
                            assertThat(response.getMetadata()).containsEntry("fallbackFrom", "primary-provider");

                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = (Map<String, Object>) response.getEnrichedData();

                            // Quality gate
                            return qualityEngine.evaluate(data, QualityStrategy.COLLECT_ALL)
                                    .flatMap(report -> {
                                        assertThat(report.isPassed()).isTrue();
                                        assertThat(report.getTotalRules()).isEqualTo(2);
                                        assertThat(report.getPassedRules()).isEqualTo(2);

                                        // Transform
                                        return chain.execute(data, transformContext);
                                    })
                                    .flatMap(transformed -> {
                                        assertThat(transformed).containsEntry("firstName", "Alice");
                                        assertThat(transformed).containsEntry("lastName", "Smith");
                                        assertThat(transformed).containsEntry("email", "alice@example.com");
                                        assertThat(transformed).containsEntry("fullName", "Alice Smith");
                                        assertThat(transformed).doesNotContainKey("first_name");

                                        // Record lineage
                                        LineageRecord record = LineageRecord.builder()
                                                .recordId(UUID.randomUUID().toString())
                                                .entityId("C-12345")
                                                .sourceSystem("fallback-provider")
                                                .operation("ENRICHMENT+TRANSFORM")
                                                .operatorId("full-stack-pipeline")
                                                .timestamp(Instant.now())
                                                .inputHash("input-hash-abc")
                                                .outputHash("output-hash-xyz")
                                                .metadata(Map.of("pipeline", "full-stack-test"))
                                                .build();

                                        return lineageTracker.record(record)
                                                .thenReturn(transformed);
                                    });
                        })
        )
        .assertNext(result -> {
            assertThat(result).containsEntry("fullName", "Alice Smith");
        })
        .verifyComplete();

        // Verify lineage was recorded
        StepVerifier.create(lineageTracker.getLineage("C-12345").collectList())
                .assertNext(records -> {
                    assertThat(records).hasSize(1);
                    LineageRecord recorded = records.get(0);
                    assertThat(recorded.getEntityId()).isEqualTo("C-12345");
                    assertThat(recorded.getSourceSystem()).isEqualTo("fallback-provider");
                    assertThat(recorded.getOperation()).isEqualTo("ENRICHMENT+TRANSFORM");
                    assertThat(recorded.getOperatorId()).isEqualTo("full-stack-pipeline");
                })
                .verifyComplete();
    }
}
