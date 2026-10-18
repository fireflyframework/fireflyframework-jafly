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

package org.fireflyframework.web.openapi;

import org.fireflyframework.web.idempotency.config.IdempotencyOpenAPIAutoConfiguration;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webflux.core.configuration.SpringDocWebFluxConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that configures a minimal Spring Boot application for
 * OpenAPI spec generation. Combines {@code @SpringBootConfiguration},
 * {@code @EnableWebFlux}, and all required auto-configurations for Springdoc
 * and WebFlux into a single annotation.
 *
 * <p>Usage in each microservice's {@code src/test/java}:
 * <pre>{@code
 * @EnableOpenApiGen
 * @ComponentScan(basePackages = "com.example.web.controllers")
 * public class OpenApiGenApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(OpenApiGenApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p>The annotated class only needs to add {@code @ComponentScan} pointing at
 * its controller package — everything else is handled by this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootConfiguration
@EnableWebFlux
@ImportAutoConfiguration({
        AutoMockMissingBeansConfig.class,
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebFluxConfiguration.class,
        ReactiveWebServerFactoryAutoConfiguration.class,
        HttpHandlerAutoConfiguration.class,
        WebFluxAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        SpringApplicationAdminJmxAutoConfiguration.class,
        IdempotencyOpenAPIAutoConfiguration.class
})
public @interface EnableOpenApiGen {
}
