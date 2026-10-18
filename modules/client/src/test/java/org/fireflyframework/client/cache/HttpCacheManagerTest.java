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

package org.fireflyframework.client.cache;

import org.fireflyframework.client.cache.HttpCacheManager.CacheStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HttpCacheManager.
 */
class HttpCacheManagerTest {

    private HttpCacheManager cacheManager;
    private HttpCacheConfig config;

    @BeforeEach
    void setUp() {
        config = HttpCacheConfig.builder()
            .enabled(true)
            .defaultTtl(Duration.ofMinutes(5))
            .maxCacheSize(100)
            .build();

        cacheManager = new HttpCacheManager(config);
    }

    @Test
    void shouldCacheValue() {
        // Given
        String key = "test-key";
        String value = "test-value";

        // When
        cacheManager.put(key, value, null, null);

        // Then
        Optional<HttpCacheManager.CacheEntry> cached = cacheManager.get(key);
        assertThat(cached).isPresent();
        assertThat(cached.get().getValue()).isEqualTo(value);
    }

    @Test
    void shouldReturnCachedValueOnHit() {
        // Given
        String key = "test-key";
        String value = "cached-value";
        cacheManager.put(key, value, null, null);

        // When
        Mono<String> result = cacheManager.getOrExecute(
            key,
            Mono.just("new-value"),
            null,
            null
        );

        // Then
        StepVerifier.create(result)
            .expectNext("cached-value")
            .verifyComplete();

        CacheStatistics stats = cacheManager.getStatistics();
        assertThat(stats.hits()).isEqualTo(1);
        assertThat(stats.misses()).isZero();
    }

    @Test
    void shouldExecuteOnCacheMiss() {
        // Given
        String key = "test-key";

        // When
        Mono<String> result = cacheManager.getOrExecute(
            key,
            Mono.just("new-value"),
            null,
            null
        );

        // Then
        StepVerifier.create(result)
            .expectNext("new-value")
            .verifyComplete();

        CacheStatistics stats = cacheManager.getStatistics();
        assertThat(stats.hits()).isZero();
        assertThat(stats.misses()).isEqualTo(1);
    }

    @Test
    void shouldExpireAfterTtl() throws InterruptedException {
        // Given
        HttpCacheConfig shortTtlConfig = HttpCacheConfig.builder()
            .enabled(true)
            .defaultTtl(Duration.ofMillis(100))
            .build();

        HttpCacheManager shortTtlCache = new HttpCacheManager(shortTtlConfig);
        String key = "test-key";
        String value = "test-value";

        shortTtlCache.put(key, value, null, null);

        // When
        Thread.sleep(150);

        // Then
        Optional<HttpCacheManager.CacheEntry> cached = shortTtlCache.get(key);
        assertThat(cached).isEmpty();
    }

    @Test
    void shouldInvalidateKey() {
        // Given
        String key = "test-key";
        cacheManager.put(key, "value", null, null);

        // When
        cacheManager.invalidate(key);

        // Then
        Optional<HttpCacheManager.CacheEntry> cached = cacheManager.get(key);
        assertThat(cached).isEmpty();
    }

    @Test
    void shouldInvalidateByPattern() {
        // Given
        cacheManager.put("service1:GET:/users", "value1", null, null);
        cacheManager.put("service1:GET:/posts", "value2", null, null);
        cacheManager.put("service2:GET:/users", "value3", null, null);

        // When
        cacheManager.invalidatePattern("service1:.*");

        // Then
        assertThat(cacheManager.get("service1:GET:/users")).isEmpty();
        assertThat(cacheManager.get("service1:GET:/posts")).isEmpty();
        assertThat(cacheManager.get("service2:GET:/users")).isPresent();
    }

    @Test
    void shouldInvalidateService() {
        // Given
        cacheManager.put("service1:GET:/users", "value1", null, null);
        cacheManager.put("service1:GET:/posts", "value2", null, null);
        cacheManager.put("service2:GET:/users", "value3", null, null);

        // When
        cacheManager.invalidateService("service1");

        // Then
        assertThat(cacheManager.get("service1:GET:/users")).isEmpty();
        assertThat(cacheManager.get("service1:GET:/posts")).isEmpty();
        assertThat(cacheManager.get("service2:GET:/users")).isPresent();
    }

    @Test
    void shouldClearAllCache() {
        // Given
        cacheManager.put("key1", "value1", null, null);
        cacheManager.put("key2", "value2", null, null);

        // When
        cacheManager.clear();

        // Then
        assertThat(cacheManager.get("key1")).isEmpty();
        assertThat(cacheManager.get("key2")).isEmpty();
        assertThat(cacheManager.getStatistics().size()).isZero();
    }

    @Test
    void shouldCalculateHitRate() {
        // Given
        cacheManager.put("key1", "value1", null, null);

        // When
        cacheManager.getOrExecute("key1", Mono.just("new"), null, null).block(); // hit
        cacheManager.getOrExecute("key2", Mono.just("new"), null, null).block(); // miss
        cacheManager.getOrExecute("key1", Mono.just("new"), null, null).block(); // hit

        // Then
        CacheStatistics stats = cacheManager.getStatistics();
        assertThat(stats.hitRate()).isEqualTo(2.0 / 3.0);
    }

    @Test
    void shouldStoreETag() {
        // Given
        String key = "test-key";
        String value = "test-value";
        String etag = "\"abc123\"";

        // When
        cacheManager.put(key, value, etag, null);

        // Then
        Optional<HttpCacheManager.CacheEntry> cached = cacheManager.get(key);
        assertThat(cached).isPresent();
        assertThat(cached.get().getEtag()).isEqualTo(etag);
    }

    @Test
    void shouldStoreLastModified() {
        // Given
        String key = "test-key";
        String value = "test-value";
        String lastModified = "Wed, 21 Oct 2015 07:28:00 GMT";

        // When
        cacheManager.put(key, value, null, lastModified);

        // Then
        Optional<HttpCacheManager.CacheEntry> cached = cacheManager.get(key);
        assertThat(cached).isPresent();
        assertThat(cached.get().getLastModified()).isEqualTo(lastModified);
    }

    @Test
    void shouldUseAggressiveConfig() {
        // Given
        HttpCacheConfig config = HttpCacheConfig.aggressive();

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getDefaultTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(config.getMaxCacheSize()).isEqualTo(5000);
    }

    @Test
    void shouldUseConservativeConfig() {
        // Given
        HttpCacheConfig config = HttpCacheConfig.conservative();

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getDefaultTtl()).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.getMaxCacheSize()).isEqualTo(500);
    }

    @Test
    void shouldRespectMaxCacheSize() {
        // Given
        HttpCacheConfig smallConfig = HttpCacheConfig.builder()
            .enabled(true)
            .maxCacheSize(2)
            .build();

        HttpCacheManager smallCache = new HttpCacheManager(smallConfig);

        // When
        smallCache.put("key1", "value1", null, null);
        smallCache.put("key2", "value2", null, null);
        smallCache.put("key3", "value3", null, null);

        // Then
        assertThat(smallCache.getStatistics().size()).isLessThanOrEqualTo(2);
    }
}

