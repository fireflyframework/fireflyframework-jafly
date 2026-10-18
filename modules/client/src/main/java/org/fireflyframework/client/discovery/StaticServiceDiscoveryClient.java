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

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static configuration-based service discovery client.
 * 
 * <p>Uses a static map of service names to endpoints.
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class StaticServiceDiscoveryClient implements ServiceDiscoveryClient {

    private final Map<String, List<String>> serviceEndpoints;

    public StaticServiceDiscoveryClient(Map<String, List<String>> serviceEndpoints) {
        this.serviceEndpoints = new ConcurrentHashMap<>(serviceEndpoints);
        log.info("Initialized Static Service Discovery with {} services", serviceEndpoints.size());
    }

    @Override
    public Mono<String> resolveEndpoint(String serviceName) {
        List<String> endpoints = serviceEndpoints.get(serviceName);
        if (endpoints == null || endpoints.isEmpty()) {
            return Mono.error(new IllegalArgumentException("No endpoints found for service: " + serviceName));
        }
        // Return first endpoint
        return Mono.just(endpoints.get(0));
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceName) {
        List<String> endpoints = serviceEndpoints.get(serviceName);
        if (endpoints == null) {
            return Flux.empty();
        }

        return Flux.fromIterable(endpoints)
            .map(endpoint -> {
                // Parse endpoint URL
                boolean secure = endpoint.startsWith("https://");
                String hostPort = endpoint.replace("https://", "").replace("http://", "");
                String[] parts = hostPort.split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : (secure ? 443 : 80);

                return new ServiceInstance(
                    serviceName + "-" + host + "-" + port,
                    serviceName,
                    host,
                    port,
                    secure,
                    HealthStatus.UP,
                    Map.of("type", "static")
                );
            });
    }

    @Override
    public Mono<ServiceInstance> getHealthyInstance(String serviceName) {
        return getInstances(serviceName)
            .filter(ServiceInstance::isHealthy)
            .next();
    }

    @Override
    public Mono<Void> register(ServiceInstance instance) {
        log.debug("Static discovery does not support registration");
        return Mono.empty();
    }

    @Override
    public Mono<Void> deregister(String instanceId) {
        log.debug("Static discovery does not support deregistration");
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> isServiceAvailable(String serviceName) {
        return Mono.just(serviceEndpoints.containsKey(serviceName) && 
                        !serviceEndpoints.get(serviceName).isEmpty());
    }
}

