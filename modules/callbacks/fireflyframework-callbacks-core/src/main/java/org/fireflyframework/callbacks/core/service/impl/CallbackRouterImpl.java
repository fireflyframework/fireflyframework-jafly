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

package org.fireflyframework.callbacks.core.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.fireflyframework.callbacks.core.service.CallbackConfigurationService;
import org.fireflyframework.callbacks.core.service.CallbackDispatcher;
import org.fireflyframework.callbacks.core.service.CallbackRouter;
import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Implementation of callback router.
 * Routes incoming events to matching callback configurations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CallbackRouterImpl implements CallbackRouter {

    private final CallbackConfigurationService configurationService;
    private final CallbackDispatcher callbackDispatcher;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Integer> routeEvent(
            String eventType,
            UUID eventId,
            JsonNode payload,
            Map<String, Object> headers) {
        
        log.debug("Routing event: type={}, id={}", eventType, eventId);

        // Find all active callback configurations subscribed to this event type
        return configurationService.findActiveByEventType(eventType)
                .doOnNext(config -> log.debug("Found matching callback configuration: id={}, name={}, url={}", 
                        config.getId(), config.getName(), config.getUrl()))
                .flatMap(config -> dispatchCallback(config, eventType, eventId, payload)
                        .onErrorResume(error -> {
                            log.error("Error dispatching callback: config={}, error={}", 
                                    config.getId(), error.getMessage(), error);
                            return Mono.empty(); // Continue with other callbacks even if one fails
                        }))
                .count()
                .map(Long::intValue)
                .defaultIfEmpty(0)
                .doOnSuccess(count -> {
                    if (count == 0) {
                        log.debug("No matching callback configurations found for event type: {}", eventType);
                    } else {
                        log.info("Routed event to {} callback configuration(s): type={}, id={}", 
                                count, eventType, eventId);
                    }
                })
                .doOnError(error -> log.error("Error routing event: type={}, id={}", eventType, eventId, error));
    }

    /**
     * Dispatches a callback to a specific configuration.
     * Applies any filter expressions if configured.
     */
    private Mono<Void> dispatchCallback(
            CallbackConfigurationDTO configuration,
            String eventType,
            UUID eventId,
            JsonNode payload) {
        
        // Check if filter expression matches (if configured)
        if (configuration.getFilterExpression() != null && !configuration.getFilterExpression().isEmpty()) {
            boolean matches = evaluateFilter(configuration.getFilterExpression(), payload);
            if (!matches) {
                log.debug("Event filtered out by configuration: config={}, filter={}", 
                        configuration.getId(), configuration.getFilterExpression());
                return Mono.empty();
            }
        }
        
        // Dispatch the callback
        return callbackDispatcher.dispatch(configuration, eventType, eventId, payload)
                .doOnSuccess(v -> log.debug("Successfully dispatched callback: config={}", configuration.getId()))
                .doOnError(error -> log.error("Failed to dispatch callback: config={}, error={}", 
                        configuration.getId(), error.getMessage()));
    }

    /**
     * Evaluates a filter expression against the payload.
     * 
     * For now, this is a simple implementation. In production, you might want to use:
     * - Spring Expression Language (SpEL)
     * - JSONPath
     * - Custom expression evaluator
     */
    private boolean evaluateFilter(String filterExpression, JsonNode payload) {
        try {
            // Simple key=value filter for now
            // Example: "customer.type=PREMIUM"
            if (filterExpression.contains("=")) {
                String[] parts = filterExpression.split("=", 2);
                String path = parts[0].trim();
                String expectedValue = parts[1].trim();
                
                // Navigate the JSON path
                JsonNode node = payload;
                for (String segment : path.split("\\.")) {
                    if (node == null) return false;
                    node = node.get(segment);
                }
                
                if (node == null) return false;
                
                String actualValue = node.isTextual() ? node.asText() : node.toString();
                return expectedValue.equals(actualValue);
            }
            
            // If no specific filter logic, allow the event
            return true;
            
        } catch (Exception e) {
            log.warn("Error evaluating filter expression: {}, allowing event through", filterExpression, e);
            return true; // Fail open - don't block events due to filter errors
        }
    }
}
