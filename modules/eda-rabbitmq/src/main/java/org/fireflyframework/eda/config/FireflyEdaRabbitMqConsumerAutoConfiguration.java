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

package org.fireflyframework.eda.config;

import org.fireflyframework.eda.properties.EdaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Firefly EDA RabbitMQ Consumer Auto-Configuration.
 *
 * <p>This configuration creates the RabbitMQ {@code ConnectionFactory} that the
 * {@link org.fireflyframework.eda.consumer.rabbitmq.RabbitMqEventConsumer} requires,
 * using ONLY {@code firefly.eda.consumer.rabbitmq.*} properties.
 *
 * <p><strong>Bug fix (dormant consumer):</strong> The
 * {@code RabbitMqEventConsumer} is guarded by
 * {@code @ConditionalOnBean(name = "fireflyEdaRabbitConsumerConnectionFactory")}
 * and injects that same bean via {@code @Qualifier}. Prior to this class, NO
 * auto-configuration ever created the {@code fireflyEdaRabbitConsumerConnectionFactory}
 * bean, so the {@code @ConditionalOnBean} never matched and the RabbitMQ consumer was
 * never instantiated (it stayed dormant). This auto-configuration creates that bean -
 * mirroring the publisher-side {@code fireflyEdaRabbitPublisherConnectionFactory} in
 * {@link FireflyEdaRabbitMqAutoConfiguration} but driven by the consumer property
 * namespace - so the consumer is now wired and functional.
 *
 * <p><strong>IMPORTANT - Hexagonal Architecture:</strong>
 * This auto-configuration reads configuration EXCLUSIVELY from
 * {@code firefly.eda.consumer.rabbitmq.*} namespace. It does NOT use
 * {@code spring.rabbitmq.*} or {@code spring.amqp.*} properties.
 */
@AutoConfiguration(after = FireflyEdaAutoConfiguration.class)
@ConditionalOnClass({ConnectionFactory.class})
@ConditionalOnProperty(prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EdaProperties.class)
@Slf4j
public class FireflyEdaRabbitMqConsumerAutoConfiguration {

    public FireflyEdaRabbitMqConsumerAutoConfiguration(EdaProperties props) {
        // Only log if consumers are enabled and RabbitMQ is configured
        if (props.getConsumer().isEnabled()) {
            var rabbitConsumer = props.getConsumer().getRabbitmq().get("default");
            if (rabbitConsumer != null && rabbitConsumer.isEnabled() &&
                rabbitConsumer.getHost() != null && !rabbitConsumer.getHost().isEmpty()) {
                log.info("--------------------------------------------------------------------------------");
                log.info("FIREFLY EDA RABBITMQ CONSUMER - INITIALIZING");
                log.info("--------------------------------------------------------------------------------");
            } else {
                log.debug("Firefly EDA RabbitMQ Consumer auto-configuration loaded but not creating beans (disabled or not configured)");
            }
        } else {
            log.debug("Firefly EDA RabbitMQ Consumer auto-configuration loaded but not creating beans (consumer globally disabled)");
        }
    }

    /**
     * Creates a RabbitMQ {@code ConnectionFactory} for consumers from Firefly EDA
     * properties when:
     * <ul>
     *   <li>RabbitMQ classes are available on classpath</li>
     *   <li>No existing ConnectionFactory bean with this name exists</li>
     *   <li>The consumer is globally enabled and the RabbitMQ consumer is enabled</li>
     *   <li>Host is configured in {@code firefly.eda.consumer.rabbitmq.default.host}</li>
     * </ul>
     *
     * <p>This is the missing bean that the
     * {@link org.fireflyframework.eda.consumer.rabbitmq.RabbitMqEventConsumer}
     * depends on via {@code @ConditionalOnBean} / {@code @Qualifier}.
     *
     * <p><strong>Configuration Source:</strong> firefly.eda.consumer.rabbitmq.default.*
     * <p><strong>NOT using:</strong> spring.rabbitmq.* properties (those are IGNORED)
     */
    @Bean(name = "fireflyEdaRabbitConsumerConnectionFactory")
    @ConditionalOnMissingBean(name = "fireflyEdaRabbitConsumerConnectionFactory")
    @ConditionalOnExpression("${firefly.eda.consumer.enabled:false} && ${firefly.eda.consumer.rabbitmq.default.enabled:false} && '${firefly.eda.consumer.rabbitmq.default.host:}'.length() > 0")
    public ConnectionFactory fireflyEdaRabbitConsumerConnectionFactory(EdaProperties props) {
        log.info("Creating RabbitMQ ConnectionFactory for consumers from firefly.eda.consumer.rabbitmq.default.* properties");
        EdaProperties.Consumer.RabbitMqConfig rabbitProps = props.getConsumer().getRabbitmq().get("default");

        CachingConnectionFactory factory = new CachingConnectionFactory();

        // Configure connection properties from Firefly configuration
        factory.setHost(rabbitProps.getHost());
        factory.setPort(rabbitProps.getPort());
        log.info("  - Host: {}:{}", rabbitProps.getHost(), rabbitProps.getPort());

        factory.setUsername(rabbitProps.getUsername());
        log.info("  - Username: {}", rabbitProps.getUsername());

        factory.setPassword(rabbitProps.getPassword());
        factory.setVirtualHost(rabbitProps.getVirtualHost());
        log.info("  - Virtual host: {}", rabbitProps.getVirtualHost());

        // Test connection
        try {
            factory.createConnection().close();
            log.info("RabbitMQ ConnectionFactory for consumers created and connection tested successfully");
        } catch (Exception e) {
            log.error("Failed to establish RabbitMQ connection for consumers: {}", e.getMessage());
            log.error("Please verify RabbitMQ is running and configuration is correct");
        }
        log.info("--------------------------------------------------------------------------------");

        return factory;
    }
}
