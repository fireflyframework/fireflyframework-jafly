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

package org.fireflyframework.data.controller;

import org.fireflyframework.data.cache.EnrichmentCacheService;
import org.fireflyframework.data.model.EnrichmentApiRequest;
import org.fireflyframework.data.model.EnrichmentApiResponse;
import org.fireflyframework.data.model.EnrichmentRequest;
import org.fireflyframework.data.model.EnrichmentResponse;
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataEnricherRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SSE streaming endpoint in SmartEnrichmentController.
 */
@ExtendWith(MockitoExtension.class)
class SmartEnrichmentControllerStreamTest {

    @Mock
    private DataEnricherRegistry registry;

    @Mock
    private EnrichmentCacheService cacheService;

    @Mock
    private DataEnricher<?, ?, ?> companyEnricher;

    @Mock
    private DataEnricher<?, ?, ?> creditEnricher;

    private SmartEnrichmentController controller;

    private static final UUID TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        controller = new SmartEnrichmentController(registry, cacheService);

        lenient().when(companyEnricher.getProviderName()).thenReturn("Company Provider");
        lenient().when(companyEnricher.getPriority()).thenReturn(100);
        lenient().when(companyEnricher.getTenantId()).thenReturn(TENANT_ID);

        lenient().when(creditEnricher.getProviderName()).thenReturn("Credit Provider");
        lenient().when(creditEnricher.getPriority()).thenReturn(90);
        lenient().when(creditEnricher.getTenantId()).thenReturn(TENANT_ID);
    }

    @Test
    void streamEnrichment_shouldStreamResultsAsSSE() {
        // Given - 3 requests, all with matching enrichers
        List<EnrichmentApiRequest> requests = List.of(
                buildRequest("company-profile", TENANT_ID),
                buildRequest("company-profile", TENANT_ID),
                buildRequest("credit-report", TENANT_ID)
        );

        when(registry.getEnricherForTypeAndTenant("company-profile", TENANT_ID))
                .thenReturn(Optional.of(companyEnricher));
        when(registry.getEnricherForTypeAndTenant("credit-report", TENANT_ID))
                .thenReturn(Optional.of(creditEnricher));

        EnrichmentResponse companyResponse = EnrichmentResponse.builder()
                .success(true)
                .type("company-profile")
                .providerName("Company Provider")
                .message("Company enriched")
                .timestamp(Instant.now())
                .build();

        EnrichmentResponse creditResponse = EnrichmentResponse.builder()
                .success(true)
                .type("credit-report")
                .providerName("Credit Provider")
                .message("Credit enriched")
                .timestamp(Instant.now())
                .build();

        when(companyEnricher.enrich(any(EnrichmentRequest.class)))
                .thenReturn(Mono.just(companyResponse));
        when(creditEnricher.enrich(any(EnrichmentRequest.class)))
                .thenReturn(Mono.just(creditResponse));

        // When & Then - expect 3 result events + 1 complete event = 4 total
        StepVerifier.create(controller.streamEnrichment(requests))
                .thenConsumeWhile(
                        event -> !"complete".equals(event.event()),
                        event -> {
                            assertThat(event.event()).isEqualTo("enrichment-result");
                            assertThat(event.data()).isNotNull();
                            assertThat(event.data().isSuccess()).isTrue();
                        }
                )
                .assertNext(completeEvent -> {
                    assertThat(completeEvent.event()).isEqualTo("complete");
                })
                .verifyComplete();
    }

    @Test
    void streamEnrichment_shouldHandleEnricherNotFound() {
        // Given - request with no matching enricher
        List<EnrichmentApiRequest> requests = List.of(
                buildRequest("unknown-type", TENANT_ID)
        );

        when(registry.getEnricherForTypeAndTenant("unknown-type", TENANT_ID))
                .thenReturn(Optional.empty());

        // When & Then - expect 1 error event + 1 complete event
        StepVerifier.create(controller.streamEnrichment(requests))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("enrichment-error");
                    assertThat(event.id()).isEqualTo("0");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().isSuccess()).isFalse();
                    assertThat(event.data().getMessage()).contains("No enricher found for type: unknown-type");
                    assertThat(event.data().getType()).isEqualTo("unknown-type");
                })
                .assertNext(completeEvent -> {
                    assertThat(completeEvent.event()).isEqualTo("complete");
                })
                .verifyComplete();
    }

    @Test
    void streamEnrichment_shouldEmitCompleteEventAtEnd() {
        // Given - single successful request
        List<EnrichmentApiRequest> requests = List.of(
                buildRequest("company-profile", TENANT_ID)
        );

        when(registry.getEnricherForTypeAndTenant("company-profile", TENANT_ID))
                .thenReturn(Optional.of(companyEnricher));

        EnrichmentResponse response = EnrichmentResponse.builder()
                .success(true)
                .type("company-profile")
                .providerName("Company Provider")
                .message("Success")
                .timestamp(Instant.now())
                .build();

        when(companyEnricher.enrich(any(EnrichmentRequest.class)))
                .thenReturn(Mono.just(response));

        // When & Then - verify last event is "complete"
        StepVerifier.create(controller.streamEnrichment(requests))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("enrichment-result");
                    assertThat(event.id()).isEqualTo("0");
                })
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("complete");
                    assertThat(event.data()).isNull();
                    assertThat(event.comment()).isEqualTo("All enrichments processed");
                })
                .verifyComplete();
    }

    private EnrichmentApiRequest buildRequest(String type, UUID tenantId) {
        return EnrichmentApiRequest.builder()
                .type(type)
                .tenantId(tenantId)
                .params(Map.of("key", "value"))
                .build();
    }
}
