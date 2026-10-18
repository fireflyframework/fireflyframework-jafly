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

package org.fireflyframework.web.cors.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Auto-configuration for CORS support using a reactive {@link CorsWebFilter}.
 *
 * <p>This configuration is activated when {@code firefly.web.cors.enabled=true}
 * (default) and at least one allowed origin is configured. When no origins are
 * configured, the filter is not registered — secure by default.</p>
 *
 * <p>Uses {@link CorsWebFilter} rather than {@link org.springframework.web.reactive.config.WebFluxConfigurer}
 * so it composes cleanly with any application-level WebFluxConfigurer without conflicts.</p>
 *
 * <p>Configuration example:</p>
 * <pre>
 * firefly:
 *   web:
 *     cors:
 *       enabled: true
 *       allowed-origins:
 *         - "https://app.example.com"
 *       allow-credentials: true
 * </pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(CorsProperties.class)
@ConditionalOnProperty(prefix = "firefly.web.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CorsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CorsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(CorsWebFilter.class)
    public CorsWebFilter corsWebFilter(CorsProperties properties) {
        if (properties.getAllowedOrigins().isEmpty()) {
            log.debug("No CORS origins configured — CORS filter will pass through all requests without adding headers");
            return new CorsWebFilter(new UrlBasedCorsConfigurationSource());
        }

        CorsConfiguration config = new CorsConfiguration();
        properties.getAllowedOrigins().forEach(config::addAllowedOrigin);
        properties.getAllowedMethods().forEach(config::addAllowedMethod);
        properties.getAllowedHeaders().forEach(config::addAllowedHeader);
        properties.getExposedHeaders().forEach(config::addExposedHeader);
        config.setAllowCredentials(properties.isAllowCredentials());
        config.setMaxAge(properties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(properties.getPathPattern(), config);

        log.info("CORS configured for origins: {}", properties.getAllowedOrigins());
        return new CorsWebFilter(source);
    }
}
