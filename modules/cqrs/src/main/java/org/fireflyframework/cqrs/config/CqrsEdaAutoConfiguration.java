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

package org.fireflyframework.cqrs.config;

import org.fireflyframework.cqrs.cache.EventDrivenCacheInvalidator;
import org.fireflyframework.cqrs.cache.QueryCacheAdapter;
import org.fireflyframework.cqrs.event.CommandEventPublisher;
import org.fireflyframework.cqrs.event.EdaCommandEventPublisher;
import org.fireflyframework.eda.event.EventEnvelope;
import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

/**
 * Auto-configuration that bridges the CQRS module with the EDA module.
 *
 * <p>This configuration is only active when {@code fireflyframework-eda} is on the classpath.
 * It provides:
 * <ul>
 *   <li>{@link CommandEventPublisher} — publishes command results as domain events
 *       when handlers are annotated with {@code @PublishDomainEvent}</li>
 *   <li>{@link EventDrivenCacheInvalidator} — invalidates CQRS query caches when
 *       domain events matching {@code @InvalidateCacheOn} are received</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(after = CqrsAutoConfiguration.class)
@ConditionalOnClass(EventPublisher.class)
@ConditionalOnProperty(prefix = "firefly.cqrs.eda", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CqrsEdaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EventPublisherFactory.class)
    public CommandEventPublisher commandEventPublisher(EventPublisherFactory publisherFactory) {
        log.info("Configuring CQRS-EDA bridge: CommandEventPublisher enabled - @PublishDomainEvent annotations will auto-publish events");
        return new EdaCommandEventPublisher(publisherFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(QueryCacheAdapter.class)
    public EventDrivenCacheInvalidator eventDrivenCacheInvalidator(
            QueryCacheAdapter cacheAdapter,
            ApplicationContext applicationContext) {
        log.info("Configuring CQRS-EDA bridge: EventDrivenCacheInvalidator enabled - @InvalidateCacheOn annotations will auto-evict query caches");
        return new EventDrivenCacheInvalidator(cacheAdapter, applicationContext);
    }

    /**
     * Bridge that listens to EDA events published via Spring's ApplicationEventPublisher
     * and forwards them to the cache invalidator.
     */
    @Bean
    @ConditionalOnBean(EventDrivenCacheInvalidator.class)
    public EdaCacheInvalidationBridge edaCacheInvalidationBridge(EventDrivenCacheInvalidator invalidator) {
        return new EdaCacheInvalidationBridge(invalidator);
    }

    /**
     * Listens to EDA {@link EventEnvelope} events published via Spring's ApplicationEventPublisher
     * and triggers CQRS query cache invalidation for matching event types.
     */
    @Slf4j
    static class EdaCacheInvalidationBridge {

        private final EventDrivenCacheInvalidator invalidator;

        EdaCacheInvalidationBridge(EventDrivenCacheInvalidator invalidator) {
            this.invalidator = invalidator;
            log.info("EdaCacheInvalidationBridge initialized - listening for EDA events to invalidate CQRS caches");
        }

        @EventListener
        public void onEdaEvent(EventEnvelope envelope) {
            if (envelope != null && envelope.eventType() != null) {
                invalidator.invalidateForEventType(envelope.eventType())
                        .doOnError(e -> log.warn("Cache invalidation failed for event type '{}': {}",
                                envelope.eventType(), e.getMessage()))
                        .subscribe();
            }
        }
    }
}
