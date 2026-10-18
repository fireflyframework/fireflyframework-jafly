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

/*
 * SmartCacheAdapter - L1 (Caffeine or local) + L2 (distributed) composite cache.
 */
package org.fireflyframework.cache.adapter.smart;

import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheHealth;
import org.fireflyframework.cache.core.CacheStats;
import org.fireflyframework.cache.core.CacheType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class SmartCacheAdapter implements CacheAdapter {
    private final CacheAdapter l1; // expected CAFFEINE
    private final CacheAdapter l2; // distributed (REDIS/HAZELCAST/JCACHE)
    private final String cacheName;
    private final Duration defaultTtl; // used for backfill
    private final boolean backfillOnRead;

    public SmartCacheAdapter(String cacheName, CacheAdapter l1, CacheAdapter l2, Duration defaultTtl, boolean backfillOnRead) {
        this.cacheName = cacheName;
        this.l1 = l1;
        this.l2 = l2;
        this.defaultTtl = defaultTtl;
        this.backfillOnRead = backfillOnRead;
    }

    private <K,V> Mono<Optional<V>> backfillL1(String key, Optional<V> o) {
        if (backfillOnRead && o.isPresent()) {
            try {
                return l1.put(key, o.get(), defaultTtl).thenReturn(o);
            } catch (Throwable t) {
                log.debug("SmartCache L1 backfill failed: {}", t.getMessage());
            }
        }
        return Mono.just(o);
    }

    @Override
    public <K, V> Mono<Optional<V>> get(K key) {
        return l1.<K,V>get(key)
                .flatMap(opt -> opt.isPresent() ? Mono.just(opt)
                        : l2.<K,V>get(key).flatMap(o2 -> backfillL1(String.valueOf(key), o2)));
    }

    @Override
    public <K, V> Mono<Optional<V>> get(K key, Class<V> valueType) {
        return l1.<K,V>get(key, valueType)
                .flatMap(opt -> opt.isPresent() ? Mono.just(opt)
                        : l2.<K,V>get(key, valueType).flatMap(o2 -> backfillL1(String.valueOf(key), o2)));
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value) {
        return Mono.when(l1.put(key, value), l2.put(key, value)).then();
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value, Duration ttl) {
        return Mono.when(l1.put(key, value, ttl != null ? ttl : defaultTtl), l2.put(key, value, ttl)).then();
    }

    @Override
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value) {
        // ensure L2 authoritative; backfill L1 if stored
        return l2.putIfAbsent(key, value)
                .flatMap(stored -> stored ? l1.putIfAbsent(key, value).thenReturn(true)
                        : Mono.just(false));
    }

    @Override
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl) {
        return l2.putIfAbsent(key, value, ttl)
                .flatMap(stored -> stored ? l1.putIfAbsent(key, value, ttl != null ? ttl : defaultTtl).thenReturn(true)
                        : Mono.just(false));
    }

    @Override
    public <K> Mono<Boolean> evict(K key) {
        return Mono.zip(l1.evict(key), l2.evict(key)).map(t -> t.getT1() || t.getT2());
    }

    @Override
    public Mono<Void> clear() {
        // Prefer clearing both; callers should use carefully
        return Mono.when(l1.clear(), l2.clear()).then();
    }

    @Override
    public <K> Mono<Boolean> exists(K key) {
        return l1.exists(key).flatMap(e -> e ? Mono.just(true) : l2.exists(key));
    }

    @Override
    public <K> Mono<Set<K>> keys() {
        // keys from L2 (authoritative)
        return l2.keys();
    }

    @Override
    public Mono<Long> size() {
        return l2.size();
    }

    @Override
    public Mono<CacheStats> getStats() {
        return Mono.zip(l1.getStats(), l2.getStats()).map(t -> {
            var s1 = t.getT1(); var s2 = t.getT2();
            return CacheStats.builder()
                    .requestCount(s1.getRequestCount() + s2.getRequestCount())
                    .hitCount(s1.getHitCount() + s2.getHitCount())
                    .missCount(s1.getMissCount() + s2.getMissCount())
                    .loadCount(s1.getLoadCount() + s2.getLoadCount())
                    .evictionCount(s1.getEvictionCount() + s2.getEvictionCount())
                    .entryCount(Math.max(s1.getEntryCount(), s2.getEntryCount()))
                    .averageLoadTime((s1.getAverageLoadTime() + s2.getAverageLoadTime()) / 2.0)
                    .estimatedSize(Math.max(s1.getEstimatedSize(), s2.getEstimatedSize()))
                    .capturedAt(Instant.now())
                    .cacheType(getCacheType())
                    .cacheName(cacheName)
                    .build();
        });
    }

    @Override
    public CacheType getCacheType() { return CacheType.AUTO; }

    @Override
    public String getCacheName() { return cacheName; }

    @Override
    public boolean isAvailable() { return l2.isAvailable(); }

    @Override
    public Mono<CacheHealth> getHealth() {
        return Mono.zip(l1.getHealth(), l2.getHealth()).map(t -> {
            var h1 = t.getT1(); var h2 = t.getT2();
            if (h2.isHealthy()) {
                return CacheHealth.healthy(h2.getCacheType(), cacheName, null);
            }
            return CacheHealth.unhealthy(h2.getCacheType(), cacheName, h2.getErrorMessage(), null);
        });
    }

    @Override
    public void close() {
        try { l1.close(); } catch (Exception ignored) {}
        try { l2.close(); } catch (Exception ignored) {}
    }
}
