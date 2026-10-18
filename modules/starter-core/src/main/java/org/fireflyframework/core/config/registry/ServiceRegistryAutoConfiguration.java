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

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for service registry clients.
 * <p>
 * This class provides auto-configuration for service registry clients
 * such as Eureka and Consul, which enable service discovery in a microservices
 * architecture.
 * <p>
 * The configuration is automatically enabled when the appropriate service registry
 * client dependency is present and the service.registry.enabled property is set to true.
 */
@Configuration
@ConditionalOnProperty(prefix = "firefly.service.registry", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ServiceRegistryProperties.class)
@Slf4j
public class ServiceRegistryAutoConfiguration {

    private final ServiceRegistryProperties properties;
    private final Environment environment;

    public ServiceRegistryAutoConfiguration(ServiceRegistryProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        log.info("Initializing service registry client of type: {}", properties.getType());
    }

    /**
     * Configuration for Eureka client.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration")
    @ConditionalOnProperty(prefix = "firefly.service.registry", name = "type", havingValue = "EUREKA", matchIfMissing = true)
    public static class EurekaClientConfiguration {

        private final ServiceRegistryProperties properties;

        public EurekaClientConfiguration(ServiceRegistryProperties properties) {
            this.properties = properties;
            log.info("Configuring Eureka client with service URL: {}", properties.getEureka().getServiceUrl());
        }

        /**
         * Sets system properties for Eureka client configuration.
         * This is done to avoid having to create a separate eureka.client.* configuration.
         */
        @ConditionalOnMissingBean
        @Bean
        public void configureEurekaClient() {
            // Set system properties for Eureka client
            System.setProperty("eureka.client.serviceUrl.defaultZone", properties.getEureka().getServiceUrl());
            System.setProperty("eureka.client.register-with-eureka", String.valueOf(properties.getEureka().isRegister()));
            System.setProperty("eureka.client.fetch-registry", String.valueOf(properties.getEureka().isFetchRegistry()));
            System.setProperty("eureka.client.registry-fetch-interval-seconds", 
                    String.valueOf(properties.getEureka().getRegistryFetchIntervalSeconds()));
            
            // Instance configuration
            if (!properties.getEureka().getInstanceId().isEmpty()) {
                System.setProperty("eureka.instance.instance-id", properties.getEureka().getInstanceId());
            }
            System.setProperty("eureka.instance.prefer-ip-address", 
                    String.valueOf(properties.getEureka().isPreferIpAddress()));
            System.setProperty("eureka.instance.lease-renewal-interval-in-seconds", 
                    String.valueOf(properties.getEureka().getLeaseRenewalIntervalInSeconds()));
            System.setProperty("eureka.instance.lease-expiration-duration-in-seconds", 
                    String.valueOf(properties.getEureka().getLeaseExpirationDurationInSeconds()));
            
            // Health check
            System.setProperty("eureka.instance.health-check-enabled", 
                    String.valueOf(properties.getEureka().isHealthCheckEnabled()));
            System.setProperty("eureka.instance.health-check-url-path", 
                    properties.getEureka().getHealthCheckUrlPath());
            System.setProperty("eureka.instance.status-page-url-path", 
                    properties.getEureka().getStatusPageUrlPath());
        }
    }

    /**
     * Configuration for Consul client.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.cloud.consul.discovery.ConsulDiscoveryClientConfiguration")
    @ConditionalOnProperty(prefix = "firefly.service.registry", name = "type", havingValue = "CONSUL")
    public static class ConsulClientConfiguration {

        private final ServiceRegistryProperties properties;

        public ConsulClientConfiguration(ServiceRegistryProperties properties) {
            this.properties = properties;
            log.info("Configuring Consul client with host: {} and port: {}", 
                    properties.getConsul().getHost(), properties.getConsul().getPort());
        }

        /**
         * Sets system properties for Consul client configuration.
         * This is done to avoid having to create a separate consul.* configuration.
         */
        @ConditionalOnMissingBean
        @Bean
        public void configureConsulClient() {
            // Set system properties for Consul client
            System.setProperty("spring.cloud.consul.host", properties.getConsul().getHost());
            System.setProperty("spring.cloud.consul.port", String.valueOf(properties.getConsul().getPort()));
            
            // Discovery configuration
            System.setProperty("spring.cloud.consul.discovery.register", 
                    String.valueOf(properties.getConsul().isRegister()));
            System.setProperty("spring.cloud.consul.discovery.deregister", 
                    String.valueOf(properties.getConsul().isDeregister()));
            
            // Service configuration
            if (!properties.getConsul().getServiceName().isEmpty()) {
                System.setProperty("spring.cloud.consul.discovery.service-name", 
                        properties.getConsul().getServiceName());
            }
            if (!properties.getConsul().getInstanceId().isEmpty()) {
                System.setProperty("spring.cloud.consul.discovery.instance-id", 
                        properties.getConsul().getInstanceId());
            }
            if (properties.getConsul().getTags().length > 0) {
                System.setProperty("spring.cloud.consul.discovery.tags", 
                        String.join(",", properties.getConsul().getTags()));
            }
            
            // Health check
            System.setProperty("spring.cloud.consul.discovery.health-check-interval", 
                    properties.getConsul().getHealthCheckInterval() + "s");
            System.setProperty("spring.cloud.consul.discovery.health-check-timeout", 
                    properties.getConsul().getHealthCheckTimeout() + "s");
            System.setProperty("spring.cloud.consul.discovery.health-check-path", 
                    properties.getConsul().getHealthCheckPath());
            System.setProperty("spring.cloud.consul.discovery.health-check-critical-timeout", "30s");
            System.setProperty("spring.cloud.consul.discovery.health-check-tls-skip-verify", "true");
            
            // Catalog services watch
            System.setProperty("spring.cloud.consul.discovery.catalog-services-watch.enabled", 
                    String.valueOf(properties.getConsul().isCatalogServicesWatch()));
            System.setProperty("spring.cloud.consul.discovery.catalog-services-watch-timeout", 
                    properties.getConsul().getCatalogServicesWatchTimeout() + "s");
            System.setProperty("spring.cloud.consul.discovery.catalog-services-watch-delay", 
                    properties.getConsul().getCatalogServicesWatchDelay() + "ms");
        }
    }

    /**
     * Fallback discovery client when no service registry is available.
     * This is useful for testing or when running in environments without a service registry.
     *
     * @return a simple discovery client
     */
    @Bean
    @ConditionalOnMissingBean(DiscoveryClient.class)
    public SimpleDiscoveryClient simpleDiscoveryClient() {
        log.info("No service registry client found, using SimpleDiscoveryClient as fallback");
        SimpleDiscoveryProperties properties = new SimpleDiscoveryProperties();
        return new SimpleDiscoveryClient(properties);
    }

    /**
     * Service registry helper that provides utility methods for working with the service registry.
     *
     * @param discoveryClient the discovery client
     * @return the service registry helper
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceRegistryHelper serviceRegistryHelper(DiscoveryClient discoveryClient) {
        return new ServiceRegistryHelper(discoveryClient, environment);
    }
}
