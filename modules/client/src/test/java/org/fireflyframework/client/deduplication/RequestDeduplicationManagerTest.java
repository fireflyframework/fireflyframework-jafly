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

package org.fireflyframework.client.deduplication;

import org.fireflyframework.client.deduplication.RequestDeduplicationManager.DeduplicationStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RequestDeduplicationManager.
 */
class RequestDeduplicationManagerTest {

    private RequestDeduplicationManager manager;

    @BeforeEach
    void setUp() {
        manager = new RequestDeduplicationManager(Duration.ofMinutes(5));
    }

    @Test
    void shouldGenerateIdempotencyKey() {
        // When
        String key = manager.generateIdempotencyKey();

        // Then
        assertThat(key).isNotNull();
        assertThat(key).isNotEmpty();
    }

    @Test
    void shouldGenerateRequestFingerprint() {
        // Given
        String method = "POST";
        String endpoint = "/users";
        Object body = Map.of("name", "John");

        // When
        String fingerprint = manager.generateFingerprint(method, endpoint, body);

        // Then
        assertThat(fingerprint).isNotNull();
        assertThat(fingerprint).hasSize(64); // SHA-256 hex string
    }

    @Test
    void shouldExecuteRequestOnce() {
        // Given
        String key = "test-key";
        Mono<String> request = Mono.just("result");

        // When
        Mono<String> first = manager.executeWithDeduplication(key, request);
        Mono<String> second = manager.executeWithDeduplication(key, Mono.just("different"));

        // Then
        StepVerifier.create(first)
            .expectNext("result")
            .verifyComplete();

        StepVerifier.create(second)
            .expectNext("result") // Should return cached result
            .verifyComplete();
    }

    @Test
    void shouldDetectDuplicate() {
        // Given
        String key = "test-key";
        manager.executeWithDeduplication(key, Mono.just("result")).block();

        // When
        boolean isDuplicate = manager.isDuplicate(key);

        // Then
        assertThat(isDuplicate).isTrue();
    }

    @Test
    void shouldGetCompletedResult() {
        // Given
        String key = "test-key";
        String result = "test-result";
        manager.executeWithDeduplication(key, Mono.just(result)).block();

        // When
        Optional<Object> cached = manager.getCompletedResult(key);

        // Then
        assertThat(cached).isPresent();
        assertThat(cached.get()).isEqualTo(result);
    }

    @Test
    void shouldClearCompletedRequests() {
        // Given
        String key = "test-key";
        manager.executeWithDeduplication(key, Mono.just("result")).block();

        // When
        manager.clear();

        // Then
        Optional<Object> cached = manager.getCompletedResult(key);
        assertThat(cached).isEmpty();
    }

    @Test
    void shouldGetStatistics() {
        // Given
        manager.executeWithDeduplication("key1", Mono.just("result1")).block();
        manager.executeWithDeduplication("key2", Mono.just("result2")).block();

        // When
        DeduplicationStatistics stats = manager.getStatistics();

        // Then
        assertThat(stats.completedRequests()).isEqualTo(2);
        assertThat(stats.inFlightRequests()).isZero();
    }
}

