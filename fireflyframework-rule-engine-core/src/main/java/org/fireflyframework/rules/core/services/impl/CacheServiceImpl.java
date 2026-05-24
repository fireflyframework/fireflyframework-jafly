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

package org.fireflyframework.rules.core.services.impl;

import org.fireflyframework.cache.CacheProviderType;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.services.CacheService;
import org.fireflyframework.rules.core.services.ConstantService;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.fireflyframework.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the CacheService interface.
 * Provides high-performance caching operations using dedicated rule engine cache.
 * <p>
 * Uses the dedicated ruleEngineCacheManager bean created by RuleEngineCacheAutoConfiguration
 * to avoid conflicts with other application caches.
 */
@Service
@Slf4j
public class CacheServiceImpl implements CacheService {

    private final FireflyCacheManager cacheManager;
    private final ConstantService constantService;

    /**
     * Constructor that injects the dedicated rule engine cache manager.
     * 
     * @param cacheManager dedicated rule engine cache manager (qualified bean)
     * @param constantService service for managing constants
     */
    public CacheServiceImpl(
            @Qualifier("ruleEngineCacheManager") FireflyCacheManager cacheManager,
            ConstantService constantService) {
        this.cacheManager = cacheManager;
        this.constantService = constantService;
    }

    // Cache key prefixes for different cache types
    // Final format: firefly:cache:{cacheName}::rule-engine:{logicalCacheType}:{key}
    private static final String RULE_ENGINE_PREFIX = ":rule-engine:";
    private static final String AST_PREFIX = RULE_ENGINE_PREFIX + "ast:";
    private static final String CONSTANT_PREFIX = RULE_ENGINE_PREFIX + "constant:";
    private static final String RULE_DEF_PREFIX = RULE_ENGINE_PREFIX + "rule-def:";
    private static final String VALIDATION_PREFIX = RULE_ENGINE_PREFIX + "validation:";

    // AST Cache Operations

    @Override
    public Mono<Optional<ASTRulesDSL>> getCachedAST(String cacheKey) {
        String fullKey = AST_PREFIX + cacheKey;
        return cacheManager.get(fullKey, ASTRulesDSL.class)
                .doOnError(e -> log.warn("Error retrieving AST from cache for key: {}", cacheKey, e))
                .onErrorReturn(Optional.empty());
    }

    @Override
    public Mono<Void> cacheAST(String cacheKey, ASTRulesDSL astModel) {
        String fullKey = AST_PREFIX + cacheKey;
        return cacheManager.put(fullKey, astModel)
                .doOnSuccess(v -> log.debug("Cached AST model with key: {}", cacheKey))
                .doOnError(e -> log.warn("Error caching AST model for key: {}", cacheKey, e))
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public String generateCacheKey(String yamlContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(yamlContent.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple hash code
            return String.valueOf(yamlContent.hashCode());
        }
    }

    @Override
    public Mono<Void> invalidateAST(String cacheKey) {
        String fullKey = AST_PREFIX + cacheKey;
        return cacheManager.evict(fullKey)
                .doOnSuccess(v -> log.debug("Invalidated AST cache entry for key: {}", cacheKey))
                .then();
    }

    @Override
    public Mono<Void> clearASTCache() {
        // Clear all AST entries by pattern (if supported) or clear entire cache
        return cacheManager.clear()
                .doOnSuccess(v -> log.info("Cleared all AST cache entries"));
    }

    // Constants Cache Operations

    @Override
    public Optional<ConstantDTO> getCachedConstant(String code) {
        try {
            String fullKey = CONSTANT_PREFIX + code;
            return cacheManager.get(fullKey, ConstantDTO.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .blockOptional()
                    .orElse(Optional.empty());
        } catch (Exception e) {
            log.warn("Error retrieving constant from cache for code: {}", code, e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheConstant(String code, ConstantDTO constant) {
        try {
            String fullKey = CONSTANT_PREFIX + code;
            cacheManager.put(fullKey, constant)
                    .doOnError(e -> log.warn("Cache write failed for constant {}: {}", code, e.getMessage()))
                    .onErrorComplete()
                .subscribe();
            log.debug("Cached constant with code: {}", code);
        } catch (Exception e) {
            log.warn("Error caching constant for code: {}", code, e);
        }
    }

    @Override
    public List<ConstantDTO> getCachedConstants(List<String> codes) {
        List<ConstantDTO> cachedConstants = new ArrayList<>();
        for (String code : codes) {
            getCachedConstant(code).ifPresent(cachedConstants::add);
        }
        return cachedConstants;
    }

    @Override
    public void cacheConstants(List<ConstantDTO> constants) {
        for (ConstantDTO constant : constants) {
            cacheConstant(constant.getCode(), constant);
        }
    }

    @Override
    public void invalidateConstant(String code) {
        String fullKey = CONSTANT_PREFIX + code;
        cacheManager.evict(fullKey)
                .doOnError(e -> log.warn("Cache evict failed for constant {}: {}", code, e.getMessage()))
                .onErrorComplete()
                .subscribe();
        log.debug("Invalidated constants cache entry for code: {}", code);
    }

    @Override
    public void clearConstantsCache() {
        cacheManager.clear()
                .doOnError(e -> log.warn("Cache clear failed for constants: {}", e.getMessage()))
                .onErrorComplete()
                .subscribe();
        log.info("Cleared all constants cache entries");
    }

    // Rule Definitions Cache Operations

    @Override
    public Optional<RuleDefinitionDTO> getCachedRuleDefinition(String code) {
        try {
            String fullKey = RULE_DEF_PREFIX + code;
            return cacheManager.get(fullKey, RuleDefinitionDTO.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .blockOptional()
                    .orElse(Optional.empty());
        } catch (Exception e) {
            log.warn("Error retrieving rule definition from cache for code: {}", code, e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheRuleDefinition(String code, RuleDefinitionDTO ruleDefinition) {
        try {
            String fullKey = RULE_DEF_PREFIX + code;
            cacheManager.put(fullKey, ruleDefinition)
                    .doOnError(e -> log.warn("Cache write failed for rule definition {}: {}", code, e.getMessage()))
                    .onErrorComplete()
                .subscribe();
            log.debug("Cached rule definition with code: {}", code);
        } catch (Exception e) {
            log.warn("Error caching rule definition for code: {}", code, e);
        }
    }

    @Override
    public void invalidateRuleDefinition(String code) {
        String fullKey = RULE_DEF_PREFIX + code;
        cacheManager.evict(fullKey)
                .doOnError(e -> log.warn("Cache evict failed for rule definition {}: {}", code, e.getMessage()))
                .onErrorComplete()
                .subscribe();
        log.debug("Invalidated rule definitions cache entry for code: {}", code);
    }

    @Override
    public void clearRuleDefinitionsCache() {
        cacheManager.clear()
                .doOnError(e -> log.warn("Cache clear failed for rule definitions: {}", e.getMessage()))
                .onErrorComplete()
                .subscribe();
        log.info("Cleared all rule definitions cache entries");
    }

    // Validation Cache Operations

    @Override
    public Optional<ValidationResult> getCachedValidationResult(String cacheKey) {
        try {
            String fullKey = VALIDATION_PREFIX + cacheKey;
            return cacheManager.get(fullKey, ValidationResult.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .blockOptional()
                    .orElse(Optional.empty());
        } catch (Exception e) {
            log.warn("Error retrieving validation result from cache for key: {}", cacheKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheValidationResult(String cacheKey, ValidationResult validationResult) {
        try {
            String fullKey = VALIDATION_PREFIX + cacheKey;
            cacheManager.put(fullKey, validationResult)
                    .doOnError(e -> log.warn("Cache write failed for validation result {}: {}", cacheKey, e.getMessage()))
                    .onErrorComplete()
                .subscribe();
            log.debug("Cached validation result with key: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Error caching validation result for key: {}", cacheKey, e);
        }
    }

    @Override
    public void invalidateValidationResult(String cacheKey) {
        String fullKey = VALIDATION_PREFIX + cacheKey;
        cacheManager.evict(fullKey)
                .doOnError(e -> log.warn("Cache evict failed for validation result {}: {}", cacheKey, e.getMessage()))
                .onErrorComplete()
                .subscribe();
        log.debug("Invalidated validation cache entry for key: {}", cacheKey);
    }

    @Override
    public void clearValidationCache() {
        cacheManager.clear()
                .doOnError(e -> log.warn("Cache clear failed for validation cache: {}", e.getMessage()))
                .onErrorComplete()
                .subscribe();
        log.info("Cleared all validation cache entries");
    }

    // Cache Management Operations

    @Override
    public Mono<CacheStatistics> getCacheStatistics() {
        return cacheManager.getStats()
                .map(stats -> {
                    CacheStat cacheStat = new CacheStat(
                            stats.getCacheName(),
                            stats.getEntryCount(),
                            stats.getHitRate(),
                            stats.getHitCount(),
                            stats.getMissCount(),
                            stats.getEvictionCount(),
                            stats.getAverageLoadTimeMillis()
                    );
                    // Return the same stats for all cache types since we're using a single cache manager
                    return new CacheStatistics(cacheStat, cacheStat, cacheStat, cacheStat);
                });
    }

    @Override
    public CacheProviderInfo getCacheProviderInfo() {
        CacheProviderType providerType = cacheManager.getCacheType() == org.fireflyframework.cache.core.CacheType.CAFFEINE
                ? CacheProviderType.CAFFEINE
                : CacheProviderType.REDIS;

        String description = providerType == CacheProviderType.CAFFEINE
                ? "High-performance in-memory cache"
                : "Distributed Redis cache";

        return new CacheProviderInfo(
                providerType,
                description,
                providerType == CacheProviderType.REDIS,
                List.of(cacheManager.getCacheName())
        );
    }

    @Override
    public void clearAllCaches() {
        cacheManager.clear()
                .doOnError(e -> log.warn("Cache clear failed: {}", e.getMessage()))
                .onErrorComplete()
                .subscribe();
        log.info("Cleared all caches");
    }

    @Override
    public Mono<Void> warmUpCaches() {
        log.info("Starting cache warm-up process");
        
        // Warm up constants cache with frequently used constants
        return constantService.filterConstants(null)
                .map(response -> response.getContent())
                .doOnNext(this::cacheConstants)
                .then()
                .doOnSuccess(v -> log.info("Cache warm-up completed successfully"))
                .doOnError(e -> log.error("Cache warm-up failed", e));
    }
}
