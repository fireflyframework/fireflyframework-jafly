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

package org.fireflyframework.cache.config;

import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the core {@link CacheAutoConfiguration} default (Caffeine-only) wiring.
 * <p>
 * The core module ships no distributed providers (Redis/Hazelcast/JCache/Postgres) on
 * its classpath, so the default cache manager must wire to Caffeine. These tests verify
 * that the library works correctly out of the box without referencing any provider type.
 */
class CacheAutoConfigurationCaffeineDefaultTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void shouldStartWithCaffeineDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FireflyCacheManager.class);
        });
    }

    @Test
    void shouldCreateCaffeineCacheManagerByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            // The default fireflyCacheManager bean must be present and Caffeine-backed.
            assertThat(context).hasBean("fireflyCacheManager");
            assertThat(context).hasSingleBean(FireflyCacheManager.class);

            FireflyCacheManager manager = context.getBean(FireflyCacheManager.class);
            assertThat(manager.getCacheType()).isEqualTo(CacheType.CAFFEINE);
            assertThat(manager.getCacheName()).isEqualTo("default");
        });
    }

    @Test
    void shouldNotCreateRedisBeans() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            // No distributed-provider beans should exist in the core-only classpath.
            assertThat(context).doesNotHaveBean("redisConnectionFactory");
            assertThat(context).doesNotHaveBean("reactiveRedisTemplate");
            assertThat(context).doesNotHaveBean("redisCacheAdapter");
        });
    }

    @Test
    void shouldWorkWithCustomCaffeineConfiguration() {
        this.contextRunner
                .withPropertyValues(
                        "firefly.cache.caffeine.maximum-size=500",
                        "firefly.cache.caffeine.expire-after-write=PT30M"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FireflyCacheManager.class);

                    FireflyCacheManager manager = context.getBean(FireflyCacheManager.class);
                    assertThat(manager.getCacheType()).isEqualTo(CacheType.CAFFEINE);
                });
    }

    @Test
    void shouldDisableCacheWhenPropertySetToFalse() {
        this.contextRunner
                .withPropertyValues("firefly.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    // Should NOT have cache manager when disabled
                    assertThat(context).doesNotHaveBean(FireflyCacheManager.class);
                });
    }

    @Test
    void shouldDisableCaffeineWhenPropertySetToFalse() {
        this.contextRunner
                .withPropertyValues("firefly.cache.caffeine.enabled=false")
                .run(context -> {
                    // Should fail because no cache adapters are available
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void shouldUseCacheNameFromCaffeineConfig() {
        this.contextRunner
                .withPropertyValues("firefly.cache.caffeine.cache-name=my-custom-cache")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    FireflyCacheManager manager = context.getBean(FireflyCacheManager.class);
                    assertThat(manager).isNotNull();
                    assertThat(manager.getCacheName()).isEqualTo("my-custom-cache");
                });
    }

    @Test
    void shouldCreateCacheSerializerBean() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("cacheSerializer");
        });
    }

    @Test
    void shouldCreateCacheObjectMapperBean() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("cacheObjectMapper");
        });
    }
}
