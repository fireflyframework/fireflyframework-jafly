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
import org.fireflyframework.webhooks.core.domain.events.WebhookReceivedEvent;
import org.fireflyframework.webhooks.core.mappers.WebhookEventMapper;
import org.fireflyframework.webhooks.core.services.WebhookBatchingService;
import org.fireflyframework.webhooks.core.services.WebhookCompressionService;
import org.fireflyframework.webhooks.core.services.WebhookProcessingService;
import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.fireflyframework.webhooks.interfaces.dto.WebhookResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of webhook processing service.
 * <p>
 * This service publishes webhook events directly to the message queue using lib-common-eda.
 * Workers consuming from the queue will handle signature validation and idempotency.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookProcessingServiceImpl implements WebhookProcessingService {

    private final EventPublisherFactory eventPublisherFactory;
    private final WebhookEventMapper webhookEventMapper;

    @Autowired(required = false)
    private WebhookBatchingService batchingService;

    @Autowired(required = false)
    private WebhookCompressionService compressionService;

    @Value("${firefly.webhooks.destination.prefix:}")
    private String destinationPrefix;

    @Value("${firefly.webhooks.destination.suffix:}")
    private String destinationSuffix;

    @Value("${firefly.webhooks.destination.use-provider-as-topic:true}")
    private boolean useProviderAsTopic;

    @Value("${firefly.webhooks.destination.custom:}")
    private String customDestination;

    @Value("${firefly.webhooks.batching.enabled:false}")
    private boolean batchingEnabled;

    @Value("${firefly.webhooks.compression.enabled:false}")
    private boolean compressionEnabled;

    @Override
    public Mono<WebhookResponseDTO> processWebhook(WebhookEventDTO eventDto) {
        log.info("Processing webhook from provider: {}", eventDto.getProviderName());

        // Convert DTO to domain event
        WebhookReceivedEvent domainEvent = webhookEventMapper.toDomainEvent(eventDto);

        // Apply compression if enabled
        if (compressionEnabled && compressionService != null) {
            byte[] compressed = compressionService.compress(domainEvent.getPayload());
            if (compressed != null) {
                domainEvent.setCompressedPayload(compressed);
                domainEvent.setCompressed(true);
                domainEvent.setPayload(null); // Clear original payload to save memory
                log.debug("Payload compressed for event: {}", domainEvent.getEventId());
            }
        }

        // Resolve destination for metadata
        String destination = resolveDestination(eventDto.getProviderName());

        // Build metadata for response
        Map<String, Object> metadata = buildResponseMetadata(eventDto, destination);

        // Publish directly to queue
        return publishToQueue(domainEvent)
                .then(Mono.just(WebhookResponseDTO.success(
                        eventDto.getEventId(),
                        eventDto.getProviderName(),
                        eventDto.getReceivedAt(),
                        eventDto.getPayload(),
                        metadata
                )))
                .doOnSuccess(response ->
                        log.info("Successfully processed webhook {} from provider: {}",
                                eventDto.getEventId(), eventDto.getProviderName()))
                .onErrorResume(error -> {
                    log.error("Error processing webhook from provider: {}",
                            eventDto.getProviderName(), error);
                    return Mono.just(WebhookResponseDTO.error(
                            eventDto.getEventId(),
                            eventDto.getProviderName(),
                            "Error processing webhook: " + error.getMessage(),
                            eventDto.getReceivedAt(),
                            eventDto.getPayload()
                    ));
                });
    }

    /**
     * Builds metadata for the webhook response.
     *
     * @param eventDto the webhook event DTO
     * @param destination the resolved destination topic/queue
     * @return metadata map
     */
    private Map<String, Object> buildResponseMetadata(WebhookEventDTO eventDto, String destination) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("destination", destination);
        metadata.put("sourceIp", eventDto.getSourceIp());
        metadata.put("httpMethod", eventDto.getHttpMethod());

        // Add payload size information
        if (eventDto.getPayload() != null) {
            metadata.put("payloadSize", eventDto.getPayload().toString().length());
        }

        // Add header count
        if (eventDto.getHeaders() != null) {
            metadata.put("headerCount", eventDto.getHeaders().size());
        }

        return metadata;
    }

    /**
     * Publishes the webhook event to the message queue.
     * <p>
     * The event is published AS-IS to preserve the original webhook payload
     * and metadata for downstream consumers.
     * <p>
     * Destination resolution strategy:
     * 1. If custom destination is set, use it
     * 2. If use-provider-as-topic is true (default), use provider name as topic
     * 3. Apply prefix and suffix if configured
     * <p>
     * Examples:
     * - Default: "stripe" (provider name only)
     * - With prefix: "webhooks.stripe" (prefix="webhooks.")
     * - With suffix: "stripe.received" (suffix=".received")
     * - Both: "webhooks.stripe.received" (prefix="webhooks.", suffix=".received")
     * - Custom: "custom.topic" (custom="custom.topic")
     *
     * @param event the webhook event
     * @return a Mono that completes when published
     */
    private Mono<Void> publishToQueue(WebhookReceivedEvent event) {
        // Resolve destination based on configuration
        String destination = resolveDestination(event.getProviderName());

        return Mono.deferContextual(ctx -> {
            // Build headers with metadata
            Map<String, Object> headers = new HashMap<>();
            headers.put("provider", event.getProviderName());
            headers.put("eventId", event.getEventId().toString());
            headers.put("receivedAt", event.getReceivedAt().toString());

            // Propagate distributed tracing context to Kafka headers
            // This allows end-to-end tracing from HTTP request -> Kafka -> Worker
            addTracingHeaders(headers, ctx);

            log.debug("Publishing webhook event to destination: {} with tracing headers", destination);

            // Use batching if enabled, otherwise publish directly
            if (batchingEnabled && batchingService != null) {
                log.debug("Adding event {} to batch for destination: {}", event.getEventId(), destination);
                return batchingService.addToBatch(event, destination, headers)
                        .doOnSuccess(v -> log.debug("Event {} added to batch successfully", event.getEventId()))
                        .doOnError(error -> log.error("Error adding event {} to batch", event.getEventId(), error));
            }

            // Direct publishing (original behavior)
            EventPublisher publisher = eventPublisherFactory.getDefaultPublisher();

            if (publisher == null) {
                log.error("No event publisher available");
                return Mono.error(new IllegalStateException("No event publisher available"));
            }

            // Publish the event AS-IS to the message queue
            return publisher.publish(event, destination, headers)
                    .doOnSuccess(v -> log.debug("Successfully published webhook event: {} with trace context",
                            event.getEventId()))
                    .doOnError(error -> log.error("Error publishing webhook event: {}",
                            event.getEventId(), error));
        });
    }

    /**
     * Adds distributed tracing headers to Kafka message headers.
     * <p>
     * Propagates B3 trace context (traceId, spanId) from the reactive context
     * to Kafka headers so that downstream consumers can continue the trace.
     *
     * @param headers the Kafka message headers
     * @param ctx the reactive context view
     */
    private void addTracingHeaders(Map<String, Object> headers, ContextView ctx) {
        // Try to get trace context from reactive context first
        String traceId = ctx.getOrDefault("traceId", null);
        String spanId = ctx.getOrDefault("spanId", null);
        String requestId = ctx.getOrDefault("requestId", null);

        // Fallback to MDC if not in reactive context
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }
        if (spanId == null) {
            spanId = MDC.get("spanId");
        }
        if (requestId == null) {
            requestId = MDC.get("requestId");
        }

        // Add B3 propagation headers to Kafka message
        if (traceId != null) {
            headers.put("X-B3-TraceId", traceId);
            log.debug("Propagating traceId to Kafka: {}", traceId);
        }
        if (spanId != null) {
            headers.put("X-B3-SpanId", spanId);
            log.debug("Propagating spanId to Kafka: {}", spanId);
        }
        if (requestId != null) {
            headers.put("X-Request-ID", requestId);
            log.debug("Propagating requestId to Kafka: {}", requestId);
        }
    }

    /**
     * Resolves the destination/topic name based on configuration.
     *
     * @param providerName the provider name
     * @return the resolved destination
     */
    private String resolveDestination(String providerName) {
        // If custom destination is set, use it directly
        if (customDestination != null && !customDestination.isEmpty()) {
            log.debug("Using custom destination: {}", customDestination);
            return customDestination;
        }

        // Build destination with provider name (default behavior)
        StringBuilder destination = new StringBuilder();

        // Add prefix if configured
        if (destinationPrefix != null && !destinationPrefix.isEmpty()) {
            destination.append(destinationPrefix);
        }

        // Add provider name if use-provider-as-topic is true (default)
        if (useProviderAsTopic) {
            destination.append(providerName);
        }

        // Add suffix if configured
        if (destinationSuffix != null && !destinationSuffix.isEmpty()) {
            destination.append(destinationSuffix);
        }

        String resolvedDestination = destination.toString();
        log.debug("Resolved destination for provider {}: {}", providerName, resolvedDestination);
        
        return resolvedDestination;
    }
}
