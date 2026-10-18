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
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Auto-configuration for orchestration engine integration with fireflyframework-starter-data.
 *
 * This configuration activates when the orchestration engine is on the classpath,
 * enabling saga, TCC, and workflow support for data processing microservices.
 */
@AutoConfiguration
@ConditionalOnClass(name = {
    "org.fireflyframework.orchestration.saga.annotation.Saga",
    "org.fireflyframework.orchestration.core.event.OrchestrationEventPublisher"
})
@ConditionalOnProperty(prefix = "firefly.orchestration", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OrchestrationAutoConfiguration {

    public OrchestrationAutoConfiguration() {
        log.info("Enabling orchestration engine integration for fireflyframework-starter-data");
    }
}
