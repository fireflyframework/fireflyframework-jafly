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

package org.fireflyframework.cqrs.event;

import org.fireflyframework.cqrs.command.Command;
import org.fireflyframework.cqrs.event.annotation.PublishDomainEvent;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * EDA-backed implementation of {@link CommandEventPublisher} that publishes
 * command results as domain events via the EDA module's messaging infrastructure.
 *
 * <p>This class is only instantiated when {@code fireflyframework-eda} is on the classpath,
 * via {@link org.fireflyframework.cqrs.config.CqrsEdaAutoConfiguration}.
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class EdaCommandEventPublisher implements CommandEventPublisher {

    private final EventPublisherFactory publisherFactory;

    public EdaCommandEventPublisher(EventPublisherFactory publisherFactory) {
        this.publisherFactory = publisherFactory;
        log.info("EdaCommandEventPublisher initialized - CQRS commands will auto-publish domain events via EDA");
    }

    @Override
    public <R> Mono<Void> publish(Command<R> command, R result, PublishDomainEvent annotation) {
        String destination = annotation.destination();
        String eventType = annotation.eventType().isEmpty()
                ? result.getClass().getSimpleName()
                : annotation.eventType();

        EventPublisher publisher = publisherFactory.getPublisher(PublisherType.AUTO);
        if (publisher == null || !publisher.isAvailable()) {
            log.warn("No available EDA publisher for domain event: destination={}, eventType={}, commandId={}",
                    destination, eventType, command.getCommandId());
            return Mono.empty();
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put("eventType", eventType);
        headers.put("commandId", command.getCommandId());
        headers.put("commandType", command.getClass().getSimpleName());
        if (command.getCorrelationId() != null) {
            headers.put("correlationId", command.getCorrelationId());
        }
        if (command.getInitiatedBy() != null) {
            headers.put("initiatedBy", command.getInitiatedBy());
        }

        log.debug("Publishing domain event: destination={}, eventType={}, commandId={}",
                destination, eventType, command.getCommandId());

        return publisher.publish(result, destination, headers)
                .doOnSuccess(v -> log.info("Domain event published: destination={}, eventType={}, commandId={}",
                        destination, eventType, command.getCommandId()));
    }
}
