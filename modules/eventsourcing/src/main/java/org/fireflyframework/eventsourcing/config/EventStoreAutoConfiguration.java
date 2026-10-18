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
import org.fireflyframework.eventsourcing.store.EventStore;
import org.fireflyframework.eventsourcing.store.r2dbc.R2dbcEventStore;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Auto-configuration for event stores.
 * <p>
 * This configuration class sets up event store implementations based on
 * the configured store type (R2DBC, MongoDB, etc.).
 */
@AutoConfiguration
@AutoConfigureAfter(R2dbcBeansAutoConfiguration.class)
@ConditionalOnProperty(prefix = "firefly.eventsourcing.store", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EventSourcingProperties.class)
@Slf4j
public class EventStoreAutoConfiguration {

    public EventStoreAutoConfiguration() {
        log.debug("Event Store Auto-Configuration initialized");
    }

    /**
     * Creates R2DBC-based EventStore when R2DBC is available and configured.
     */
    @Bean
    @ConditionalOnProperty(name = "firefly.eventsourcing.store.type", havingValue = "r2dbc", matchIfMissing = true)
    @ConditionalOnBean({DatabaseClient.class, R2dbcEntityTemplate.class, ConnectionFactory.class})
    @ConditionalOnMissingBean(EventStore.class)
    public EventStore r2dbcEventStore(
            DatabaseClient databaseClient,
            R2dbcEntityTemplate entityTemplate,
            ObjectMapper objectMapper,
            EventSourcingProperties properties,
            ReactiveTransactionManager transactionManager,
            ConnectionFactory connectionFactory) {
        
        log.info("Creating R2DBC EventStore with store type: {}", 
                properties.getStore().getType());
                
        TransactionalOperator transactionalOperator = TransactionalOperator.create(transactionManager);
        
        return new R2dbcEventStore(
                databaseClient,
                entityTemplate,
                objectMapper,
                properties,
                transactionManager,
                transactionalOperator,
                connectionFactory
        );
    }
}