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

package org.fireflyframework.callbacks.interfaces.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for configuring dynamic event subscriptions.
 * This allows the system to subscribe to new topics/queues at runtime.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSubscriptionDTO {

    /**
     * Unique identifier for the subscription.
     */
    private UUID id;

    /**
     * Name of the subscription (e.g., "Customer Service Events").
     * Must be between 1 and 255 characters.
     */
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;

    /**
     * Description of the subscription.
     * Maximum 2000 characters.
     */
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    /**
     * Messaging system type (KAFKA, RABBITMQ, PULSAR, AWS_SQS, etc.).
     * Must be between 1 and 50 characters.
     */
    @NotBlank(message = "Messaging system type is required")
    @Size(min = 1, max = 50, message = "Messaging system type must be between 1 and 50 characters")
    private String messagingSystemType;

    /**
     * Connection configuration for the messaging system.
     * Example for Kafka: {"bootstrap.servers": "localhost:9092", "group.id": "callbacks-consumer"}
     * Example for RabbitMQ: {"host": "localhost", "port": "5672", "username": "guest"}
     * Must contain at least one configuration entry.
     */
    @NotNull(message = "Connection config is required")
    @Size(min = 1, message = "Connection config must contain at least one entry")
    private Map<String, String> connectionConfig;

    /**
     * Topic/queue name to subscribe to.
     * For Kafka: topic name
     * For RabbitMQ: queue name
     * For SQS: queue URL
     * Must be between 1 and 500 characters.
     */
    @NotBlank(message = "Topic/queue name is required")
    @Size(min = 1, max = 500, message = "Topic/queue name must be between 1 and 500 characters")
    private String topicOrQueue;

    /**
     * Consumer group ID (for systems that support it like Kafka).
     * Maximum 255 characters.
     */
    @Size(max = 255, message = "Consumer group ID must not exceed 255 characters")
    private String consumerGroupId;

    /**
     * Whether this subscription is active.
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Additional consumer properties specific to the messaging system.
     */
    private Map<String, String> consumerProperties;

    /**
     * Event type patterns to match (wildcards supported).
     * Example: ["customer.*", "loan.approved"]
     */
    private String[] eventTypePatterns;

    /**
     * Maximum number of concurrent consumers for this subscription.
     * Must be between 1 and 100.
     */
    @Builder.Default
    @Min(value = 1, message = "Max concurrent consumers must be at least 1")
    @Max(value = 100, message = "Max concurrent consumers must not exceed 100")
    private Integer maxConcurrentConsumers = 1;

    /**
     * Polling interval in milliseconds (for pull-based systems).
     * Must be between 100ms and 60000ms (1 minute).
     */
    @Builder.Default
    @Min(value = 100, message = "Polling interval must be at least 100ms")
    @Max(value = 60000, message = "Polling interval must not exceed 60000ms (1 minute)")
    private Integer pollingIntervalMs = 1000;

    /**
     * Tenant ID for multi-tenancy.
     * Maximum 100 characters.
     */
    @Size(max = 100, message = "Tenant ID must not exceed 100 characters")
    private String tenantId;

    /**
     * Metadata for the subscription.
     */
    private Map<String, Object> metadata;

    /**
     * Last time a message was received from this subscription.
     */
    private Instant lastMessageAt;

    /**
     * Total messages received from this subscription.
     * Must be non-negative.
     */
    @Builder.Default
    @Min(value = 0, message = "Total messages received must be non-negative")
    private Long totalMessagesReceived = 0L;

    /**
     * Total messages failed to process.
     * Must be non-negative.
     */
    @Builder.Default
    @Min(value = 0, message = "Total messages failed must be non-negative")
    private Long totalMessagesFailed = 0L;

    /**
     * Creation timestamp.
     */
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    private Instant updatedAt;

    /**
     * User who created the subscription.
     * Maximum 255 characters.
     */
    @Size(max = 255, message = "Created by must not exceed 255 characters")
    private String createdBy;

    /**
     * User who last updated the subscription.
     * Maximum 255 characters.
     */
    @Size(max = 255, message = "Updated by must not exceed 255 characters")
    private String updatedBy;
}
