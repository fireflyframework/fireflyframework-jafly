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

package org.fireflyframework.callbacks.web;

import org.fireflyframework.callbacks.web.controller.AuthorizedDomainController;
import org.fireflyframework.callbacks.web.controller.CallbackConfigurationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class SpringContextTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("firefly.eda.consumer.kafka.default.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void controllerBeansArePresent() {
        assertThat(context.getBean(AuthorizedDomainController.class)).isNotNull();
        assertThat(context.getBean(CallbackConfigurationController.class)).isNotNull();
    }

    @Test
    void handlerMappingIsPresent() {
        var handlerMapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        assertThat(handlerMapping).isNotNull();
        
        var handlerMethods = handlerMapping.getHandlerMethods();
        System.out.println("=== Registered Handler Methods ===");
        handlerMethods.forEach((key, value) -> {
            System.out.println(key + " -> " + value);
        });
        
        System.out.println("Total handlers: " + handlerMethods.size());
        assertThat(handlerMethods).isNotEmpty();
    }
}
