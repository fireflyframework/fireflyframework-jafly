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


package org.fireflyframework.core.config.registry;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Helper class for working with the service registry.
 * <p>
 * This class provides utility methods for discovering services and building
 * URIs to service instances.
 */
@Slf4j
public class ServiceRegistryHelper {

    private final DiscoveryClient discoveryClient;
    private final Environment environment;
    private final Random random = new Random();

    /**
     * Creates a new ServiceRegistryHelper.
     *
     * @param discoveryClient the discovery client
     * @param environment the Spring environment
     */
    public ServiceRegistryHelper(DiscoveryClient discoveryClient, Environment environment) {
        this.discoveryClient = discoveryClient;
        this.environment = environment;
    }

    /**
     * Gets all instances of a service.
     *
     * @param serviceId the service ID
     * @return a list of service instances
     */
    public List<ServiceInstance> getInstances(String serviceId) {
        return discoveryClient.getInstances(serviceId);
    }

    /**
     * Gets a random instance of a service.
     *
     * @param serviceId the service ID
     * @return an optional containing a service instance, or empty if none are available
     */
    public Optional<ServiceInstance> getInstance(String serviceId) {
        List<ServiceInstance> instances = getInstances(serviceId);
        if (instances.isEmpty()) {
            log.warn("No instances found for service: {}", serviceId);
            return Optional.empty();
        }
        return Optional.of(instances.get(random.nextInt(instances.size())));
    }

    /**
     * Builds a URI to a service instance.
     *
     * @param serviceId the service ID
     * @param path the path
     * @return an optional containing the URI, or empty if no instances are available
     */
    public Optional<URI> getServiceUri(String serviceId, String path) {
        return getInstance(serviceId).map(instance -> {
            URI uri = instance.getUri();
            return UriComponentsBuilder.fromUri(uri)
                    .path(path)
                    .build()
                    .toUri();
        });
    }

    /**
     * Gets the current application name.
     *
     * @return the application name
     */
    public String getApplicationName() {
        return environment.getProperty("spring.application.name", "unknown");
    }

    /**
     * Gets all services registered with the discovery client.
     *
     * @return a list of service IDs
     */
    public List<String> getServices() {
        return discoveryClient.getServices();
    }
}
