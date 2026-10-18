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

package org.fireflyframework.client.deduplication;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Request deduplication manager for preventing duplicate operations.
 * 
 * <p>Features:
 * <ul>
 *   <li>Idempotency key generation and validation</li>
 *   <li>Request fingerprinting</li>
 *   <li>In-flight request tracking</li>
 *   <li>Automatic deduplication</li>
 *   <li>TTL-based cleanup</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * RequestDeduplicationManager dedup = new RequestDeduplicationManager(
 *     Duration.ofMinutes(5)
 * );
 *
 * // Generate idempotency key
 * String key = dedup.generateIdempotencyKey();
 *
 * // Execute with deduplication
 * Mono<Response> response = dedup.executeWithDeduplication(
 *     key,
 *     () -> performOperation()
 * );
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class RequestDeduplicationManager {

    private final Map<String, DeduplicationEntry> inFlightRequests = new ConcurrentHashMap<>();
    private final Map<String, DeduplicationEntry> completedRequests = new ConcurrentHashMap<>();
    private final Duration ttl;

    public RequestDeduplicationManager(Duration ttl) {
        this.ttl = ttl;
        log.info("Request Deduplication Manager initialized with TTL: {}", ttl);
    }

    /**
     * Generates a unique idempotency key.
     */
    public String generateIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a request fingerprint based on method, endpoint, and body.
     */
    public String generateFingerprint(String method, String endpoint, Object body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = method + ":" + endpoint + ":" + (body != null ? body.toString() : "");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Executes a request with deduplication.
     */
    public <T> Mono<T> executeWithDeduplication(String idempotencyKey, Mono<T> requestMono) {
        // Check if request is already completed
        DeduplicationEntry completed = completedRequests.get(idempotencyKey);
        if (completed != null && !completed.isExpired()) {
            log.debug("Request already completed, returning cached result for key: {}", idempotencyKey);
            @SuppressWarnings("unchecked")
            T result = (T) completed.getResult();
            return Mono.just(result);
        }

        // Check if request is in-flight
        DeduplicationEntry inFlight = inFlightRequests.get(idempotencyKey);
        if (inFlight != null) {
            log.debug("Request already in-flight, waiting for completion: {}", idempotencyKey);
            @SuppressWarnings("unchecked")
            Mono<T> existingMono = (Mono<T>) inFlight.getResultMono();
            return existingMono;
        }

        // Execute new request
        log.debug("Executing new request with idempotency key: {}", idempotencyKey);
        
        Mono<T> sharedMono = requestMono
            .doOnSuccess(result -> {
                // Move from in-flight to completed
                inFlightRequests.remove(idempotencyKey);
                completedRequests.put(idempotencyKey, new DeduplicationEntry(
                    idempotencyKey,
                    Instant.now(),
                    result,
                    null
                ));
                log.debug("Request completed successfully: {}", idempotencyKey);
            })
            .doOnError(error -> {
                // Remove from in-flight on error
                inFlightRequests.remove(idempotencyKey);
                log.debug("Request failed: {}", idempotencyKey);
            })
            .cache(); // Cache the result for concurrent subscribers

        // Add to in-flight
        inFlightRequests.put(idempotencyKey, new DeduplicationEntry(
            idempotencyKey,
            Instant.now(),
            null,
            sharedMono
        ));

        return sharedMono;
    }

    /**
     * Checks if a request is a duplicate.
     */
    public boolean isDuplicate(String idempotencyKey) {
        DeduplicationEntry completed = completedRequests.get(idempotencyKey);
        if (completed != null && !completed.isExpired()) {
            return true;
        }

        return inFlightRequests.containsKey(idempotencyKey);
    }

    /**
     * Gets the result of a completed request.
     */
    public <T> Optional<T> getCompletedResult(String idempotencyKey) {
        DeduplicationEntry entry = completedRequests.get(idempotencyKey);
        if (entry != null && !entry.isExpired()) {
            @SuppressWarnings("unchecked")
            T result = (T) entry.getResult();
            return Optional.ofNullable(result);
        }
        return Optional.empty();
    }

    /**
     * Cleans up expired entries.
     */
    public void cleanup() {
        int removedCompleted = 0;
        int removedInFlight = 0;

        // Clean completed requests
        for (Map.Entry<String, DeduplicationEntry> entry : completedRequests.entrySet()) {
            if (entry.getValue().isExpired()) {
                completedRequests.remove(entry.getKey());
                removedCompleted++;
            }
        }

        // Clean in-flight requests (shouldn't normally expire, but cleanup anyway)
        for (Map.Entry<String, DeduplicationEntry> entry : inFlightRequests.entrySet()) {
            if (entry.getValue().isExpired()) {
                inFlightRequests.remove(entry.getKey());
                removedInFlight++;
            }
        }

        if (removedCompleted > 0 || removedInFlight > 0) {
            log.debug("Cleaned up {} completed and {} in-flight deduplication entries", 
                removedCompleted, removedInFlight);
        }
    }

    /**
     * Gets deduplication statistics.
     */
    public DeduplicationStatistics getStatistics() {
        return new DeduplicationStatistics(
            inFlightRequests.size(),
            completedRequests.size()
        );
    }

    /**
     * Clears all deduplication data.
     */
    public void clear() {
        inFlightRequests.clear();
        completedRequests.clear();
        log.info("Cleared all deduplication data");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Represents a deduplication entry.
     */
    private class DeduplicationEntry {
        private final String key;
        private final Instant createdAt;
        private final Object result;
        private final Mono<?> resultMono;

        public DeduplicationEntry(String key, Instant createdAt, Object result, Mono<?> resultMono) {
            this.key = key;
            this.createdAt = createdAt;
            this.result = result;
            this.resultMono = resultMono;
        }

        public Object getResult() { return result; }
        public Mono<?> getResultMono() { return resultMono; }

        public boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(ttl));
        }
    }

    /**
     * Deduplication statistics.
     */
    public record DeduplicationStatistics(
        int inFlightRequests,
        int completedRequests
    ) {
        public int getTotalTracked() {
            return inFlightRequests + completedRequests;
        }
    }
}

