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

import java.util.HashMap;
import java.util.Map;

/**
 * Kubernetes-based service discovery client.
 * 
 * <p>Resolves services using Kubernetes DNS and Service API.
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class KubernetesServiceDiscoveryClient implements ServiceDiscoveryClient {

    private final String namespace;

    public KubernetesServiceDiscoveryClient(String namespace) {
        this.namespace = namespace;
        log.info("Initialized Kubernetes Service Discovery for namespace: {}", namespace);
    }

    @Override
    public Mono<String> resolveEndpoint(String serviceName) {
        // Kubernetes DNS format: <service-name>.<namespace>.svc.cluster.local
        String endpoint = String.format("http://%s.%s.svc.cluster.local", serviceName, namespace);
        log.debug("Resolved Kubernetes endpoint for {}: {}", serviceName, endpoint);
        return Mono.just(endpoint);
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceName) {
        // In Kubernetes, we typically get a single service endpoint
        // For actual pod discovery, you'd use the Kubernetes API
        return Mono.just(new ServiceInstance(
            serviceName + "-instance",
            serviceName,
            serviceName + "." + namespace + ".svc.cluster.local",
            80,
            false,
            HealthStatus.UP,
            Map.of("namespace", namespace)
        )).flux();
    }

    @Override
    public Mono<ServiceInstance> getHealthyInstance(String serviceName) {
        return getInstances(serviceName)
            .filter(ServiceInstance::isHealthy)
            .next();
    }

    @Override
    public Mono<Void> register(ServiceInstance instance) {
        // Kubernetes handles registration automatically
        log.debug("Kubernetes handles service registration automatically");
        return Mono.empty();
    }

    @Override
    public Mono<Void> deregister(String instanceId) {
        // Kubernetes handles deregistration automatically
        log.debug("Kubernetes handles service deregistration automatically");
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> isServiceAvailable(String serviceName) {
        return getInstances(serviceName)
            .hasElements();
    }
}

