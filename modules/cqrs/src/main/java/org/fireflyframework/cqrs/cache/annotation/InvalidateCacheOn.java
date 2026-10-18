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

package org.fireflyframework.cqrs.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for QueryHandler classes that triggers automatic cache invalidation
 * when specified event types are received via the EDA module.
 *
 * <p>When an event matching one of the specified {@code eventTypes} arrives,
 * the CQRS query cache is cleared so that subsequent queries return fresh data.
 * This provides eventual consistency between command-side mutations and query-side reads.
 *
 * <p>This annotation is opt-in: query handlers without it are unaffected.
 * Requires {@code fireflyframework-eda} on the classpath for event-driven invalidation to activate.
 *
 * <p>Example usage:
 * <pre>{@code
 * @QueryHandlerComponent(cacheable = true, cacheTtl = 300)
 * @InvalidateCacheOn(eventTypes = {"UserCreated", "UserUpdated", "UserDeleted"})
 * public class GetUserQueryHandler extends QueryHandler<GetUserQuery, UserDTO> {
 *     @Override
 *     protected Mono<UserDTO> doHandle(GetUserQuery query) {
 *         return userRepository.findById(query.getUserId());
 *     }
 * }
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvalidateCacheOn {

    /**
     * The event type identifiers that trigger cache invalidation for this query handler.
     * These should match the {@code eventType} values used in {@code @PublishDomainEvent}
     * or manually published EDA events.
     *
     * @return the event types that trigger invalidation
     */
    String[] eventTypes();
}
