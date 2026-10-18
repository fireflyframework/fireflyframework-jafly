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

package org.fireflyframework.callbacks.models.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for event subscriptions.
 * Represents a dynamic subscription to a messaging system (Kafka, RabbitMQ, Pulsar, AWS SQS, etc.).
 * This entity is mapped to the "event_subscriptions" table in the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("event_subscriptions")
public class EventSubscription {

    /**
     * Unique identifier for the subscription.
     */
    @Id
    private UUID id;

    /**
     * Name of the subscription (e.g., "Customer Service Events").
     */
    private String name;

    /**
     * Description of the subscription.
     */
    private String description;

    /**
     * Messaging system type (KAFKA, RABBITMQ, PULSAR, AWS_SQS, etc.).
     */
    @Column("messaging_system_type")
    private String messagingSystemType;

    /**
     * Connection configuration for the messaging system.
     * Stored as JSON TEXT in the database.
     */
    @Column("connection_config")
    private String connectionConfig;

    /**
     * Topic/queue name to subscribe to.
     */
    @Column("topic_or_queue")
    private String topicOrQueue;

    /**
     * Consumer group ID (for systems that support it like Kafka).
     */
    @Column("consumer_group_id")
    private String consumerGroupId;

    /**
     * Whether this subscription is active.
     */
    private Boolean active;

    /**
     * Additional consumer properties specific to the messaging system.
     * Stored as JSON TEXT in the database.
     */
    @Column("consumer_properties")
    private String consumerProperties;

    /**
     * Event type patterns to match (wildcards supported).
     * Stored as TEXT[] in the database.
     */
    @Column("event_type_patterns")
    private String[] eventTypePatterns;

    /**
     * Maximum number of concurrent consumers for this subscription.
     */
    @Column("max_concurrent_consumers")
    private Integer maxConcurrentConsumers;

    /**
     * Polling interval in milliseconds (for pull-based systems).
     */
    @Column("polling_interval_ms")
    private Integer pollingIntervalMs;

    /**
     * Tenant ID for multi-tenancy.
     */
    @Column("tenant_id")
    private String tenantId;

    /**
     * Metadata for the subscription.
     * Stored as JSON TEXT in the database.
     */
    private String metadata;

    /**
     * Last time a message was received from this subscription.
     */
    @Column("last_message_at")
    private Instant lastMessageAt;

    /**
     * Total messages received from this subscription.
     */
    @Column("total_messages_received")
    private Long totalMessagesReceived;

    /**
     * Total messages failed to process.
     */
    @Column("total_messages_failed")
    private Long totalMessagesFailed;

    /**
     * Creation timestamp.
     */
    @Column("created_at")
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    @Column("updated_at")
    private Instant updatedAt;

    /**
     * User who created the subscription.
     */
    @Column("created_by")
    private String createdBy;

    /**
     * User who last updated the subscription.
     */
    @Column("updated_by")
    private String updatedBy;
}
