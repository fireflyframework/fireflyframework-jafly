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

package org.fireflyframework.eventsourcing.health;

import org.fireflyframework.eventsourcing.projection.ProjectionHealth;
import org.fireflyframework.eventsourcing.projection.ProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Health indicator for event sourcing projections.
 * Integrates with Spring Boot Actuator to provide health status of all registered projections.
 */
@Slf4j
public class ProjectionHealthIndicator implements HealthIndicator {
    
    private final List<ProjectionService<?>> projectionServices;
    private final Duration healthCheckTimeout;
    private final long maxAcceptableLag;
    
    /**
     * Constructor with default values.
     */
    public ProjectionHealthIndicator(List<ProjectionService<?>> projectionServices) {
        this(projectionServices, Duration.ofSeconds(5), 1000L);
    }
    
    /**
     * Constructor with custom timeout and max acceptable lag.
     */
    public ProjectionHealthIndicator(List<ProjectionService<?>> projectionServices, 
                                   Duration healthCheckTimeout, 
                                   long maxAcceptableLag) {
        this.projectionServices = projectionServices;
        this.healthCheckTimeout = healthCheckTimeout;
        this.maxAcceptableLag = maxAcceptableLag;
    }
    
    @Override
    public Health health() {
        try {
            return checkProjectionsHealth()
                    .timeout(healthCheckTimeout)
                    .block();
        } catch (Exception e) {
            log.error("Failed to check projection health", e);
            return Health.down()
                    .withDetail("error", "Health check failed: " + e.getMessage())
                    .build();
        }
    }
    
    private Mono<Health> checkProjectionsHealth() {
        if (projectionServices.isEmpty()) {
            return Mono.just(Health.up()
                    .withDetail("message", "No projections configured")
                    .build());
        }
        
        List<Mono<ProjectionHealth>> healthChecks = projectionServices.stream()
                .map(ProjectionService::checkHealth)
                .collect(Collectors.toList());
                
        return Mono.zip(healthChecks, results -> {
            Health.Builder healthBuilder = Health.up();
            boolean overallHealthy = true;
            
            for (Object result : results) {
                ProjectionHealth projectionHealth = (ProjectionHealth) result;
                
                Map<String, Object> projectionDetails = Map.of(
                        "position", projectionHealth.getCurrentPosition(),
                        "lag", projectionHealth.getLag(),
                        "completion", String.format("%.2f%%", projectionHealth.getCompletionPercentage() * 100),
                        "status", projectionHealth.getStatusDescription(),
                        "lastUpdated", projectionHealth.getLastUpdated(),
                        "processingRate", projectionHealth.getProcessingRate()
                );
                
                healthBuilder.withDetail(projectionHealth.getProjectionName(), projectionDetails);
                
                if (!projectionHealth.getIsHealthy()) {
                    overallHealthy = false;
                    
                    if (projectionHealth.getErrorMessage() != null) {
                        healthBuilder.withDetail(
                                projectionHealth.getProjectionName() + ".error", 
                                projectionHealth.getErrorMessage()
                        );
                    }
                }
                
                // Check if lag is too high
                if (projectionHealth.getLag() != null && projectionHealth.getLag() > maxAcceptableLag) {
                    overallHealthy = false;
                    healthBuilder.withDetail(
                            projectionHealth.getProjectionName() + ".warning", 
                            "High lag detected: " + projectionHealth.getLag() + " events behind"
                    );
                }
            }
            
            // Add summary information
            long totalProjections = results.length;
            long healthyProjections = java.util.Arrays.stream(results)
                    .mapToLong(r -> ((ProjectionHealth) r).getIsHealthy() ? 1 : 0)
                    .sum();
                    
            healthBuilder.withDetail("summary", Map.of(
                    "totalProjections", totalProjections,
                    "healthyProjections", healthyProjections,
                    "unhealthyProjections", totalProjections - healthyProjections
            ));
            
            return overallHealthy ? healthBuilder.build() : healthBuilder.down().build();
        });
    }
}