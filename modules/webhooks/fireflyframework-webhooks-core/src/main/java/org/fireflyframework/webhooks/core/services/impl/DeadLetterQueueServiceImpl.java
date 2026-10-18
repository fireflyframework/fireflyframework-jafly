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

package org.fireflyframework.webhooks.core.services.impl;

import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.fireflyframework.webhooks.core.domain.events.WebhookRejectedEvent;
import org.fireflyframework.webhooks.core.services.DeadLetterQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of Dead Letter Queue service.
 * <p>
 * Publishes rejected webhooks to a dedicated Kafka topic for monitoring and debugging.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeadLetterQueueServiceImpl implements DeadLetterQueueService {

    private final EventPublisherFactory eventPublisherFactory;

    @Value("${firefly.webhooks.dlq.topic:webhooks.dlq}")
    private String dlqTopic;

    @Value("${firefly.webhooks.dlq.enabled:true}")
    private boolean dlqEnabled;

    @Override
    public Mono<Void> publishToDeadLetterQueue(WebhookRejectedEvent rejectedEvent) {
        if (!dlqEnabled) {
            log.debug("DLQ is disabled, skipping publication of rejected webhook: eventId={}", 
                    rejectedEvent.getEventId());
            return Mono.empty();
        }

        log.warn("Publishing rejected webhook to DLQ: eventId={}, provider={}, reason={}, category={}",
                rejectedEvent.getEventId(),
                rejectedEvent.getProviderName(),
                rejectedEvent.getRejectionReason(),
                rejectedEvent.getRejectionCategory());

        // Build headers with metadata
        Map<String, Object> headers = new HashMap<>();
        headers.put("provider", rejectedEvent.getProviderName());
        headers.put("eventId", rejectedEvent.getEventId().toString());
        headers.put("rejectionReason", rejectedEvent.getRejectionReason());
        headers.put("rejectionCategory", rejectedEvent.getRejectionCategory().name());
        headers.put("receivedAt", rejectedEvent.getReceivedAt().toString());
        headers.put("rejectedAt", rejectedEvent.getRejectedAt().toString());
        
        if (rejectedEvent.getRetryCount() != null) {
            headers.put("retryCount", rejectedEvent.getRetryCount().toString());
        }
        
        if (rejectedEvent.getExceptionType() != null) {
            headers.put("exceptionType", rejectedEvent.getExceptionType());
        }

        // Get default publisher from factory
        EventPublisher publisher = eventPublisherFactory.getDefaultPublisher();
        
        if (publisher == null) {
            log.error("No event publisher available for DLQ");
            return Mono.error(new IllegalStateException("No event publisher available for DLQ"));
        }

        // Publish to DLQ topic
        return publisher.publish(rejectedEvent, dlqTopic, headers)
                .doOnSuccess(v -> log.info("Successfully published rejected webhook to DLQ: eventId={}, topic={}",
                        rejectedEvent.getEventId(), dlqTopic))
                .doOnError(error -> log.error("Error publishing rejected webhook to DLQ: eventId={}, error={}",
                        rejectedEvent.getEventId(), error.getMessage(), error))
                .onErrorResume(error -> {
                    // Don't fail the original request if DLQ publishing fails
                    log.error("Failed to publish to DLQ, continuing anyway: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public String getDeadLetterQueueTopic() {
        return dlqTopic;
    }
}

