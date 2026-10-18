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

package org.fireflyframework.cache.adapter.smart;

import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheAdapter;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SmartCacheAdapterTest {

    private CacheAdapter l1;
    private CacheAdapter l2;
    private SmartCacheAdapter smart;

    @BeforeEach
    void setUp() {
        CaffeineCacheConfig cfg1 = CaffeineCacheConfig.builder()
                .keyPrefix("test:l1")
                .maximumSize(1000L)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats(true)
                .build();
        CaffeineCacheConfig cfg2 = CaffeineCacheConfig.builder()
                .keyPrefix("test:l2")
                .maximumSize(1000L)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats(true)
                .build();
        l1 = new CaffeineCacheAdapter("smart", cfg1);
        l2 = new CaffeineCacheAdapter("smart", cfg2);
        smart = new SmartCacheAdapter("smart", l1, l2, Duration.ofMinutes(10), true);
        // clean
        l1.clear().block();
        l2.clear().block();
    }

    @Test
    void shouldHitL1WhenPresent() {
        smart.put("k1", "v1").block();
        StepVerifier.create(smart.get("k1", String.class))
                .assertNext(opt -> assertThat(opt).contains("v1"))
                .verifyComplete();
    }

    @Test
    void shouldBackfillL1OnL2Hit() {
        // put only in L2
        l2.put("k2", "v2").block();
        // first read fills L1
        StepVerifier.create(smart.get("k2", String.class))
                .assertNext(opt -> assertThat(opt).contains("v2"))
                .verifyComplete();
        // remove from L2 to ensure L1 has it now
        l2.evict("k2").block();
        StepVerifier.create(l1.get("k2", String.class))
                .assertNext(opt -> assertThat(opt).contains("v2"))
                .verifyComplete();
    }

    @Test
    void shouldWriteThroughOnPut() {
        smart.put("k3", "v3").block();
        StepVerifier.create(Mono.zip(l1.get("k3", String.class), l2.get("k3", String.class)))
                .assertNext(tuple -> {
                    assertThat(tuple.getT1()).contains(Optional.of("v3").get());
                    assertThat(tuple.getT2()).contains(Optional.of("v3").get());
                })
                .verifyComplete();
    }

    @Test
    void shouldPutIfAbsentWriteThrough() {
        StepVerifier.create(smart.putIfAbsent("k4", "v4"))
                .expectNext(true)
                .verifyComplete();
        // second attempt should be false
        StepVerifier.create(smart.putIfAbsent("k4", "v4b"))
                .expectNext(false)
                .verifyComplete();
        // both layers see v4
        StepVerifier.create(Mono.zip(l1.get("k4", String.class), l2.get("k4", String.class)))
                .assertNext(tuple -> {
                    assertThat(tuple.getT1()).contains(Optional.of("v4").get());
                    assertThat(tuple.getT2()).contains(Optional.of("v4").get());
                })
                .verifyComplete();
    }

    @Test
    void shouldEvictBothLayers() {
        smart.put("k5", "v5").block();
        smart.evict("k5").block();
        StepVerifier.create(Mono.zip(l1.get("k5", String.class), l2.get("k5", String.class)))
                .assertNext(tuple -> {
                    assertThat(tuple.getT1()).isEmpty();
                    assertThat(tuple.getT2()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldClearBothLayers() {
        smart.put("k6", "v6").block();
        smart.clear().block();
        StepVerifier.create(smart.get("k6", String.class))
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldAggregateStats() {
        smart.put("k7", "v7").block();
        smart.get("k7", String.class).block();
        CacheStats stats = smart.getStats().block();
        assertThat(stats).isNotNull();
        assertThat(stats.getCacheName()).isEqualTo("smart");
    }
}
