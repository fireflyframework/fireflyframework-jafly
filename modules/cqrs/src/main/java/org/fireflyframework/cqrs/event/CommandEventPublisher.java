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
import reactor.core.publisher.Mono;

/**
 * Interface for publishing domain events after successful command execution.
 *
 * <p>This interface is implemented by an EDA-backed publisher when
 * {@code fireflyframework-eda} is on the classpath. The default implementation
 * uses {@code EventPublisherFactory} to publish events to the configured
 * messaging infrastructure (Kafka, RabbitMQ, or Spring Application Events).
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 * @see PublishDomainEvent
 */
public interface CommandEventPublisher {

    /**
     * Publishes the command result as a domain event.
     *
     * @param command the command that produced the result
     * @param result the command execution result to publish as an event
     * @param annotation the {@code @PublishDomainEvent} annotation containing destination and event type
     * @param <R> the result type
     * @return a Mono that completes when the event is published
     */
    <R> Mono<Void> publish(Command<R> command, R result, PublishDomainEvent annotation);
}
