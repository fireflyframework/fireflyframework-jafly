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

package org.fireflyframework.webhooks.processor.port;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Port interface for webhook idempotency/deduplication service.
 * <p>
 * This service ensures that webhook events are processed exactly once,
 * even when multiple workers are running in a cluster (autoscaling).
 * <p>
 * <b>Use Cases:</b>
 * <ul>
 *   <li>Prevent duplicate processing when Kafka rebalances consumer groups</li>
 *   <li>Handle at-least-once delivery semantics from message queue</li>
 *   <li>Ensure idempotency during worker autoscaling</li>
 *   <li>Prevent race conditions when multiple workers pick the same event</li>
 * </ul>
 * <p>
 * <b>Implementation Strategies:</b>
 * <ul>
 *   <li><b>Redis-based:</b> Use Redis SET NX with TTL for distributed locking</li>
 *   <li><b>Database-based:</b> Use unique constraints on event_id with processed_at timestamp</li>
 *   <li><b>Event Store-based:</b> Query event store for processing status</li>
 * </ul>
 * <p>
 * <b>Example Implementation (Redis):</b>
 * <pre>
 * {@code
 * @Component
 * public class RedisWebhookIdempotencyService implements WebhookIdempotencyService {
 *     
 *     private final ReactiveRedisTemplate<String, String> redisTemplate;
 *     
 *     @Override
 *     public Mono<Boolean> tryAcquireProcessingLock(UUID eventId, Duration lockDuration) {
 *         String key = "webhook:processing:" + eventId;
 *         return redisTemplate.opsForValue()
 *             .setIfAbsent(key, "locked", lockDuration)
 *             .defaultIfEmpty(false);
 *     }
 *     
 *     @Override
 *     public Mono<Void> markAsProcessed(UUID eventId, Duration ttl) {
 *         String key = "webhook:processed:" + eventId;
 *         return redisTemplate.opsForValue()
 *             .set(key, "processed", ttl)
 *             .then();
 *     }
 * }
 * }
 * </pre>
 */
public interface WebhookIdempotencyService {

    /**
     * Attempts to acquire a processing lock for the given event.
     * <p>
     * This method should atomically check if the event is already being processed
     * and acquire a lock if not. This prevents multiple workers from processing
     * the same event concurrently.
     * <p>
     * The lock should automatically expire after the specified duration to handle
     * worker crashes.
     *
     * @param eventId the unique event identifier
     * @param lockDuration how long the lock should be held (e.g., 5 minutes)
     * @return a Mono emitting true if lock was acquired, false if already locked
     */
    Mono<Boolean> tryAcquireProcessingLock(UUID eventId, Duration lockDuration);

    /**
     * Checks if an event has already been processed.
     * <p>
     * This is a fast check to skip events that have already been successfully
     * processed, even if the lock has expired.
     *
     * @param eventId the unique event identifier
     * @return a Mono emitting true if already processed, false otherwise
     */
    Mono<Boolean> isAlreadyProcessed(UUID eventId);

    /**
     * Marks an event as successfully processed.
     * <p>
     * This should be called after successful processing to prevent reprocessing.
     * The record should be kept for a reasonable TTL (e.g., 7 days) to handle
     * late duplicates.
     *
     * @param eventId the unique event identifier
     * @param ttl how long to keep the processed record (e.g., 7 days)
     * @return a Mono that completes when marked
     */
    Mono<Void> markAsProcessed(UUID eventId, Duration ttl);

    /**
     * Releases the processing lock for an event.
     * <p>
     * This should be called if processing fails and the event should be
     * retried by another worker.
     *
     * @param eventId the unique event identifier
     * @return a Mono that completes when lock is released
     */
    Mono<Void> releaseProcessingLock(UUID eventId);

    /**
     * Records a processing failure for an event.
     * <p>
     * This can be used for monitoring and alerting on events that
     * repeatedly fail processing.
     *
     * @param eventId the unique event identifier
     * @param error the error that occurred
     * @return a Mono that completes when recorded
     */
    default Mono<Void> recordProcessingFailure(UUID eventId, Throwable error) {
        // Default implementation does nothing
        return Mono.empty();
    }
}

