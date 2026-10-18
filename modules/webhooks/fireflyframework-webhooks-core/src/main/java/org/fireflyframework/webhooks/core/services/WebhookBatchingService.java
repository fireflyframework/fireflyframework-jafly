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

import org.fireflyframework.webhooks.core.domain.events.WebhookReceivedEvent;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service for batching webhook events before publishing to Kafka.
 * <p>
 * Batching improves throughput by grouping multiple events and publishing
 * them together, reducing network overhead and improving Kafka efficiency.
 */
public interface WebhookBatchingService {

    /**
     * Adds an event to the batch buffer.
     * <p>
     * The event will be published either when:
     * - The batch reaches maxBatchSize
     * - The maxWaitTime elapses
     * - The buffer is manually flushed
     *
     * @param event the webhook event
     * @param destination the Kafka topic
     * @param headers the message headers
     * @return a Mono that completes when the event is buffered
     */
    Mono<Void> addToBatch(WebhookReceivedEvent event, String destination, Map<String, Object> headers);

    /**
     * Manually flushes all pending batches.
     * <p>
     * This is useful for graceful shutdown or testing.
     *
     * @return a Mono that completes when all batches are flushed
     */
    Mono<Void> flushAll();

    /**
     * Gets the current buffer size for a destination.
     *
     * @param destination the destination topic
     * @return the number of events currently buffered
     */
    int getBufferSize(String destination);
}

