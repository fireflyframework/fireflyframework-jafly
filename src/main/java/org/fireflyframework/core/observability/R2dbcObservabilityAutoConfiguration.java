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

package org.fireflyframework.core.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Wires observability beans (metrics + health) for fireflyframework-r2dbc.
 * <p>
 * Beans honor the master switches under {@code firefly.observability.*}; if a host service
 * disables metrics/health they back off cleanly.
 */
@AutoConfiguration
@ConditionalOnClass({MeterRegistry.class, HealthIndicator.class, ConnectionFactory.class})
public class R2dbcObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "firefly.observability.metrics", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    R2dbcMetrics r2dbcMetrics(MeterRegistry meterRegistry) {
        return new R2dbcMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "fireflyR2dbcHealthIndicator")
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnProperty(prefix = "firefly.observability.health", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    HealthIndicator fireflyR2dbcHealthIndicator(ConnectionFactory connectionFactory) {
        return new R2dbcHealthIndicator(connectionFactory);
    }
}
