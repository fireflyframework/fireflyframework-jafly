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

package org.fireflyframework.cqrs.authorization;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection for authorization operations.
 *
 * <p>This class provides comprehensive metrics for monitoring authorization
 * performance, success rates, and failure patterns in production environments.
 *
 * <p>Metrics collected include:
 * <ul>
 *   <li>Authorization attempt counts (success/failure)</li>
 *   <li>Authorization timing metrics</li>
 *   <li>Custom authorization metrics</li>
 *   <li>Cache hit/miss rates</li>
 *   <li>Error categorization</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class AuthorizationMetrics extends FireflyMetricsSupport {

    private final AtomicLong activeAuthorizationRequests = new AtomicLong(0);
    private final AtomicLong cacheSize = new AtomicLong(0);

    public AuthorizationMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "cqrs");

        gauge("authorization.active.requests", activeAuthorizationRequests, AtomicLong::get);
        gauge("authorization.cache.size", cacheSize, AtomicLong::get);
    }

    /**
     * Records an authorization attempt.
     *
     * @param commandType the type of command being authorized
     * @param userId the user ID making the request
     */
    public void recordAuthorizationAttempt(String commandType, String userId) {
        counter("authorization.attempts").increment();
        activeAuthorizationRequests.incrementAndGet();

        log.debug("Authorization attempt recorded - Command: {}, User: {}", commandType, userId);
    }

    /**
     * Records a successful authorization.
     *
     * @param commandType the type of command that was authorized
     * @param duration the time taken for authorization
     * @param authType the type of authorization (custom)
     */
    public void recordAuthorizationSuccess(String commandType, Duration duration, String authType) {
        counter("authorization.successes").increment();
        timer("authorization.duration").record(duration);
        activeAuthorizationRequests.decrementAndGet();

        counter("authorization.custom.successes").increment();
        timer("authorization.custom.duration").record(duration);

        log.debug("Authorization success recorded - Command: {}, Duration: {}ms, Type: {}",
            commandType, duration.toMillis(), authType);
    }

    /**
     * Records a failed authorization.
     *
     * @param commandType the type of command that failed authorization
     * @param duration the time taken before failure
     * @param authType the type of authorization that failed
     * @param reason the reason for failure
     */
    public void recordAuthorizationFailure(String commandType, Duration duration, String authType, String reason) {
        counter("authorization.failures").increment();
        timer("authorization.duration").record(duration);
        activeAuthorizationRequests.decrementAndGet();

        counter("authorization.custom.failures").increment();
        timer("authorization.custom.duration").record(duration);

        counter("authorization.failures.by.reason",
                "reason", sanitizeReason(reason),
                "command.type", commandType,
                "auth.type", authType)
                .increment();

        log.debug("Authorization failure recorded - Command: {}, Duration: {}ms, Type: {}, Reason: {}",
            commandType, duration.toMillis(), authType, reason);
    }

    /**
     * Records a cache hit for authorization results.
     *
     * @param commandType the type of command
     * @param cacheKey the cache key that was hit
     */
    public void recordCacheHit(String commandType, String cacheKey) {
        counter("authorization.cache.hits").increment();
        counter("authorization.cache.hits.by.command", "command.type", commandType).increment();

        log.debug("Authorization cache hit - Command: {}, Key: {}", commandType, cacheKey);
    }

    /**
     * Records a cache miss for authorization results.
     *
     * @param commandType the type of command
     * @param cacheKey the cache key that was missed
     */
    public void recordCacheMiss(String commandType, String cacheKey) {
        counter("authorization.cache.misses").increment();
        counter("authorization.cache.misses.by.command", "command.type", commandType).increment();

        log.debug("Authorization cache miss - Command: {}, Key: {}", commandType, cacheKey);
    }

    /**
     * Updates the current cache size.
     *
     * @param size the current number of entries in the authorization cache
     */
    public void updateCacheSize(long size) {
        cacheSize.set(size);
    }

    /**
     * Records custom authorization specific timing.
     *
     * @param duration the time taken for custom authorization operation
     */
    public void recordCustomAuthTiming(Duration duration) {
        timer("authorization.custom.duration").record(duration);
    }

    /**
     * Gets the current authorization success rate.
     *
     * @return success rate as a percentage (0.0 to 100.0)
     */
    public double getSuccessRate() {
        double total = counter("authorization.attempts").count();
        if (total == 0) {
            return 100.0;
        }
        return (counter("authorization.successes").count() / total) * 100.0;
    }

    /**
     * Gets the current cache hit rate.
     *
     * @return cache hit rate as a percentage (0.0 to 100.0)
     */
    public double getCacheHitRate() {
        double totalCacheRequests = counter("authorization.cache.hits").count()
                + counter("authorization.cache.misses").count();
        if (totalCacheRequests == 0) {
            return 0.0;
        }
        return (counter("authorization.cache.hits").count() / totalCacheRequests) * 100.0;
    }

    /**
     * Sanitizes failure reasons for use as metric tags.
     *
     * @param reason the raw failure reason
     * @return sanitized reason suitable for metric tags
     */
    private String sanitizeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "unknown";
        }

        return reason.toLowerCase()
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_{2,}", "_")
            .replaceAll("^_|_$", "");
    }
}
