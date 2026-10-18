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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Auto-configuration for fireflyframework-eda integration with fireflyframework-starter-data.
 *
 * This configuration enables event-driven architecture support for core-data microservices,
 * allowing them to publish and consume events related to data processing jobs.
 */
@AutoConfiguration(value = "dataEdaAutoConfiguration")
@ConditionalOnClass(name = {
    "org.fireflyframework.eda.publisher.EventPublisher",
    "org.fireflyframework.eda.subscriber.EventSubscriber"
})
@ConditionalOnProperty(prefix = "firefly.data.eda", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
    "org.fireflyframework.eda"
})
@Slf4j
public class EdaAutoConfiguration {

    public EdaAutoConfiguration() {
        log.info("Enabling fireflyframework-eda integration for fireflyframework-starter-data");
    }
}
