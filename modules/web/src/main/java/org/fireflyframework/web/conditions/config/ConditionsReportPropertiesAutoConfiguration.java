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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Base auto-configuration that registers ConditionsReportProperties.
 * 
 * <p>This configuration is always loaded (unconditionally) to ensure that
 * the {@link ConditionsReportProperties} bean is available in the application context,
 * even when the conditions report feature is disabled.</p>
 * 
 * <p>This allows the properties to be bound from configuration files without
 * requiring the feature to be enabled, which is necessary for proper Spring Boot
 * configuration property binding.</p>
 * 
 * <p>The actual conditions report functionality is only activated when
 * {@code firefly.web.conditions-report.enabled=true} via
 * {@link ConditionsReportAutoConfiguration}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ConditionsReportProperties.class)
public class ConditionsReportPropertiesAutoConfiguration {
    // This class intentionally has no beans
    // It only exists to register ConditionsReportProperties unconditionally
}

