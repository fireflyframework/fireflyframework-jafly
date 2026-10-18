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
import org.fireflyframework.data.model.PreviewResponse;
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataEnricherRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the preview endpoint in {@link SmartEnrichmentController}.
 */
@ExtendWith(MockitoExtension.class)
class SmartEnrichmentControllerPreviewTest {

    @Mock
    private DataEnricherRegistry enricherRegistry;

    @Mock
    private EnrichmentCacheService cacheService;

    @Mock
    private DataEnricher<?, ?, ?> enricher;

    private SmartEnrichmentController controller;

    private static final UUID SPAIN_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        controller = new SmartEnrichmentController(enricherRegistry, cacheService);

        lenient().when(enricher.getProviderName()).thenReturn("Financial Data Provider");
        lenient().when(enricher.getEnricherVersion()).thenReturn("2.1.0");
        lenient().when(enricher.getPriority()).thenReturn(100);
        lenient().when(enricher.getEnricherDescription()).thenReturn("Enriches company data with financial information");
        lenient().when(enricher.getTags()).thenReturn(List.of("production", "gdpr-compliant"));
    }

    @Test
    void preview_shouldReturnProviderInfo() {
        // Given
        EnrichmentApiRequest request = EnrichmentApiRequest.builder()
                .type("company-profile")
                .tenantId(SPAIN_TENANT_ID)
                .build();

        when(enricherRegistry.getEnricherForTypeAndTenant("company-profile", SPAIN_TENANT_ID))
                .thenReturn(Optional.of(enricher));
        when(cacheService.isCacheEnabled()).thenReturn(true);

        // When & Then
        StepVerifier.create(controller.previewEnrichment(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

                    PreviewResponse preview = response.getBody();
                    assertThat(preview).isNotNull();
                    assertThat(preview.getProviderName()).isEqualTo("Financial Data Provider");
                    assertThat(preview.getEnrichmentType()).isEqualTo("company-profile");
                    assertThat(preview.getProviderVersion()).isEqualTo("2.1.0");
                    assertThat(preview.getPriority()).isEqualTo(100);
                    assertThat(preview.isCached()).isTrue();
                    assertThat(preview.getTenantId()).isEqualTo(SPAIN_TENANT_ID);
                    assertThat(preview.isAutoSelected()).isTrue();
                    assertThat(preview.getDescription()).isEqualTo("Enriches company data with financial information");
                    assertThat(preview.getTags()).containsExactly("production", "gdpr-compliant");
                })
                .verifyComplete();
    }

    @Test
    void preview_shouldReturn404WhenNoEnricherFound() {
        // Given
        UUID unknownTenant = UUID.fromString("00000000-0000-0000-0000-000000000099");

        EnrichmentApiRequest request = EnrichmentApiRequest.builder()
                .type("unknown-type")
                .tenantId(unknownTenant)
                .build();

        when(enricherRegistry.getEnricherForTypeAndTenant("unknown-type", unknownTenant))
                .thenReturn(Optional.empty());

        // When & Then
        StepVerifier.create(controller.previewEnrichment(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody()).isNull();
                })
                .verifyComplete();
    }
}
