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

package org.fireflyframework.data.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Auto-configuration for fireflyframework-cqrs integration with fireflyframework-starter-data.
 *
 * This configuration enables CQRS (Command Query Responsibility Segregation) support
 * for core-data microservices, separating read and write operations for better scalability.
 */
@AutoConfiguration(value = "dataCqrsAutoConfiguration")
@ConditionalOnClass(name = {
    "org.fireflyframework.cqrs.command.CommandHandler",
    "org.fireflyframework.cqrs.query.QueryHandler"
})
@ConditionalOnProperty(prefix = "firefly.data.cqrs", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataConfiguration.class)
@ComponentScan(basePackages = {
    "org.fireflyframework.cqrs"
})
@Slf4j
public class CqrsAutoConfiguration {

    public CqrsAutoConfiguration(DataConfiguration dataConfiguration) {
        log.info("Enabling fireflyframework-cqrs integration for fireflyframework-starter-data - enabled: {}",
                dataConfiguration.getCqrs().isEnabled());
    }
}
