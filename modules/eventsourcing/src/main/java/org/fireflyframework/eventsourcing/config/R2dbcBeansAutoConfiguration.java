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

import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Auto-configuration that ensures R2DBC beans are available for Event Sourcing.
 * <p>
 * This configuration explicitly imports Spring Boot's R2DBC auto-configurations
 * to ensure that DatabaseClient and R2dbcEntityTemplate beans are created
 * before EventStoreAutoConfiguration runs.
 * <p>
 * This is necessary because EventStore requires these beans to be present,
 * and the @ConditionalOnBean checks need them to exist at configuration time.
 */
@AutoConfiguration
@AutoConfigureBefore(EventSourcingAutoConfiguration.class)
@ConditionalOnClass({ConnectionFactory.class, R2dbcEntityTemplate.class})
@Import({
        org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration.class
})
@Slf4j
public class R2dbcBeansAutoConfiguration {

    public R2dbcBeansAutoConfiguration() {
        log.info("R2DBC Beans Auto-Configuration initialized - ensuring R2DBC beans are available for Event Sourcing");
    }

    /**
     * Creates DatabaseClient bean if not already present.
     * This is a fallback in case Spring Boot's auto-configuration doesn't create it.
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        log.info("Creating DatabaseClient bean for event sourcing");
        return DatabaseClient.create(connectionFactory);
    }

    /**
     * Creates R2dbcEntityTemplate bean if not already present.
     * This is a fallback in case Spring Boot's auto-configuration doesn't create it.
     */
    @Bean
    @ConditionalOnMissingBean
    public R2dbcEntityTemplate r2dbcEntityTemplate(ConnectionFactory connectionFactory) {
        log.info("Creating R2dbcEntityTemplate bean for event sourcing");
        return new R2dbcEntityTemplate(connectionFactory);
    }
}
