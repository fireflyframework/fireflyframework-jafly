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

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.fireflyframework.eventsourcing.domain.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for automatically discovering and registering event types with Jackson.
 * <p>
 * This component scans the classpath for classes that implement {@link Event} and are
 * annotated with {@link JsonTypeName}, then registers them with the ObjectMapper
 * for proper polymorphic serialization/deserialization.
 * <p>
 * Usage:
 * <pre>{@code
 * @JsonTypeName("user.created")
 * public class UserCreatedEvent implements Event {
 *     // Event implementation
 * }
 * }</pre>
 * <p>
 * The event will be automatically discovered and registered when the application starts.
 */
@RequiredArgsConstructor
@Slf4j
public class EventTypeRegistry {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final Set<String> registeredTypes = ConcurrentHashMap.newKeySet();

    /**
     * Automatically registers all discovered event types when the application is ready.
     * Scans the classpath for {@link Event} implementations annotated with {@link JsonTypeName}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerEventTypes() {
        log.info("Starting automatic event type registration...");

        registerEventClasses();

        log.info("Completed automatic event type registration: {} event types registered", registeredTypes.size());
    }

    /**
     * Manually register an event type with the ObjectMapper.
     * This is useful for events that aren't discoverable via classpath scanning.
     *
     * @param eventClass the event class to register
     */
    public void registerEventType(Class<? extends Event> eventClass) {
        JsonTypeName annotation = eventClass.getAnnotation(JsonTypeName.class);
        if (annotation != null) {
            String typeName = annotation.value();
            objectMapper.registerSubtypes(new NamedType(eventClass, typeName));
            registeredTypes.add(typeName);
            log.debug("Manually registered event type: {} -> {}", typeName, eventClass.getSimpleName());
        } else {
            log.warn("Event class {} does not have @JsonTypeName annotation, skipping registration",
                    eventClass.getSimpleName());
        }
    }

    /**
     * Register an event type with a custom type name.
     *
     * @param eventClass the event class to register
     * @param typeName the custom type name to use
     */
    public void registerEventType(Class<? extends Event> eventClass, String typeName) {
        objectMapper.registerSubtypes(new NamedType(eventClass, typeName));
        registeredTypes.add(typeName);
        log.debug("Manually registered event type: {} -> {}", typeName, eventClass.getSimpleName());
    }

    /**
     * Get all registered event type names.
     *
     * @return unmodifiable set of registered type names
     */
    public Set<String> getRegisteredEventTypes() {
        return Collections.unmodifiableSet(registeredTypes);
    }

    /**
     * Scans the classpath for classes implementing {@link Event} that are annotated
     * with {@link JsonTypeName}, and registers them with Jackson's ObjectMapper.
     */
    @SuppressWarnings("unchecked")
    private void registerEventClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Event.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(JsonTypeName.class));

        String[] basePackages = applicationContext.getEnvironment()
                .getProperty("firefly.eventsourcing.event-scan-packages", String.class, "org.fireflyframework")
                .split(",");

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage.trim());
            for (BeanDefinition candidate : candidates) {
                try {
                    Class<?> eventClass = Class.forName(candidate.getBeanClassName());
                    if (Event.class.isAssignableFrom(eventClass)) {
                        JsonTypeName annotation = eventClass.getAnnotation(JsonTypeName.class);
                        if (annotation != null) {
                            String typeName = annotation.value();
                            objectMapper.registerSubtypes(new NamedType(eventClass, typeName));
                            registeredTypes.add(typeName);
                            log.debug("Auto-registered event type: {} -> {}", typeName, eventClass.getSimpleName());
                        }
                    }
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load event class: {}", candidate.getBeanClassName(), e);
                }
            }
        }
    }
}
