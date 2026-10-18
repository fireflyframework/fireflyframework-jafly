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

package org.fireflyframework.eventsourcing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.fireflyframework.eventsourcing.monitoring.EventStoreMetrics;
import org.fireflyframework.eventsourcing.outbox.EventOutboxProcessor;
import org.fireflyframework.eventsourcing.outbox.EventOutboxRepository;
import org.fireflyframework.eventsourcing.outbox.EventOutboxService;
import org.fireflyframework.eventsourcing.publisher.EventSourcingPublisher;
import org.fireflyframework.eventsourcing.transaction.EventSourcingTransactionalAspect;
import org.fireflyframework.eventsourcing.upcasting.EventUpcaster;
import org.fireflyframework.eventsourcing.upcasting.EventUpcastingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.ReactiveTransactionManager;

import java.util.List;

/**
 * Auto-configuration for the Event Sourcing library.
 * <p>
 * This configuration class automatically sets up the event sourcing library
 * when included in a Spring Boot application. It enables configuration properties,
 * sets up component scanning, and provides default beans where needed.
 * <p>
 * Components automatically discovered and configured:
 * <ul>
 *   <li>Event stores (R2DBC, MongoDB, etc.)</li>
 *   <li>Snapshot stores with optional caching</li>
 *   <li>Event publishers integrated with EDA</li>
 *   <li>Health indicators for Spring Boot Actuator</li>
 *   <li>Metrics collection via Micrometer</li>
 *   <li>Distributed tracing integration</li>
 * </ul>
 * <p>
 * The auto-configuration is enabled by default but can be disabled using:
 * <pre>
 * firefly:
 *   eventsourcing:
 *     enabled: false
 * </pre>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.eventsourcing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EventSourcingProperties.class)
@EnableAsync
@EnableScheduling
@Slf4j
public class EventSourcingAutoConfiguration {

    public EventSourcingAutoConfiguration() {
        log.info("Firefly Event Sourcing Auto-Configuration - Starting initialization");
        log.info("Event sourcing components will be auto-discovered: stores, snapshots, publishers, health, metrics");
    }

    /**
     * Creates the main event sourcing publisher if EDA is available.
     * This integrates event sourcing with the EDA messaging infrastructure.
     */
    @Bean
    @ConditionalOnBean(EventPublisherFactory.class)
    @ConditionalOnProperty(prefix = "firefly.eventsourcing.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public EventSourcingPublisher eventSourcingPublisher(
            EventPublisherFactory publisherFactory,
            EventSourcingProperties properties) {
        log.info("Creating EventSourcingPublisher with type: {}", properties.getPublisher().getType());
        return new EventSourcingPublisher(publisherFactory, properties);
    }

    /**
     * Provides a default ObjectMapper for event serialization if none exists.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper eventSourcingObjectMapper() {
        log.debug("Creating default ObjectMapper for event sourcing");
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Creates the EventStoreMetrics bean for monitoring event store operations.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    public EventStoreMetrics eventStoreMetrics(MeterRegistry meterRegistry) {
        log.debug("Creating EventStoreMetrics bean");
        return new EventStoreMetrics(meterRegistry);
    }

    /**
     * Creates the EventTypeRegistry bean for automatic event type discovery and registration.
     */
    @Bean
    @ConditionalOnMissingBean
    public EventTypeRegistry eventTypeRegistry(ApplicationContext applicationContext, ObjectMapper objectMapper) {
        log.debug("Creating EventTypeRegistry bean");
        return new EventTypeRegistry(applicationContext, objectMapper);
    }

    /**
     * Creates the EventSourcingTransactionalAspect bean for transactional event sourcing operations.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ReactiveTransactionManager.class, EventSourcingPublisher.class})
    public EventSourcingTransactionalAspect eventSourcingTransactionalAspect(
            ReactiveTransactionManager transactionManager,
            EventSourcingPublisher eventPublisher) {
        log.debug("Creating EventSourcingTransactionalAspect bean");
        return new EventSourcingTransactionalAspect(transactionManager, eventPublisher);
    }

    /**
     * Creates the EventUpcastingService bean for managing event upcasting.
     */
    @Bean
    @ConditionalOnMissingBean
    public EventUpcastingService eventUpcastingService(List<EventUpcaster> upcasters) {
        log.debug("Creating EventUpcastingService bean");
        return new EventUpcastingService(upcasters);
    }

    /**
     * Creates the EventOutboxService bean for managing Event Outbox operations.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({EventOutboxRepository.class, EventSourcingPublisher.class})
    public EventOutboxService eventOutboxService(
            EventOutboxRepository outboxRepository,
            EventSourcingPublisher eventPublisher,
            ObjectMapper objectMapper) {
        log.debug("Creating EventOutboxService bean");
        return new EventOutboxService(outboxRepository, eventPublisher, objectMapper);
    }

    /**
     * Creates the EventOutboxProcessor bean for background processing of outbox entries.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "firefly.eventsourcing.outbox.processor",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public EventOutboxProcessor eventOutboxProcessor(EventOutboxService outboxService) {
        log.debug("Creating EventOutboxProcessor bean");
        return new EventOutboxProcessor(outboxService);
    }
}