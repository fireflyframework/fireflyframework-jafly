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

package org.fireflyframework.cache.core;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Health information model for cache monitoring.
 * <p>
 * This class provides health status and diagnostic information about
 * a cache instance, including availability, performance metrics, and
 * configuration details.
 */
@Data
@Builder
public class CacheHealth {

    /**
     * Overall health status.
     */
    @Builder.Default
    private final String status = "UP";

    /**
     * Cache type being monitored.
     */
    private final CacheType cacheType;

    /**
     * Cache name being monitored.
     */
    private final String cacheName;

    /**
     * Whether the cache is available for operations.
     */
    private final boolean available;

    /**
     * Whether the cache is properly configured.
     */
    private final boolean configured;

    /**
     * Timestamp when this health check was performed.
     */
    @Builder.Default
    private final Instant checkedAt = Instant.now();

    /**
     * Response time for a health check operation (in milliseconds).
     */
    private final Long responseTimeMs;

    /**
     * Last successful operation timestamp.
     */
    private final Instant lastSuccessfulOperation;

    /**
     * Number of consecutive failures.
     */
    @Builder.Default
    private final int consecutiveFailures = 0;

    /**
     * Additional details about the cache health.
     */
    private final Map<String, Object> details;

    /**
     * Error message if the cache is unhealthy.
     */
    private final String errorMessage;

    /**
     * Exception that caused the health check to fail, if any.
     */
    private final Throwable cause;

    /**
     * Checks if the cache is healthy.
     *
     * @return true if the cache is healthy
     */
    public boolean isHealthy() {
        return "UP".equals(status) && available && configured;
    }

    /**
     * Gets the overall status considering all health factors.
     *
     * @return health status string
     */
    public String getOverallStatus() {
        if (!configured) {
            return "NOT_CONFIGURED";
        }
        if (!available) {
            return "UNAVAILABLE";
        }
        if (consecutiveFailures > 0) {
            return "DEGRADED";
        }
        return status;
    }

    /**
     * Creates a healthy cache health instance.
     *
     * @param cacheType the cache type
     * @param cacheName the cache name
     * @param responseTime response time in milliseconds
     * @return healthy cache health
     */
    public static CacheHealth healthy(CacheType cacheType, String cacheName, Long responseTime) {
        return CacheHealth.builder()
                .status("UP")
                .cacheType(cacheType)
                .cacheName(cacheName)
                .available(true)
                .configured(true)
                .responseTimeMs(responseTime)
                .lastSuccessfulOperation(Instant.now())
                .consecutiveFailures(0)
                .build();
    }

    /**
     * Creates an unhealthy cache health instance.
     *
     * @param cacheType the cache type
     * @param cacheName the cache name
     * @param errorMessage error description
     * @param cause the causing exception
     * @return unhealthy cache health
     */
    public static CacheHealth unhealthy(CacheType cacheType, String cacheName, 
                                       String errorMessage, Throwable cause) {
        return CacheHealth.builder()
                .status("DOWN")
                .cacheType(cacheType)
                .cacheName(cacheName)
                .available(false)
                .configured(true)
                .errorMessage(errorMessage)
                .cause(cause)
                .consecutiveFailures(1)
                .build();
    }

    /**
     * Creates a not configured cache health instance.
     *
     * @param cacheType the cache type
     * @param cacheName the cache name
     * @return not configured cache health
     */
    public static CacheHealth notConfigured(CacheType cacheType, String cacheName) {
        return CacheHealth.builder()
                .status("DOWN")
                .cacheType(cacheType)
                .cacheName(cacheName)
                .available(false)
                .configured(false)
                .errorMessage("Cache is not properly configured")
                .build();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CacheHealth{");
        sb.append("cache='").append(cacheName).append("'");
        sb.append(", type=").append(cacheType.getIdentifier());
        sb.append(", status=").append(getOverallStatus());
        
        if (responseTimeMs != null) {
            sb.append(", responseTime=").append(responseTimeMs).append("ms");
        }
        
        if (consecutiveFailures > 0) {
            sb.append(", failures=").append(consecutiveFailures);
        }
        
        if (errorMessage != null) {
            sb.append(", error='").append(errorMessage).append("'");
        }
        
        sb.append("}");
        return sb.toString();
    }
}