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

package org.fireflyframework.data.cache;

import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.data.config.DataEnrichmentProperties;
import org.fireflyframework.data.model.EnrichmentRequest;
import org.fireflyframework.data.model.EnrichmentResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Service for caching enrichment responses with tenant isolation.
 * 
 * <p>This service provides a high-level API for caching enrichment results,
 * with automatic tenant isolation, TTL management, and cache statistics.</p>
 * 
 * <p><b>Features:</b></p>
 * <ul>
 *   <li><b>Tenant Isolation</b> - Each tenant has completely isolated cache entries</li>
 *   <li><b>Automatic TTL</b> - Configurable time-to-live for cache entries</li>
 *   <li><b>Cache Statistics</b> - Tracks hit/miss rates per tenant</li>
 *   <li><b>Conditional Caching</b> - Only caches successful responses</li>
 *   <li><b>Pattern-based Eviction</b> - Evict by tenant, provider, or enrichment type</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Try to get from cache
 * Mono<Optional<EnrichmentResponse>> cached = cacheService.get(request, "Financial Data Provider");
 * 
 * // If not in cache, fetch and cache
 * return cached.flatMap(opt -> opt
 *     .map(Mono::just)
 *     .orElseGet(() -> fetchFromProvider(request)
 *         .flatMap(response -> cacheService.put(request, "Financial Data Provider", response)
 *             .thenReturn(response))
 *     )
 * );
 * }</pre>
 */
@Slf4j
public class EnrichmentCacheService {

    private final CacheAdapter cacheAdapter;
    private final EnrichmentCacheKeyGenerator keyGenerator;
    private final DataEnrichmentProperties properties;
    private final boolean cacheEnabled;
    private final Duration defaultTtl;

    public EnrichmentCacheService(
            CacheAdapter cacheAdapter,
            EnrichmentCacheKeyGenerator keyGenerator,
            DataEnrichmentProperties properties) {
        this.cacheAdapter = cacheAdapter;
        this.keyGenerator = keyGenerator;
        this.properties = properties;
        this.cacheEnabled = properties.isCacheEnabled();
        this.defaultTtl = Duration.ofSeconds(properties.getCacheTtlSeconds());
        
        log.info("Enrichment Cache Service initialized");
        log.info("  • Cache enabled: {}", cacheEnabled);
        log.info("  • Default TTL: {} seconds", properties.getCacheTtlSeconds());
        log.info("  • Cache type: {}", cacheAdapter.getCacheType());
    }

    /**
     * Gets a cached enrichment response.
     *
     * @param request the enrichment request
     * @param providerName the provider name
     * @return a Mono containing the cached response if found, or empty
     */
    public Mono<Optional<EnrichmentResponse>> get(EnrichmentRequest request, String providerName) {
        if (!cacheEnabled) {
            return Mono.just(Optional.empty());
        }

        String cacheKey = keyGenerator.generateKey(request, providerName);
        
        return cacheAdapter.<String, EnrichmentResponse>get(cacheKey, EnrichmentResponse.class)
            .doOnNext(opt -> {
                if (opt.isPresent()) {
                    log.debug("Cache HIT for key: {}", cacheKey);
                } else {
                    log.debug("Cache MISS for key: {}", cacheKey);
                }
            })
            .onErrorResume(e -> {
                log.warn("Error retrieving from cache for key {}: {}", cacheKey, e.getMessage());
                return Mono.just(Optional.empty());
            });
    }

    /**
     * Caches an enrichment response.
     * 
     * <p>Only successful responses are cached. Failed responses are not cached.</p>
     *
     * @param request the enrichment request
     * @param providerName the provider name
     * @param response the enrichment response to cache
     * @return a Mono that completes when the response is cached
     */
    public Mono<Void> put(EnrichmentRequest request, String providerName, EnrichmentResponse response) {
        if (!cacheEnabled) {
            return Mono.empty();
        }

        // Only cache successful responses
        if (!response.isSuccess()) {
            log.debug("Not caching failed response for provider: {}", providerName);
            return Mono.empty();
        }

        String cacheKey = keyGenerator.generateKey(request, providerName);
        
        return cacheAdapter.put(cacheKey, response, defaultTtl)
            .doOnSuccess(v -> log.debug("Cached response for key: {}", cacheKey))
            .onErrorResume(e -> {
                log.warn("Error caching response for key {}: {}", cacheKey, e.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Caches an enrichment response with a custom TTL.
     *
     * @param request the enrichment request
     * @param providerName the provider name
     * @param response the enrichment response to cache
     * @param ttl the time-to-live for the cache entry
     * @return a Mono that completes when the response is cached
     */
    public Mono<Void> put(EnrichmentRequest request, String providerName, EnrichmentResponse response, Duration ttl) {
        if (!cacheEnabled) {
            return Mono.empty();
        }

        if (!response.isSuccess()) {
            return Mono.empty();
        }

        String cacheKey = keyGenerator.generateKey(request, providerName);
        
        return cacheAdapter.put(cacheKey, response, ttl)
            .doOnSuccess(v -> log.debug("Cached response for key: {} with TTL: {}", cacheKey, ttl))
            .onErrorResume(e -> {
                log.warn("Error caching response for key {}: {}", cacheKey, e.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Evicts a specific cache entry.
     *
     * @param request the enrichment request
     * @param providerName the provider name
     * @return a Mono containing true if the entry was evicted, false otherwise
     */
    public Mono<Boolean> evict(EnrichmentRequest request, String providerName) {
        if (!cacheEnabled) {
            return Mono.just(false);
        }

        String cacheKey = keyGenerator.generateKey(request, providerName);
        
        return cacheAdapter.evict(cacheKey)
            .doOnNext(evicted -> {
                if (evicted) {
                    log.debug("Evicted cache entry for key: {}", cacheKey);
                }
            })
            .onErrorResume(e -> {
                log.warn("Error evicting cache entry for key {}: {}", cacheKey, e.getMessage());
                return Mono.just(false);
            });
    }

    /**
     * Evicts all cache entries for a tenant.
     * 
     * <p>This is useful when a tenant's configuration changes or when
     * tenant-specific data needs to be refreshed.</p>
     *
     * @param tenantId the tenant ID
     * @return a Mono that completes when all entries are evicted
     */
    public Mono<Void> evictTenant(String tenantId) {
        if (!cacheEnabled) {
            return Mono.empty();
        }

        String pattern = keyGenerator.generateTenantPattern(tenantId);
        String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
        log.info("Evicting cache entries for tenant: {} (prefix: {})", tenantId, prefix);

        return evictByPrefix(prefix)
            .doOnNext(count -> log.info("Evicted {} cache entries for tenant: {}", count, tenantId))
            .then()
            .onErrorResume(e -> {
                log.warn("Error evicting cache for tenant {}: {}", tenantId, e.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Evicts all cache entries for a provider.
     *
     * @param tenantId the tenant ID
     * @param providerName the provider name
     * @return a Mono that completes when all entries are evicted
     */
    public Mono<Void> evictProvider(String tenantId, String providerName) {
        if (!cacheEnabled) {
            return Mono.empty();
        }

        String pattern = keyGenerator.generateProviderPattern(tenantId, providerName);
        String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
        log.info("Evicting cache entries for provider: {} in tenant: {} (prefix: {})",
                 providerName, tenantId, prefix);

        return evictByPrefix(prefix)
            .doOnNext(count -> log.info("Evicted {} cache entries for provider: {} in tenant: {}",
                    count, providerName, tenantId))
            .then()
            .onErrorResume(e -> {
                log.warn("Error evicting cache for provider {} in tenant {}: {}",
                         providerName, tenantId, e.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Clears all cache entries.
     *
     * @return a Mono that completes when the cache is cleared
     */
    public Mono<Void> clearAll() {
        if (!cacheEnabled) {
            return Mono.empty();
        }

        log.info("Clearing all enrichment cache entries");
        
        return cacheAdapter.clear()
            .doOnSuccess(v -> log.info("All enrichment cache entries cleared"))
            .onErrorResume(e -> {
                log.warn("Error clearing all cache entries: {}", e.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Checks if caching is enabled.
     *
     * @return true if caching is enabled
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * Gets the default TTL for cache entries.
     *
     * @return the default TTL
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    private Mono<Long> evictByPrefix(String keyPrefix) {
        return cacheAdapter.<String>keys()
            .flatMap(allKeys -> {
                var matching = allKeys.stream()
                    .filter(k -> k.toString().startsWith(keyPrefix))
                    .toList();
                if (matching.isEmpty()) {
                    return Mono.just(0L);
                }
                return Flux.fromIterable(matching)
                    .flatMap(cacheAdapter::evict)
                    .filter(Boolean::booleanValue)
                    .count();
            });
    }
}

