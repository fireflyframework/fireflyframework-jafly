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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.eventsourcing.snapshot.SnapshotStore;
import org.fireflyframework.eventsourcing.snapshot.SnapshotTrigger;
import org.fireflyframework.eventsourcing.snapshot.r2dbc.R2dbcSnapshotStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Auto-configuration for snapshot stores.
 * <p>
 * This configuration class sets up the R2DBC-backed snapshot store and a
 * configurable snapshot trigger that creates snapshots after every N events.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.eventsourcing.snapshot", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EventSourcingProperties.class)
@Slf4j
public class SnapshotAutoConfiguration {

    public SnapshotAutoConfiguration() {
        log.debug("Snapshot Auto-Configuration initialized");
    }

    @Bean
    @ConditionalOnMissingBean
    public SnapshotStore snapshotStore(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        log.info("Creating R2dbcSnapshotStore bean");
        return new R2dbcSnapshotStore(databaseClient, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SnapshotTrigger snapshotTrigger(SnapshotStore snapshotStore, EventSourcingProperties properties) {
        int frequency = properties.getSnapshot().getThreshold();
        log.info("Creating SnapshotTrigger bean with frequency: {} events", frequency);
        return new SnapshotTrigger(snapshotStore, frequency);
    }
}
