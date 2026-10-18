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
 * Eureka-based service discovery client.
 *
 * <p>Calls the Eureka REST API to resolve service instances, register, and deregister.
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class EurekaServiceDiscoveryClient implements ServiceDiscoveryClient {

    private final String eurekaUrl;
    private final WebClient webClient;

    public EurekaServiceDiscoveryClient(String eurekaUrl) {
        this.eurekaUrl = eurekaUrl.endsWith("/") ? eurekaUrl.substring(0, eurekaUrl.length() - 1) : eurekaUrl;
        this.webClient = WebClient.builder()
                .baseUrl(this.eurekaUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("Initialized Eureka Service Discovery with URL: {}", this.eurekaUrl);
    }

    @Override
    public Mono<String> resolveEndpoint(String serviceName) {
        log.debug("Resolving endpoint for service: {} via Eureka", serviceName);
        return getHealthyInstance(serviceName)
                .map(ServiceInstance::getUri)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No healthy instance found for service '{}' in Eureka, falling back to name-based resolution",
                            serviceName);
                    return Mono.just("http://" + serviceName.toLowerCase());
                }));
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceName) {
        log.debug("Getting instances for service: {} from Eureka at {}", serviceName, eurekaUrl);
        return webClient.get()
                .uri("/eureka/apps/{appName}", serviceName.toUpperCase())
                .retrieve()
                .bodyToMono(EurekaApplicationResponse.class)
                .flatMapIterable(response -> {
                    if (response.getApplication() == null || response.getApplication().getInstance() == null) {
                        return Collections.emptyList();
                    }
                    return response.getApplication().getInstance().stream()
                            .map(ei -> new ServiceInstance(
                                    ei.getInstanceId(),
                                    serviceName,
                                    ei.getHostName(),
                                    ei.getPort() != null ? ei.getPort().getValue() : 8080,
                                    ei.getSecurePort() != null && ei.getSecurePort().isEnabled(),
                                    "UP".equalsIgnoreCase(ei.getStatus()) ? HealthStatus.UP : HealthStatus.DOWN,
                                    ei.getMetadata() != null ? ei.getMetadata() : Map.of()
                            ))
                            .toList();
                })
                .onErrorResume(e -> {
                    log.error("Failed to get instances for service '{}' from Eureka: {}", serviceName, e.getMessage());
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
        log.info("Registering instance {} with Eureka for service {}", instance.instanceId(), instance.serviceName());

        EurekaInstanceInfo info = new EurekaInstanceInfo();
        info.setInstanceId(instance.instanceId());
        info.setApp(instance.serviceName().toUpperCase());
        info.setHostName(instance.host());
        info.setIpAddr(instance.host());
        info.setStatus("UP");
        EurekaPort port = new EurekaPort();
        port.setValue(instance.port());
        port.setEnabled(true);
        info.setPort(port);

        EurekaRegistrationRequest body = new EurekaRegistrationRequest();
        body.setInstance(info);

        return webClient.post()
                .uri("/eureka/apps/{appName}", instance.serviceName().toUpperCase())
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Successfully registered {} with Eureka", instance.instanceId()))
                .doOnError(e -> log.error("Failed to register {} with Eureka: {}", instance.instanceId(), e.getMessage()))
                .then();
    }

    @Override
    public Mono<Void> deregister(String instanceId) {
        log.info("Deregistering instance {} from Eureka", instanceId);

        // Eureka instanceId format is typically: hostname:appName:port
        // The DELETE endpoint is: /eureka/apps/{appName}/{instanceId}
        String[] parts = instanceId.split(":");
        String appName = parts.length > 1 ? parts[1] : instanceId;

        return webClient.delete()
                .uri("/eureka/apps/{appName}/{instanceId}", appName.toUpperCase(), instanceId)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Successfully deregistered {} from Eureka", instanceId))
                .doOnError(e -> log.error("Failed to deregister {} from Eureka: {}", instanceId, e.getMessage()))
                .then();
    }

    @Override
    public Mono<Boolean> isServiceAvailable(String serviceName) {
        return getInstances(serviceName).hasElements();
    }

    // --- Eureka REST API DTOs ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EurekaApplicationResponse {
        private EurekaApplication application;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EurekaApplication {
        private String name;
        private List<EurekaInstanceInfo> instance;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EurekaInstanceInfo {
        private String instanceId;
        private String app;
        private String hostName;
        private String ipAddr;
        private String status;
        private EurekaPort port;
        private EurekaPort securePort;
        private Map<String, String> metadata;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EurekaPort {
        @JsonProperty("$")
        private int value;
        @JsonProperty("@enabled")
        private boolean enabled;
    }

    @Data
    static class EurekaRegistrationRequest {
        private EurekaInstanceInfo instance;
    }
}

