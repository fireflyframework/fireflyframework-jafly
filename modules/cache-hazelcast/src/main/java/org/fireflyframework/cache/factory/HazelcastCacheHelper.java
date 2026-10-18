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

package org.fireflyframework.cache.factory;

import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheHealth;
import org.fireflyframework.cache.core.CacheStats;
import org.fireflyframework.cache.core.CacheType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Reflection-based helper to create a Hazelcast-backed CacheAdapter without a compile-time dependency.
 *
 * Requirements:
 * - com.hazelcast.core.HazelcastInstance bean available in the Spring context
 */
@Slf4j
public class HazelcastCacheHelper {

    public static CacheAdapter createHazelcastCacheAdapter(String cacheName,
                                                           String keyPrefix,
                                                           Duration defaultTtl,
                                                           Object hazelcastInstance) {
        return new HazelcastCacheAdapterReflective(cacheName, keyPrefix, defaultTtl, hazelcastInstance);
    }

    /** Reflection-based Hazelcast adapter implementation. */
    private static class HazelcastCacheAdapterReflective implements CacheAdapter {
        private final String cacheName;
        private final String keyPrefix;
        private final Duration defaultTtl;
        private final Object hazelcastInstance;
        private final Object map; // IMap<Object,Object>

        // Cached reflective methods
        private final Method getMapMethod;
        private final Method getMethod;      // IMap.get(Object)
        private final Method putMethod;      // IMap.put(Object,Object,long,TimeUnit)
        private final Method putNoTtlMethod; // IMap.put(Object,Object)
        private final Method setMethod;      // IMap.set(Object,Object,long,TimeUnit)
        private final Method containsKeyMethod;
        private final Method deleteMethod;
        private final Method clearMethod;
        private final Method sizeMethod;
        private final Method keySetMethod;

        HazelcastCacheAdapterReflective(String cacheName, String keyPrefix, Duration defaultTtl, Object hazelcastInstance) {
            try {
                this.cacheName = cacheName;
                this.keyPrefix = (keyPrefix != null && !keyPrefix.isBlank())
                        ? keyPrefix + ":" + cacheName + ":"
                        : "cache:" + cacheName + ":";
                this.defaultTtl = defaultTtl;
                this.hazelcastInstance = hazelcastInstance;

                Class<?> hzClass = Class.forName("com.hazelcast.core.HazelcastInstance");
                this.getMapMethod = hzClass.getMethod("getMap", String.class);
                this.map = getMapMethod.invoke(hazelcastInstance, this.cacheName);

                Class<?> iMapClass = Class.forName("com.hazelcast.map.IMap");
                this.getMethod = iMapClass.getMethod("get", Object.class);
                this.putMethod = iMapClass.getMethod("put", Object.class, Object.class, long.class, TimeUnit.class);
                this.putNoTtlMethod = iMapClass.getMethod("put", Object.class, Object.class);
                this.setMethod = iMapClass.getMethod("set", Object.class, Object.class, long.class, TimeUnit.class);
                this.containsKeyMethod = iMapClass.getMethod("containsKey", Object.class);
                this.deleteMethod = iMapClass.getMethod("delete", Object.class);
                this.clearMethod = iMapClass.getMethod("clear");
                this.sizeMethod = iMapClass.getMethod("size");
                this.keySetMethod = iMapClass.getMethod("keySet");

                log.info("Hazelcast cache adapter created for cache '{}' with prefix '{}'", cacheName, this.keyPrefix);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize Hazelcast reflective adapter: " + e.getMessage(), e);
            }
        }

        private String buildKey(Object key) {
            return keyPrefix + String.valueOf(key);
        }

        @Override
        public <K, V> Mono<Optional<V>> get(K key) {
            return Mono.fromCallable(() -> {
                try {
                    @SuppressWarnings("unchecked") V value = (V) getMethod.invoke(map, buildKey(key));
                    return Optional.ofNullable(value);
                } catch (Exception e) {
                    log.warn("Hazelcast get error: {}", e.getMessage());
                    return Optional.empty();
                }
            });
        }

        @Override
        public <K, V> Mono<Optional<V>> get(K key, Class<V> valueType) {
            return get(key).map(opt -> opt.filter(valueType::isInstance).map(valueType::cast));
        }

        @Override
        public <K, V> Mono<Void> put(K key, V value) {
            return Mono.fromRunnable(() -> {
                try {
                    putNoTtlMethod.invoke(map, buildKey(key), value);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public <K, V> Mono<Void> put(K key, V value, Duration ttl) {
            return Mono.fromRunnable(() -> {
                try {
                    Duration effective = ttl != null ? ttl : defaultTtl;
                    if (effective != null && !effective.isZero() && !effective.isNegative()) {
                        putMethod.invoke(map, buildKey(key), value, effective.toMillis(), TimeUnit.MILLISECONDS);
                    } else {
                        setMethod.invoke(map, buildKey(key), value, 0L, TimeUnit.MILLISECONDS);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public <K, V> Mono<Boolean> putIfAbsent(K key, V value) {
            // Fallback simple implementation: check then put
            return exists(key).flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) return Mono.just(false);
                return put(key, value).thenReturn(true);
            });
        }

        @Override
        public <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl) {
            return exists(key).flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) return Mono.just(false);
                return put(key, value, ttl).thenReturn(true);
            });
        }

        @Override
        public <K> Mono<Boolean> evict(K key) {
            return Mono.fromCallable(() -> {
                try {
                    boolean existed = (Boolean) containsKeyMethod.invoke(map, buildKey(key));
                    deleteMethod.invoke(map, buildKey(key));
                    return existed;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public Mono<Void> clear() {
            return Mono.fromRunnable(() -> {
                try {
                    clearMethod.invoke(map);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public <K> Mono<Boolean> exists(K key) {
            return Mono.fromCallable(() -> {
                try {
                    return (Boolean) containsKeyMethod.invoke(map, buildKey(key));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        @SuppressWarnings("unchecked")
        public <K> Mono<Set<K>> keys() {
            return Mono.fromCallable(() -> {
                try {
                    Set<Object> keys = (Set<Object>) keySetMethod.invoke(map);
                    // Filter by our prefix and strip it
                    return (Set<K>) keys.stream()
                        .map(Object::toString)
                        .filter(k -> k.startsWith(keyPrefix))
                        .map(k -> (K) k.substring(keyPrefix.length()))
                        .collect(Collectors.toSet());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public Mono<Long> size() {
            return Mono.fromCallable(() -> {
                try {
                    Object sz = sizeMethod.invoke(map);
                    if (sz instanceof Integer i) return (long) i;
                    if (sz instanceof Long l) return l;
                    return 0L;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public Mono<CacheStats> getStats() {
            // Minimal stats for Hazelcast adapter (compatible with simple CacheStats record)
return size().map(count -> CacheStats.empty(CacheType.HAZELCAST, cacheName));
        }

        @Override
        public CacheType getCacheType() {
            return CacheType.HAZELCAST;
        }

        @Override
        public String getCacheName() {
            return cacheName;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Mono<CacheHealth> getHealth() {
            return Mono.just(CacheHealth.healthy(CacheType.HAZELCAST, cacheName, null));
        }

        @Override
        public void close() {
            // No-op; lifecycle managed externally
        }
    }
}
