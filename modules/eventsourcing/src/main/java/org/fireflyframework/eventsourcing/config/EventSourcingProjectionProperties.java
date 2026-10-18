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

package org.fireflyframework.eventsourcing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for Event Sourcing projections.
 * Allows customization of projection behavior, health checks, and monitoring.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "firefly.eventsourcing.projection")
public class EventSourcingProjectionProperties {
    
    /**
     * Default batch processing configuration.
     */
    private BatchProcessing batchProcessing = new BatchProcessing();
    
    /**
     * Health check configuration.
     */
    private HealthCheck healthCheck = new HealthCheck();
    
    /**
     * Retry configuration.
     */
    private Retry retry = new Retry();
    
    /**
     * Metrics configuration.
     */
    private Metrics metrics = new Metrics();
    
    @Data
    public static class BatchProcessing {
        /**
         * Default batch size for event processing.
         */
        private int defaultBatchSize = 100;
        
        /**
         * Default batch processing interval.
         */
        private Duration defaultInterval = Duration.ofSeconds(5);
        
        /**
         * Maximum batch size allowed.
         */
        private int maxBatchSize = 1000;
        
        /**
         * Minimum batch processing interval.
         */
        private Duration minInterval = Duration.ofMillis(100);
    }
    
    @Data
    public static class HealthCheck {
        /**
         * Timeout for health check operations.
         */
        private Duration timeout = Duration.ofSeconds(5);
        
        /**
         * Maximum acceptable lag for a projection to be considered healthy.
         */
        private long maxAcceptableLag = 1000L;
        
        /**
         * Whether to include detailed projection information in health checks.
         */
        private boolean includeDetails = true;
        
        /**
         * Whether to fail the overall health check if any projection is unhealthy.
         */
        private boolean failOnUnhealthyProjection = true;
    }
    
    @Data
    public static class Retry {
        /**
         * Default maximum retry attempts.
         */
        private int defaultMaxAttempts = 3;
        
        /**
         * Default retry delay.
         */
        private Duration defaultDelay = Duration.ofSeconds(1);
        
        /**
         * Maximum retry delay.
         */
        private Duration maxDelay = Duration.ofMinutes(5);
        
        /**
         * Backoff multiplier for exponential backoff.
         */
        private double backoffMultiplier = 2.0;
    }
    
    @Data
    public static class Metrics {
        /**
         * Whether to enable detailed metrics collection.
         */
        private boolean enabled = true;
        
        /**
         * Whether to include projection-specific tags in metrics.
         */
        private boolean includeProjectionTags = true;
        
        /**
         * Whether to track individual event processing times.
         */
        private boolean trackEventProcessingTime = true;
        
        /**
         * Percentiles to track for timing metrics.
         */
        private double[] percentiles = {0.5, 0.95, 0.99};
        
        /**
         * Whether to export metrics to external systems.
         */
        private boolean enableExport = true;
    }
}