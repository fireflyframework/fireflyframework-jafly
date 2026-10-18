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


package org.fireflyframework.core.actuator.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Configuration class for Spring Boot Actuator.
 * <p>
 * This class provides configuration for Spring Boot Actuator endpoints and metrics.
 * It is automatically enabled when the actuator dependency is present, without requiring
 * any additional configuration from the user.
 * <p>
 * The configuration includes:
 * - Custom meter registry configuration for application-specific metrics
 * - Health indicators for various components
 * - Default actuator endpoint configuration
 * - Integration with the transaction ID mechanism for distributed tracing
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration")
@EnableConfigurationProperties(ActuatorProperties.class)
@AutoConfigureBefore(ManagementContextAutoConfiguration.class)
public class ActuatorAutoConfiguration {

    /**
     * Customizes the meter registry with application-specific tags.
     *
     * @param environment the Spring environment
     * @return a customizer for the meter registry
     */
    private final ActuatorProperties actuatorProperties;

    /**
     * Constructor for ActuatorAutoConfiguration.
     *
     * @param actuatorProperties the actuator properties
     */
    public ActuatorAutoConfiguration(ActuatorProperties actuatorProperties) {
        this.actuatorProperties = actuatorProperties;
    }

    /**
     * Customizes the meter registry with application-specific tags.
     *
     * @param environment the Spring environment
     * @return a customizer for the meter registry
     */
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(Environment environment) {
        return registry -> {
            String applicationName = environment.getProperty("spring.application.name", "application");
            registry.config().commonTags(
                    "application", applicationName
            );

            // Add environment tag if available
            String activeProfile = environment.getProperty("spring.profiles.active");
            if (activeProfile != null) {
                registry.config().commonTags("environment", activeProfile);
            }
        };
    }

    /**
     * Provides default actuator endpoint configuration.
     * This ensures that basic endpoints are enabled by default.
     *
     * @return the default actuator properties
     */
    @Bean
    @ConditionalOnMissingBean(name = "defaultActuatorEndpointConfiguration")
    public ActuatorProperties.Endpoints defaultActuatorEndpointConfiguration() {
        ActuatorProperties.Endpoints endpoints = actuatorProperties.getEndpoints();

        // Ensure web exposure is configured
        if (endpoints.getWeb().getExposure() == null || endpoints.getWeb().getExposure().isEmpty()) {
            // By default, expose health, info, and prometheus endpoints
            endpoints.getWeb().setExposure("health,info,prometheus,metrics");
        }

        return endpoints;
    }
}
