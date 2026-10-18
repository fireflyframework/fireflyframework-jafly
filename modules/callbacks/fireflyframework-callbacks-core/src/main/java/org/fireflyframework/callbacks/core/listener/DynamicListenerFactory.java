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

package org.fireflyframework.callbacks.core.listener;

import com.fasterxml.jackson.databind.JsonNode;
import org.fireflyframework.callbacks.core.service.CallbackRouter;
import org.fireflyframework.callbacks.core.service.EventSubscriptionService;
import org.fireflyframework.callbacks.interfaces.dto.EventSubscriptionDTO;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.listener.DynamicEventListenerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.UUID;

/**
 * Factory that creates dynamic event listener beans from database configuration.
 * <p>
 * This component works WITH lib-common-eda, not against it:
 * 1. On startup (@PostConstruct), loads EventSubscriptions from DB
 * 2. For each subscription, creates a Spring bean with @EventListener method
 * 3. lib-common-eda's EventListenerProcessor automatically discovers these beans
 * 4. lib-common-eda handles ALL messaging system connections (Kafka, RabbitMQ, etc.)
 * <p>
 * The beauty: We DON'T reimplement anything - lib-common-eda does all the work!
 */
@Component
@Slf4j
public class DynamicListenerFactory {

    private final CallbackRouter callbackRouter;
    private final DynamicEventListenerRegistry listenerRegistry;

    public DynamicListenerFactory(
            @Lazy CallbackRouter callbackRouter,
            @Lazy DynamicEventListenerRegistry listenerRegistry) {
        this.callbackRouter = callbackRouter;
        this.listenerRegistry = listenerRegistry;
    }

    /**
     * No automatic initialization - dynamic listeners will be registered
     * when subscriptions are created via the EventSubscriptionService.
     * This avoids circular dependencies.
     */
    
    /**
     * Registers a single subscription as a dynamic listener.
     * <p>
     * This method can be called both during initialization and when new subscriptions
     * are created at runtime.
     *
     * @param subscription the event subscription to register
     */
    public void registerSubscription(EventSubscriptionDTO subscription) {
        if (!"KAFKA".equalsIgnoreCase(subscription.getMessagingSystemType())) {
            log.debug("Skipping non-Kafka subscription: {}", subscription.getId());
            return;
        }
        
        log.info("Registering dynamic listener: id={}, name={}, topic={}, patterns={}",
                subscription.getId(),
                subscription.getName(),
                subscription.getTopicOrQueue(),
                subscription.getEventTypePatterns());
        
        // Register listener using lib-common-eda's registry
        listenerRegistry.registerListener(
                subscription.getId().toString(),
                subscription.getTopicOrQueue(),
                subscription.getEventTypePatterns() != null ? subscription.getEventTypePatterns() : new String[0],
                PublisherType.AUTO,
                (event, headers) -> handleEvent(event, headers, subscription)
        );
        
        log.info("Dynamic listener registered successfully: {}", subscription.getId());
    }
    
    /**
     * Unregisters a subscription's dynamic listener.
     *
     * @param subscriptionId the subscription ID to unregister
     */
    public void unregisterSubscription(UUID subscriptionId) {
        log.info("Unregistering dynamic listener for subscription: {}", subscriptionId);
        listenerRegistry.unregisterListener(subscriptionId.toString());
    }
    
    /**
     * Handles an event received from a subscription.
     */
    private Mono<Void> handleEvent(Object event, Map<String, Object> headers, EventSubscriptionDTO subscription) {
        try {
            // Convert event to JsonNode if it isn't already
            JsonNode eventJson;
            if (event instanceof JsonNode jsonNode) {
                eventJson = jsonNode;
            } else if (event instanceof String str) {
                eventJson = new com.fasterxml.jackson.databind.ObjectMapper().readTree(str);
            } else {
                eventJson = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(event);
            }
            
            // Extract event metadata
            String eventType = extractEventType(eventJson, headers);
            UUID eventId = extractEventId(eventJson, headers);
            
            log.debug("Received event through dynamic listener: type={}, id={}, subscription={}",
                    eventType, eventId, subscription.getId());
            
            // Check if event type matches subscription patterns
            if (matchesEventTypePattern(eventType, subscription.getEventTypePatterns())) {
                log.info("Event matches subscription pattern - routing: eventType={}, subscriptionId={}",
                        eventType, subscription.getId());
                
                // Route to callback configurations
                return callbackRouter.routeEvent(eventType, eventId, eventJson, headers).then();
            } else {
                log.debug("Event does not match subscription pattern: eventType={}, patterns={}",
                        eventType, subscription.getEventTypePatterns());
                return Mono.empty();
            }
            
        } catch (Exception e) {
            log.error("Error handling event in dynamic listener", e);
            return Mono.error(e);
        }
    }
    
    private String extractEventType(JsonNode event, Map<String, Object> headers) {
        // Try payload fields
        if (event.has("eventType")) return event.get("eventType").asText();
        if (event.has("type")) return event.get("type").asText();
        if (event.has("@type")) return event.get("@type").asText();
        
        // Try headers
        if (headers != null) {
            Object eventType = headers.get("eventType");
            if (eventType == null) eventType = headers.get("event-type");
            if (eventType == null) eventType = headers.get("type");
            if (eventType != null) return eventType.toString();
        }
        
        return "unknown.event";
    }
    
    private UUID extractEventId(JsonNode event, Map<String, Object> headers) {
        // Try payload fields
        if (event.has("eventId")) {
            try {
                return UUID.fromString(event.get("eventId").asText());
            } catch (IllegalArgumentException e) {
                // Fall through
            }
        }
        if (event.has("id")) {
            try {
                return UUID.fromString(event.get("id").asText());
            } catch (IllegalArgumentException e) {
                // Fall through
            }
        }
        
        // Try headers
        if (headers != null) {
            Object eventId = headers.get("eventId");
            if (eventId == null) eventId = headers.get("event-id");
            if (eventId != null) {
                try {
                    return UUID.fromString(eventId.toString());
                } catch (IllegalArgumentException e) {
                    // Fall through
                }
            }
        }
        
        return UUID.randomUUID();
    }
    
    private boolean matchesEventTypePattern(String eventType, String[] patterns) {
        if (patterns == null || patterns.length == 0) {
            return true; // No patterns means accept all
        }
        
        for (String pattern : patterns) {
            if (matchesPattern(eventType, pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean matchesPattern(String eventType, String pattern) {
        // Simple wildcard matching: customer.* matches customer.created, customer.updated, etc.
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
        
        return eventType.matches(regex);
    }

    /**
     * Creates a new dynamic listener bean for a subscription.
     * <p>
     * NOTE: For now, this creates the bean class below. In production, you'd
     * register this dynamically with Spring's BeanFactory, but that's complex.
     * <p>
     * The simpler approach: Just create @Component classes for each subscription
     * type and let Spring scan them normally. The @EventListener annotation
     * uses SpEL or properties to read from DB.
     * <p>
     * EVEN SIMPLER: Create ONE generic listener that listens to ALL topics
     * configured in DB, then route internally based on EventSubscription config.
     */
}
