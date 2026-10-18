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

package org.fireflyframework.web.idempotency.config;

import org.fireflyframework.web.openapi.IdempotencyOpenAPICustomizer;
import org.fireflyframework.web.openapi.IdempotencyOperationCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for idempotency OpenAPI customizers.
 * 
 * <p>This configuration is separate from {@link IdempotencyAutoConfiguration} because
 * the OpenAPI customizers should be available even when fireflyframework-cache is not configured.
 * The customizers only add documentation to the OpenAPI spec; they don't require the
 * actual idempotency functionality to be active.</p>
 * 
 * <p>This allows microservices to have the idempotency header documented in Swagger
 * even if they don't have caching configured, which is useful for API documentation
 * purposes.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnClass(name = "org.springdoc.core.customizers.OpenApiCustomizer")
public class IdempotencyOpenAPIAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyOpenAPIAutoConfiguration.class);

    /**
     * Creates the IdempotencyOpenAPICustomizer bean.
     * 
     * <p>This customizer adds the idempotency header to OpenAPI documentation
     * for all HTTP operations. It is only created when SpringDoc OpenAPI is
     * available on the classpath.</p>
     *
     * @param properties the idempotency configuration properties
     * @return the OpenAPI customizer
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyOpenAPICustomizer.class)
    public IdempotencyOpenAPICustomizer idempotencyOpenAPICustomizer(
            IdempotencyProperties properties) {
        
        log.info("Configuring idempotency OpenAPI customizer");
        
        return new IdempotencyOpenAPICustomizer(properties);
    }

    /**
     * Creates the IdempotencyOperationCustomizer bean.
     * 
     * <p>This customizer marks operations with the DisableIdempotency annotation
     * in the OpenAPI documentation. It is only created when SpringDoc OpenAPI is
     * available on the classpath.</p>
     *
     * @return the operation customizer
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyOperationCustomizer.class)
    public IdempotencyOperationCustomizer idempotencyOperationCustomizer() {
        
        log.info("Configuring idempotency operation customizer");
        
        return new IdempotencyOperationCustomizer();
    }
}

