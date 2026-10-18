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

package org.fireflyframework.eventsourcing.annotation;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Marks a class as a domain event and specifies its event type identifier.
 * <p>
 * This annotation combines the functionality of {@link JsonTypeName} for JSON serialization
 * with domain event metadata. It provides a Spring-aligned, declarative way to define
 * domain events without requiring manual implementation of {@code getEventType()}.
 * <p>
 * <b>Usage Example:</b>
 * <pre>
 * {@code
 * @DomainEvent("account.created")
 * @SuperBuilder
 * @Getter
 * @NoArgsConstructor
 * @AllArgsConstructor
 * public class AccountCreatedEvent extends AbstractDomainEvent {
 *     private String accountNumber;
 *     private BigDecimal initialBalance;
 *     // No need to override getEventType() - it's read from the annotation!
 * }
 * }
 * </pre>
 * <p>
 * <b>Benefits:</b>
 * <ul>
 *   <li>Declarative event type definition - more Spring-like</li>
 *   <li>No need to override getEventType() method</li>
 *   <li>Automatic JSON type handling via @JsonTypeName</li>
 *   <li>Compile-time validation of event type presence</li>
 *   <li>Cleaner, more maintainable code</li>
 * </ul>
 * <p>
 * <b>Event Type Naming Conventions:</b>
 * <ul>
 *   <li>Use dot notation: "aggregate.action" (e.g., "account.created")</li>
 *   <li>Use past tense: "created", "updated", "deleted"</li>
 *   <li>Be specific: "money.withdrawn" not just "withdrawn"</li>
 *   <li>Keep it stable: changing event types breaks deserialization</li>
 * </ul>
 * <p>
 * The annotation also serves as a marker for component scanning and automatic
 * event type registration with Jackson's polymorphic type handling.
 *
 * @see org.fireflyframework.eventsourcing.domain.Event
 * @see org.fireflyframework.eventsourcing.domain.AbstractDomainEvent
 * @see JsonTypeName
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JsonTypeName
public @interface DomainEvent {

    /**
     * The unique event type identifier.
     * <p>
     * This value is used for:
     * <ul>
     *   <li>Event serialization/deserialization (JSON type discrimination)</li>
     *   <li>Event routing and filtering</li>
     *   <li>Event store queries</li>
     *   <li>Event handler registration</li>
     * </ul>
     * <p>
     * <b>Naming Guidelines:</b>
     * <pre>
     * Good examples:
     *   - "account.created"
     *   - "money.withdrawn"
     *   - "payment.processed"
     *   - "order.shipped"
     * 
     * Bad examples:
     *   - "AccountCreated" (use lowercase with dots)
     *   - "create" (use past tense)
     *   - "event1" (not descriptive)
     * </pre>
     *
     * @return the event type identifier, must be unique across all events
     */
    @AliasFor(annotation = JsonTypeName.class, attribute = "value")
    String value();

    /**
     * Optional description of what this event represents.
     * <p>
     * This is useful for documentation and can be used by tools to generate
     * event catalogs or API documentation.
     *
     * @return a human-readable description of the event
     */
    String description() default "";

    /**
     * The version of this event schema.
     * <p>
     * Increment this when you make breaking changes to the event structure.
     * This helps with event evolution and migration strategies.
     * <p>
     * Example:
     * <pre>
     * {@code
     * // Version 1
     * @DomainEvent(value = "account.created", version = 1)
     * public class AccountCreatedEvent extends AbstractDomainEvent {
     *     private String accountNumber;
     * }
     * 
     * // Version 2 - added new field
     * @DomainEvent(value = "account.created", version = 2)
     * public class AccountCreatedEvent extends AbstractDomainEvent {
     *     private String accountNumber;
     *     private String accountType; // New field
     * }
     * }
     * </pre>
     *
     * @return the event schema version, defaults to 1
     */
    int version() default 1;

    /**
     * Indicates whether this event should be published to external systems.
     * <p>
     * When true, the event will be published to configured message brokers
     * (Kafka, RabbitMQ, etc.) for consumption by other services.
     * <p>
     * When false, the event is only stored in the event store and not published.
     * This is useful for internal events that don't need to be shared.
     *
     * @return true if the event should be published externally, defaults to true
     */
    boolean publishable() default true;

    /**
     * Tags for categorizing and filtering events.
     * <p>
     * Tags can be used for:
     * <ul>
     *   <li>Event filtering in queries</li>
     *   <li>Routing to specific handlers</li>
     *   <li>Monitoring and analytics</li>
     *   <li>Access control</li>
     * </ul>
     * <p>
     * Example:
     * <pre>
     * {@code
     * @DomainEvent(value = "payment.processed", tags = {"financial", "audit-required"})
     * public class PaymentProcessedEvent extends AbstractDomainEvent {
     *     // ...
     * }
     * }
     * </pre>
     *
     * @return array of tags, defaults to empty array
     */
    String[] tags() default {};
}

