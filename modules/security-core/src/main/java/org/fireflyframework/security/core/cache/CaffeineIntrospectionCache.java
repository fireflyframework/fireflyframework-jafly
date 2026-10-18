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

package org.fireflyframework.security.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.TokenIntrospectionCachePort;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Default in-memory {@link TokenIntrospectionCachePort} backed by Caffeine with per-entry TTL,
 * so a high-traffic resource server caches opaque introspection results up to each token's expiry.
 */
public class CaffeineIntrospectionCache implements TokenIntrospectionCachePort {

    private record Entry(SecurityPrincipal principal, long ttlNanos) {
    }

    private final Cache<String, Entry> cache;

    public CaffeineIntrospectionCache(long maximumSize) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(new Expiry<String, Entry>() {
                    @Override
                    public long expireAfterCreate(String key, Entry value, long currentTime) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, Entry value, long currentTime, long currentDuration) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, Entry value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public Mono<SecurityPrincipal> get(String tokenKey) {
        return Mono.defer(() -> {
            Entry entry = cache.getIfPresent(tokenKey);
            return entry == null ? Mono.empty() : Mono.just(entry.principal());
        });
    }

    @Override
    public Mono<Void> put(String tokenKey, SecurityPrincipal principal, Duration ttl) {
        return Mono.fromRunnable(() -> cache.put(tokenKey, new Entry(principal, Math.max(0L, ttl.toNanos()))));
    }

    @Override
    public Mono<Void> evict(String tokenKey) {
        return Mono.fromRunnable(() -> cache.invalidate(tokenKey));
    }
}
