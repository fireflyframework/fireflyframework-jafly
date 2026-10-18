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
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataEnricherRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Optional;

/**
 * Executes enrichment operations with automatic fallback chain support.
 *
 * <p>When a primary enricher fails or returns empty results, this executor
 * automatically tries the fallback enricher declared via {@link EnricherFallback}.
 * Fallback chains are traversed recursively up to the configured maximum depth.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * FallbackEnrichmentExecutor executor = new FallbackEnrichmentExecutor(registry);
 * Mono<EnrichmentResponse> response = executor.enrichWithFallback(primaryEnricher, request);
 * }</pre>
 *
 * @see EnricherFallback
 * @see FallbackStrategy
 */
@Slf4j
public class FallbackEnrichmentExecutor {

    private final DataEnricherRegistry registry;

    /**
     * Creates a new fallback enrichment executor.
     *
     * @param registry the enricher registry for resolving fallback providers
     */
    public FallbackEnrichmentExecutor(DataEnricherRegistry registry) {
        this.registry = registry;
    }

    /**
     * Enriches data using the primary enricher, falling back to alternative providers
     * if the primary fails or returns empty results according to the configured strategy.
     *
     * @param primary the primary enricher to try first
     * @param request the enrichment request
     * @return a Mono emitting the enrichment response from the primary or a fallback enricher
     */
    public Mono<EnrichmentResponse> enrichWithFallback(DataEnricher<?, ?, ?> primary, EnrichmentRequest request) {
        return primary.enrich(request)
            .flatMap(response -> {
                if (shouldFallback(primary, response)) {
                    return executeFallbackChain(primary, request, response, 0);
                }
                return Mono.just(response);
            });
    }

    /**
     * Recursively executes the fallback chain starting from the current enricher.
     *
     * <p>At each step, the method checks whether the current enricher declares a fallback
     * via {@link EnricherFallback}, resolves the fallback provider from the registry,
     * and invokes it. The chain stops when:</p>
     * <ul>
     *   <li>The fallback enricher succeeds (and the strategy is satisfied)</li>
     *   <li>The maximum chain depth is reached</li>
     *   <li>No further fallback is declared</li>
     *   <li>The fallback provider is not found in the registry</li>
     * </ul>
     */
    private Mono<EnrichmentResponse> executeFallbackChain(
            DataEnricher<?, ?, ?> current, EnrichmentRequest request,
            EnrichmentResponse lastResponse, int depth) {

        EnricherFallback annotation = current.getClass().getAnnotation(EnricherFallback.class);
        if (annotation == null || depth >= annotation.maxFallbacks()) {
            return Mono.just(lastResponse);
        }

        String fallbackProvider = annotation.fallbackTo();
        Optional<DataEnricher<?, ?, ?>> fallbackOpt = registry.getEnricherByProvider(fallbackProvider);

        if (fallbackOpt.isEmpty()) {
            log.warn("Fallback provider '{}' not found in registry", fallbackProvider);
            return Mono.just(lastResponse);
        }

        DataEnricher<?, ?, ?> fallback = fallbackOpt.get();
        log.info("Falling back from '{}' to '{}' (depth {})",
            current.getProviderName(), fallbackProvider, depth + 1);

        return fallback.enrich(request)
            .flatMap(fbResponse -> {
                if (shouldFallback(fallback, fbResponse)) {
                    return executeFallbackChain(fallback, request, fbResponse, depth + 1);
                }
                addFallbackMetadata(fbResponse, current.getProviderName(), depth + 1);
                return Mono.just(fbResponse);
            });
    }

    /**
     * Determines whether the enricher's response warrants a fallback attempt
     * based on the configured {@link FallbackStrategy}.
     */
    private boolean shouldFallback(DataEnricher<?, ?, ?> enricher, EnrichmentResponse response) {
        EnricherFallback annotation = enricher.getClass().getAnnotation(EnricherFallback.class);
        if (annotation == null) {
            return false;
        }

        return switch (annotation.strategy()) {
            case ON_ERROR -> !response.isSuccess();
            case ON_EMPTY -> response.isSuccess() && response.getFieldsEnriched() == 0;
            case ON_ERROR_OR_EMPTY -> !response.isSuccess() || response.getFieldsEnriched() == 0;
        };
    }

    /**
     * Adds fallback provenance metadata to the response.
     */
    private void addFallbackMetadata(EnrichmentResponse response, String originalProvider, int depth) {
        if (response.getMetadata() == null) {
            response.setMetadata(new HashMap<>());
        }
        response.getMetadata().put("fallbackFrom", originalProvider);
        response.getMetadata().put("fallbackDepth", String.valueOf(depth));
    }
}
