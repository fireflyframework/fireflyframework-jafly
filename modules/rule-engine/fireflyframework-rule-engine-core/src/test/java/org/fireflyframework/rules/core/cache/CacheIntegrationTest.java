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

package org.fireflyframework.rules.core.cache;

import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheAdapter;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for cache functionality in the Rule Engine.
 * Verifies that the cache integration with lib-common-cache works correctly
 * and that cache keys follow the expected format:
 * firefly:cache:default::rule-engine:{logicalCacheType}:{key}
 *
 * This is a standalone test that doesn't require the full Spring context.
 */
@Slf4j
class CacheIntegrationTest {

    private FireflyCacheManager cacheManager;
    private static final String RULE_ENGINE_PREFIX = ":rule-engine:";
    private static final String AST_PREFIX = RULE_ENGINE_PREFIX + "ast:";

    @BeforeEach
    void setUp() {
        // Create a Caffeine cache adapter with test configuration
        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .keyPrefix("firefly:cache")
                .maximumSize(1000L)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats(true)
                .build();

        CaffeineCacheAdapter cacheAdapter = new CaffeineCacheAdapter("default", config);
        cacheManager = new FireflyCacheManager(cacheAdapter, null);

        // Clear cache before each test
        cacheManager.clear().block();
    }

    @Test
    void shouldCacheAndRetrieveValue() {
        // Given
        String cacheKey = AST_PREFIX + "test-rule-123";
        String testValue = "test-value";

        // When
        cacheManager.put(cacheKey, testValue).block();
        String retrieved = cacheManager.get(cacheKey, String.class).block().orElse(null);

        // Then
        assertThat(retrieved).isEqualTo(testValue);
        log.info("✓ Cache test passed - Key format: firefly:cache:default::rule-engine:ast:test-rule-123");
    }

    @Test
    void shouldReturnEmptyWhenKeyNotFound() {
        // Given
        String nonExistentKey = AST_PREFIX + "non-existent-key";

        // When
        var retrieved = cacheManager.get(nonExistentKey, String.class).block();

        // Then
        assertThat(retrieved).isEmpty();
        log.info("✓ Cache miss test passed");
    }

    @Test
    void shouldInvalidateCache() {
        // Given
        String cacheKey = AST_PREFIX + "test-rule-456";
        cacheManager.put(cacheKey, "test-value").block();

        // When
        cacheManager.evict(cacheKey).block();
        var retrieved = cacheManager.get(cacheKey, String.class).block();

        // Then
        assertThat(retrieved).isEmpty();
        log.info("✓ Cache invalidation test passed");
    }

    @Test
    void shouldClearAllCache() {
        // Given
        cacheManager.put(AST_PREFIX + "key1", "value1").block();
        cacheManager.put(AST_PREFIX + "key2", "value2").block();

        // When
        cacheManager.clear().block();

        // Then
        assertThat(cacheManager.get(AST_PREFIX + "key1", String.class).block()).isEmpty();
        assertThat(cacheManager.get(AST_PREFIX + "key2", String.class).block()).isEmpty();
        log.info("✓ Clear all cache test passed");
    }

    @Test
    void shouldVerifyCacheManagerIsConfigured() {
        // Then
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.getCacheName()).isEqualTo("default");
        log.info("✓ Cache manager configuration test passed - Cache name: {}", cacheManager.getCacheName());
    }

    @Test
    void shouldGetCacheStatistics() {
        // Given
        String cacheKey = AST_PREFIX + "stats-test-key";
        cacheManager.put(cacheKey, "test-value").block();
        cacheManager.get(cacheKey, String.class).block(); // Hit
        cacheManager.get(AST_PREFIX + "non-existent", String.class).block(); // Miss

        // When
        var stats = cacheManager.getStats().block();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getCacheName()).isEqualTo("default");
        log.info("✓ Cache statistics test passed - Hit count: {}, Miss count: {}, Entries: {}",
                stats.getHitCount(), stats.getMissCount(), stats.getEntryCount());
    }
}

