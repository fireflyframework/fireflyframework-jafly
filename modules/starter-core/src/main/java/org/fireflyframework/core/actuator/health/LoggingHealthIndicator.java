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


package org.fireflyframework.core.actuator.health;

import org.fireflyframework.core.logging.LoggingUtils;
import org.fireflyframework.core.logging.config.LoggingProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import java.util.Map;

/**
 * Health indicator for the enhanced structured logging system.
 * <p>
 * This health indicator monitors the performance and health of the JSON parsing
 * functionality within the logging system, including cache utilization,
 * error rates, and system limits.
 * <p>
 * The health status is determined based on:
 * <ul>
 *   <li>Cache utilization levels</li>
 *   <li>Number of oversized JSON rejections</li>
 *   <li>Recursion limit exceeded count</li>
 *   <li>Overall system performance</li>
 * </ul>
 */
public class LoggingHealthIndicator implements HealthIndicator {

    private final LoggingProperties loggingProperties;

    // Health thresholds
    private static final double CACHE_UTILIZATION_WARNING_THRESHOLD = 0.8;
    private static final double CACHE_UTILIZATION_ERROR_THRESHOLD = 0.95;
    private static final long OVERSIZED_JSON_WARNING_THRESHOLD = 100;
    private static final long OVERSIZED_JSON_ERROR_THRESHOLD = 1000;
    private static final long RECURSION_LIMIT_WARNING_THRESHOLD = 10;
    private static final long RECURSION_LIMIT_ERROR_THRESHOLD = 100;

    public LoggingHealthIndicator(LoggingProperties loggingProperties) {
        this.loggingProperties = loggingProperties;
    }

    @Override
    public Health health() {
        try {
            Map<String, Object> metrics = LoggingUtils.getMetrics();
            Map<String, Object> cacheStats = LoggingUtils.getCacheStats();
            
            // Evaluate health status and build response
            if (isSystemHealthy(metrics, cacheStats)) {
                Health.Builder healthBuilder = Health.up();
                healthBuilder.withDetails(metrics);
                healthBuilder.withDetails(cacheStats);
                addConfigurationDetails(healthBuilder);
                return healthBuilder.build();
            } else {
                Health.Builder healthBuilder = Health.down();
                healthBuilder.withDetails(metrics);
                healthBuilder.withDetails(cacheStats);
                addConfigurationDetails(healthBuilder);
                return healthBuilder.build();
            }
            
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", "Failed to retrieve logging system health")
                    .withDetail("exception", e.getMessage())
                    .build();
        }
    }

    /**
     * Determines if the system is healthy based on current metrics and thresholds.
     */
    private boolean isSystemHealthy(Map<String, Object> metrics, Map<String, Object> cacheStats) {
        // Check cache utilization
        double validationCacheUtilization = (Double) cacheStats.get("validationCacheUtilization");
        double parseCacheUtilization = (Double) cacheStats.get("parseCacheUtilization");
        
        if (validationCacheUtilization >= CACHE_UTILIZATION_ERROR_THRESHOLD || 
            parseCacheUtilization >= CACHE_UTILIZATION_ERROR_THRESHOLD) {
            return false;
        }
        
        // Check oversized JSON rejections
        long oversizedJsonCount = (Long) metrics.get("oversizedJsonCount");
        if (oversizedJsonCount >= OVERSIZED_JSON_ERROR_THRESHOLD) {
            return false;
        }
        
        // Check recursion limit exceeded
        long recursionLimitExceeded = (Long) metrics.get("recursionLimitExceeded");
        if (recursionLimitExceeded >= RECURSION_LIMIT_ERROR_THRESHOLD) {
            return false;
        }
        
        return true;
    }

    /**
     * Adds configuration details to the health response.
     */
    private void addConfigurationDetails(Health.Builder healthBuilder) {
        healthBuilder.withDetail("configuration", Map.of(
            "recursiveParsingEnabled", loggingProperties.getJson().isRecursiveParsingEnabled(),
            "maxRecursionDepth", loggingProperties.getJson().getMaxRecursionDepth(),
            "maxJsonSizeBytes", loggingProperties.getJson().getMaxJsonSizeBytes(),
            "cacheEnabled", loggingProperties.getCache().isEnabled(),
            "maxValidationCacheSize", loggingProperties.getCache().getMaxValidationCacheSize(),
            "maxParseCacheSize", loggingProperties.getCache().getMaxParseCacheSize(),
            "metricsEnabled", loggingProperties.getPerformance().isMetricsEnabled()
        ));
        
        healthBuilder.withDetail("thresholds", Map.of(
            "cacheUtilizationWarning", CACHE_UTILIZATION_WARNING_THRESHOLD,
            "cacheUtilizationError", CACHE_UTILIZATION_ERROR_THRESHOLD,
            "oversizedJsonWarning", OVERSIZED_JSON_WARNING_THRESHOLD,
            "oversizedJsonError", OVERSIZED_JSON_ERROR_THRESHOLD,
            "recursionLimitWarning", RECURSION_LIMIT_WARNING_THRESHOLD,
            "recursionLimitError", RECURSION_LIMIT_ERROR_THRESHOLD
        ));
        
        // Add warning flags if thresholds are exceeded
        Map<String, Object> metrics = LoggingUtils.getMetrics();
        Map<String, Object> cacheStats = LoggingUtils.getCacheStats();
        
        boolean hasWarnings = false;
        if ((Double) cacheStats.get("validationCacheUtilization") >= CACHE_UTILIZATION_WARNING_THRESHOLD) {
            healthBuilder.withDetail("warning.validationCacheUtilization", "High cache utilization");
            hasWarnings = true;
        }
        if ((Double) cacheStats.get("parseCacheUtilization") >= CACHE_UTILIZATION_WARNING_THRESHOLD) {
            healthBuilder.withDetail("warning.parseCacheUtilization", "High cache utilization");
            hasWarnings = true;
        }
        if ((Long) metrics.get("oversizedJsonCount") >= OVERSIZED_JSON_WARNING_THRESHOLD) {
            healthBuilder.withDetail("warning.oversizedJson", "High number of oversized JSON rejections");
            hasWarnings = true;
        }
        if ((Long) metrics.get("recursionLimitExceeded") >= RECURSION_LIMIT_WARNING_THRESHOLD) {
            healthBuilder.withDetail("warning.recursionLimit", "Recursion limits being exceeded frequently");
            hasWarnings = true;
        }
        
        healthBuilder.withDetail("hasWarnings", hasWarnings);
    }
}