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

package org.fireflyframework.eda.health;

import org.fireflyframework.eda.consumer.EventConsumer;
import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.fireflyframework.eda.publisher.PublisherHealth;
import org.fireflyframework.observability.health.FireflyHealthIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health indicator for the EDA library that reports the status of publishers and consumers.
 * <p>
 * Extends {@link FireflyHealthIndicator} for consistent health reporting across the Firefly Framework.
 * <p>
 * This indicator provides comprehensive health information about:
 * <ul>
 *   <li>EDA library configuration and availability</li>
 *   <li>All registered event publishers and their connection status</li>
 *   <li>All registered event consumers and their running status</li>
 *   <li>Overall system health based on component availability</li>
 * </ul>
 */
@Component
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
@ConditionalOnProperty(prefix = "firefly.eda", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@Lazy
@Slf4j
public class EdaHealthIndicator extends FireflyHealthIndicator {

    private final EdaProperties edaProperties;
    private final EventPublisherFactory publisherFactory;
    private final ObjectProvider<EventConsumer> eventConsumersProvider;

    public EdaHealthIndicator(EdaProperties edaProperties,
                              EventPublisherFactory publisherFactory,
                              ObjectProvider<EventConsumer> eventConsumersProvider) {
        super("eda");
        this.edaProperties = edaProperties;
        this.publisherFactory = publisherFactory;
        this.eventConsumersProvider = eventConsumersProvider;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try {
            log.debug("Checking EDA health status");

            builder.withDetail("enabled", edaProperties.isEnabled());
            builder.withDetail("default.publisher.type", edaProperties.getDefaultPublisherType().name());
            builder.withDetail("metrics.enabled", edaProperties.isMetricsEnabled());
            builder.withDetail("tracing.enabled", edaProperties.isTracingEnabled());

            if (!edaProperties.isEnabled()) {
                builder.down().withDetail("message", "EDA library is disabled");
                return;
            }

            // Check publishers health
            Map<String, Object> publishersHealth = checkPublishersHealth();
            builder.withDetail("publishers", publishersHealth);

            // Check consumers health
            Map<String, Object> consumersHealth = checkConsumersHealth();
            builder.withDetail("consumers", consumersHealth);

            // Check resilience configuration
            builder.withDetail("resilience", Map.of(
                    "enabled", edaProperties.getResilience().isEnabled(),
                    "circuit.breaker.enabled", edaProperties.getResilience().getCircuitBreaker().isEnabled(),
                    "retry.enabled", edaProperties.getResilience().getRetry().isEnabled(),
                    "rate.limiter.enabled", edaProperties.getResilience().getRateLimiter().isEnabled()
            ));

            // Determine overall health status
            boolean hasHealthyPublishers = isHealthyMap(publishersHealth);
            boolean hasHealthyConsumers = consumersHealth.isEmpty() || isHealthyMap(consumersHealth);

            if (hasHealthyPublishers && hasHealthyConsumers) {
                builder.up().withDetail("message", "All EDA components are healthy");
            } else {
                builder.down().withDetail("message", "Some EDA components are unhealthy");
            }

        } catch (Exception e) {
            log.error("Error checking EDA health: {}", e.getMessage(), e);
            builder.down()
                    .withDetail("message", "Error checking EDA health")
                    .withException(e);
        }
    }

    private Map<String, Object> checkPublishersHealth() {
        try {
            Map<String, PublisherHealth> publishersHealth = publisherFactory.getPublishersHealth();

            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, PublisherHealth> entry : publishersHealth.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), convertPublisherHealthToMap(entry.getValue()));
                } else {
                    result.put(entry.getKey(), Map.of("status", "UNKNOWN"));
                }
            }

            return result;

        } catch (Exception e) {
            log.warn("Error checking publishers health: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> checkConsumersHealth() {
        List<EventConsumer> eventConsumers = eventConsumersProvider.stream().toList();
        if (eventConsumers.isEmpty()) {
            return Map.of();
        }

        try {
            Map<String, Object> result = new HashMap<>();
            for (EventConsumer consumer : eventConsumers) {
                try {
                    var health = consumer.getHealth().block(Duration.ofSeconds(5));
                    if (health != null) {
                        result.put(consumer.getConsumerType(), convertConsumerHealthToMap(health));
                    } else {
                        result.put(consumer.getConsumerType(), Map.of("status", "UNKNOWN"));
                    }
                } catch (Exception e) {
                    result.put(consumer.getConsumerType(), Map.of("status", "ERROR", "error", e.getMessage()));
                }
            }
            return result;

        } catch (Exception e) {
            log.warn("Error checking consumers health: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> convertPublisherHealthToMap(PublisherHealth health) {
        Map<String, Object> healthMap = new HashMap<>();
        healthMap.put("status", health.getStatus());
        healthMap.put("available", health.isAvailable());
        healthMap.put("publisher.type", health.getPublisherType());
        healthMap.put("connection.id", health.getConnectionId());
        healthMap.put("last.checked", health.getLastChecked());

        if (health.getDetails() != null) {
            healthMap.put("details", health.getDetails());
        }
        if (health.getErrorMessage() != null) {
            healthMap.put("error", health.getErrorMessage());
        }

        return healthMap;
    }

    private Map<String, Object> convertConsumerHealthToMap(org.fireflyframework.eda.consumer.ConsumerHealth health) {
        Map<String, Object> healthMap = new HashMap<>();
        healthMap.put("status", health.getStatus());
        healthMap.put("available", health.isAvailable());
        healthMap.put("running", health.isRunning());
        healthMap.put("consumer.type", health.getConsumerType());
        healthMap.put("last.checked", health.getLastChecked());

        if (health.getDetails() != null) {
            healthMap.put("details", health.getDetails());
        }
        if (health.getErrorMessage() != null) {
            healthMap.put("error", health.getErrorMessage());
        }
        if (health.getMessagesConsumed() != null) {
            healthMap.put("messages.consumed", health.getMessagesConsumed());
        }
        if (health.getMessagesProcessed() != null) {
            healthMap.put("messages.processed", health.getMessagesProcessed());
        }
        if (health.getMessagesFailures() != null) {
            healthMap.put("messages.failures", health.getMessagesFailures());
        }

        return healthMap;
    }

    private boolean isHealthyMap(Map<String, Object> healthMap) {
        if (healthMap.isEmpty()) {
            return true;
        }

        return healthMap.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(componentHealth -> {
                    Object status = componentHealth.get("status");
                    return status != null && status.toString().equals("UP");
                });
    }
}
