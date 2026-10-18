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

package org.fireflyframework.client.resilience;

import org.fireflyframework.client.exception.BulkheadFullException;
import org.fireflyframework.client.exception.LoadSheddingException;
import org.fireflyframework.client.exception.RateLimitExceededException;
import org.fireflyframework.resilience.CircuitBreakerManager;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.management.*;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Advanced resilience manager providing bulkhead isolation, rate limiting, and adaptive patterns.
 * 
 * <p>This manager implements advanced resilience patterns beyond basic circuit breakers and retries:
 * <ul>
 *   <li>Bulkhead isolation to prevent resource exhaustion</li>
 *   <li>Rate limiting to control request throughput</li>
 *   <li>Adaptive timeout based on historical performance</li>
 *   <li>Load shedding under high load conditions</li>
 *   <li>Graceful degradation strategies</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class AdvancedResilienceManager {

    private final Map<String, BulkheadIsolation> bulkheads = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final Map<String, AdaptiveTimeout> adaptiveTimeouts = new ConcurrentHashMap<>();
    private final LoadSheddingStrategy loadSheddingStrategy;
    private final CircuitBreakerManager circuitBreakerManager;

    public AdvancedResilienceManager(LoadSheddingStrategy loadSheddingStrategy, CircuitBreakerManager circuitBreakerManager) {
        this.loadSheddingStrategy = loadSheddingStrategy;
        this.circuitBreakerManager = circuitBreakerManager;
    }

    public AdvancedResilienceManager(LoadSheddingStrategy loadSheddingStrategy) {
        this.loadSheddingStrategy = loadSheddingStrategy;
        this.circuitBreakerManager = null; // For backward compatibility
    }

    /**
     * Applies resilience patterns to a service operation.
     */
    public <T> Mono<T> applyResilience(String serviceName, Mono<T> operation, ResilienceConfig config) {
        return Mono.defer(() -> {
            // Check load shedding first
            if (loadSheddingStrategy.shouldShedLoad(serviceName)) {
                return Mono.error(new LoadSheddingException("Request shed due to high load for service: " + serviceName));
            }

            // Apply rate limiting
            RateLimiter rateLimiter = getRateLimiter(serviceName, config);
            if (!rateLimiter.tryAcquire()) {
                return Mono.error(new RateLimitExceededException("Rate limit exceeded for service: " + serviceName));
            }

            // Apply bulkhead isolation
            BulkheadIsolation bulkhead = getBulkhead(serviceName, config);

            return bulkhead.execute(() -> {
                // Apply adaptive timeout
                AdaptiveTimeout adaptiveTimeout = getAdaptiveTimeout(serviceName, config);
                Duration timeout = adaptiveTimeout.calculateTimeout();

                long startTime = System.nanoTime();

                Mono<T> resilientOperation = operation
                    .timeout(timeout)
                    .doOnSuccess(result -> {
                        long duration = System.nanoTime() - startTime;
                        adaptiveTimeout.recordSuccess(Duration.ofNanos(duration));
                        rateLimiter.onSuccess();
                    })
                    .doOnError(error -> {
                        long duration = System.nanoTime() - startTime;
                        adaptiveTimeout.recordFailure(Duration.ofNanos(duration), error);
                        rateLimiter.onError();
                    });

                // Apply enhanced circuit breaker if available
                if (circuitBreakerManager != null) {
                    return circuitBreakerManager.executeWithCircuitBreaker(serviceName, () -> resilientOperation);
                }

                return resilientOperation;
            });
        });
    }

    private BulkheadIsolation getBulkhead(String serviceName, ResilienceConfig config) {
        return bulkheads.computeIfAbsent(serviceName, 
            key -> new BulkheadIsolation(config.getMaxConcurrentCalls(), config.getMaxWaitTime()));
    }

    private RateLimiter getRateLimiter(String serviceName, ResilienceConfig config) {
        return rateLimiters.computeIfAbsent(serviceName, 
            key -> new RateLimiter(config.getRequestsPerSecond(), config.getBurstCapacity()));
    }

    private AdaptiveTimeout getAdaptiveTimeout(String serviceName, ResilienceConfig config) {
        return adaptiveTimeouts.computeIfAbsent(serviceName, 
            key -> new AdaptiveTimeout(config.getBaseTimeout(), config.getMaxTimeout()));
    }

    /**
     * Bulkhead isolation implementation.
     */
    public static class BulkheadIsolation {
        private final Semaphore semaphore;
        private final Duration maxWaitTime;
        private final Scheduler scheduler;

        public BulkheadIsolation(int maxConcurrentCalls, Duration maxWaitTime) {
            this.semaphore = new Semaphore(maxConcurrentCalls);
            this.maxWaitTime = maxWaitTime;
            this.scheduler = Schedulers.newBoundedElastic(maxConcurrentCalls, Integer.MAX_VALUE, "bulkhead");
        }

        public <T> Mono<T> execute(java.util.function.Supplier<Mono<T>> operation) {
            return Mono.fromCallable(() -> {
                if (!semaphore.tryAcquire(maxWaitTime.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    throw new BulkheadFullException("Bulkhead is full, cannot acquire permit within " + maxWaitTime);
                }
                return true;
            })
            .subscribeOn(scheduler)
            .flatMap(acquired -> operation.get())
            .doFinally(signal -> semaphore.release());
        }

        public int getAvailablePermits() {
            return semaphore.availablePermits();
        }
    }

    /**
     * Rate limiter implementation using token bucket algorithm.
     */
    public static class RateLimiter {
        private final double requestsPerSecond;
        private final int burstCapacity;
        private final AtomicLong lastRefillTime = new AtomicLong(System.nanoTime());
        private volatile double availableTokens;

        public RateLimiter(double requestsPerSecond, int burstCapacity) {
            this.requestsPerSecond = requestsPerSecond;
            this.burstCapacity = burstCapacity;
            this.availableTokens = burstCapacity;
        }

        public boolean tryAcquire() {
            refillTokens();
            
            synchronized (this) {
                if (availableTokens >= 1.0) {
                    availableTokens -= 1.0;
                    return true;
                }
                return false;
            }
        }

        private void refillTokens() {
            long now = System.nanoTime();
            long lastRefill = lastRefillTime.get();
            
            if (now > lastRefill) {
                double timeDelta = (now - lastRefill) / 1_000_000_000.0; // Convert to seconds
                double tokensToAdd = timeDelta * requestsPerSecond;
                
                synchronized (this) {
                    availableTokens = Math.min(burstCapacity, availableTokens + tokensToAdd);
                }
                
                lastRefillTime.set(now);
            }
        }

        public void onSuccess() {
            // Could implement adaptive rate limiting based on success rate
        }

        public void onError() {
            // Could implement adaptive rate limiting based on error rate
        }

        public double getAvailableTokens() {
            refillTokens();
            return availableTokens;
        }
    }

    /**
     * Adaptive timeout implementation that adjusts based on historical performance.
     */
    public static class AdaptiveTimeout {
        private final Duration baseTimeout;
        private final Duration maxTimeout;
        private final java.util.concurrent.atomic.DoubleAdder totalResponseTime = new java.util.concurrent.atomic.DoubleAdder();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failureCount = new AtomicLong();
        private volatile Duration currentTimeout;

        public AdaptiveTimeout(Duration baseTimeout, Duration maxTimeout) {
            this.baseTimeout = baseTimeout;
            this.maxTimeout = maxTimeout;
            this.currentTimeout = baseTimeout;
        }

        public Duration calculateTimeout() {
            long totalCalls = successCount.get() + failureCount.get();
            
            if (totalCalls < 10) {
                return baseTimeout; // Not enough data, use base timeout
            }

            double avgResponseTime = totalResponseTime.sum() / successCount.get();
            double failureRate = (double) failureCount.get() / totalCalls;

            // Adjust timeout based on average response time and failure rate
            double multiplier = 1.0 + (failureRate * 2.0); // Increase timeout if high failure rate
            Duration adaptedTimeout = Duration.ofMillis((long) (avgResponseTime * multiplier * 2.0)); // 2x avg response time

            // Ensure timeout is within bounds
            if (adaptedTimeout.compareTo(baseTimeout) < 0) {
                adaptedTimeout = baseTimeout;
            } else if (adaptedTimeout.compareTo(maxTimeout) > 0) {
                adaptedTimeout = maxTimeout;
            }

            currentTimeout = adaptedTimeout;
            return adaptedTimeout;
        }

        public void recordSuccess(Duration responseTime) {
            totalResponseTime.add(responseTime.toMillis());
            successCount.incrementAndGet();
        }

        public void recordFailure(Duration responseTime, Throwable error) {
            failureCount.incrementAndGet();
            
            // Don't include timeout failures in average response time calculation
            if (!(error instanceof java.util.concurrent.TimeoutException)) {
                totalResponseTime.add(responseTime.toMillis());
            }
        }

        public Duration getCurrentTimeout() {
            return currentTimeout;
        }
    }

    /**
     * Load shedding strategy interface.
     */
    public interface LoadSheddingStrategy {
        boolean shouldShedLoad(String serviceName);
    }

    /**
     * Intelligent load shedding strategy based on comprehensive system monitoring.
     *
     * <p>This implementation monitors multiple system metrics and applies intelligent
     * load shedding decisions based on:
     * <ul>
     *   <li>CPU usage with moving average</li>
     *   <li>Memory usage (heap and non-heap)</li>
     *   <li>Thread pool utilization</li>
     *   <li>GC pressure indicators</li>
     *   <li>Service-specific request rates</li>
     *   <li>Response time degradation</li>
     * </ul>
     */
    public static class SystemLoadSheddingStrategy implements LoadSheddingStrategy {
        private final double maxCpuUsage;
        private final double maxMemoryUsage;
        private final double maxThreadPoolUtilization;
        private final long maxResponseTimeMs;
        private final int maxRequestsPerSecond;

        // System monitoring components
        private final OperatingSystemMXBean osBean;
        private final MemoryMXBean memoryBean;
        private final ThreadMXBean threadBean;
        private final List<GarbageCollectorMXBean> gcBeans;

        // Moving averages for CPU monitoring
        private final AtomicReference<Double> cpuMovingAverage = new AtomicReference<>(0.0);
        private final AtomicLong lastCpuCheck = new AtomicLong(System.currentTimeMillis());

        // Service-specific metrics
        private final Map<String, ServiceLoadMetrics> serviceMetrics = new ConcurrentHashMap<>();

        public SystemLoadSheddingStrategy(double maxCpuUsage, double maxMemoryUsage,
                                        double maxThreadPoolUtilization, long maxResponseTimeMs,
                                        int maxRequestsPerSecond) {
            this.maxCpuUsage = maxCpuUsage;
            this.maxMemoryUsage = maxMemoryUsage;
            this.maxThreadPoolUtilization = maxThreadPoolUtilization;
            this.maxResponseTimeMs = maxResponseTimeMs;
            this.maxRequestsPerSecond = maxRequestsPerSecond;

            // Initialize system monitoring beans
            this.osBean = ManagementFactory.getOperatingSystemMXBean();
            this.memoryBean = ManagementFactory.getMemoryMXBean();
            this.threadBean = ManagementFactory.getThreadMXBean();
            this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        }

        public SystemLoadSheddingStrategy(double maxCpuUsage, double maxMemoryUsage) {
            this(maxCpuUsage, maxMemoryUsage, 0.9, 5000, 1000);
        }

        @Override
        public boolean shouldShedLoad(String serviceName) {
            try {
                // Get or create service metrics
                ServiceLoadMetrics metrics = serviceMetrics.computeIfAbsent(serviceName,
                    k -> new ServiceLoadMetrics());

                // Update current request metrics
                metrics.recordRequest();

                // Check multiple load indicators
                boolean cpuOverloaded = isCpuOverloaded();
                boolean memoryOverloaded = isMemoryOverloaded();
                boolean threadPoolOverloaded = isThreadPoolOverloaded();
                boolean gcPressureHigh = isGcPressureHigh();
                boolean serviceOverloaded = isServiceOverloaded(metrics);

                // Apply intelligent decision logic
                boolean shouldShed = cpuOverloaded || memoryOverloaded || threadPoolOverloaded ||
                                   gcPressureHigh || serviceOverloaded;

                if (shouldShed) {
                    log.warn("Load shedding activated for service '{}' - CPU: {}, Memory: {}, ThreadPool: {}, GC: {}, Service: {}",
                        serviceName, cpuOverloaded, memoryOverloaded, threadPoolOverloaded,
                        gcPressureHigh, serviceOverloaded);
                }

                return shouldShed;

            } catch (Exception e) {
                log.warn("Error in load shedding calculation for service '{}': {}", serviceName, e.getMessage());
                // Fail safe - don't shed load if we can't determine system state
                return false;
            }
        }

        /**
         * Checks if CPU usage is above threshold using moving average.
         */
        private boolean isCpuOverloaded() {
            try {
                double currentCpu = -1;

                // Try to get CPU load from platform-specific MXBean
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sunOsBean =
                        (com.sun.management.OperatingSystemMXBean) osBean;
                    currentCpu = sunOsBean.getProcessCpuLoad();
                    if (currentCpu < 0) {
                        // Process CPU load not available, use system CPU load
                    currentCpu = sunOsBean.getCpuLoad();
                    }
                }

                // Fallback: estimate CPU usage from available processors and thread activity
                if (currentCpu < 0) {
                    int availableProcessors = Runtime.getRuntime().availableProcessors();
                    int activeThreads = threadBean.getThreadCount();
                    // Rough estimation: if we have more threads than 2x processors, assume high CPU
                    currentCpu = Math.min(1.0, (double) activeThreads / (availableProcessors * 2));
                }

                if (currentCpu >= 0) {
                    // Update moving average (exponential smoothing)
                    double alpha = 0.3; // Smoothing factor
                    double previousAverage = cpuMovingAverage.get();
                    double newAverage = alpha * currentCpu + (1 - alpha) * previousAverage;
                    cpuMovingAverage.set(newAverage);

                    return newAverage > maxCpuUsage;
                }

                return false;
            } catch (Exception e) {
                log.debug("Error checking CPU load: {}", e.getMessage());
                return false;
            }
        }

        /**
         * Checks if memory usage is above threshold.
         */
        private boolean isMemoryOverloaded() {
            try {
                MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
                MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

                double heapUtilization = (double) heapUsage.getUsed() / heapUsage.getMax();
                double nonHeapUtilization = (double) nonHeapUsage.getUsed() / nonHeapUsage.getMax();

                return heapUtilization > maxMemoryUsage || nonHeapUtilization > 0.95;
            } catch (Exception e) {
                log.debug("Error checking memory usage: {}", e.getMessage());
                return false;
            }
        }

        /**
         * Checks if thread pools are overloaded.
         */
        private boolean isThreadPoolOverloaded() {
            try {
                // Check common ForkJoinPool utilization
                ForkJoinPool commonPool = ForkJoinPool.commonPool();
                if (commonPool != null) {
                    int activeThreads = commonPool.getActiveThreadCount();
                    int parallelism = commonPool.getParallelism();
                    double utilization = (double) activeThreads / parallelism;

                    if (utilization > maxThreadPoolUtilization) {
                        return true;
                    }
                }

                // Check total thread count vs available processors
                int totalThreads = threadBean.getThreadCount();
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                double threadRatio = (double) totalThreads / (availableProcessors * 10); // Allow 10x threads per core

                return threadRatio > 1.0;
            } catch (Exception e) {
                log.debug("Error checking thread pool utilization: {}", e.getMessage());
                return false;
            }
        }

        /**
         * Checks if GC pressure is high.
         */
        private boolean isGcPressureHigh() {
            try {
                long totalGcTime = 0;
                long totalCollections = 0;

                for (GarbageCollectorMXBean gcBean : gcBeans) {
                    totalGcTime += gcBean.getCollectionTime();
                    totalCollections += gcBean.getCollectionCount();
                }

                // If GC is taking more than 10% of total time, consider it high pressure
                long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
                double gcTimeRatio = (double) totalGcTime / uptime;

                return gcTimeRatio > 0.1 || totalCollections > 1000;
            } catch (Exception e) {
                log.debug("Error checking GC pressure: {}", e.getMessage());
                return false;
            }
        }

        /**
         * Checks if specific service is overloaded.
         */
        private boolean isServiceOverloaded(ServiceLoadMetrics metrics) {
            try {
                // Check request rate
                double currentRequestRate = metrics.getCurrentRequestRate();
                if (currentRequestRate > maxRequestsPerSecond) {
                    return true;
                }

                // Check average response time
                double avgResponseTime = metrics.getAverageResponseTime();
                if (avgResponseTime > maxResponseTimeMs) {
                    return true;
                }

                // Check error rate
                double errorRate = metrics.getErrorRate();
                if (errorRate > 0.5) { // 50% error rate threshold
                    return true;
                }

                return false;
            } catch (Exception e) {
                log.debug("Error checking service load metrics: {}", e.getMessage());
                return false;
            }
        }

        /**
         * Records response time for a service.
         */
        public void recordResponseTime(String serviceName, long responseTimeMs, boolean success) {
            ServiceLoadMetrics metrics = serviceMetrics.computeIfAbsent(serviceName,
                k -> new ServiceLoadMetrics());
            metrics.recordResponse(responseTimeMs, success);
        }

        /**
         * Gets current load metrics for a service.
         */
        public ServiceLoadMetrics getServiceMetrics(String serviceName) {
            return serviceMetrics.get(serviceName);
        }
    }

    /**
     * Service-specific load metrics for intelligent load shedding.
     */
    public static class ServiceLoadMetrics {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong responseCount = new AtomicLong(0);
        private final AtomicLong lastRequestTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());

        private static final long WINDOW_SIZE_MS = 60000; // 1 minute window

        public void recordRequest() {
            requestCount.incrementAndGet();
            lastRequestTime.set(System.currentTimeMillis());
        }

        public void recordResponse(long responseTimeMs, boolean success) {
            responseCount.incrementAndGet();
            totalResponseTime.addAndGet(responseTimeMs);

            if (!success) {
                errorCount.incrementAndGet();
            }
        }

        public double getCurrentRequestRate() {
            long now = System.currentTimeMillis();
            long windowStart = windowStartTime.get();
            long windowDuration = now - windowStart;

            if (windowDuration >= WINDOW_SIZE_MS) {
                // Reset window
                windowStartTime.set(now);
                long currentRequests = requestCount.getAndSet(0);
                return (double) currentRequests / (windowDuration / 1000.0); // requests per second
            }

            // Prevent division by very small numbers that would produce unrealistic rates
            // Use minimum 1 second window to get meaningful rate calculations
            double effectiveWindowSeconds = Math.max(windowDuration / 1000.0, 1.0);
            return (double) requestCount.get() / effectiveWindowSeconds;
        }

        public double getAverageResponseTime() {
            long responses = responseCount.get();
            if (responses == 0) {
                return 0.0;
            }
            return (double) totalResponseTime.get() / responses;
        }

        public double getErrorRate() {
            long responses = responseCount.get();
            if (responses == 0) {
                return 0.0;
            }
            return (double) errorCount.get() / responses;
        }
    }

    /**
     * Configuration for resilience patterns.
     */
    public static class ResilienceConfig {
        private final int maxConcurrentCalls;
        private final Duration maxWaitTime;
        private final double requestsPerSecond;
        private final int burstCapacity;
        private final Duration baseTimeout;
        private final Duration maxTimeout;

        public ResilienceConfig(int maxConcurrentCalls, Duration maxWaitTime, double requestsPerSecond, 
                              int burstCapacity, Duration baseTimeout, Duration maxTimeout) {
            this.maxConcurrentCalls = maxConcurrentCalls;
            this.maxWaitTime = maxWaitTime;
            this.requestsPerSecond = requestsPerSecond;
            this.burstCapacity = burstCapacity;
            this.baseTimeout = baseTimeout;
            this.maxTimeout = maxTimeout;
        }

        // Getters
        public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
        public Duration getMaxWaitTime() { return maxWaitTime; }
        public double getRequestsPerSecond() { return requestsPerSecond; }
        public int getBurstCapacity() { return burstCapacity; }
        public Duration getBaseTimeout() { return baseTimeout; }
        public Duration getMaxTimeout() { return maxTimeout; }
    }

}
