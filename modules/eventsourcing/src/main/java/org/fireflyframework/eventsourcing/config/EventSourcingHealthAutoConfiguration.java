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

import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.eventsourcing.health.EventStoreHealthIndicator;
import org.fireflyframework.eventsourcing.health.OutboxHealthIndicator;
import org.fireflyframework.eventsourcing.health.ProjectionHealthIndicator;
import org.fireflyframework.eventsourcing.health.SnapshotStoreHealthIndicator;
import org.fireflyframework.eventsourcing.outbox.EventOutboxService;
import org.fireflyframework.eventsourcing.projection.ProjectionService;
import org.fireflyframework.eventsourcing.snapshot.SnapshotStore;
import org.fireflyframework.eventsourcing.store.EventStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.util.List;

/**
 * Auto-configuration for event sourcing health indicators.
 * <p>
 * Registers Spring Boot Actuator health checks for the event store,
 * outbox, snapshot store, and projections when Actuator is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuator.health.HealthIndicator")
@ConditionalOnProperty(prefix = "firefly.eventsourcing.performance", name = "health-checks-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EventSourcingHealthAutoConfiguration {

    public EventSourcingHealthAutoConfiguration() {
        log.debug("Event Sourcing Health Configuration initialized");
    }

    @Bean
    @ConditionalOnBean(EventStore.class)
    @ConditionalOnMissingBean(EventStoreHealthIndicator.class)
    public EventStoreHealthIndicator eventStoreHealthIndicator(EventStore eventStore) {
        log.debug("Creating EventStoreHealthIndicator bean");
        return new EventStoreHealthIndicator(eventStore);
    }

    @Bean
    @ConditionalOnBean(EventOutboxService.class)
    @ConditionalOnMissingBean(OutboxHealthIndicator.class)
    public OutboxHealthIndicator outboxHealthIndicator(EventOutboxService outboxService) {
        log.debug("Creating OutboxHealthIndicator bean");
        return new OutboxHealthIndicator(outboxService);
    }

    @Bean
    @ConditionalOnBean(SnapshotStore.class)
    @ConditionalOnMissingBean(SnapshotStoreHealthIndicator.class)
    public SnapshotStoreHealthIndicator snapshotStoreHealthIndicator(SnapshotStore snapshotStore) {
        log.debug("Creating SnapshotStoreHealthIndicator bean");
        return new SnapshotStoreHealthIndicator(snapshotStore);
    }

    @Bean
    @ConditionalOnMissingBean(ProjectionHealthIndicator.class)
    public ProjectionHealthIndicator projectionHealthIndicator(List<ProjectionService<?>> projectionServices,
                                                                EventSourcingProjectionProperties projectionProperties) {
        log.debug("Creating ProjectionHealthIndicator bean with {} projections", projectionServices.size());
        return new ProjectionHealthIndicator(
                projectionServices,
                projectionProperties.getHealthCheck().getTimeout(),
                projectionProperties.getHealthCheck().getMaxAcceptableLag()
        );
    }
}
