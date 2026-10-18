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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.data.config.DataEnrichmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OperationCacheService.
 */
@ExtendWith(MockitoExtension.class)
class OperationCacheServiceTest {

    @Mock
    private CacheAdapter cacheAdapter;

    private ObjectMapper objectMapper;
    private OperationCacheService cacheService;
    private DataEnrichmentProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        properties = new DataEnrichmentProperties();
        properties.getOperations().setCacheEnabled(true);
        properties.getOperations().setCacheTtlSeconds(1800);

        lenient().when(cacheAdapter.getCacheType()).thenReturn(CacheType.CAFFEINE);

        cacheService = new OperationCacheService(cacheAdapter, objectMapper, properties);
    }

    @Test
    void get_shouldReturnEmptyWhenCacheDisabled() {
        // Given
        properties.getOperations().setCacheEnabled(false);
        cacheService = new OperationCacheService(cacheAdapter, objectMapper, properties);

        // When
        Mono<Optional<String>> result = cacheService.get("tenant-1", "ProviderA", "op-1", "request", String.class);

        // Then
        StepVerifier.create(result)
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();

        verify(cacheAdapter, never()).get(anyString(), any());
    }

    @Test
    void get_shouldReturnCachedValueWhenPresent() {
        // Given
        when(cacheAdapter.<String, String>get(anyString(), eq(String.class)))
                .thenReturn(Mono.just(Optional.of("cached-response")));

        // When
        Mono<Optional<String>> result = cacheService.get("tenant-1", "ProviderA", "op-1", "request", String.class);

        // Then
        StepVerifier.create(result)
                .assertNext(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get()).isEqualTo("cached-response");
                })
                .verifyComplete();
    }

    @Test
    void get_shouldReturnEmptyWhenNotInCache() {
        // Given
        when(cacheAdapter.<String, String>get(anyString(), eq(String.class)))
                .thenReturn(Mono.just(Optional.empty()));

        // When
        Mono<Optional<String>> result = cacheService.get("tenant-1", "ProviderA", "op-1", "request", String.class);

        // Then
        StepVerifier.create(result)
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void get_shouldHandleCacheAdapterError() {
        // Given
        when(cacheAdapter.<String, String>get(anyString(), eq(String.class)))
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When
        Mono<Optional<String>> result = cacheService.get("tenant-1", "ProviderA", "op-1", "request", String.class);

        // Then - Should return empty on error (fail-safe)
        StepVerifier.create(result)
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void put_shouldNotCacheWhenCacheDisabled() {
        // Given
        properties.getOperations().setCacheEnabled(false);
        cacheService = new OperationCacheService(cacheAdapter, objectMapper, properties);

        // When
        Mono<Void> result = cacheService.put("tenant-1", "ProviderA", "op-1", "request", "response");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter, never()).put(anyString(), any(), any(Duration.class));
    }

    @Test
    void put_shouldCacheResponse() {
        // Given
        when(cacheAdapter.put(anyString(), eq("response"), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheService.put("tenant-1", "ProviderA", "op-1", "request", "response");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter).put(anyString(), eq("response"), eq(Duration.ofSeconds(1800)));
    }

    @Test
    void put_shouldHandleCacheAdapterError() {
        // Given
        when(cacheAdapter.put(anyString(), eq("response"), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When
        Mono<Void> result = cacheService.put("tenant-1", "ProviderA", "op-1", "request", "response");

        // Then - Should complete without error (fail-safe)
        StepVerifier.create(result)
                .verifyComplete();
    }

    // --- evictByTenant tests ---

    @Test
    void evictByTenant_shouldEvictByPrefix() {
        // Given
        when(cacheAdapter.<String>keys()).thenReturn(Mono.just(Set.of(
                "operation:tenant-1:ProviderA:op1:hash1", "operation:tenant-1:ProviderB:op2:hash2",
                "operation:tenant-2:ProviderA:op1:hash3")));
        when(cacheAdapter.evict(anyString())).thenReturn(Mono.just(true));

        // When
        Mono<Void> result = cacheService.evictByTenant("tenant-1");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter).keys();
        verify(cacheAdapter, never()).clear();
    }

    @Test
    void evictByTenant_shouldReturnEmptyWhenCacheDisabled() {
        // Given
        properties.getOperations().setCacheEnabled(false);
        cacheService = new OperationCacheService(cacheAdapter, objectMapper, properties);

        // When
        Mono<Void> result = cacheService.evictByTenant("tenant-1");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter, never()).keys();
        verify(cacheAdapter, never()).clear();
    }

    @Test
    void evictByTenant_shouldHandleError() {
        // Given
        when(cacheAdapter.<String>keys())
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When
        Mono<Void> result = cacheService.evictByTenant("tenant-1");

        // Then - Should complete without error (fail-safe)
        StepVerifier.create(result)
                .verifyComplete();
    }

    // --- evictByProvider tests ---

    @Test
    void evictByProvider_shouldEvictByPrefix() {
        // Given
        when(cacheAdapter.<String>keys()).thenReturn(Mono.just(Set.of(
                "operation:tenant-1:ProviderA:op1:hash1", "operation:tenant-1:ProviderA:op2:hash2",
                "operation:tenant-1:ProviderB:op1:hash3")));
        when(cacheAdapter.evict(anyString())).thenReturn(Mono.just(true));

        // When
        Mono<Void> result = cacheService.evictByProvider("tenant-1", "ProviderA");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter).keys();
        verify(cacheAdapter, never()).clear();
    }

    @Test
    void evictByProvider_shouldReturnEmptyWhenCacheDisabled() {
        // Given
        properties.getOperations().setCacheEnabled(false);
        cacheService = new OperationCacheService(cacheAdapter, objectMapper, properties);

        // When
        Mono<Void> result = cacheService.evictByProvider("tenant-1", "ProviderA");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter, never()).keys();
        verify(cacheAdapter, never()).clear();
    }

    @Test
    void evictByProvider_shouldHandleError() {
        // Given
        when(cacheAdapter.<String>keys())
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When
        Mono<Void> result = cacheService.evictByProvider("tenant-1", "ProviderA");

        // Then - Should complete without error (fail-safe)
        StepVerifier.create(result)
                .verifyComplete();
    }

    // --- evictByOperation tests ---

    @Test
    void evictByOperation_shouldEvictByPrefix() {
        // Given
        when(cacheAdapter.<String>keys()).thenReturn(Mono.just(Set.of(
                "operation:tenant-1:ProviderA:search-company:hash1",
                "operation:tenant-1:ProviderA:search-company:hash2",
                "operation:tenant-1:ProviderA:other-op:hash3")));
        when(cacheAdapter.evict(anyString())).thenReturn(Mono.just(true));

        // When
        Mono<Void> result = cacheService.evictByOperation("tenant-1", "ProviderA", "search-company");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter).keys();
        verify(cacheAdapter, never()).clear();
    }

    @Test
    void evictByOperation_shouldReturnEmptyWhenCacheDisabled() {
        // Given
        properties.getOperations().setCacheEnabled(false);
        cacheService = new OperationCacheService(cacheAdapter, objectMapper, properties);

        // When
        Mono<Void> result = cacheService.evictByOperation("tenant-1", "ProviderA", "search-company");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter, never()).keys();
        verify(cacheAdapter, never()).clear();
    }

    @Test
    void evictByOperation_shouldHandleError() {
        // Given
        when(cacheAdapter.<String>keys())
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When
        Mono<Void> result = cacheService.evictByOperation("tenant-1", "ProviderA", "search-company");

        // Then - Should complete without error (fail-safe)
        StepVerifier.create(result)
                .verifyComplete();
    }

    // --- isCacheEnabled tests ---

    @Test
    void isCacheEnabled_shouldReturnTrueWhenEnabled() {
        assertThat(cacheService.isCacheEnabled()).isTrue();
    }

    @Test
    void isCacheEnabled_shouldReturnFalseWhenDisabled() {
        properties.getOperations().setCacheEnabled(false);
        cacheService = new OperationCacheService(cacheAdapter, objectMapper, properties);

        assertThat(cacheService.isCacheEnabled()).isFalse();
    }
}
