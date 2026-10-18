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
import org.fireflyframework.webhooks.core.config.BatchingProperties;
import org.fireflyframework.webhooks.core.domain.events.WebhookReceivedEvent;
import org.fireflyframework.webhooks.core.services.WebhookBatchingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of webhook batching service.
 * <p>
 * Uses Project Reactor's Sinks and bufferTimeout to batch events efficiently.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firefly.webhooks.batching.enabled", havingValue = "true")
public class WebhookBatchingServiceImpl implements WebhookBatchingService {

    private final EventPublisherFactory eventPublisherFactory;
    private final BatchingProperties batchingProperties;

    // Sink per destination for batching
    private final Map<String, Sinks.Many<BatchedEvent>> sinks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> bufferSizes = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing WebhookBatchingService with maxBatchSize={}, maxWaitTime={}",
                batchingProperties.getMaxBatchSize(),
                batchingProperties.getMaxWaitTime());
    }

    @Override
    public Mono<Void> addToBatch(WebhookReceivedEvent event, String destination, Map<String, Object> headers) {
        return Mono.fromRunnable(() -> {
            Sinks.Many<BatchedEvent> sink = getOrCreateSink(destination);
            BatchedEvent batchedEvent = new BatchedEvent(event, destination, headers);

            Sinks.EmitResult result = sink.tryEmitNext(batchedEvent);

            if (result.isFailure()) {
                log.warn("Failed to add event to batch buffer for destination {}: {}", destination, result);
                // Fallback: publish directly
                publishDirectly(event, destination, headers).subscribe();
            } else {
                bufferSizes.computeIfAbsent(destination, k -> new AtomicInteger(0)).incrementAndGet();
                log.debug("Added event {} to batch buffer for destination {}", event.getEventId(), destination);
            }
        });
    }

    @Override
    public Mono<Void> flushAll() {
        log.info("Flushing all pending batches");
        return Flux.fromIterable(sinks.keySet())
                .flatMap(destination -> {
                    Sinks.Many<BatchedEvent> sink = sinks.get(destination);
                    if (sink != null) {
                        sink.tryEmitComplete();
                    }
                    return Mono.empty();
                })
                .then()
                .doOnSuccess(v -> log.info("All batches flushed successfully"));
    }

    @Override
    public int getBufferSize(String destination) {
        AtomicInteger size = bufferSizes.get(destination);
        return size != null ? size.get() : 0;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down WebhookBatchingService, flushing all pending batches");
        flushAll().block(Duration.ofSeconds(10));
    }

    /**
     * Gets or creates a sink for the given destination.
     */
    private Sinks.Many<BatchedEvent> getOrCreateSink(String destination) {
        return sinks.computeIfAbsent(destination, dest -> {
            Sinks.Many<BatchedEvent> sink = Sinks.many().multicast().onBackpressureBuffer(
                    batchingProperties.getBufferSize()
            );

            // Subscribe to the sink with batching logic
            sink.asFlux()
                    .bufferTimeout(
                            batchingProperties.getMaxBatchSize(),
                            batchingProperties.getMaxWaitTime()
                    )
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(batch -> publishBatch(batch, dest))
                    .subscribe(
                            v -> log.debug("Batch published successfully for destination: {}", dest),
                            error -> log.error("Error publishing batch for destination: {}", dest, error),
                            () -> log.info("Batch stream completed for destination: {}", dest)
                    );

            log.info("Created batching sink for destination: {}", dest);
            return sink;
        });
    }

    /**
     * Publishes a batch of events to Kafka.
     */
    private Mono<Void> publishBatch(List<BatchedEvent> batch, String destination) {
        if (batch.isEmpty()) {
            return Mono.empty();
        }

        log.info("Publishing batch of {} events to destination: {}", batch.size(), destination);

        EventPublisher publisher = eventPublisherFactory.getDefaultPublisher();
        if (publisher == null) {
            log.error("No event publisher available for batch publishing");
            return Mono.error(new IllegalStateException("No event publisher available"));
        }

        // Publish all events in the batch
        return Flux.fromIterable(batch)
                .flatMap(batchedEvent -> {
                    bufferSizes.get(destination).decrementAndGet();
                    return publisher.publish(
                            batchedEvent.event,
                            batchedEvent.destination,
                            batchedEvent.headers
                    );
                })
                .then()
                .doOnSuccess(v -> log.debug("Successfully published batch of {} events to {}",
                        batch.size(), destination))
                .doOnError(error -> log.error("Error publishing batch to {}", destination, error));
    }

    /**
     * Publishes an event directly (fallback when batching fails).
     */
    private Mono<Void> publishDirectly(WebhookReceivedEvent event, String destination, Map<String, Object> headers) {
        log.warn("Publishing event {} directly (batching failed)", event.getEventId());
        EventPublisher publisher = eventPublisherFactory.getDefaultPublisher();
        if (publisher == null) {
            return Mono.error(new IllegalStateException("No event publisher available"));
        }
        return publisher.publish(event, destination, headers);
    }

    /**
     * Internal class to hold batched event data.
     */
    private record BatchedEvent(
            WebhookReceivedEvent event,
            String destination,
            Map<String, Object> headers
    ) {
    }
}

