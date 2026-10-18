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


package org.fireflyframework.core.actuator.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration class for thread pool metrics and health monitoring.
 * <p>
 * This class provides comprehensive monitoring of thread pools and executor services including:
 * - Thread pool queue sizes and task counts
 * - Active thread counts and utilization
 * - Thread pool health status
 * - Fork Join Pool monitoring
 * - Custom executor service metrics
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "firefly.actuator.metrics.threadpool", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
public class ThreadPoolMetricsConfig {

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public ThreadPoolMetricsConfig(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * Provides thread pool health indicator.
     * Monitors the health and performance of all thread pools in the application.
     *
     * @param threadPoolTaskExecutors list of Spring ThreadPoolTaskExecutor instances
     * @param executorServices list of ExecutorService instances
     * @return thread pool health indicator
     */
    @Bean
    public HealthIndicator threadPoolHealthIndicator(
            List<ThreadPoolTaskExecutor> threadPoolTaskExecutors,
            List<ExecutorService> executorServices) {
        
        return () -> {
            Map<String, Object> details = new HashMap<>();
            boolean allHealthy = true;
            
            // Check Spring ThreadPoolTaskExecutor instances
            for (int i = 0; i < threadPoolTaskExecutors.size(); i++) {
                ThreadPoolTaskExecutor executor = threadPoolTaskExecutors.get(i);
                String executorName = "springThreadPool_" + i;
                
                Map<String, Object> executorDetails = checkSpringThreadPoolHealth(executor);
                details.put(executorName, executorDetails);
                
                if (!"UP".equals(executorDetails.get("status"))) {
                    allHealthy = false;
                }
            }
            
            // Check general ExecutorService instances
            for (int i = 0; i < executorServices.size(); i++) {
                ExecutorService executor = executorServices.get(i);
                String executorName = "executorService_" + i;
                
                Map<String, Object> executorDetails = checkExecutorServiceHealth(executor);
                details.put(executorName, executorDetails);
                
                if (!"UP".equals(executorDetails.get("status"))) {
                    allHealthy = false;
                }
                
                // Register metrics for this executor
                registerExecutorMetrics(executor, executorName);
            }
            
            // Check ForkJoinPool.commonPool()
            Map<String, Object> commonPoolDetails = checkForkJoinPoolHealth(ForkJoinPool.commonPool());
            details.put("forkJoinCommonPool", commonPoolDetails);
            
            // Add general thread information
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            Map<String, Object> threadInfo = new HashMap<>();
            threadInfo.put("totalThreadCount", threadMXBean.getThreadCount());
            threadInfo.put("peakThreadCount", threadMXBean.getPeakThreadCount());
            threadInfo.put("daemonThreadCount", threadMXBean.getDaemonThreadCount());
            threadInfo.put("totalStartedThreadCount", threadMXBean.getTotalStartedThreadCount());
            details.put("systemThreads", threadInfo);
            
            if (threadPoolTaskExecutors.isEmpty() && executorServices.isEmpty()) {
                details.put("message", "No custom thread pools configured");
            }
            
            return allHealthy 
                    ? org.springframework.boot.actuate.health.Health.up().withDetails(details).build()
                    : org.springframework.boot.actuate.health.Health.down().withDetails(details).build();
        };
    }

    /**
     * Checks the health of a Spring ThreadPoolTaskExecutor.
     */
    private Map<String, Object> checkSpringThreadPoolHealth(ThreadPoolTaskExecutor executor) {
        Map<String, Object> details = new HashMap<>();
        
        try {
            ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
            
            details.put("status", threadPoolExecutor.isShutdown() ? "DOWN" : "UP");
            details.put("corePoolSize", threadPoolExecutor.getCorePoolSize());
            details.put("maximumPoolSize", threadPoolExecutor.getMaximumPoolSize());
            details.put("activeCount", threadPoolExecutor.getActiveCount());
            details.put("poolSize", threadPoolExecutor.getPoolSize());
            details.put("largestPoolSize", threadPoolExecutor.getLargestPoolSize());
            details.put("taskCount", threadPoolExecutor.getTaskCount());
            details.put("completedTaskCount", threadPoolExecutor.getCompletedTaskCount());
            details.put("queueSize", threadPoolExecutor.getQueue().size());
            details.put("queueRemainingCapacity", threadPoolExecutor.getQueue().remainingCapacity());
            
            // Calculate utilization
            double utilization = threadPoolExecutor.getMaximumPoolSize() > 0 
                    ? (double) threadPoolExecutor.getActiveCount() / threadPoolExecutor.getMaximumPoolSize() * 100
                    : 0.0;
            details.put("utilizationPercentage", Math.round(utilization * 100.0) / 100.0);
            
        } catch (Exception e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
        }
        
        return details;
    }

    /**
     * Checks the health of a general ExecutorService.
     */
    private Map<String, Object> checkExecutorServiceHealth(ExecutorService executor) {
        Map<String, Object> details = new HashMap<>();
        
        try {
            details.put("status", executor.isShutdown() ? "DOWN" : "UP");
            details.put("isTerminated", executor.isTerminated());
            details.put("type", executor.getClass().getSimpleName());
            
            // If it's a ThreadPoolExecutor, get more details
            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
                details.put("corePoolSize", tpe.getCorePoolSize());
                details.put("maximumPoolSize", tpe.getMaximumPoolSize());
                details.put("activeCount", tpe.getActiveCount());
                details.put("poolSize", tpe.getPoolSize());
                details.put("taskCount", tpe.getTaskCount());
                details.put("completedTaskCount", tpe.getCompletedTaskCount());
                details.put("queueSize", tpe.getQueue().size());
            }
            
        } catch (Exception e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
        }
        
        return details;
    }

    /**
     * Checks the health of ForkJoinPool.
     */
    private Map<String, Object> checkForkJoinPoolHealth(ForkJoinPool pool) {
        Map<String, Object> details = new HashMap<>();
        
        try {
            details.put("status", pool.isShutdown() ? "DOWN" : "UP");
            details.put("parallelism", pool.getParallelism());
            details.put("activeThreadCount", pool.getActiveThreadCount());
            details.put("runningThreadCount", pool.getRunningThreadCount());
            details.put("queuedSubmissionCount", pool.getQueuedSubmissionCount());
            details.put("queuedTaskCount", pool.getQueuedTaskCount());
            details.put("stealCount", pool.getStealCount());
            details.put("isTerminated", pool.isTerminated());
            details.put("isQuiescent", pool.isQuiescent());
            
        } catch (Exception e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
        }
        
        return details;
    }

    /**
     * Registers metrics for an ExecutorService with Micrometer.
     */
    private void registerExecutorMetrics(ExecutorService executor, String executorName) {
        try {
            MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
            if (meterRegistry != null) {
                ExecutorServiceMetrics.monitor(meterRegistry, executor, executorName);
            }
        } catch (Exception e) {
            // Log warning but don't fail - metrics registration is optional
        }
    }
}