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

package org.fireflyframework.webhooks.processor.idempotency;

import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.webhooks.processor.port.WebhookIdempotencyService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Cache-based implementation of webhook idempotency service.
 * <p>
 * This implementation uses lib-common-cache (Redis or Caffeine) to ensure
 * webhook events are processed exactly once, even when multiple workers
 * are running in a cluster.
 * <p>
 * <b>Key Prefixes:</b>
 * <ul>
 *   <li>{@code webhook:processing:{eventId}} - Processing lock (short TTL)</li>
 *   <li>{@code webhook:processed:{eventId}} - Processed marker (long TTL)</li>
 *   <li>{@code webhook:failures:{eventId}} - Failure counter (medium TTL)</li>
 * </ul>
 * <p>
 * <b>Example Configuration:</b>
 * <pre>
 * firefly:
 *   cache:
 *     enabled: true
 *     default-cache-type: REDIS
 *     redis:
 *       enabled: true
 *       host: ${REDIS_HOST:localhost}
 *       port: ${REDIS_PORT:6379}
 *       key-prefix: "firefly:webhooks"
 * </pre>
 */
@Slf4j
public class CacheBasedWebhookIdempotencyService implements WebhookIdempotencyService {

    private final FireflyCacheManager cacheManager;

    // Key prefixes for different purposes
    private static final String PROCESSING_LOCK_PREFIX = "webhook:processing:";
    private static final String PROCESSED_PREFIX = "webhook:processed:";
    private static final String FAILURE_PREFIX = "webhook:failures:";

    // Lock value to store
    private static final String LOCK_VALUE = "locked";
    private static final String PROCESSED_VALUE = "processed";

    /**
     * Creates a cache-based idempotency service.
     *
     * @param cacheManager the Firefly cache manager (Redis or Caffeine)
     */
    public CacheBasedWebhookIdempotencyService(FireflyCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Mono<Boolean> tryAcquireProcessingLock(UUID eventId, Duration lockDuration) {
        String lockKey = PROCESSING_LOCK_PREFIX + eventId;

        log.debug("Attempting to acquire processing lock: eventId={}, duration={}", eventId, lockDuration);

        return cacheManager.putIfAbsent(lockKey, LOCK_VALUE, lockDuration)
                .doOnNext(acquired -> {
                    if (acquired) {
                        log.debug("Successfully acquired processing lock: eventId={}", eventId);
                    } else {
                        log.debug("Failed to acquire processing lock (already locked): eventId={}", eventId);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error acquiring processing lock for eventId={}: {}", eventId, error.getMessage(), error);
                    // On error, assume lock not acquired to be safe
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Boolean> isAlreadyProcessed(UUID eventId) {
        String processedKey = PROCESSED_PREFIX + eventId;

        return cacheManager.exists(processedKey)
                .doOnNext(exists -> {
                    if (exists) {
                        log.debug("Event already processed: eventId={}", eventId);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error checking if event is processed for eventId={}: {}",
                            eventId, error.getMessage(), error);
                    // On error, assume not processed to allow retry
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Void> markAsProcessed(UUID eventId, Duration ttl) {
        String processedKey = PROCESSED_PREFIX + eventId;

        log.debug("Marking event as processed: eventId={}, ttl={}", eventId, ttl);

        // Store processed marker with metadata
        ProcessedMetadata metadata = ProcessedMetadata.builder()
                .eventId(eventId)
                .processedAt(Instant.now())
                .build();

        return cacheManager.put(processedKey, metadata, ttl)
                .doOnSuccess(v -> log.debug("Event marked as processed: eventId={}", eventId))
                .doOnError(error -> log.error("Error marking event as processed for eventId={}: {}",
                        eventId, error.getMessage(), error));
    }

    @Override
    public Mono<Void> releaseProcessingLock(UUID eventId) {
        String lockKey = PROCESSING_LOCK_PREFIX + eventId;

        log.debug("Releasing processing lock: eventId={}", eventId);

        return cacheManager.evict(lockKey)
                .doOnNext(released -> {
                    if (released) {
                        log.debug("Successfully released processing lock: eventId={}", eventId);
                    } else {
                        log.debug("Processing lock not found (may have expired): eventId={}", eventId);
                    }
                })
                .then()
                .doOnError(error -> log.error("Error releasing processing lock for eventId={}: {}",
                        eventId, error.getMessage(), error));
    }

    @Override
    public Mono<Void> recordProcessingFailure(UUID eventId, Throwable error) {
        String failureKey = FAILURE_PREFIX + eventId;

        log.debug("Recording processing failure: eventId={}, error={}", eventId, error.getMessage());

        // Get current failure count and increment
        return cacheManager.<String, FailureMetadata>get(failureKey, FailureMetadata.class)
                .map(opt -> opt.orElse(FailureMetadata.builder()
                        .eventId(eventId)
                        .failureCount(0)
                        .firstFailureAt(Instant.now())
                        .build()))
                .flatMap(metadata -> {
                    // Increment failure count
                    metadata.setFailureCount(metadata.getFailureCount() + 1);
                    metadata.setLastFailureAt(Instant.now());
                    metadata.setLastError(error.getMessage());

                    // Store with 24 hour TTL
                    return cacheManager.put(failureKey, metadata, Duration.ofHours(24));
                })
                .doOnSuccess(v -> log.debug("Recorded processing failure: eventId={}", eventId))
                .doOnError(err -> log.error("Error recording processing failure for eventId={}: {}",
                        eventId, err.getMessage(), err));
    }

    /**
     * Gets the failure count for an event.
     * <p>
     * This can be used for monitoring and alerting.
     *
     * @param eventId the event ID
     * @return a Mono emitting the failure count
     */
    public Mono<Integer> getFailureCount(UUID eventId) {
        String failureKey = FAILURE_PREFIX + eventId;

        return cacheManager.<String, FailureMetadata>get(failureKey, FailureMetadata.class)
                .map(opt -> opt.map(FailureMetadata::getFailureCount).orElse(0))
                .onErrorReturn(0);
    }

    /**
     * Metadata stored when an event is marked as processed.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ProcessedMetadata {
        private UUID eventId;
        private Instant processedAt;
    }

    /**
     * Metadata stored when processing failures occur.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class FailureMetadata {
        private UUID eventId;
        private int failureCount;
        private Instant firstFailureAt;
        private Instant lastFailureAt;
        private String lastError;
    }
}

