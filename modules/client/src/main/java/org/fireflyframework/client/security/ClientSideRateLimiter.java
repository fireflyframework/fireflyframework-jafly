package org.fireflyframework.client.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side rate limiter to prevent overwhelming downstream services.
 * 
 * <p>This rate limiter implements multiple strategies:
 * <ul>
 *   <li>Token bucket algorithm for smooth rate limiting</li>
 *   <li>Fixed window rate limiting</li>
 *   <li>Sliding window rate limiting</li>
 *   <li>Concurrent request limiting</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Create rate limiter (100 requests per minute)
 * ClientSideRateLimiter rateLimiter = ClientSideRateLimiter.builder()
 *     .serviceName("payment-service")
 *     .maxRequestsPerSecond(100.0 / 60.0)  // ~1.67 RPS
 *     .maxConcurrentRequests(10)
 *     .build();
 * 
 * // Acquire permit before making request
 * if (rateLimiter.tryAcquire()) {
 *     try {
 *         // Make API call
 *         makeApiCall();
 *     } finally {
 *         rateLimiter.release();
 *     }
 * } else {
 *     // Rate limit exceeded
 *     throw new RateLimitExceededException();
 * }
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class ClientSideRateLimiter {

    /**
     * Service name for logging and identification.
     */
    @Getter
    private final String serviceName;

    /**
     * Maximum requests per second.
     */
    private final double maxRequestsPerSecond;

    /**
     * Maximum concurrent requests.
     */
    private final int maxConcurrentRequests;

    /**
     * Rate limiting strategy.
     */
    private final RateLimitStrategy strategy;

    /**
     * Whether to enable rate limiting.
     */
    private final boolean enabled;

    /**
     * Timeout for acquiring permit.
     */
    private final Duration acquireTimeout;

    /**
     * Token bucket: available tokens.
     */
    private volatile double availableTokens;

    /**
     * Token bucket: last refill time.
     */
    private volatile Instant lastRefillTime;

    /**
     * Fixed window: request count in current window.
     */
    private final AtomicLong windowRequestCount = new AtomicLong(0);

    /**
     * Fixed window: current window start time.
     */
    private volatile Instant windowStartTime;

    /**
     * Sliding window: request timestamps.
     */
    private final Map<Long, Instant> requestTimestamps = new ConcurrentHashMap<>();

    /**
     * Concurrent request semaphore.
     */
    private final Semaphore concurrentRequestSemaphore;

    /**
     * Request counter for sliding window.
     */
    private final AtomicLong requestCounter = new AtomicLong(0);

    /**
     * Statistics: total requests.
     */
    private final AtomicLong totalRequests = new AtomicLong(0);

    /**
     * Statistics: rejected requests.
     */
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    /**
     * Rate limiting strategy enum.
     */
    public enum RateLimitStrategy {
        TOKEN_BUCKET,
        FIXED_WINDOW,
        SLIDING_WINDOW
    }

    /**
     * Private constructor for builder.
     */
    private ClientSideRateLimiter(Builder builder) {
        this.serviceName = builder.serviceName;
        this.maxRequestsPerSecond = builder.maxRequestsPerSecond;
        this.maxConcurrentRequests = builder.maxConcurrentRequests;
        this.strategy = builder.strategy;
        this.enabled = builder.enabled;
        this.acquireTimeout = builder.acquireTimeout;
        this.availableTokens = maxRequestsPerSecond;
        this.lastRefillTime = Instant.now();
        this.windowStartTime = Instant.now();
        this.concurrentRequestSemaphore = new Semaphore(maxConcurrentRequests, true);
        log.info("Created rate limiter for service '{}': {} RPS, {} concurrent, strategy: {}",
            serviceName, maxRequestsPerSecond, maxConcurrentRequests, strategy);
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ClientSideRateLimiter.
     */
    public static class Builder {
        private String serviceName;
        private double maxRequestsPerSecond = 10.0;
        private int maxConcurrentRequests = 100;
        private RateLimitStrategy strategy = RateLimitStrategy.TOKEN_BUCKET;
        private boolean enabled = true;
        private Duration acquireTimeout = Duration.ofSeconds(5);

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder maxRequestsPerSecond(double maxRequestsPerSecond) {
            this.maxRequestsPerSecond = maxRequestsPerSecond;
            return this;
        }

        public Builder maxConcurrentRequests(int maxConcurrentRequests) {
            this.maxConcurrentRequests = maxConcurrentRequests;
            return this;
        }

        public Builder strategy(RateLimitStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder acquireTimeout(Duration acquireTimeout) {
            this.acquireTimeout = acquireTimeout;
            return this;
        }

        public ClientSideRateLimiter build() {
            return new ClientSideRateLimiter(this);
        }
    }

    /**
     * Tries to acquire a permit to make a request.
     *
     * @return true if permit acquired, false if rate limit exceeded
     */
    public boolean tryAcquire() {
        if (!enabled) {
            return true;
        }

        totalRequests.incrementAndGet();

        // Check concurrent request limit
        if (!concurrentRequestSemaphore.tryAcquire()) {
            rejectedRequests.incrementAndGet();
            log.warn("Concurrent request limit exceeded for service: {}", serviceName);
            return false;
        }

        // Check rate limit based on strategy
        boolean acquired = switch (strategy) {
            case TOKEN_BUCKET -> tryAcquireTokenBucket();
            case FIXED_WINDOW -> tryAcquireFixedWindow();
            case SLIDING_WINDOW -> tryAcquireSlidingWindow();
        };

        if (!acquired) {
            concurrentRequestSemaphore.release();
            rejectedRequests.incrementAndGet();
            log.warn("Rate limit exceeded for service: {} (strategy: {})", serviceName, strategy);
        }

        return acquired;
    }

    /**
     * Tries to acquire a permit with timeout.
     *
     * @param timeout the timeout duration
     * @return true if permit acquired, false if timeout or rate limit exceeded
     */
    public boolean tryAcquire(Duration timeout) {
        if (!enabled) {
            return true;
        }

        try {
            if (!concurrentRequestSemaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                rejectedRequests.incrementAndGet();
                return false;
            }

            boolean acquired = tryAcquire();
            if (!acquired) {
                concurrentRequestSemaphore.release();
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rejectedRequests.incrementAndGet();
            return false;
        }
    }

    /**
     * Releases a permit after request completion.
     */
    public void release() {
        if (!enabled) {
            return;
        }
        concurrentRequestSemaphore.release();
    }

    /**
     * Token bucket algorithm implementation.
     *
     * @return true if token acquired, false otherwise
     */
    private synchronized boolean tryAcquireTokenBucket() {
        Instant now = Instant.now();
        
        // Refill tokens based on time elapsed
        Duration elapsed = Duration.between(lastRefillTime, now);
        double tokensToAdd = elapsed.toMillis() / 1000.0 * maxRequestsPerSecond;
        availableTokens = Math.min(maxRequestsPerSecond, availableTokens + tokensToAdd);
        lastRefillTime = now;

        // Try to consume a token
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }

        return false;
    }

    /**
     * Fixed window algorithm implementation.
     *
     * @return true if request allowed, false otherwise
     */
    private synchronized boolean tryAcquireFixedWindow() {
        Instant now = Instant.now();
        Duration windowDuration = Duration.ofSeconds(1);

        // Check if we need to reset the window
        if (Duration.between(windowStartTime, now).compareTo(windowDuration) >= 0) {
            windowStartTime = now;
            windowRequestCount.set(0);
        }

        // Check if we're within the limit
        long count = windowRequestCount.incrementAndGet();
        return count <= maxRequestsPerSecond;
    }

    /**
     * Sliding window algorithm implementation.
     *
     * @return true if request allowed, false otherwise
     */
    private synchronized boolean tryAcquireSlidingWindow() {
        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofSeconds(1));

        // Remove old timestamps
        requestTimestamps.entrySet().removeIf(entry -> entry.getValue().isBefore(windowStart));

        // Check if we're within the limit
        if (requestTimestamps.size() < maxRequestsPerSecond) {
            long requestId = requestCounter.incrementAndGet();
            requestTimestamps.put(requestId, now);
            return true;
        }

        return false;
    }

    /**
     * Gets the current rate limit utilization (0.0 to 1.0).
     *
     * @return utilization percentage
     */
    public double getUtilization() {
        return switch (strategy) {
            case TOKEN_BUCKET -> 1.0 - (availableTokens / maxRequestsPerSecond);
            case FIXED_WINDOW -> windowRequestCount.get() / maxRequestsPerSecond;
            case SLIDING_WINDOW -> requestTimestamps.size() / maxRequestsPerSecond;
        };
    }

    /**
     * Gets statistics about rate limiting.
     *
     * @return rate limiter statistics
     */
    public RateLimiterStats getStats() {
        return new RateLimiterStats(
            totalRequests.get(),
            rejectedRequests.get(),
            concurrentRequestSemaphore.availablePermits(),
            getUtilization()
        );
    }

    /**
     * Resets the rate limiter state.
     */
    public synchronized void reset() {
        availableTokens = maxRequestsPerSecond;
        lastRefillTime = Instant.now();
        windowRequestCount.set(0);
        windowStartTime = Instant.now();
        requestTimestamps.clear();
        totalRequests.set(0);
        rejectedRequests.set(0);
        log.info("Rate limiter reset for service: {}", serviceName);
    }

    /**
     * Rate limiter statistics.
     */
    public record RateLimiterStats(
        long totalRequests,
        long rejectedRequests,
        int availableConcurrentSlots,
        double utilization
    ) {}
}

