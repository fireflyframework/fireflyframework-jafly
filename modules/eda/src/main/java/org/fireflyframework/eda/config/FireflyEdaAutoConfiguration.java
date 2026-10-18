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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.eda.properties.EdaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

/**
 * Main auto-configuration class for Firefly EDA library.
 * <p>
 * This class provides a general overview of the EDA configuration and delegates
 * to specific auto-configuration classes for each provider (Kafka, RabbitMQ, etc.).
 * <p>
 * <strong>Configuration Namespace:</strong> firefly.eda.*
 * <p>
 * <strong>Key Properties:</strong>
 * <ul>
 *   <li>firefly.eda.enabled - Enable/disable the entire EDA library (default: true)</li>
 *   <li>firefly.eda.publishers.enabled - Enable/disable all publishers (default: true)</li>
 *   <li>firefly.eda.consumer.enabled - Enable/disable all consumers (default: true)</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "firefly.eda", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EdaProperties.class)
@org.springframework.context.annotation.ComponentScan(basePackages = "org.fireflyframework.eda")
@org.springframework.scheduling.annotation.EnableAsync
@org.springframework.context.annotation.EnableAspectJAutoProxy
public class FireflyEdaAutoConfiguration {

    public FireflyEdaAutoConfiguration(EdaProperties props) {
        log.info("================================================================================");
        log.info("FIREFLY EDA - EVENT-DRIVEN ARCHITECTURE LIBRARY");
        log.info("================================================================================");
        log.info("");
        log.info("Global Configuration:");
        log.info("  - EDA Library: {}", props.isEnabled() ? "ENABLED" : "DISABLED");
        log.info("  - Publishers: {}", props.getPublishers().isEnabled() ? "ENABLED" : "DISABLED");
        log.info("  - Consumers: {}", props.getConsumer().isEnabled() ? "ENABLED" : "DISABLED");
        log.info("  - Default Publisher Type: {}", props.getDefaultPublisherType());
        log.info("  - Default Serialization: {}", props.getDefaultSerializationFormat());
        log.info("  - Consumer Group ID: {}", props.getConsumer().getGroupId());
        log.info("  - Metrics: {}", props.isMetricsEnabled() ? "ENABLED" : "DISABLED");
        log.info("  - Health Checks: {}", props.isHealthEnabled() ? "ENABLED" : "DISABLED");
        log.info("  - Tracing: {}", props.isTracingEnabled() ? "ENABLED" : "DISABLED");
        log.info("");

        // Log publisher details
        log.info("Publishers Configuration:");
        if (props.getPublishers().isEnabled()) {
            // Kafka Publisher
            var kafkaPublisher = props.getPublishers().getKafka().get("default");
            if (kafkaPublisher != null && kafkaPublisher.isEnabled()) {
                String bootstrap = kafkaPublisher.getBootstrapServers();
                if (bootstrap != null && !bootstrap.isEmpty()) {
                    log.info("  - Kafka Publisher: CONFIGURED (bootstrap: {})", bootstrap);
                } else {
                    log.info("  - Kafka Publisher: NOT CONFIGURED (bootstrap servers not set)");
                }
            } else {
                log.info("  - Kafka Publisher: DISABLED");
            }

            // RabbitMQ Publisher
            var rabbitPublisher = props.getPublishers().getRabbitmq().get("default");
            if (rabbitPublisher != null && rabbitPublisher.isEnabled()) {
                String host = rabbitPublisher.getHost();
                if (host != null && !host.isEmpty()) {
                    log.info("  - RabbitMQ Publisher: CONFIGURED (host: {}:{})",
                        rabbitPublisher.getHost(), rabbitPublisher.getPort());
                } else {
                    log.info("  - RabbitMQ Publisher: NOT CONFIGURED (host not set)");
                }
            } else {
                log.info("  - RabbitMQ Publisher: DISABLED");
            }

            // Postgres Publisher
            var postgresPublisher = props.getPublishers().getPostgres().get("default");
            if (postgresPublisher != null && postgresPublisher.isEnabled()) {
                String host = postgresPublisher.getHost();
                if (host != null && !host.isEmpty()) {
                    log.info("  - Postgres Publisher: CONFIGURED (host: {}:{}, table: {}.{})",
                        host, postgresPublisher.getPort(),
                        postgresPublisher.getSchema(), postgresPublisher.getOutboxTable());
                } else {
                    log.info("  - Postgres Publisher: NOT CONFIGURED (host not set)");
                }
            } else {
                log.info("  - Postgres Publisher: DISABLED");
            }

            // Application Event Publisher
            if (props.getPublishers().getApplicationEvent().isEnabled()) {
                log.info("  - Application Event Publisher: ENABLED");
            } else {
                log.info("  - Application Event Publisher: DISABLED");
            }
        } else {
            log.info("  - All Publishers: GLOBALLY DISABLED");
        }

        log.info("");

        // Log consumer details
        log.info("Consumers Configuration:");
        if (props.getConsumer().isEnabled()) {
            // Kafka Consumer
            var kafkaConsumer = props.getConsumer().getKafka().get("default");
            if (kafkaConsumer != null && kafkaConsumer.isEnabled()) {
                String bootstrap = kafkaConsumer.getBootstrapServers();
                if (bootstrap != null && !bootstrap.isEmpty()) {
                    log.info("  - Kafka Consumer: CONFIGURED (bootstrap: {})", bootstrap);
                } else {
                    log.info("  - Kafka Consumer: NOT CONFIGURED (bootstrap servers not set)");
                }
            } else {
                log.info("  - Kafka Consumer: DISABLED");
            }

            // RabbitMQ Consumer
            var rabbitConsumer = props.getConsumer().getRabbitmq().get("default");
            if (rabbitConsumer != null && rabbitConsumer.isEnabled()) {
                String host = rabbitConsumer.getHost();
                if (host != null && !host.isEmpty()) {
                    log.info("  - RabbitMQ Consumer: CONFIGURED (host: {}:{})",
                        rabbitConsumer.getHost(), rabbitConsumer.getPort());
                } else {
                    log.info("  - RabbitMQ Consumer: NOT CONFIGURED (host not set)");
                }
            } else {
                log.info("  - RabbitMQ Consumer: DISABLED");
            }

            // Postgres Consumer
            var postgresConsumer = props.getConsumer().getPostgres().get("default");
            if (postgresConsumer != null && postgresConsumer.isEnabled()) {
                String host = postgresConsumer.getHost();
                if (host != null && !host.isEmpty()) {
                    log.info("  - Postgres Consumer: CONFIGURED (host: {}:{}, table: {}.{}, polling: {})",
                        host, postgresConsumer.getPort(),
                        postgresConsumer.getSchema(), postgresConsumer.getOutboxTable(),
                        postgresConsumer.getPollingInterval());
                } else {
                    log.info("  - Postgres Consumer: NOT CONFIGURED (host not set)");
                }
            } else {
                log.info("  - Postgres Consumer: DISABLED");
            }

            // Application Event Consumer
            if (props.getConsumer().getApplicationEvent().isEnabled()) {
                log.info("  - Application Event Consumer: ENABLED");
            } else {
                log.info("  - Application Event Consumer: DISABLED");
            }
        } else {
            log.info("  - All Consumers: GLOBALLY DISABLED");
        }

        log.info("");
        log.info("Provider-specific beans will be created based on above configuration");
        log.info("================================================================================");
    }

    /**
     * Provides the application's primary {@link ObjectMapper} bean.
     * <p>
     * This is the general-purpose mapper consumed by controllers, EDA serializers,
     * CQRS handlers, and any component that injects {@code ObjectMapper} without a
     * qualifier. It is marked {@link Primary @Primary} so it wins disambiguation
     * over module-specific qualified mappers (e.g. {@code cacheObjectMapper},
     * {@code orchestrationPersistenceObjectMapper}, {@code sagaObjectMapper}) that
     * coexist in the Spring context for internal framework use.
     * <p>
     * Built through Spring Boot's autoconfigured {@link Jackson2ObjectMapperBuilder},
     * so it honours every {@code spring.jackson.*} property the application sets in
     * its {@code application.yaml} — most importantly
     * {@code spring.jackson.serialization.write-dates-as-timestamps=false} which
     * makes {@link java.time.Instant} / {@link java.time.LocalDateTime} fields
     * serialize as ISO-8601 strings instead of numeric epochs in WebFlux responses.
     * The builder also auto-registers every Jackson module on the classpath
     * ({@code jackson-datatype-jsr310}, {@code jackson-datatype-jdk8},
     * {@code jackson-module-parameter-names}, …).
     * <p>
     * The {@link ConditionalOnMissingBean @ConditionalOnMissingBean} guard preserves
     * compatibility with applications that already declare their own primary mapper.
     *
     * @param builder Spring Boot's autoconfigured {@code Jackson2ObjectMapperBuilder}
     * @return configured ObjectMapper instance
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper(org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }
}

