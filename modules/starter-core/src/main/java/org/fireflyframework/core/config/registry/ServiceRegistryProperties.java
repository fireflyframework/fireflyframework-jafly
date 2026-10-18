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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for service registry.
 * <p>
 * This class defines properties for configuring service registry clients
 * such as Eureka and Consul, which enable service discovery in a microservices
 * architecture.
 * <p>
 * Properties can be configured in application.yml or application.properties using
 * the prefix "service.registry".
 */
@Validated
@ConfigurationProperties(prefix = "firefly.service.registry")
@Getter
@Setter
public class ServiceRegistryProperties {

    /**
     * Whether to enable service registry.
     * Disabled by default.
     */
    private boolean enabled = false;

    /**
     * Type of service registry to use.
     */
    private RegistryType type = RegistryType.EUREKA;

    /**
     * Eureka client configuration.
     */
    private EurekaConfig eureka = new EurekaConfig();

    /**
     * Consul client configuration.
     */
    private ConsulConfig consul = new ConsulConfig();

    /**
     * Enum representing the type of service registry.
     */
    public enum RegistryType {
        /**
         * Netflix Eureka service registry.
         */
        EUREKA,

        /**
         * HashiCorp Consul service registry.
         */
        CONSUL
    }

    /**
     * Configuration properties for Eureka client.
     */
    @Getter
    @Setter
    public static class EurekaConfig {
        /**
         * Service URL of the Eureka server.
         */
        private String serviceUrl = "http://localhost:8761/eureka/";

        /**
         * Whether to register with Eureka.
         */
        private boolean register = true;

        /**
         * Whether to fetch registry from Eureka.
         */
        private boolean fetchRegistry = true;

        /**
         * Registry fetch interval in seconds.
         */
        private int registryFetchIntervalSeconds = 30;

        /**
         * Instance ID to use when registering with Eureka.
         * If not specified, a default ID will be generated.
         */
        private String instanceId = "";

        /**
         * Prefer IP address rather than hostname for registration.
         */
        private boolean preferIpAddress = true;

        /**
         * Lease renewal interval in seconds.
         */
        private int leaseRenewalIntervalInSeconds = 30;

        /**
         * Lease expiration duration in seconds.
         */
        private int leaseExpirationDurationInSeconds = 90;

        /**
         * Whether to enable health check.
         */
        private boolean healthCheckEnabled = true;

        /**
         * Health check URL path.
         */
        private String healthCheckUrlPath = "/actuator/health";

        /**
         * Status page URL path.
         */
        private String statusPageUrlPath = "/actuator/info";
    }

    /**
     * Configuration properties for Consul client.
     */
    @Getter
    @Setter
    public static class ConsulConfig {
        /**
         * Host of the Consul server.
         */
        private String host = "localhost";

        /**
         * Port of the Consul server.
         */
        private int port = 8500;

        /**
         * Whether to register with Consul.
         */
        private boolean register = true;

        /**
         * Whether to deregister on shutdown.
         */
        private boolean deregister = true;

        /**
         * Service name to use when registering with Consul.
         * If not specified, the spring.application.name will be used.
         */
        private String serviceName = "";

        /**
         * Instance ID to use when registering with Consul.
         * If not specified, a default ID will be generated.
         */
        private String instanceId = "";

        /**
         * Tags to apply to the service.
         */
        private String[] tags = new String[0];

        /**
         * Health check interval in seconds.
         */
        private int healthCheckInterval = 10;

        /**
         * Health check timeout in seconds.
         */
        private int healthCheckTimeout = 5;

        /**
         * Health check URL path.
         */
        private String healthCheckPath = "/actuator/health";

        /**
         * Whether to enable health check.
         */
        private boolean healthCheckEnabled = true;

        /**
         * Whether to use the catalog services API.
         */
        private boolean catalogServicesWatch = true;

        /**
         * Catalog services watch timeout in seconds.
         */
        private int catalogServicesWatchTimeout = 10;

        /**
         * Catalog services watch delay in milliseconds.
         */
        private int catalogServicesWatchDelay = 1000;
    }
}
