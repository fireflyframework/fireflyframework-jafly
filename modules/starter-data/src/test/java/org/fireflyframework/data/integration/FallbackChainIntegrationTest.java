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
import org.fireflyframework.data.model.EnrichmentRequest;
import org.fireflyframework.data.model.EnrichmentResponse;
import org.fireflyframework.data.model.EnrichmentStrategy;
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataEnricherRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for enrichment fallback chains, verifying that primary
 * and fallback enrichers are invoked correctly in various scenarios.
 */
@ExtendWith(MockitoExtension.class)
class FallbackChainIntegrationTest {

    @Mock
    private DataEnricherRegistry registry;

    private FallbackEnrichmentExecutor executor;
    private EnrichmentRequest request;

    /**
     * Primary enricher annotated with ON_ERROR fallback strategy.
     */
    @EnricherFallback(fallbackTo = "backup-provider", strategy = FallbackStrategy.ON_ERROR)
    abstract static class PrimaryWithOnErrorFallback extends DataEnricher<Object, Object, Object> {
        PrimaryWithOnErrorFallback() { super(null, null, null, Object.class); }
    }

    /**
     * Backup enricher that also declares a fallback (chained).
     */
    @EnricherFallback(fallbackTo = "last-resort-provider", strategy = FallbackStrategy.ON_ERROR)
    abstract static class BackupWithChainedFallback extends DataEnricher<Object, Object, Object> {
        BackupWithChainedFallback() { super(null, null, null, Object.class); }
    }

    @BeforeEach
    void setUp() {
        executor = new FallbackEnrichmentExecutor(registry);
        request = EnrichmentRequest.builder()
                .type("credit-report")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", "C-99999"))
                .requestId("fallback-chain-test")
                .build();
    }

    @Test
    void fallbackChain_primaryFails_fallbackSucceeds() {
        // Given - primary returns error, fallback returns success
        DataEnricher<?, ?, ?> primary = mock(PrimaryWithOnErrorFallback.class);
        DataEnricher<?, ?, ?> backup = mock(DataEnricher.class);

        EnrichmentResponse errorResponse = EnrichmentResponse.failure(
                "credit-report", "primary-provider", "Connection timeout");

        Map<String, Object> fallbackData = Map.of("creditScore", 720, "rating", "A");
        EnrichmentResponse successResponse = EnrichmentResponse.builder()
                .success(true)
                .providerName("backup-provider")
                .type("credit-report")
                .fieldsEnriched(2)
                .enrichedData(fallbackData)
                .metadata(new HashMap<>())
                .message("Backup enrichment successful")
                .build();

        when(primary.enrich(any())).thenReturn(Mono.just(errorResponse));
        when(primary.getProviderName()).thenReturn("primary-provider");
        when(backup.enrich(any())).thenReturn(Mono.just(successResponse));
        when(registry.getEnricherByProvider("backup-provider")).thenReturn(Optional.of(backup));

        // When & Then
        StepVerifier.create(executor.enrichWithFallback(primary, request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getProviderName()).isEqualTo("backup-provider");
                    assertThat(response.getFieldsEnriched()).isEqualTo(2);
                    assertThat(response.getMetadata()).containsEntry("fallbackFrom", "primary-provider");
                    assertThat(response.getMetadata()).containsEntry("fallbackDepth", "1");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.getEnrichedData();
                    assertThat(data).containsEntry("creditScore", 720);
                    assertThat(data).containsEntry("rating", "A");
                })
                .verifyComplete();

        verify(primary).enrich(request);
        verify(backup).enrich(request);
    }

    @Test
    void fallbackChain_allFail_returnsLastFailure() {
        // Given - primary fails, backup also fails, both with ON_ERROR strategy
        DataEnricher<?, ?, ?> primary = mock(PrimaryWithOnErrorFallback.class);
        DataEnricher<?, ?, ?> backup = mock(BackupWithChainedFallback.class);
        DataEnricher<?, ?, ?> lastResort = mock(DataEnricher.class);

        EnrichmentResponse primaryFailure = EnrichmentResponse.failure(
                "credit-report", "primary-provider", "Primary unavailable");
        EnrichmentResponse backupFailure = EnrichmentResponse.failure(
                "credit-report", "backup-provider", "Backup also unavailable");
        EnrichmentResponse lastResortFailure = EnrichmentResponse.failure(
                "credit-report", "last-resort-provider", "Last resort failed too");

        when(primary.enrich(any())).thenReturn(Mono.just(primaryFailure));
        when(primary.getProviderName()).thenReturn("primary-provider");
        when(backup.enrich(any())).thenReturn(Mono.just(backupFailure));
        when(backup.getProviderName()).thenReturn("backup-provider");
        when(lastResort.enrich(any())).thenReturn(Mono.just(lastResortFailure));
        when(registry.getEnricherByProvider("backup-provider")).thenReturn(Optional.of(backup));
        when(registry.getEnricherByProvider("last-resort-provider")).thenReturn(Optional.of(lastResort));

        // When & Then - should return the last failure from the chain
        StepVerifier.create(executor.enrichWithFallback(primary, request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getError()).isNotNull();
                })
                .verifyComplete();

        // All enrichers in the chain should have been called
        verify(primary).enrich(request);
        verify(backup).enrich(request);
        verify(lastResort).enrich(request);
    }

    @Test
    void fallbackChain_primarySucceeds_noFallbackTriggered() {
        // Given - primary succeeds, no fallback should be attempted
        DataEnricher<?, ?, ?> primary = mock(PrimaryWithOnErrorFallback.class);

        Map<String, Object> primaryData = Map.of("creditScore", 800, "rating", "AAA");
        EnrichmentResponse successResponse = EnrichmentResponse.builder()
                .success(true)
                .providerName("primary-provider")
                .type("credit-report")
                .fieldsEnriched(2)
                .enrichedData(primaryData)
                .message("Primary enrichment successful")
                .build();

        when(primary.enrich(any())).thenReturn(Mono.just(successResponse));

        // When & Then
        StepVerifier.create(executor.enrichWithFallback(primary, request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getProviderName()).isEqualTo("primary-provider");
                    assertThat(response.getFieldsEnriched()).isEqualTo(2);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.getEnrichedData();
                    assertThat(data).containsEntry("creditScore", 800);
                    assertThat(data).containsEntry("rating", "AAA");
                })
                .verifyComplete();

        // Registry should never be consulted for fallback
        verify(registry, never()).getEnricherByProvider(any());
    }
}
