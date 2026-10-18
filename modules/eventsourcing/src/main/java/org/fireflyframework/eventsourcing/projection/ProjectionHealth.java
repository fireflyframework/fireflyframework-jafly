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

package org.fireflyframework.eventsourcing.projection;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Health information for event sourcing projections.
 * Used for monitoring projection lag, position, and overall health.
 */
@Data
@Builder
@Jacksonized
public class ProjectionHealth {
    
    /**
     * Name of the projection.
     */
    private final String projectionName;
    
    /**
     * Current position of the projection (last processed global sequence).
     */
    private final Long currentPosition;
    
    /**
     * Latest global sequence available in the event store.
     */
    private final Long latestGlobalSequence;
    
    /**
     * Lag between current position and latest global sequence.
     */
    private final Long lag;
    
    /**
     * Whether the projection is considered healthy.
     */
    private final Boolean isHealthy;
    
    /**
     * When this health check was performed.
     */
    private final Instant lastUpdated;
    
    /**
     * Optional error message if projection is unhealthy.
     */
    private final String errorMessage;
    
    /**
     * Processing rate (events per second) if available.
     */
    private final Double processingRate;
    
    /**
     * Gets the completion percentage (0.0 to 1.0).
     */
    public Double getCompletionPercentage() {
        if (latestGlobalSequence == null || latestGlobalSequence == 0) {
            return 1.0;
        }
        if (currentPosition == null) {
            return 0.0;
        }
        return Math.min(1.0, (double) currentPosition / latestGlobalSequence);
    }
    
    /**
     * Gets a human-readable status description.
     */
    public String getStatusDescription() {
        if (!isHealthy) {
            if (errorMessage != null) {
                return "Unhealthy: " + errorMessage;
            }
            return "Unhealthy: High lag (" + lag + " events behind)";
        }
        
        if (lag == 0) {
            return "Healthy: Up to date";
        }
        
        return "Healthy: " + lag + " events behind";
    }
}