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

package org.fireflyframework.web.conditions.config;

import org.fireflyframework.web.conditions.listener.ConditionsReportListener;
import org.fireflyframework.web.conditions.service.ConditionsReportService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Conditions Evaluation Report feature.
 *
 * <p>This configuration is activated when the property
 * {@code firefly.web.conditions-report.enabled} is set to {@code true}.</p>
 *
 * <p>The report shows which Spring Boot auto-configurations were activated
 * and which were not, organized by technology categories with detailed
 * explanations.</p>
 *
 * <p>The {@link ConditionsReportProperties} bean is registered by
 * {@link ConditionsReportPropertiesAutoConfiguration} which is always loaded,
 * ensuring properties are available even when this feature is disabled.</p>
 *
 * <p>Example configuration:</p>
 * <pre>
 * firefly:
 *   conditions-report:
 *     enabled: true              # Enable the report
 *     show-negative: true        # Show non-activated conditions
 *     show-details: true         # Show detailed explanations
 *     summary-only: false        # false = detailed, true = summary only
 *     use-colors: true           # Use ANSI colors in console output
 * </pre>
 */
@AutoConfiguration(after = ConditionsReportPropertiesAutoConfiguration.class)
@ConditionalOnProperty(prefix = "firefly.web.conditions-report", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ConditionsReportAutoConfiguration {

    /**
     * Creates the conditions report service bean.
     *
     * @param properties the configuration properties
     * @return the conditions report service
     */
    @Bean
    @ConditionalOnMissingBean
    public ConditionsReportService conditionsReportService(ConditionsReportProperties properties) {
        return new ConditionsReportService(properties);
    }

    /**
     * Creates the application listener that generates the report on startup.
     *
     * @param reportService the report service
     * @return the application listener
     */
    @Bean
    @ConditionalOnMissingBean
    public ConditionsReportListener conditionsReportListener(ConditionsReportService reportService) {
        return new ConditionsReportListener(reportService);
    }
}

