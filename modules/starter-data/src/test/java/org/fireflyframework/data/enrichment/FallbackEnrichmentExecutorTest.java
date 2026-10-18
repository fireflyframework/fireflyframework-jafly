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

package org.fireflyframework.data.enrichment;

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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FallbackEnrichmentExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class FallbackEnrichmentExecutorTest {

    @Mock
    private DataEnricherRegistry registry;

    private FallbackEnrichmentExecutor executor;

    private EnrichmentRequest request;

    // Annotated abstract classes for Mockito to preserve @EnricherFallback metadata

    @EnricherFallback(fallbackTo = "backup", strategy = FallbackStrategy.ON_ERROR)
    abstract static class AnnotatedPrimaryOnError extends DataEnricher<Object, Object, Object> {
        AnnotatedPrimaryOnError() { super(null, null, null, Object.class); }
    }

    @EnricherFallback(fallbackTo = "backup", strategy = FallbackStrategy.ON_EMPTY)
    abstract static class AnnotatedPrimaryOnEmpty extends DataEnricher<Object, Object, Object> {
        AnnotatedPrimaryOnEmpty() { super(null, null, null, Object.class); }
    }

    @EnricherFallback(fallbackTo = "fallback-2", strategy = FallbackStrategy.ON_ERROR, maxFallbacks = 3)
    abstract static class ChainedFallback1 extends DataEnricher<Object, Object, Object> {
        ChainedFallback1() { super(null, null, null, Object.class); }
    }

    @EnricherFallback(fallbackTo = "fallback-3", strategy = FallbackStrategy.ON_ERROR, maxFallbacks = 3)
    abstract static class ChainedFallback2 extends DataEnricher<Object, Object, Object> {
        ChainedFallback2() { super(null, null, null, Object.class); }
    }

    @EnricherFallback(fallbackTo = "fallback-4", strategy = FallbackStrategy.ON_ERROR, maxFallbacks = 3)
    abstract static class ChainedFallback3 extends DataEnricher<Object, Object, Object> {
        ChainedFallback3() { super(null, null, null, Object.class); }
    }

    @BeforeEach
    void setUp() {
        executor = new FallbackEnrichmentExecutor(registry);
        request = EnrichmentRequest.builder()
            .type("credit-report")
            .strategy(EnrichmentStrategy.ENHANCE)
            .parameters(Map.of("companyId", "12345"))
            .requestId("test-req-001")
            .build();
    }

    @Test
    void enrichWithFallback_shouldFallbackOnError() {
        // Given - primary fails, backup succeeds
        DataEnricher<?, ?, ?> primary = mock(AnnotatedPrimaryOnError.class);
        DataEnricher<?, ?, ?> backup = mock(DataEnricher.class);

        EnrichmentResponse failureResponse = EnrichmentResponse.failure("credit-report", "primary", "Provider unavailable");
        EnrichmentResponse successResponse = EnrichmentResponse.success(Map.of("score", "750"), "backup", "credit-report", "OK");

        when(primary.enrich(any())).thenReturn(Mono.just(failureResponse));
        when(primary.getProviderName()).thenReturn("primary");
        when(backup.enrich(any())).thenReturn(Mono.just(successResponse));
        when(registry.getEnricherByProvider("backup")).thenReturn(Optional.of(backup));

        // When & Then
        StepVerifier.create(executor.enrichWithFallback(primary, request))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getProviderName()).isEqualTo("backup");
                assertThat(response.getMetadata()).containsEntry("fallbackFrom", "primary");
                assertThat(response.getMetadata()).containsEntry("fallbackDepth", "1");
            })
            .verifyComplete();

        verify(backup).enrich(request);
    }

    @Test
    void enrichWithFallback_shouldNotFallbackOnSuccess() {
        // Given - primary succeeds
        DataEnricher<?, ?, ?> primary = mock(AnnotatedPrimaryOnError.class);

        EnrichmentResponse successResponse = EnrichmentResponse.builder()
            .success(true)
            .providerName("primary")
            .type("credit-report")
            .fieldsEnriched(5)
            .message("OK")
            .build();

        when(primary.enrich(any())).thenReturn(Mono.just(successResponse));

        // When & Then
        StepVerifier.create(executor.enrichWithFallback(primary, request))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getProviderName()).isEqualTo("primary");
                assertThat(response.getFieldsEnriched()).isEqualTo(5);
            })
            .verifyComplete();

        // Backup should never be consulted
        verify(registry, never()).getEnricherByProvider(any());
    }

    @Test
    void enrichWithFallback_shouldRespectMaxDepth() {
        // Given - chain of 4 fallbacks, but maxFallbacks=3 so the 4th is never reached
        DataEnricher<?, ?, ?> primary = mock(AnnotatedPrimaryOnError.class);
        DataEnricher<?, ?, ?> fallback1 = mock(ChainedFallback1.class);
        DataEnricher<?, ?, ?> fallback2 = mock(ChainedFallback2.class);
        DataEnricher<?, ?, ?> fallback3 = mock(ChainedFallback3.class);
        DataEnricher<?, ?, ?> fallback4 = mock(DataEnricher.class);

        EnrichmentResponse failure = EnrichmentResponse.failure("credit-report", "any", "error");

        when(primary.enrich(any())).thenReturn(Mono.just(failure));
        when(primary.getProviderName()).thenReturn("primary");
        when(fallback1.enrich(any())).thenReturn(Mono.just(failure));
        when(fallback1.getProviderName()).thenReturn("fallback-1");
        when(fallback2.enrich(any())).thenReturn(Mono.just(failure));
        when(fallback2.getProviderName()).thenReturn("fallback-2");
        when(fallback3.enrich(any())).thenReturn(Mono.just(failure));

        when(registry.getEnricherByProvider("backup")).thenReturn(Optional.of(fallback1));
        when(registry.getEnricherByProvider("fallback-2")).thenReturn(Optional.of(fallback2));
        when(registry.getEnricherByProvider("fallback-3")).thenReturn(Optional.of(fallback3));

        // When & Then - should stop at depth 3, never reaching fallback-4
        StepVerifier.create(executor.enrichWithFallback(primary, request))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isFalse();
            })
            .verifyComplete();

        verify(fallback4, never()).enrich(any());
    }

    @Test
    void enrichWithFallback_shouldHandleMissingFallbackProvider() {
        // Given - primary fails, but fallback provider is not registered
        DataEnricher<?, ?, ?> primary = mock(AnnotatedPrimaryOnError.class);

        EnrichmentResponse failureResponse = EnrichmentResponse.failure("credit-report", "primary", "Provider unavailable");

        when(primary.enrich(any())).thenReturn(Mono.just(failureResponse));
        when(registry.getEnricherByProvider("backup")).thenReturn(Optional.empty());

        // When & Then - should return the last (failed) response
        StepVerifier.create(executor.enrichWithFallback(primary, request))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isFalse();
                assertThat(response.getProviderName()).isEqualTo("primary");
                assertThat(response.getError()).isEqualTo("Provider unavailable");
            })
            .verifyComplete();
    }
}
