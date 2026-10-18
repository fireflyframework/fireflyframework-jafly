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

import org.fireflyframework.data.quality.DataQualityEngine;
import org.fireflyframework.data.quality.DataQualityRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for the data quality framework.
 *
 * <p>This configuration automatically sets up:</p>
 * <ul>
 *   <li>{@link DataQualityEngine} with all discovered {@link DataQualityRule} beans</li>
 *   <li>Event publishing for quality reports (when an {@link ApplicationEventPublisher} is available)</li>
 * </ul>
 *
 * <p>The configuration is activated when:</p>
 * <ul>
 *   <li>The property {@code firefly.data.quality.enabled} is true (default)</li>
 *   <li>Or the property is not set (enabled by default)</li>
 * </ul>
 *
 * <p><b>Example Configuration:</b></p>
 * <pre>{@code
 * firefly:
 *   data:
 *     quality:
 *       enabled: true
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "firefly.data.quality",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DataQualityAutoConfiguration {

    /**
     * Creates the data quality engine bean.
     *
     * <p>Discovers all {@link DataQualityRule} beans and wires them into the engine.
     * If no rules are found, the engine is created with an empty rule set.
     * An {@link ApplicationEventPublisher} is injected when available to enable
     * quality report event publishing.</p>
     *
     * @param rules          the list of quality rules, or {@code null} if none are registered
     * @param eventPublisher the event publisher, or {@code null} if unavailable
     * @return the configured data quality engine
     */
    @Bean
    @ConditionalOnMissingBean
    public DataQualityEngine dataQualityEngine(
            @Autowired(required = false) List<DataQualityRule<?>> rules,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        List<DataQualityRule<?>> activeRules = rules != null ? rules : List.of();
        log.info("Configuring Data Quality Engine with {} rules", activeRules.size());
        return new DataQualityEngine(activeRules, eventPublisher);
    }
}
