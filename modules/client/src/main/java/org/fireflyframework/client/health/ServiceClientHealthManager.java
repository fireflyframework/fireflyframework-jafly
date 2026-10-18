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

import org.fireflyframework.client.ServiceClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Health check manager for ServiceClient instances.
 * 
 * <p>This manager provides comprehensive health monitoring for all registered ServiceClient
 * instances, including periodic health checks, failure detection, and recovery monitoring.
 *
 * <p>Features:
 * <ul>
 *   <li>Periodic health checks with configurable intervals</li>
 *   <li>Failure detection and alerting</li>
 *   <li>Health status aggregation</li>
 *   <li>Recovery monitoring</li>
 *   <li>Health metrics collection</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class ServiceClientHealthManager {

    private final Map<String, ServiceClientHealthStatus> healthStatuses = new ConcurrentHashMap<>();
    private final Map<String, ServiceClient> registeredClients = new ConcurrentHashMap<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Duration healthCheckInterval;
    private final Duration healthCheckTimeout;
    private final int maxConsecutiveFailures;

    public ServiceClientHealthManager(Duration healthCheckInterval, Duration healthCheckTimeout, int maxConsecutiveFailures) {
        this.healthCheckInterval = healthCheckInterval;
        this.healthCheckTimeout = healthCheckTimeout;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    /**
     * Registers a ServiceClient for health monitoring.
     */
    public void registerClient(ServiceClient client) {
        String serviceName = client.getServiceName();
        registeredClients.put(serviceName, client);
        healthStatuses.put(serviceName, new ServiceClientHealthStatus(serviceName, HealthState.UNKNOWN));
        
        log.info("Registered ServiceClient '{}' for health monitoring", serviceName);
    }

    /**
     * Unregisters a ServiceClient from health monitoring.
     */
    public void unregisterClient(String serviceName) {
        registeredClients.remove(serviceName);
        healthStatuses.remove(serviceName);
        
        log.info("Unregistered ServiceClient '{}' from health monitoring", serviceName);
    }

    /**
     * Starts the health check monitoring.
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("Starting ServiceClient health monitoring with interval: {}", healthCheckInterval);
            
            Flux.interval(healthCheckInterval, Schedulers.boundedElastic())
                .takeWhile(tick -> isRunning.get())
                .flatMap(tick -> performHealthChecks())
                .subscribe(
                    healthStatus -> log.debug("Health check completed for service: {}", healthStatus.getServiceName()),
                    error -> log.error("Error during health check monitoring", error),
                    () -> log.info("Health check monitoring stopped")
                );
        }
    }

    /**
     * Stops the health check monitoring.
     */
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("Stopping ServiceClient health monitoring");
        }
    }

    /**
     * Performs health checks for all registered clients.
     */
    public Flux<ServiceClientHealthStatus> performHealthChecks() {
        return Flux.fromIterable(registeredClients.entrySet())
            .flatMap(entry -> performHealthCheck(entry.getKey(), entry.getValue()))
            .doOnComplete(() -> log.debug("Completed health checks for {} services", registeredClients.size()));
    }

    /**
     * Performs a health check for a specific client.
     */
    public Mono<ServiceClientHealthStatus> performHealthCheck(String serviceName, ServiceClient client) {
        return client.healthCheck()
            .timeout(healthCheckTimeout)
            .then(Mono.fromCallable(() -> handleHealthCheckSuccess(serviceName))) // Handle empty success
            .onErrorResume(error -> Mono.just(handleHealthCheckFailure(serviceName, error)))
            .doOnNext(status -> updateHealthStatus(serviceName, status));
    }

    /**
     * Gets the current health status for a specific service.
     */
    public ServiceClientHealthStatus getHealthStatus(String serviceName) {
        return healthStatuses.get(serviceName);
    }

    /**
     * Gets the current health status for all services.
     */
    public Map<String, ServiceClientHealthStatus> getAllHealthStatuses() {
        return Map.copyOf(healthStatuses);
    }

    /**
     * Gets the overall health state across all services.
     */
    public OverallHealthState getOverallHealthState() {
        if (healthStatuses.isEmpty()) {
            return OverallHealthState.UNKNOWN;
        }

        boolean hasUnhealthy = healthStatuses.values().stream()
            .anyMatch(status -> status.getState() == HealthState.UNHEALTHY);
        
        boolean hasDegraded = healthStatuses.values().stream()
            .anyMatch(status -> status.getState() == HealthState.DEGRADED);

        boolean hasUnknown = healthStatuses.values().stream()
            .anyMatch(status -> status.getState() == HealthState.UNKNOWN);

        if (hasUnhealthy) {
            return OverallHealthState.UNHEALTHY;
        } else if (hasDegraded) {
            return OverallHealthState.DEGRADED;
        } else if (hasUnknown) {
            return OverallHealthState.UNKNOWN;
        } else {
            return OverallHealthState.HEALTHY;
        }
    }

    private ServiceClientHealthStatus handleHealthCheckSuccess(String serviceName) {
        ServiceClientHealthStatus currentStatus = healthStatuses.get(serviceName);
        
        if (currentStatus != null && currentStatus.getState() != HealthState.HEALTHY) {
            log.info("Service '{}' recovered - health check successful", serviceName);
        }

        return new ServiceClientHealthStatus(
            serviceName,
            HealthState.HEALTHY,
            Instant.now(),
            0,
            "Health check successful",
            null
        );
    }

    private ServiceClientHealthStatus handleHealthCheckFailure(String serviceName, Throwable error) {
        ServiceClientHealthStatus currentStatus = healthStatuses.get(serviceName);
        int consecutiveFailures = currentStatus != null ? currentStatus.getConsecutiveFailures() + 1 : 1;

        HealthState newState;
        if (consecutiveFailures >= maxConsecutiveFailures) {
            newState = HealthState.UNHEALTHY;
        } else {
            newState = HealthState.DEGRADED;
        }

        if (currentStatus == null || currentStatus.getState() != newState) {
            log.warn("Service '{}' health check failed (attempt {}/{}): {}", 
                serviceName, consecutiveFailures, maxConsecutiveFailures, error.getMessage());
        }

        return new ServiceClientHealthStatus(
            serviceName,
            newState,
            Instant.now(),
            consecutiveFailures,
            "Health check failed: " + error.getMessage(),
            error
        );
    }

    private void updateHealthStatus(String serviceName, ServiceClientHealthStatus newStatus) {
        ServiceClientHealthStatus oldStatus = healthStatuses.put(serviceName, newStatus);
        
        // Log state changes
        if (oldStatus == null || oldStatus.getState() != newStatus.getState()) {
            log.info("Service '{}' health state changed from {} to {}", 
                serviceName, 
                oldStatus != null ? oldStatus.getState() : "UNKNOWN", 
                newStatus.getState());
        }
    }

    /**
     * Health state enumeration.
     */
    public enum HealthState {
        HEALTHY,    // Service is responding normally
        DEGRADED,   // Service is responding but with some failures
        UNHEALTHY,  // Service is not responding or consistently failing
        UNKNOWN     // Health state is not yet determined
    }

    /**
     * Overall health state across all services.
     */
    public enum OverallHealthState {
        HEALTHY,    // All services are healthy
        DEGRADED,   // Some services are degraded but none are unhealthy
        UNHEALTHY,  // At least one service is unhealthy
        UNKNOWN     // Health state cannot be determined
    }

    /**
     * Represents the health status of a specific ServiceClient.
     */
    public static class ServiceClientHealthStatus {
        private final String serviceName;
        private final HealthState state;
        private final Instant lastCheckTime;
        private final int consecutiveFailures;
        private final String message;
        private final Throwable lastError;

        public ServiceClientHealthStatus(String serviceName, HealthState state) {
            this(serviceName, state, Instant.now(), 0, null, null);
        }

        public ServiceClientHealthStatus(String serviceName, HealthState state, Instant lastCheckTime, 
                                       int consecutiveFailures, String message, Throwable lastError) {
            this.serviceName = serviceName;
            this.state = state;
            this.lastCheckTime = lastCheckTime;
            this.consecutiveFailures = consecutiveFailures;
            this.message = message;
            this.lastError = lastError;
        }

        public String getServiceName() { return serviceName; }
        public HealthState getState() { return state; }
        public Instant getLastCheckTime() { return lastCheckTime; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public String getMessage() { return message; }
        public Throwable getLastError() { return lastError; }

        public boolean isHealthy() {
            return state == HealthState.HEALTHY;
        }

        public boolean isUnhealthy() {
            return state == HealthState.UNHEALTHY;
        }

        @Override
        public String toString() {
            return String.format("ServiceClientHealthStatus{serviceName='%s', state=%s, consecutiveFailures=%d, message='%s'}", 
                serviceName, state, consecutiveFailures, message);
        }
    }
}
