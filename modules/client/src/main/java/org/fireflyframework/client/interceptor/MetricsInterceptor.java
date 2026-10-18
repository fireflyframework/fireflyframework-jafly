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

package org.fireflyframework.client.interceptor;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics collection interceptor for ServiceClient operations.
 * 
 * <p>This interceptor collects comprehensive metrics about service client operations
 * including request counts, response times, error rates, and success rates.
 * The metrics can be exported to monitoring systems like Prometheus, Micrometer, etc.
 *
 * <p>Collected metrics include:
 * <ul>
 *   <li>Request count by service, endpoint, and status</li>
 *   <li>Response time histograms</li>
 *   <li>Error rates and types</li>
 *   <li>Circuit breaker state changes</li>
 *   <li>Retry attempt counts</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class MetricsInterceptor implements ServiceClientInterceptor {

    private final MetricsCollector metricsCollector;
    private final boolean collectDetailedMetrics;
    private final boolean collectHistograms;

    public MetricsInterceptor(MetricsCollector metricsCollector, boolean collectDetailedMetrics, boolean collectHistograms) {
        this.metricsCollector = metricsCollector;
        this.collectDetailedMetrics = collectDetailedMetrics;
        this.collectHistograms = collectHistograms;
    }

    @Override
    public Mono<InterceptorResponse> intercept(InterceptorRequest request, InterceptorChain chain) {
        long startTime = System.nanoTime();
        String serviceName = request.getServiceName();
        String endpoint = request.getEndpoint();
        String method = request.getMethod();
        String clientType = request.getClientType();

        // Increment request counter
        metricsCollector.incrementRequestCount(serviceName, endpoint, method, clientType);

        return chain.proceed(request)
            .doOnSuccess(response -> {
                long duration = System.nanoTime() - startTime;
                recordSuccessMetrics(serviceName, endpoint, method, clientType, response, duration);
            })
            .doOnError(error -> {
                long duration = System.nanoTime() - startTime;
                recordErrorMetrics(serviceName, endpoint, method, clientType, error, duration);
            });
    }

    @Override
    public int getOrder() {
        return 50; // Execute in the middle of the chain
    }

    private void recordSuccessMetrics(String serviceName, String endpoint, String method, 
                                    String clientType, InterceptorResponse response, long durationNanos) {
        Duration duration = Duration.ofNanos(durationNanos);
        
        // Record response time
        metricsCollector.recordResponseTime(serviceName, endpoint, method, clientType, duration);
        
        // Record success count
        metricsCollector.incrementSuccessCount(serviceName, endpoint, method, clientType, response.getStatusCode());
        
        if (collectHistograms) {
            metricsCollector.recordResponseTimeHistogram(serviceName, clientType, duration);
        }
        
        if (collectDetailedMetrics) {
            metricsCollector.recordDetailedMetrics(serviceName, endpoint, method, clientType, 
                response.getStatusCode(), duration, null);
        }
    }

    private void recordErrorMetrics(String serviceName, String endpoint, String method, 
                                  String clientType, Throwable error, long durationNanos) {
        Duration duration = Duration.ofNanos(durationNanos);
        
        // Record error count
        metricsCollector.incrementErrorCount(serviceName, endpoint, method, clientType, error.getClass().getSimpleName());
        
        // Record response time even for errors
        metricsCollector.recordResponseTime(serviceName, endpoint, method, clientType, duration);
        
        if (collectHistograms) {
            metricsCollector.recordResponseTimeHistogram(serviceName, clientType, duration);
        }
        
        if (collectDetailedMetrics) {
            metricsCollector.recordDetailedMetrics(serviceName, endpoint, method, clientType, 
                -1, duration, error);
        }
    }

    /**
     * Interface for metrics collection backend.
     */
    public interface MetricsCollector {
        void incrementRequestCount(String serviceName, String endpoint, String method, String clientType);
        void incrementSuccessCount(String serviceName, String endpoint, String method, String clientType, int statusCode);
        void incrementErrorCount(String serviceName, String endpoint, String method, String clientType, String errorType);
        void recordResponseTime(String serviceName, String endpoint, String method, String clientType, Duration duration);
        void recordResponseTimeHistogram(String serviceName, String clientType, Duration duration);
        void recordDetailedMetrics(String serviceName, String endpoint, String method, String clientType, 
                                 int statusCode, Duration duration, Throwable error);
    }

    /**
     * Simple in-memory metrics collector implementation.
     */
    public static class InMemoryMetricsCollector implements MetricsCollector {
        
        private final Map<String, LongAdder> requestCounts = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> successCounts = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> errorCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> totalResponseTimes = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> maxResponseTimes = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> minResponseTimes = new ConcurrentHashMap<>();

        @Override
        public void incrementRequestCount(String serviceName, String endpoint, String method, String clientType) {
            String key = buildKey(serviceName, endpoint, method, clientType);
            requestCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        }

        @Override
        public void incrementSuccessCount(String serviceName, String endpoint, String method, String clientType, int statusCode) {
            String key = buildKey(serviceName, endpoint, method, clientType, String.valueOf(statusCode));
            successCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        }

        @Override
        public void incrementErrorCount(String serviceName, String endpoint, String method, String clientType, String errorType) {
            String key = buildKey(serviceName, endpoint, method, clientType, errorType);
            errorCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        }

        @Override
        public void recordResponseTime(String serviceName, String endpoint, String method, String clientType, Duration duration) {
            String key = buildKey(serviceName, endpoint, method, clientType);
            long durationMs = duration.toMillis();
            
            totalResponseTimes.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(durationMs);
            maxResponseTimes.computeIfAbsent(key, k -> new AtomicLong()).updateAndGet(current -> Math.max(current, durationMs));
            minResponseTimes.computeIfAbsent(key, k -> new AtomicLong(Long.MAX_VALUE)).updateAndGet(current -> Math.min(current, durationMs));
        }

        @Override
        public void recordResponseTimeHistogram(String serviceName, String clientType, Duration duration) {
            // Simple histogram implementation - in practice, you'd use a proper histogram
            String bucket = getTimeBucket(duration);
            String key = buildKey(serviceName, clientType, "histogram", bucket);
            requestCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        }

        @Override
        public void recordDetailedMetrics(String serviceName, String endpoint, String method, String clientType, 
                                        int statusCode, Duration duration, Throwable error) {
            // Record additional detailed metrics
            log.debug("Detailed metrics: service={}, endpoint={}, method={}, clientType={}, status={}, duration={}ms, error={}", 
                serviceName, endpoint, method, clientType, statusCode, duration.toMillis(), 
                error != null ? error.getClass().getSimpleName() : "none");
        }

        private String buildKey(String... parts) {
            return String.join(".", parts);
        }

        private String getTimeBucket(Duration duration) {
            long ms = duration.toMillis();
            if (ms < 10) return "0-10ms";
            if (ms < 50) return "10-50ms";
            if (ms < 100) return "50-100ms";
            if (ms < 500) return "100-500ms";
            if (ms < 1000) return "500ms-1s";
            if (ms < 5000) return "1s-5s";
            if (ms < 10000) return "5s-10s";
            return "10s+";
        }

        /**
         * Gets current metrics snapshot.
         */
        public MetricsSnapshot getSnapshot() {
            return new MetricsSnapshot(
                copyMap(requestCounts),
                copyMap(successCounts),
                copyMap(errorCounts),
                copyAtomicMap(totalResponseTimes),
                copyAtomicMap(maxResponseTimes),
                copyAtomicMap(minResponseTimes)
            );
        }

        private Map<String, Long> copyMap(Map<String, LongAdder> source) {
            return source.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().sum()
                ));
        }

        private Map<String, Long> copyAtomicMap(Map<String, AtomicLong> source) {
            return source.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().get()
                ));
        }
    }

    /**
     * Immutable snapshot of metrics data.
     */
    public static class MetricsSnapshot {
        private final Map<String, Long> requestCounts;
        private final Map<String, Long> successCounts;
        private final Map<String, Long> errorCounts;
        private final Map<String, Long> totalResponseTimes;
        private final Map<String, Long> maxResponseTimes;
        private final Map<String, Long> minResponseTimes;

        public MetricsSnapshot(Map<String, Long> requestCounts, Map<String, Long> successCounts, 
                             Map<String, Long> errorCounts, Map<String, Long> totalResponseTimes,
                             Map<String, Long> maxResponseTimes, Map<String, Long> minResponseTimes) {
            this.requestCounts = Map.copyOf(requestCounts);
            this.successCounts = Map.copyOf(successCounts);
            this.errorCounts = Map.copyOf(errorCounts);
            this.totalResponseTimes = Map.copyOf(totalResponseTimes);
            this.maxResponseTimes = Map.copyOf(maxResponseTimes);
            this.minResponseTimes = Map.copyOf(minResponseTimes);
        }

        public Map<String, Long> getRequestCounts() { return requestCounts; }
        public Map<String, Long> getSuccessCounts() { return successCounts; }
        public Map<String, Long> getErrorCounts() { return errorCounts; }
        public Map<String, Long> getTotalResponseTimes() { return totalResponseTimes; }
        public Map<String, Long> getMaxResponseTimes() { return maxResponseTimes; }
        public Map<String, Long> getMinResponseTimes() { return minResponseTimes; }
    }
}
