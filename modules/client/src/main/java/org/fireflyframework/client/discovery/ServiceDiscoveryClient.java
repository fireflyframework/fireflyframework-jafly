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

package org.fireflyframework.client.discovery;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Service discovery client for dynamic endpoint resolution.
 *
 * <p>Supports multiple service discovery mechanisms:
 * <ul>
 *   <li>Kubernetes Service Discovery</li>
 *   <li>Eureka (Netflix OSS)</li>
 *   <li>Consul (HashiCorp)</li>
 *   <li>Static configuration</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ServiceDiscoveryClient discovery = ServiceDiscoveryClient.builder()
 *     .type(ServiceDiscoveryType.KUBERNETES)
 *     .namespace("default")
 *     .build();
 *
 * // Resolve service endpoint
 * String endpoint = discovery.resolveEndpoint("user-service").block();
 *
 * // Get all instances
 * List<ServiceInstance> instances = discovery.getInstances("user-service").collectList().block();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
public interface ServiceDiscoveryClient {

    /**
     * Resolves a service endpoint.
     *
     * @param serviceName the service name
     * @return a Mono containing the resolved endpoint URL
     */
    Mono<String> resolveEndpoint(String serviceName);

    /**
     * Gets all instances of a service.
     *
     * @param serviceName the service name
     * @return a Flux of service instances
     */
    Flux<ServiceInstance> getInstances(String serviceName);

    /**
     * Gets a healthy instance of a service.
     *
     * @param serviceName the service name
     * @return a Mono containing a healthy service instance
     */
    Mono<ServiceInstance> getHealthyInstance(String serviceName);

    /**
     * Registers a service instance.
     *
     * @param instance the service instance to register
     * @return a Mono that completes when registration is done
     */
    Mono<Void> register(ServiceInstance instance);

    /**
     * Deregisters a service instance.
     *
     * @param instanceId the instance ID to deregister
     * @return a Mono that completes when deregistration is done
     */
    Mono<Void> deregister(String instanceId);

    /**
     * Checks if a service is available.
     *
     * @param serviceName the service name
     * @return a Mono containing true if the service is available
     */
    Mono<Boolean> isServiceAvailable(String serviceName);

    /**
     * Service discovery types.
     */
    enum ServiceDiscoveryType {
        KUBERNETES,     // Kubernetes Service Discovery
        EUREKA,         // Netflix Eureka
        CONSUL,         // HashiCorp Consul
        STATIC,         // Static configuration
        DNS             // DNS-based discovery
    }

    /**
     * Represents a service instance.
     */
    record ServiceInstance(
        String instanceId,
        String serviceName,
        String host,
        int port,
        boolean secure,
        HealthStatus healthStatus,
        java.util.Map<String, String> metadata
    ) {
        public String getUri() {
            String scheme = secure ? "https" : "http";
            return String.format("%s://%s:%d", scheme, host, port);
        }

        public boolean isHealthy() {
            return healthStatus == HealthStatus.UP;
        }
    }

    /**
     * Health status of a service instance.
     */
    enum HealthStatus {
        UP,             // Instance is healthy
        DOWN,           // Instance is down
        OUT_OF_SERVICE, // Instance is out of service
        UNKNOWN         // Health status is unknown
    }

    /**
     * Creates a Kubernetes service discovery client.
     */
    static ServiceDiscoveryClient kubernetes(String namespace) {
        return new KubernetesServiceDiscoveryClient(namespace);
    }

    /**
     * Creates an Eureka service discovery client.
     */
    static ServiceDiscoveryClient eureka(String eurekaUrl) {
        return new EurekaServiceDiscoveryClient(eurekaUrl);
    }

    /**
     * Creates a Consul service discovery client.
     */
    static ServiceDiscoveryClient consul(String consulUrl) {
        return new ConsulServiceDiscoveryClient(consulUrl);
    }

    /**
     * Creates a static service discovery client.
     */
    static ServiceDiscoveryClient staticConfig(java.util.Map<String, List<String>> serviceEndpoints) {
        return new StaticServiceDiscoveryClient(serviceEndpoints);
    }
}

