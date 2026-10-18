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

package org.fireflyframework.eda.testconfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers configuration for RabbitMQ integration tests.
 * <p>
 * This configuration provides a reusable RabbitMQ container instance.
 * <p>
 * Note: this transport adapter ships a RabbitMQ-only variant of the shared
 * scaffolding (the original core variant coupled both Kafka and RabbitMQ).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    /**
     * RabbitMQ container for integration tests.
     * Uses official RabbitMQ image with management plugin.
     */
    @Bean
    @ServiceConnection
    public RabbitMQContainer rabbitMQContainer() {
        RabbitMQContainer rabbitmq = new RabbitMQContainer(
                DockerImageName.parse("rabbitmq:3.12-management-alpine")
        );
        rabbitmq.withReuse(true);
        return rabbitmq;
    }

}
