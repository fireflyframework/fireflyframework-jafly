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

package org.fireflyframework.cqrs.event.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for CommandHandler classes that triggers automatic domain event publishing
 * after successful command execution.
 *
 * <p>When a CommandHandler annotated with {@code @PublishDomainEvent} successfully processes
 * a command, the result is automatically published as a domain event via the EDA module's
 * {@code EventPublisher}. This provides a declarative bridge between CQRS commands and
 * event-driven architecture.
 *
 * <p>This annotation is opt-in: handlers without it work exactly as before.
 * Requires {@code fireflyframework-eda} on the classpath for event publishing to activate.
 *
 * <p>Example usage:
 * <pre>{@code
 * @CommandHandlerComponent
 * @PublishDomainEvent(destination = "user-events", eventType = "UserCreated")
 * public class CreateUserCommandHandler extends CommandHandler<CreateUserCommand, UserCreatedResult> {
 *     @Override
 *     protected Mono<UserCreatedResult> doHandle(CreateUserCommand command) {
 *         // The result will be auto-published to "user-events" topic
 *         return userService.createUser(command);
 *     }
 * }
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublishDomainEvent {

    /**
     * The destination (topic, queue, exchange) to publish the domain event to.
     *
     * @return the event destination
     */
    String destination();

    /**
     * The event type identifier. If empty, defaults to the result class simple name.
     *
     * @return the event type, or empty to use the result class name
     */
    String eventType() default "";
}
