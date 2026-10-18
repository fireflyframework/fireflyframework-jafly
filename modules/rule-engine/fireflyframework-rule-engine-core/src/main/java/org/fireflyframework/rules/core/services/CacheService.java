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

package org.fireflyframework.rules.core.services;

import org.fireflyframework.cache.CacheProviderType;
import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.fireflyframework.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing cache operations in the Firefly Rule Engine.
 * Provides high-level caching operations for AST models, constants, and other frequently accessed data.
 */
public interface CacheService {

    // AST Cache Operations

    /**
     * Get a cached AST model by its cache key.
     *
     * @param cacheKey The cache key (typically a hash of the YAML content)
     * @return Mono containing Optional with the cached AST model if found
     */
    Mono<Optional<ASTRulesDSL>> getCachedAST(String cacheKey);

    /**
     * Cache an AST model with the specified key.
     *
     * @param cacheKey The cache key (typically a hash of the YAML content)
     * @param astModel The AST model to cache
     * @return Mono that completes when caching is done
     */
    Mono<Void> cacheAST(String cacheKey, ASTRulesDSL astModel);
    
    /**
     * Generate a cache key for YAML content.
     * Uses SHA-256 hash for consistent and collision-resistant keys.
     * 
     * @param yamlContent The YAML content to generate a key for
     * @return The generated cache key
     */
    String generateCacheKey(String yamlContent);
    
    /**
     * Invalidate a specific AST cache entry.
     *
     * @param cacheKey The cache key to invalidate
     * @return Mono that completes when invalidation is done
     */
    Mono<Void> invalidateAST(String cacheKey);

    /**
     * Clear all AST cache entries.
     *
     * @return Mono that completes when clearing is done
     */
    Mono<Void> clearASTCache();

    // Constants Cache Operations
    
    /**
     * Get a cached constant by its code.
     * 
     * @param code The constant code
     * @return Optional containing the cached constant if found
     */
    Optional<ConstantDTO> getCachedConstant(String code);
    
    /**
     * Cache a constant with its code as the key.
     * 
     * @param code The constant code
     * @param constant The constant to cache
     */
    void cacheConstant(String code, ConstantDTO constant);
    
    /**
     * Get multiple cached constants by their codes.
     * 
     * @param codes The list of constant codes
     * @return List of cached constants (may be partial if some are not cached)
     */
    List<ConstantDTO> getCachedConstants(List<String> codes);
    
    /**
     * Cache multiple constants.
     * 
     * @param constants The list of constants to cache
     */
    void cacheConstants(List<ConstantDTO> constants);
    
    /**
     * Invalidate a specific constant cache entry.
     * 
     * @param code The constant code to invalidate
     */
    void invalidateConstant(String code);
    
    /**
     * Clear all constants cache entries.
     */
    void clearConstantsCache();

    // Rule Definitions Cache Operations
    
    /**
     * Get a cached rule definition by its code.
     * 
     * @param code The rule definition code
     * @return Optional containing the cached rule definition if found
     */
    Optional<RuleDefinitionDTO> getCachedRuleDefinition(String code);
    
    /**
     * Cache a rule definition with its code as the key.
     * 
     * @param code The rule definition code
     * @param ruleDefinition The rule definition to cache
     */
    void cacheRuleDefinition(String code, RuleDefinitionDTO ruleDefinition);
    
    /**
     * Invalidate a specific rule definition cache entry.
     * 
     * @param code The rule definition code to invalidate
     */
    void invalidateRuleDefinition(String code);
    
    /**
     * Clear all rule definitions cache entries.
     */
    void clearRuleDefinitionsCache();

    // Validation Cache Operations
    
    /**
     * Get a cached validation result by its cache key.
     * 
     * @param cacheKey The cache key (typically a hash of the YAML content)
     * @return Optional containing the cached validation result if found
     */
    Optional<ValidationResult> getCachedValidationResult(String cacheKey);
    
    /**
     * Cache a validation result with the specified key.
     * 
     * @param cacheKey The cache key (typically a hash of the YAML content)
     * @param validationResult The validation result to cache
     */
    void cacheValidationResult(String cacheKey, ValidationResult validationResult);
    
    /**
     * Invalidate a specific validation cache entry.
     * 
     * @param cacheKey The cache key to invalidate
     */
    void invalidateValidationResult(String cacheKey);
    
    /**
     * Clear all validation cache entries.
     */
    void clearValidationCache();

    // Cache Management Operations
    
    /**
     * Get cache statistics for monitoring and optimization.
     *
     * @return Mono containing cache statistics
     */
    Mono<CacheStatistics> getCacheStatistics();

    /**
     * Get cache provider information.
     *
     * @return Information about the cache providers being used
     */
    CacheProviderInfo getCacheProviderInfo();
    
    /**
     * Clear all caches.
     */
    void clearAllCaches();
    
    /**
     * Warm up caches with frequently used data.
     * This method can be called during application startup or periodically.
     * 
     * @return Mono that completes when cache warm-up is finished
     */
    Mono<Void> warmUpCaches();

    /**
     * Cache statistics data transfer object.
     */
    record CacheStatistics(
            CacheStat astCache,
            CacheStat constantsCache,
            CacheStat ruleDefinitionsCache,
            CacheStat validationCache
    ) {}

    /**
     * Individual cache statistics.
     */
    record CacheStat(
            String name,
            long size,
            double hitRate,
            long hitCount,
            long missCount,
            long evictionCount,
            double averageLoadTime
    ) {}

    /**
     * Cache provider information.
     */
    record CacheProviderInfo(
            CacheProviderType providerType,
            String description,
            boolean isDistributed,
            List<String> cacheNames
    ) {}
}
