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

package org.fireflyframework.cache.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Wires observability beans (metrics + health) for fireflyframework-cache.
 * <p>
 * Activates when {@code firefly.observability.metrics.enabled} or
 * {@code firefly.observability.health.enabled} (defaults: true) are not disabled.
 */
@AutoConfiguration
@ConditionalOnClass({MeterRegistry.class, HealthIndicator.class})
@ConditionalOnProperty(prefix = "firefly.cache", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CacheObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "firefly.observability.metrics", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    CacheMetrics cacheMetrics(MeterRegistry meterRegistry) {
        return new CacheMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "cacheHealthIndicator")
    @ConditionalOnBean({HealthIndicator.class, CacheAdapter.class})
    @ConditionalOnProperty(prefix = "firefly.observability.health", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    HealthIndicator cacheHealthIndicator(ObjectProvider<FireflyCacheManager> managerProvider,
                                         ObjectProvider<CacheAdapter> adapterProvider) {
        CacheAdapter adapter = adapterProvider.getIfAvailable();
        FireflyCacheManager manager = managerProvider.getIfAvailable();
        String adapterName = manager != null ? "firefly" : "cache";
        return new CacheHealthIndicator(adapter, adapterName);
    }
}
