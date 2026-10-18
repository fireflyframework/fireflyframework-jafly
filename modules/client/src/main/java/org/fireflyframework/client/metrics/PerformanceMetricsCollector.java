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

package org.fireflyframework.client.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced performance metrics collector for ServiceClient.
 *
 * <p>This collector provides comprehensive performance metrics including:
 * <ul>
 *   <li>Request throughput (requests per second)</li>
 *   <li>Response time percentiles (p50, p95, p99)</li>
 *   <li>Concurrent request tracking</li>
 *   <li>Payload size metrics</li>
 *   <li>Connection pool metrics</li>
 *   <li>Retry and timeout metrics</li>
 *   <li>Cache hit/miss rates</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class PerformanceMetricsCollector extends FireflyMetricsSupport {

    private final Map<String, AtomicLong> concurrentRequestsMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastRequestTimeMap = new ConcurrentHashMap<>();
    private final Instant startTime;

    /**
     * Creates a new PerformanceMetricsCollector.
     *
     * @param meterRegistry the Micrometer meter registry
     */
    public PerformanceMetricsCollector(MeterRegistry meterRegistry) {
        super(meterRegistry, "client");
        this.startTime = Instant.now();
        log.info("Initialized PerformanceMetricsCollector");
    }

    /**
     * Records a completed request.
     *
     * @param serviceName the service name
     * @param method the HTTP method
     * @param endpoint the endpoint path
     * @param duration the request duration
     * @param statusCode the HTTP status code
     * @param responseSize the response size in bytes
     */
    public void recordRequest(String serviceName, String method, String endpoint,
                              Duration duration, int statusCode, long responseSize) {
        ensureServiceRegistered(serviceName);

        counter("performance.requests.total", "service", serviceName).increment();
        timer("performance.request.duration", "service", serviceName).record(duration);
        distributionSummary("performance.response.size", "service", serviceName).record(responseSize);

        if (statusCode >= 200 && statusCode < 300) {
            counter("performance.requests.success", "service", serviceName).increment();
        } else if (statusCode >= 400 && statusCode < 500) {
            counter("performance.requests.client.errors", "service", serviceName).increment();
        } else if (statusCode >= 500) {
            counter("performance.requests.server.errors", "service", serviceName).increment();
        }

        AtomicLong lastTime = lastRequestTimeMap.get(serviceName);
        if (lastTime != null) {
            lastTime.set(System.currentTimeMillis());
        }

        log.debug("Recorded request for service '{}': {} {} -> {} [{}ms, {}bytes]",
            serviceName, method, endpoint, statusCode, duration.toMillis(), responseSize);
    }

    /**
     * Records a request start (for tracking concurrent requests).
     *
     * @param serviceName the service name
     */
    public void recordRequestStart(String serviceName) {
        ensureServiceRegistered(serviceName);
        AtomicLong concurrent = concurrentRequestsMap.get(serviceName);
        if (concurrent != null) {
            concurrent.incrementAndGet();
        }
    }

    /**
     * Records a request end (for tracking concurrent requests).
     *
     * @param serviceName the service name
     */
    public void recordRequestEnd(String serviceName) {
        AtomicLong concurrent = concurrentRequestsMap.get(serviceName);
        if (concurrent != null) {
            concurrent.decrementAndGet();
        }
    }

    /**
     * Records a retry attempt.
     *
     * @param serviceName the service name
     * @param attemptNumber the retry attempt number
     */
    public void recordRetry(String serviceName, int attemptNumber) {
        counter("performance.retries", "service", serviceName).increment();
        log.debug("Recorded retry for service '{}': attempt {}", serviceName, attemptNumber);
    }

    /**
     * Records a timeout.
     *
     * @param serviceName the service name
     */
    public void recordTimeout(String serviceName) {
        counter("performance.timeouts", "service", serviceName).increment();
        log.debug("Recorded timeout for service '{}'", serviceName);
    }

    /**
     * Records a cache hit.
     *
     * @param serviceName the service name
     * @param cacheType the cache type (e.g., "query", "token")
     */
    public void recordCacheHit(String serviceName, String cacheType) {
        counter("performance.cache.hits", "service", serviceName, "cache.type", cacheType).increment();
    }

    /**
     * Records a cache miss.
     *
     * @param serviceName the service name
     * @param cacheType the cache type (e.g., "query", "token")
     */
    public void recordCacheMiss(String serviceName, String cacheType) {
        counter("performance.cache.misses", "service", serviceName, "cache.type", cacheType).increment();
    }

    /**
     * Records connection pool usage.
     *
     * @param serviceName the service name
     * @param activeConnections number of active connections
     * @param idleConnections number of idle connections
     * @param maxConnections maximum connections
     */
    public void recordConnectionPool(String serviceName, int activeConnections,
                                    int idleConnections, int maxConnections) {
        gauge("performance.connection.pool.active", () -> activeConnections, "service", serviceName);
        gauge("performance.connection.pool.idle", () -> idleConnections, "service", serviceName);
        gauge("performance.connection.pool.max", () -> maxConnections, "service", serviceName);
    }

    /**
     * Gets a performance metrics summary for a service.
     *
     * @param serviceName the service name
     * @return the metrics summary
     */
    public PerformanceMetricsSummary getSummary(String serviceName) {
        if (!concurrentRequestsMap.containsKey(serviceName)) {
            return null;
        }

        long totalRequests = (long) counter("performance.requests.total", "service", serviceName).count();
        long successfulRequests = (long) counter("performance.requests.success", "service", serviceName).count();
        long clientErrors = (long) counter("performance.requests.client.errors", "service", serviceName).count();
        long serverErrors = (long) counter("performance.requests.server.errors", "service", serviceName).count();
        long retries = (long) counter("performance.retries", "service", serviceName).count();
        long timeouts = (long) counter("performance.timeouts", "service", serviceName).count();

        double avgDuration = timer("performance.request.duration", "service", serviceName)
                .mean(TimeUnit.MILLISECONDS);
        double maxDuration = timer("performance.request.duration", "service", serviceName)
                .max(TimeUnit.MILLISECONDS);

        Duration uptime = Duration.between(startTime, Instant.now());
        double requestsPerSecond = uptime.getSeconds() > 0
                ? totalRequests / (double) uptime.getSeconds() : 0.0;

        return PerformanceMetricsSummary.builder()
            .serviceName(serviceName)
            .totalRequests(totalRequests)
            .successfulRequests(successfulRequests)
            .clientErrors(clientErrors)
            .serverErrors(serverErrors)
            .retries(retries)
            .timeouts(timeouts)
            .averageDurationMs(avgDuration)
            .maxDurationMs(maxDuration)
            .requestsPerSecond(requestsPerSecond)
            .successRate(totalRequests > 0 ? (double) successfulRequests / totalRequests * 100.0 : 0.0)
            .errorRate(totalRequests > 0 ? (double) (clientErrors + serverErrors) / totalRequests * 100.0 : 0.0)
            .build();
    }

    /**
     * Ensures gauge tracking is initialized for a service.
     */
    private void ensureServiceRegistered(String serviceName) {
        concurrentRequestsMap.computeIfAbsent(serviceName, name -> {
            AtomicLong concurrent = new AtomicLong(0);
            gauge("performance.concurrent.requests", concurrent, AtomicLong::get, "service", name);
            AtomicLong lastTime = new AtomicLong(System.currentTimeMillis());
            lastRequestTimeMap.put(name, lastTime);
            gauge("performance.last.request.time", lastTime, AtomicLong::get, "service", name);
            return concurrent;
        });
    }

    /**
     * Performance metrics summary.
     */
    @Data
    @Builder
    public static class PerformanceMetricsSummary {
        private final String serviceName;
        private final long totalRequests;
        private final long successfulRequests;
        private final long clientErrors;
        private final long serverErrors;
        private final long retries;
        private final long timeouts;
        private final double averageDurationMs;
        private final double maxDurationMs;
        private final double requestsPerSecond;
        private final double successRate;
        private final double errorRate;
    }
}
