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

package org.fireflyframework.cache.adapter.caffeine;

import org.fireflyframework.cache.core.CacheHealth;
import org.fireflyframework.cache.core.CacheStats;
import org.fireflyframework.cache.core.CacheType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CaffeineCacheAdapter.
 */
class CaffeineCacheAdapterTest {

    private CaffeineCacheAdapter cacheAdapter;
    private CaffeineCacheConfig config;

    @BeforeEach
    void setUp() {
        config = CaffeineCacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats(true)
                .build();
        
        cacheAdapter = new CaffeineCacheAdapter("test-cache", config);
    }

    @Test
    void shouldCreateCacheAdapterWithCorrectProperties() {
        assertThat(cacheAdapter.getCacheName()).isEqualTo("test-cache");
        assertThat(cacheAdapter.getCacheType()).isEqualTo(CacheType.CAFFEINE);
        assertThat(cacheAdapter.isAvailable()).isTrue();
    }

    @Test
    void shouldStoreAndRetrieveValue() {
        // Given
        String key = "test-key";
        String value = "test-value";

        // When & Then
        StepVerifier.create(cacheAdapter.put(key, value))
                .verifyComplete();

        StepVerifier.create(cacheAdapter.get(key))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyForNonExistentKey() {
        // Given
        String nonExistentKey = "non-existent-key";

        // When & Then
        StepVerifier.create(cacheAdapter.get(nonExistentKey))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldStoreValueWithTtl() {
        // Given
        String key = "ttl-key";
        String value = "ttl-value";
        Duration ttl = Duration.ofMillis(100);

        // When & Then
        StepVerifier.create(cacheAdapter.put(key, value, ttl))
                .verifyComplete();

        StepVerifier.create(cacheAdapter.get(key))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value);
                })
                .verifyComplete();

        // Wait for expiration and check again
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        StepVerifier.create(cacheAdapter.get(key))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldPutIfAbsent() {
        // Given
        String key = "absent-key";
        String value1 = "first-value";
        String value2 = "second-value";

        // When & Then - first put should succeed
        StepVerifier.create(cacheAdapter.putIfAbsent(key, value1))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();

        // Second put should fail
        StepVerifier.create(cacheAdapter.putIfAbsent(key, value2))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();

        // Value should be the first one
        StepVerifier.create(cacheAdapter.get(key))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value1);
                })
                .verifyComplete();
    }

    @Test
    void shouldEvictKey() {
        // Given
        String key = "evict-key";
        String value = "evict-value";

        // Store value first
        StepVerifier.create(cacheAdapter.put(key, value))
                .verifyComplete();

        // Verify it exists
        StepVerifier.create(cacheAdapter.exists(key))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();

        // When & Then - evict should return true
        StepVerifier.create(cacheAdapter.evict(key))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();

        // Key should no longer exist
        StepVerifier.create(cacheAdapter.exists(key))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldClearAllEntries() {
        // Given
        StepVerifier.create(cacheAdapter.put("key1", "value1"))
                .verifyComplete();
        StepVerifier.create(cacheAdapter.put("key2", "value2"))
                .verifyComplete();

        // Verify entries exist
        StepVerifier.create(cacheAdapter.size())
                .assertNext(size -> assertThat(size).isEqualTo(2))
                .verifyComplete();

        // When & Then
        StepVerifier.create(cacheAdapter.clear())
                .verifyComplete();

        StepVerifier.create(cacheAdapter.size())
                .assertNext(size -> assertThat(size).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void shouldEvictByPrefix() {
        // Given - store entries with different prefixes
        StepVerifier.create(cacheAdapter.put("user:1", "alice"))
                .verifyComplete();
        StepVerifier.create(cacheAdapter.put("user:2", "bob"))
                .verifyComplete();
        StepVerifier.create(cacheAdapter.put("session:abc", "data1"))
                .verifyComplete();
        StepVerifier.create(cacheAdapter.put("session:def", "data2"))
                .verifyComplete();

        // When & Then - evict all "session:" entries
        StepVerifier.create(cacheAdapter.evictByPrefix("session:"))
                .assertNext(count -> assertThat(count).isEqualTo(2))
                .verifyComplete();

        // "user:" entries should remain
        StepVerifier.create(cacheAdapter.exists("user:1"))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
        StepVerifier.create(cacheAdapter.exists("user:2"))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();

        // "session:" entries should be gone
        StepVerifier.create(cacheAdapter.exists("session:abc"))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
        StepVerifier.create(cacheAdapter.exists("session:def"))
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldReturnZeroWhenEvictByPrefixMatchesNothing() {
        // Given
        StepVerifier.create(cacheAdapter.put("key1", "value1"))
                .verifyComplete();

        // When & Then
        StepVerifier.create(cacheAdapter.evictByPrefix("nonexistent:"))
                .assertNext(count -> assertThat(count).isEqualTo(0))
                .verifyComplete();

        // Original entry should remain
        StepVerifier.create(cacheAdapter.exists("key1"))
                .assertNext(result -> assertThat(result).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldReturnCacheKeys() {
        // Given
        String key1 = "key1";
        String key2 = "key2";

        StepVerifier.create(cacheAdapter.put(key1, "value1"))
                .verifyComplete();
        StepVerifier.create(cacheAdapter.put(key2, "value2"))
                .verifyComplete();

        // When & Then
        StepVerifier.create(cacheAdapter.keys())
                .assertNext(keys -> {
                    assertThat(keys).hasSize(2);
                    assertThat(keys).containsExactlyInAnyOrder(key1, key2);
                })
                .verifyComplete();
    }

    @Test
    void shouldProvideHealthInformation() {
        // When & Then
        StepVerifier.create(cacheAdapter.getHealth())
                .assertNext(health -> {
                    assertThat(health.getCacheType()).isEqualTo(CacheType.CAFFEINE);
                    assertThat(health.getCacheName()).isEqualTo("test-cache");
                    assertThat(health.isHealthy()).isTrue();
                    assertThat(health.getResponseTimeMs()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldProvideStatistics() {
        // Given - perform some cache operations
        StepVerifier.create(cacheAdapter.put("stats-key", "stats-value"))
                .verifyComplete();
        StepVerifier.create(cacheAdapter.get("stats-key"))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(cacheAdapter.get("non-existent"))
                .expectNextCount(1)
                .verifyComplete();

        // When & Then
        StepVerifier.create(cacheAdapter.getStats())
                .assertNext(stats -> {
                    assertThat(stats.getCacheType()).isEqualTo(CacheType.CAFFEINE);
                    assertThat(stats.getCacheName()).isEqualTo("test-cache");
                    assertThat(stats.getRequestCount()).isGreaterThan(0);
                    assertThat(stats.getHitCount()).isGreaterThan(0);
                    assertThat(stats.getMissCount()).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleGetWithTypeChecking() {
        // Given
        String key = "type-key";
        String value = "type-value";

        StepVerifier.create(cacheAdapter.put(key, value))
                .verifyComplete();

        // When & Then - correct type
        StepVerifier.create(cacheAdapter.get(key, String.class))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get()).isEqualTo(value);
                })
                .verifyComplete();

        // Wrong type should return empty
        StepVerifier.create(cacheAdapter.get(key, Integer.class))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }
}