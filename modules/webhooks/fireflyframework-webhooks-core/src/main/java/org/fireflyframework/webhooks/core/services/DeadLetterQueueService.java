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

package org.fireflyframework.webhooks.core.services;

import org.fireflyframework.webhooks.core.domain.events.WebhookRejectedEvent;
import reactor.core.publisher.Mono;

/**
 * Service for publishing rejected webhooks to the Dead Letter Queue (DLQ).
 * <p>
 * The DLQ is a Kafka topic that stores webhooks that failed validation or processing.
 * This allows for:
 * - Manual inspection and debugging
 * - Replay after fixing issues
 * - Monitoring and alerting
 */
public interface DeadLetterQueueService {

    /**
     * Publishes a rejected webhook to the DLQ.
     *
     * @param rejectedEvent the rejected webhook event
     * @return a Mono that completes when published
     */
    Mono<Void> publishToDeadLetterQueue(WebhookRejectedEvent rejectedEvent);

    /**
     * Gets the DLQ topic name.
     *
     * @return the DLQ topic name
     */
    String getDeadLetterQueueTopic();
}

