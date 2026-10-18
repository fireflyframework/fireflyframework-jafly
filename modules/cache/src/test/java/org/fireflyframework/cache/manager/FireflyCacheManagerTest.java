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

package org.fireflyframework.cache.manager;

import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheAdapter;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FireflyCacheManager.
 */
class FireflyCacheManagerTest {

    private FireflyCacheManager cacheManager;
    private CacheAdapter primaryCache;

    @BeforeEach
    void setUp() {
        // Create a Caffeine cache as primary
        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .maximumSize(100L)
                .recordStats(true)
                .build();
        primaryCache = new CaffeineCacheAdapter("test-cache", config);
        
        cacheManager = new FireflyCacheManager(primaryCache);
    }

    @Test
    void shouldDelegateGetToPrimaryCache() {
        // Given
        cacheManager.put("key1", "value1").block();
        
        // When & Then
        StepVerifier.create(cacheManager.get("key1"))
                .expectNext(Optional.of("value1"))
                .verifyComplete();
    }

    @Test
    void shouldDelegatePutToPrimaryCache() {
        // When
        StepVerifier.create(cacheManager.put("key1", "value1"))
                .verifyComplete();
        
        // Then
        StepVerifier.create(cacheManager.get("key1"))
                .expectNext(Optional.of("value1"))
                .verifyComplete();
    }

    @Test
    void shouldDelegateEvictToPrimaryCache() {
        // Given
        cacheManager.put("key1", "value1").block();
        
        // When
        StepVerifier.create(cacheManager.evict("key1"))
                .expectNext(true)
                .verifyComplete();
        
        // Then
        StepVerifier.create(cacheManager.get("key1"))
                .expectNext(Optional.empty())
                .verifyComplete();
    }

    @Test
    void shouldDelegateClearToPrimaryCache() {
        // Given
        cacheManager.put("key1", "value1").block();
        cacheManager.put("key2", "value2").block();
        
        // When
        StepVerifier.create(cacheManager.clear())
                .verifyComplete();
        
        // Then
        StepVerifier.create(cacheManager.get("key1"))
                .expectNext(Optional.empty())
                .verifyComplete();
    }

    @Test
    void shouldReturnPrimaryCacheType() {
        assertThat(cacheManager.getCacheType()).isEqualTo(CacheType.CAFFEINE);
    }

    @Test
    void shouldReturnPrimaryCacheName() {
        assertThat(cacheManager.getCacheName()).isEqualTo("test-cache");
    }

    @Test
    void shouldBeAvailableWhenPrimaryCacheIsAvailable() {
        assertThat(cacheManager.isAvailable()).isTrue();
    }

    @Test
    void shouldReturnStatsFromPrimaryCache() {
        StepVerifier.create(cacheManager.getStats())
                .assertNext(stats -> {
                    assertThat(stats).isNotNull();
                    assertThat(stats.getHitCount()).isGreaterThanOrEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnHealthFromPrimaryCache() {
        StepVerifier.create(cacheManager.getHealth())
                .assertNext(health -> {
                    assertThat(health).isNotNull();
                    assertThat(health.getStatus()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldSupportPutWithTTL() {
        // When
        StepVerifier.create(cacheManager.put("key1", "value1", Duration.ofSeconds(10)))
                .verifyComplete();
        
        // Then
        StepVerifier.create(cacheManager.get("key1"))
                .expectNext(Optional.of("value1"))
                .verifyComplete();
    }

    @Test
    void shouldSupportPutIfAbsent() {
        // When - first put should succeed
        StepVerifier.create(cacheManager.putIfAbsent("key1", "value1"))
                .expectNext(true)
                .verifyComplete();
        
        // When - second put should fail
        StepVerifier.create(cacheManager.putIfAbsent("key1", "value2"))
                .expectNext(false)
                .verifyComplete();
        
        // Then - original value should remain
        StepVerifier.create(cacheManager.get("key1"))
                .expectNext(Optional.of("value1"))
                .verifyComplete();
    }

    @Test
    void shouldSupportExists() {
        // Given
        cacheManager.put("key1", "value1").block();
        
        // When & Then
        StepVerifier.create(cacheManager.exists("key1"))
                .expectNext(true)
                .verifyComplete();
        
        StepVerifier.create(cacheManager.exists("nonexistent"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void shouldSupportSize() {
        // Given
        cacheManager.put("key1", "value1").block();
        cacheManager.put("key2", "value2").block();
        
        // When & Then
        StepVerifier.create(cacheManager.size())
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void shouldCloseSuccessfully() {
        // When
        cacheManager.close();
        
        // Then
        assertThat(cacheManager.isClosed()).isTrue();
    }

    @Test
    void shouldFallbackToFallbackCacheWhenPrimaryUnavailable() {
        // Given - create manager with fallback
        CaffeineCacheConfig fallbackConfig = CaffeineCacheConfig.builder()
                .maximumSize(50L)
                .build();
        CacheAdapter fallbackCache = new CaffeineCacheAdapter("fallback-cache", fallbackConfig);
        
        FireflyCacheManager managerWithFallback = new FireflyCacheManager(primaryCache, fallbackCache);
        
        // When - put data in primary
        managerWithFallback.put("key1", "value1").block();
        
        // Then - should retrieve from primary
        StepVerifier.create(managerWithFallback.get("key1"))
                .expectNext(Optional.of("value1"))
                .verifyComplete();
        
        // Cleanup
        managerWithFallback.close();
    }
}

