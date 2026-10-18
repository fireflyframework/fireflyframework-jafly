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

import org.fireflyframework.eventsourcing.health.ProjectionHealthIndicator;
import org.fireflyframework.eventsourcing.projection.ProjectionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.List;

/**
 * Auto-configuration for Event Sourcing projection monitoring and health checks.
 * Provides automatic registration of health indicators and metrics for projections.
 */
@AutoConfiguration
@ConditionalOnClass({ProjectionService.class, MeterRegistry.class})
@EnableConfigurationProperties(EventSourcingProjectionProperties.class)
@Slf4j
public class EventSourcingProjectionAutoConfiguration {
    
    /**
     * Creates a health indicator for all registered projection services.
     */
    @Bean
    @ConditionalOnEnabledHealthIndicator("projection")
    @ConditionalOnMissingBean(name = "projectionHealthIndicator")
    public HealthIndicator projectionHealthIndicator(
            List<ProjectionService<?>> projectionServices,
            EventSourcingProjectionProperties properties) {
        
        log.info("Registering ProjectionHealthIndicator with {} projection services", 
                projectionServices.size());
        
        return new ProjectionHealthIndicator(
                projectionServices, 
                properties.getHealthCheck().getTimeout(),
                properties.getHealthCheck().getMaxAcceptableLag()
        );
    }
}