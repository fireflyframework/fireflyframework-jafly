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

package org.fireflyframework.eventsourcing.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.eventsourcing.monitoring.EventStoreMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Auto-configuration for event sourcing metrics collection.
 * <p>
 * Wires {@link EventStoreMetrics} to the Micrometer {@link MeterRegistry} when
 * Micrometer is on the classpath and metrics are enabled.
 *
 * <p>Note: The {@link EventStoreMetrics} bean is also defined in
 * {@link EventSourcingAutoConfiguration}. This configuration class ensures
 * the metrics bean is available even when loaded through component scanning
 * independently of the main auto-configuration.</p>
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(prefix = "firefly.eventsourcing.performance", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EventSourcingMetricsAutoConfiguration {

    public EventSourcingMetricsAutoConfiguration() {
        log.debug("Event Sourcing Metrics Configuration initialized");
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public EventStoreMetrics eventStoreMetrics(MeterRegistry meterRegistry) {
        log.info("Creating EventStoreMetrics bean via metrics configuration");
        return new EventStoreMetrics(meterRegistry);
    }
}
