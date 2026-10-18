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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Consul-based service discovery client.
 *
 * <p>Calls the Consul HTTP API to resolve healthy service instances, register, and deregister.
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class ConsulServiceDiscoveryClient implements ServiceDiscoveryClient {

    private final String consulUrl;
    private final WebClient webClient;

    public ConsulServiceDiscoveryClient(String consulUrl) {
        this.consulUrl = consulUrl.endsWith("/") ? consulUrl.substring(0, consulUrl.length() - 1) : consulUrl;
        this.webClient = WebClient.builder()
                .baseUrl(this.consulUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("Initialized Consul Service Discovery with URL: {}", this.consulUrl);
    }

    @Override
    public Mono<String> resolveEndpoint(String serviceName) {
        log.debug("Resolving endpoint for service: {} via Consul", serviceName);
        return getHealthyInstance(serviceName)
                .map(ServiceInstance::getUri)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No healthy instance found for service '{}' in Consul, falling back to name-based resolution",
                            serviceName);
                    return Mono.just("http://" + serviceName.toLowerCase());
                }));
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceName) {
        log.debug("Getting instances for service: {} from Consul at {}", serviceName, consulUrl);
        return webClient.get()
                .uri("/v1/health/service/{serviceName}?passing=true", serviceName)
                .retrieve()
                .bodyToFlux(ConsulHealthServiceEntry.class)
                .map(entry -> {
                    ConsulService svc = entry.getService();
                    ConsulCheck[] checks = entry.getChecks();

                    HealthStatus status = HealthStatus.UP;
                    if (checks != null) {
                        for (ConsulCheck check : checks) {
                            if (!"passing".equalsIgnoreCase(check.getStatus())) {
                                status = HealthStatus.DOWN;
                                break;
                            }
                        }
                    }

                    return new ServiceInstance(
                            svc.getId(),
                            serviceName,
                            svc.getAddress() != null ? svc.getAddress() : entry.getNode().getAddress(),
                            svc.getPort(),
                            false,
                            status,
                            svc.getMeta() != null ? svc.getMeta() : Map.of()
                    );
                })
                .onErrorResume(e -> {
                    log.error("Failed to get instances for service '{}' from Consul: {}", serviceName, e.getMessage());
                    return Flux.empty();
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
        log.info("Registering instance {} with Consul for service {}", instance.instanceId(), instance.serviceName());

        ConsulServiceRegistration reg = new ConsulServiceRegistration();
        reg.setId(instance.instanceId());
        reg.setName(instance.serviceName());
        reg.setAddress(instance.host());
        reg.setPort(instance.port());
        reg.setMeta(instance.metadata());

        return webClient.put()
                .uri("/v1/agent/service/register")
                .bodyValue(reg)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Successfully registered {} with Consul", instance.instanceId()))
                .doOnError(e -> log.error("Failed to register {} with Consul: {}", instance.instanceId(), e.getMessage()))
                .then();
    }

    @Override
    public Mono<Void> deregister(String instanceId) {
        log.info("Deregistering instance {} from Consul", instanceId);
        return webClient.put()
                .uri("/v1/agent/service/deregister/{serviceId}", instanceId)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Successfully deregistered {} from Consul", instanceId))
                .doOnError(e -> log.error("Failed to deregister {} from Consul: {}", instanceId, e.getMessage()))
                .then();
    }

    @Override
    public Mono<Boolean> isServiceAvailable(String serviceName) {
        return getInstances(serviceName).hasElements();
    }

    // --- Consul REST API DTOs ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ConsulHealthServiceEntry {
        @JsonProperty("Node")
        private ConsulNode node;
        @JsonProperty("Service")
        private ConsulService service;
        @JsonProperty("Checks")
        private ConsulCheck[] checks;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ConsulNode {
        @JsonProperty("Address")
        private String address;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ConsulService {
        @JsonProperty("ID")
        private String id;
        @JsonProperty("Service")
        private String service;
        @JsonProperty("Address")
        private String address;
        @JsonProperty("Port")
        private int port;
        @JsonProperty("Meta")
        private Map<String, String> meta;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ConsulCheck {
        @JsonProperty("Status")
        private String status;
    }

    @Data
    static class ConsulServiceRegistration {
        @JsonProperty("ID")
        private String id;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Address")
        private String address;
        @JsonProperty("Port")
        private int port;
        @JsonProperty("Meta")
        private Map<String, String> meta;
    }
}

