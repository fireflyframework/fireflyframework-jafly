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

package org.fireflyframework.cqrs.cache;

import org.fireflyframework.cqrs.cache.annotation.InvalidateCacheOn;
import org.fireflyframework.cqrs.query.QueryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Invalidates CQRS query caches when domain events arrive that match
 * {@link InvalidateCacheOn} annotations on query handlers.
 *
 * <p>At startup, this component scans all registered {@link QueryHandler} beans for
 * {@code @InvalidateCacheOn} annotations and builds an event-type-to-handler mapping.
 * When {@link #invalidateForEventType(String)} is called (typically from the EDA bridge),
 * matching query caches are cleared.
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class EventDrivenCacheInvalidator {

    private final QueryCacheAdapter cacheAdapter;
    private final Map<String, Set<String>> eventTypeToHandlers = new ConcurrentHashMap<>();

    @SuppressWarnings("rawtypes")
    public EventDrivenCacheInvalidator(QueryCacheAdapter cacheAdapter, ApplicationContext applicationContext) {
        this.cacheAdapter = cacheAdapter;
        scanQueryHandlers(applicationContext);
    }

    @SuppressWarnings("rawtypes")
    private void scanQueryHandlers(ApplicationContext applicationContext) {
        Map<String, QueryHandler> handlers = applicationContext.getBeansOfType(QueryHandler.class);

        for (Map.Entry<String, QueryHandler> entry : handlers.entrySet()) {
            InvalidateCacheOn annotation = entry.getValue().getClass().getAnnotation(InvalidateCacheOn.class);
            if (annotation != null) {
                String handlerName = entry.getValue().getClass().getSimpleName();
                for (String eventType : annotation.eventTypes()) {
                    eventTypeToHandlers
                            .computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet())
                            .add(handlerName);
                }
            }
        }

        if (!eventTypeToHandlers.isEmpty()) {
            log.info("EventDrivenCacheInvalidator initialized with {} event type mappings: {}",
                    eventTypeToHandlers.size(), eventTypeToHandlers);
        } else {
            log.debug("EventDrivenCacheInvalidator initialized - no @InvalidateCacheOn annotations found");
        }
    }

    /**
     * Invalidates CQRS query caches for the given event type.
     *
     * @param eventType the event type that was received
     * @return a Mono that completes when the cache is cleared
     */
    public Mono<Void> invalidateForEventType(String eventType) {
        Set<String> handlers = eventTypeToHandlers.get(eventType);
        if (handlers != null && !handlers.isEmpty()) {
            log.info("Invalidating CQRS query cache for event type '{}', affected handlers: {}", eventType, handlers);
            return cacheAdapter.clear()
                    .doOnSuccess(v -> log.debug("CQRS query cache cleared due to event: {}", eventType));
        }
        return Mono.empty();
    }

    /**
     * Returns the set of event types that this invalidator is listening for.
     *
     * @return unmodifiable set of registered event type names
     */
    public Set<String> getRegisteredEventTypes() {
        return Collections.unmodifiableSet(eventTypeToHandlers.keySet());
    }
}
