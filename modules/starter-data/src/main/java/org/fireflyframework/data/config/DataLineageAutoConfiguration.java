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

import org.fireflyframework.data.lineage.InMemoryLineageTracker;
import org.fireflyframework.data.lineage.LineageTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for data lineage tracking.
 *
 * <p>This configuration provides an in-memory {@link LineageTracker} by default.
 * Production deployments should provide their own {@link LineageTracker} bean
 * backed by a persistent store (e.g., a graph database or event log).</p>
 *
 * <p>The configuration is activated when:</p>
 * <ul>
 *   <li>The property {@code firefly.data.lineage.enabled} is explicitly set to true</li>
 * </ul>
 *
 * <p><b>Example Configuration:</b></p>
 * <pre>{@code
 * firefly:
 *   data:
 *     lineage:
 *       enabled: true
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "firefly.data.lineage",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class DataLineageAutoConfiguration {

    /**
     * Creates an in-memory lineage tracker bean.
     *
     * <p>This default implementation stores lineage records in a thread-safe
     * in-memory structure. It is suitable for development, testing, and
     * single-instance deployments. Production environments should define
     * a custom {@link LineageTracker} bean backed by a persistent store.</p>
     *
     * @return the in-memory lineage tracker
     */
    @Bean
    @ConditionalOnMissingBean
    public LineageTracker lineageTracker() {
        log.info("Configuring in-memory lineage tracker (production: implement LineageTracker with persistent store)");
        return new InMemoryLineageTracker();
    }
}
