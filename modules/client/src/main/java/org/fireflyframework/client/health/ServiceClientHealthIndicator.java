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

package org.fireflyframework.client.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot Actuator Health Indicator for ServiceClient instances.
 * 
 * <p>This health indicator integrates with Spring Boot Actuator to provide
 * health information about all registered ServiceClient instances. It exposes
 * health status through the /actuator/health endpoint.
 *
 * <p>Health status includes:
 * <ul>
 *   <li>Overall health state (UP, DOWN, OUT_OF_SERVICE)</li>
 *   <li>Individual service health statuses</li>
 *   <li>Consecutive failure counts</li>
 *   <li>Last check timestamps</li>
 *   <li>Error messages and details</li>
 * </ul>
 *
 * <p>Example health response:
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "serviceClients": {
 *       "status": "UP",
 *       "details": {
 *         "overallState": "HEALTHY",
 *         "totalServices": 3,
 *         "healthyServices": 3,
 *         "degradedServices": 0,
 *         "unhealthyServices": 0,
 *         "services": {
 *           "user-service": {
 *             "state": "HEALTHY",
 *             "consecutiveFailures": 0,
 *             "lastCheckTime": "2025-10-25T10:30:00Z",
 *             "message": "Health check successful"
 *           }
 *         }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class ServiceClientHealthIndicator implements ReactiveHealthIndicator {

    private final ServiceClientHealthManager healthManager;
    private final Duration healthCheckTimeout;

    /**
     * Creates a new ServiceClientHealthIndicator.
     *
     * @param healthManager the health manager
     * @param healthCheckTimeout timeout for health checks
     */
    public ServiceClientHealthIndicator(ServiceClientHealthManager healthManager, Duration healthCheckTimeout) {
        this.healthManager = healthManager;
        this.healthCheckTimeout = healthCheckTimeout;
        log.info("Initialized ServiceClient health indicator for Spring Boot Actuator");
    }

    /**
     * Creates a new ServiceClientHealthIndicator with default timeout.
     *
     * @param healthManager the health manager
     */
    public ServiceClientHealthIndicator(ServiceClientHealthManager healthManager) {
        this(healthManager, Duration.ofSeconds(5));
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(() -> {
            Map<String, ServiceClientHealthManager.ServiceClientHealthStatus> allStatuses = 
                healthManager.getAllHealthStatuses();
            
            ServiceClientHealthManager.OverallHealthState overallState = 
                healthManager.getOverallHealthState();

            // Build health details
            Map<String, Object> details = new HashMap<>();
            details.put("overallState", overallState.name());
            details.put("totalServices", allStatuses.size());
            
            // Count services by state
            long healthyCount = allStatuses.values().stream()
                .filter(status -> status.getState() == ServiceClientHealthManager.HealthState.HEALTHY)
                .count();
            long degradedCount = allStatuses.values().stream()
                .filter(status -> status.getState() == ServiceClientHealthManager.HealthState.DEGRADED)
                .count();
            long unhealthyCount = allStatuses.values().stream()
                .filter(status -> status.getState() == ServiceClientHealthManager.HealthState.UNHEALTHY)
                .count();
            long unknownCount = allStatuses.values().stream()
                .filter(status -> status.getState() == ServiceClientHealthManager.HealthState.UNKNOWN)
                .count();

            details.put("healthyServices", healthyCount);
            details.put("degradedServices", degradedCount);
            details.put("unhealthyServices", unhealthyCount);
            details.put("unknownServices", unknownCount);

            // Add individual service details
            Map<String, Object> servicesDetails = new HashMap<>();
            allStatuses.forEach((serviceName, status) -> {
                Map<String, Object> serviceDetail = new HashMap<>();
                serviceDetail.put("state", status.getState().name());
                serviceDetail.put("consecutiveFailures", status.getConsecutiveFailures());
                serviceDetail.put("lastCheckTime", status.getLastCheckTime().toString());
                
                if (status.getMessage() != null) {
                    serviceDetail.put("message", status.getMessage());
                }
                
                if (status.getLastError() != null) {
                    serviceDetail.put("errorType", status.getLastError().getClass().getSimpleName());
                    serviceDetail.put("errorMessage", status.getLastError().getMessage());
                }
                
                // Calculate time since last check
                Duration timeSinceLastCheck = Duration.between(status.getLastCheckTime(), Instant.now());
                serviceDetail.put("timeSinceLastCheck", timeSinceLastCheck.toMillis() + "ms");
                
                servicesDetails.put(serviceName, serviceDetail);
            });
            
            details.put("services", servicesDetails);

            // Determine Spring Boot Health status based on overall state
            Health.Builder healthBuilder;
            switch (overallState) {
                case HEALTHY:
                    healthBuilder = Health.up();
                    break;
                case DEGRADED:
                    healthBuilder = Health.status("DEGRADED");
                    break;
                case UNHEALTHY:
                    healthBuilder = Health.down();
                    break;
                case UNKNOWN:
                default:
                    healthBuilder = Health.unknown();
                    break;
            }

            return healthBuilder.withDetails(details).build();
        })
        .timeout(healthCheckTimeout)
        .onErrorResume(error -> {
            log.error("Error performing health check", error);
            return Mono.just(Health.down()
                .withDetail("error", error.getMessage())
                .withDetail("errorType", error.getClass().getSimpleName())
                .build());
        });
    }

    /**
     * Gets the health status for a specific service.
     *
     * @param serviceName the service name
     * @return the health status
     */
    public Mono<Health> getServiceHealth(String serviceName) {
        return Mono.fromCallable(() -> {
            ServiceClientHealthManager.ServiceClientHealthStatus status = 
                healthManager.getHealthStatus(serviceName);

            if (status == null) {
                return Health.unknown()
                    .withDetail("message", "Service not registered")
                    .build();
            }

            Map<String, Object> details = new HashMap<>();
            details.put("state", status.getState().name());
            details.put("consecutiveFailures", status.getConsecutiveFailures());
            details.put("lastCheckTime", status.getLastCheckTime().toString());
            
            if (status.getMessage() != null) {
                details.put("message", status.getMessage());
            }
            
            if (status.getLastError() != null) {
                details.put("errorType", status.getLastError().getClass().getSimpleName());
                details.put("errorMessage", status.getLastError().getMessage());
            }

            Health.Builder healthBuilder;
            switch (status.getState()) {
                case HEALTHY:
                    healthBuilder = Health.up();
                    break;
                case DEGRADED:
                    healthBuilder = Health.status("DEGRADED");
                    break;
                case UNHEALTHY:
                    healthBuilder = Health.down();
                    break;
                case UNKNOWN:
                default:
                    healthBuilder = Health.unknown();
                    break;
            }

            return healthBuilder.withDetails(details).build();
        })
        .timeout(healthCheckTimeout)
        .onErrorResume(error -> {
            log.error("Error getting health for service '{}'", serviceName, error);
            return Mono.just(Health.down()
                .withDetail("error", error.getMessage())
                .build());
        });
    }
}

