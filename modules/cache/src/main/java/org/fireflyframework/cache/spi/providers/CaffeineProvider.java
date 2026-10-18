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

package org.fireflyframework.cache.spi.providers;

import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheAdapter;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.spi.CacheProviderFactory;

import java.time.Duration;

public class CaffeineProvider implements CacheProviderFactory {
    @Override
    public CacheType getType() { return CacheType.CAFFEINE; }
    @Override
    public int priority() { return 40; }
    @Override
    public boolean isAvailable(ProviderContext ctx) { return ctx.properties.getCaffeine().isEnabled(); }
    @Override
    public CacheAdapter create(String cacheName, String keyPrefix, Duration defaultTtl, ProviderContext ctx) {
        var caffeineProps = ctx.properties.getCaffeine();
        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .keyPrefix(keyPrefix)
                .maximumSize(caffeineProps.getMaximumSize())
                .expireAfterWrite(defaultTtl != null ? defaultTtl : caffeineProps.getExpireAfterWrite())
                .expireAfterAccess(caffeineProps.getExpireAfterAccess())
                .refreshAfterWrite(caffeineProps.getRefreshAfterWrite())
                .recordStats(caffeineProps.isRecordStats())
                .weakKeys(caffeineProps.isWeakKeys())
                .weakValues(caffeineProps.isWeakValues())
                .softValues(caffeineProps.isSoftValues())
                .build();
        return new CaffeineCacheAdapter(cacheName, config);
    }
}
