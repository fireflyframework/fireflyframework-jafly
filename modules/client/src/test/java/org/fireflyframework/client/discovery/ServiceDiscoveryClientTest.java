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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for ServiceDiscoveryClient implementations.
 * Tests all discovery mechanisms with real endpoint resolution.
 */
@DisplayName("Service Discovery Client Tests")
class ServiceDiscoveryClientTest {

    @Nested
    @DisplayName("Static Service Discovery Tests")
    class StaticServiceDiscoveryTests {

        @Test
        @DisplayName("Should create static discovery client")
        void shouldCreateStaticDiscoveryClient() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "user-service", List.of("http://localhost:8080", "http://localhost:8081")
            );

            // When
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // Then
            assertThat(client).isInstanceOf(StaticServiceDiscoveryClient.class);
        }

        @Test
        @DisplayName("Should resolve single static endpoint")
        void shouldResolveStaticEndpoint() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "user-service", List.of("http://localhost:8080")
            );
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // When/Then
            StepVerifier.create(client.resolveEndpoint("user-service"))
                .expectNext("http://localhost:8080")
                .verifyComplete();
        }

        @Test
        @DisplayName("Should resolve first endpoint when multiple available")
        void shouldResolveFirstEndpointWhenMultiple() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "user-service", List.of("http://localhost:8080", "http://localhost:8081", "http://localhost:8082")
            );
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // When/Then
            StepVerifier.create(client.resolveEndpoint("user-service"))
                .expectNext("http://localhost:8080")
                .verifyComplete();
        }

        @Test
        @DisplayName("Should get all static instances")
        void shouldGetStaticInstances() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "user-service", List.of("http://localhost:8080", "http://localhost:8081")
            );
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // When/Then
            StepVerifier.create(client.getInstances("user-service").collectList())
                .assertNext(instances -> {
                    assertThat(instances).hasSize(2);
                    assertThat(instances.get(0).serviceName()).isEqualTo("user-service");
                    assertThat(instances.get(0).host()).isEqualTo("localhost");
                    assertThat(instances.get(0).port()).isEqualTo(8080);
                    assertThat(instances.get(0).healthStatus()).isEqualTo(ServiceDiscoveryClient.HealthStatus.UP);
                    assertThat(instances.get(1).port()).isEqualTo(8081);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should parse HTTPS endpoints correctly")
        void shouldParseHttpsEndpoints() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "secure-service", List.of("https://api.example.com:443")
            );
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // When/Then
            StepVerifier.create(client.getInstances("secure-service").collectList())
                .assertNext(instances -> {
                    assertThat(instances).hasSize(1);
                    assertThat(instances.get(0).secure()).isTrue();
                    assertThat(instances.get(0).host()).isEqualTo("api.example.com");
                    assertThat(instances.get(0).port()).isEqualTo(443);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should get healthy instance")
        void shouldGetHealthyInstance() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "user-service", List.of("http://localhost:8080", "http://localhost:8081")
            );
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // When/Then
            StepVerifier.create(client.getHealthyInstance("user-service"))
                .assertNext(instance -> {
                    assertThat(instance.serviceName()).isEqualTo("user-service");
                    assertThat(instance.isHealthy()).isTrue();
                    assertThat(instance.healthStatus()).isEqualTo(ServiceDiscoveryClient.HealthStatus.UP);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should check if service is available")
        void shouldCheckServiceAvailability() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "user-service", List.of("http://localhost:8080")
            );
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // When/Then
            StepVerifier.create(client.isServiceAvailable("user-service"))
                .expectNext(true)
                .verifyComplete();

            StepVerifier.create(client.isServiceAvailable("non-existent-service"))
                .expectNext(false)
                .verifyComplete();
        }

        @Test
        @DisplayName("Should handle error when service not found")
        void shouldHandleServiceNotFound() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "user-service", List.of("http://localhost:8080")
            );
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // When/Then
            StepVerifier.create(client.resolveEndpoint("non-existent-service"))
                .expectErrorMatches(error ->
                    error instanceof IllegalArgumentException &&
                    error.getMessage().contains("No endpoints found for service"))
                .verify();
        }

        @Test
        @DisplayName("Should handle multiple services")
        void shouldHandleMultipleServices() {
            // Given
            Map<String, List<String>> endpoints = Map.of(
                "user-service", List.of("http://localhost:8080"),
                "payment-service", List.of("http://localhost:9090"),
                "notification-service", List.of("http://localhost:7070", "http://localhost:7071")
            );
            ServiceDiscoveryClient client = ServiceDiscoveryClient.staticConfig(endpoints);

            // When/Then - Verify each service
            StepVerifier.create(client.resolveEndpoint("user-service"))
                .expectNext("http://localhost:8080")
                .verifyComplete();

            StepVerifier.create(client.resolveEndpoint("payment-service"))
                .expectNext("http://localhost:9090")
                .verifyComplete();

            StepVerifier.create(client.getInstances("notification-service").collectList())
                .assertNext(instances -> assertThat(instances).hasSize(2))
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Kubernetes Service Discovery Tests")
    class KubernetesServiceDiscoveryTests {

        @Test
        @DisplayName("Should create Kubernetes discovery client")
        void shouldCreateKubernetesDiscoveryClient() {
            // When
            ServiceDiscoveryClient client = ServiceDiscoveryClient.kubernetes("default");

            // Then
            assertThat(client).isInstanceOf(KubernetesServiceDiscoveryClient.class);
        }

        @Test
        @DisplayName("Should resolve Kubernetes DNS endpoint")
        void shouldResolveKubernetesDnsEndpoint() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.kubernetes("default");

            // When/Then - Verify Kubernetes DNS format
            StepVerifier.create(client.resolveEndpoint("user-service"))
                .assertNext(endpoint -> {
                    assertThat(endpoint).isEqualTo("http://user-service.default.svc.cluster.local");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should resolve endpoint for different namespace")
        void shouldResolveEndpointForDifferentNamespace() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.kubernetes("production");

            // When/Then
            StepVerifier.create(client.resolveEndpoint("payment-service"))
                .assertNext(endpoint -> {
                    assertThat(endpoint).isEqualTo("http://payment-service.production.svc.cluster.local");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should get Kubernetes service instance")
        void shouldGetKubernetesServiceInstance() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.kubernetes("default");

            // When/Then
            StepVerifier.create(client.getInstances("user-service").collectList())
                .assertNext(instances -> {
                    assertThat(instances).hasSize(1);
                    ServiceDiscoveryClient.ServiceInstance instance = instances.get(0);
                    assertThat(instance.serviceName()).isEqualTo("user-service");
                    assertThat(instance.host()).isEqualTo("user-service.default.svc.cluster.local");
                    assertThat(instance.port()).isEqualTo(80);
                    assertThat(instance.secure()).isFalse();
                    assertThat(instance.healthStatus()).isEqualTo(ServiceDiscoveryClient.HealthStatus.UP);
                    assertThat(instance.metadata()).containsEntry("namespace", "default");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should check Kubernetes service availability")
        void shouldCheckKubernetesServiceAvailability() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.kubernetes("default");

            // When/Then
            StepVerifier.create(client.isServiceAvailable("any-service"))
                .expectNext(true)
                .verifyComplete();
        }

        @Test
        @DisplayName("Should get healthy Kubernetes instance")
        void shouldGetHealthyKubernetesInstance() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.kubernetes("default");

            // When/Then
            StepVerifier.create(client.getHealthyInstance("user-service"))
                .assertNext(instance -> {
                    assertThat(instance.isHealthy()).isTrue();
                    assertThat(instance.healthStatus()).isEqualTo(ServiceDiscoveryClient.HealthStatus.UP);
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Eureka Service Discovery Tests")
    class EurekaServiceDiscoveryTests {

        @Test
        @DisplayName("Should create Eureka discovery client")
        void shouldCreateEurekaDiscoveryClient() {
            // When
            ServiceDiscoveryClient client = ServiceDiscoveryClient.eureka("http://eureka:8761");

            // Then
            assertThat(client).isInstanceOf(EurekaServiceDiscoveryClient.class);
        }

        @Test
        @DisplayName("Should resolve Eureka endpoint")
        void shouldResolveEurekaEndpoint() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.eureka("http://eureka:8761");

            // When/Then
            StepVerifier.create(client.resolveEndpoint("USER-SERVICE"))
                .assertNext(endpoint -> {
                    assertThat(endpoint).isEqualTo("http://user-service");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should handle service name case conversion")
        void shouldHandleServiceNameCaseConversion() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.eureka("http://eureka:8761");

            // When/Then - Eureka converts to lowercase
            StepVerifier.create(client.resolveEndpoint("PAYMENT-SERVICE"))
                .assertNext(endpoint -> {
                    assertThat(endpoint).isEqualTo("http://payment-service");
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Consul Service Discovery Tests")
    class ConsulServiceDiscoveryTests {

        @Test
        @DisplayName("Should create Consul discovery client")
        void shouldCreateConsulDiscoveryClient() {
            // When
            ServiceDiscoveryClient client = ServiceDiscoveryClient.consul("http://consul:8500");

            // Then
            assertThat(client).isInstanceOf(ConsulServiceDiscoveryClient.class);
        }

        @Test
        @DisplayName("Should resolve Consul endpoint")
        void shouldResolveConsulEndpoint() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.consul("http://consul:8500");

            // When/Then
            StepVerifier.create(client.resolveEndpoint("USER-SERVICE"))
                .assertNext(endpoint -> {
                    assertThat(endpoint).isEqualTo("http://user-service");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("Should handle Consul service name case conversion")
        void shouldHandleConsulServiceNameCaseConversion() {
            // Given
            ServiceDiscoveryClient client = ServiceDiscoveryClient.consul("http://consul:8500");

            // When/Then - Consul converts to lowercase
            StepVerifier.create(client.resolveEndpoint("NOTIFICATION-SERVICE"))
                .assertNext(endpoint -> {
                    assertThat(endpoint).isEqualTo("http://notification-service");
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Service Instance Tests")
    class ServiceInstanceTests {

        @Test
        @DisplayName("Should create service instance with all properties")
        void shouldCreateServiceInstanceWithAllProperties() {
            // Given
            Map<String, String> metadata = Map.of(
                "version", "1.0.0",
                "region", "us-east-1"
            );

            // When
            ServiceDiscoveryClient.ServiceInstance instance = new ServiceDiscoveryClient.ServiceInstance(
                "user-service-1",
                "user-service",
                "localhost",
                8080,
                false,
                ServiceDiscoveryClient.HealthStatus.UP,
                metadata
            );

            // Then
            assertThat(instance.instanceId()).isEqualTo("user-service-1");
            assertThat(instance.serviceName()).isEqualTo("user-service");
            assertThat(instance.host()).isEqualTo("localhost");
            assertThat(instance.port()).isEqualTo(8080);
            assertThat(instance.secure()).isFalse();
            assertThat(instance.healthStatus()).isEqualTo(ServiceDiscoveryClient.HealthStatus.UP);
            assertThat(instance.isHealthy()).isTrue();
            assertThat(instance.metadata()).containsEntry("version", "1.0.0");
            assertThat(instance.metadata()).containsEntry("region", "us-east-1");
        }

        @Test
        @DisplayName("Should identify healthy instances")
        void shouldIdentifyHealthyInstances() {
            // Given
            ServiceDiscoveryClient.ServiceInstance upInstance = new ServiceDiscoveryClient.ServiceInstance(
                "instance-1", "service", "host", 8080, false,
                ServiceDiscoveryClient.HealthStatus.UP, Map.of()
            );

            ServiceDiscoveryClient.ServiceInstance downInstance = new ServiceDiscoveryClient.ServiceInstance(
                "instance-2", "service", "host", 8080, false,
                ServiceDiscoveryClient.HealthStatus.DOWN, Map.of()
            );

            ServiceDiscoveryClient.ServiceInstance outOfServiceInstance = new ServiceDiscoveryClient.ServiceInstance(
                "instance-3", "service", "host", 8080, false,
                ServiceDiscoveryClient.HealthStatus.OUT_OF_SERVICE, Map.of()
            );

            // Then
            assertThat(upInstance.isHealthy()).isTrue();
            assertThat(downInstance.isHealthy()).isFalse();
            assertThat(outOfServiceInstance.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("Should build URI from instance")
        void shouldBuildUriFromInstance() {
            // Given
            ServiceDiscoveryClient.ServiceInstance httpInstance = new ServiceDiscoveryClient.ServiceInstance(
                "instance-1", "service", "api.example.com", 8080, false,
                ServiceDiscoveryClient.HealthStatus.UP, Map.of()
            );

            ServiceDiscoveryClient.ServiceInstance httpsInstance = new ServiceDiscoveryClient.ServiceInstance(
                "instance-2", "service", "api.example.com", 443, true,
                ServiceDiscoveryClient.HealthStatus.UP, Map.of()
            );

            // Then
            assertThat(httpInstance.getUri()).isEqualTo("http://api.example.com:8080");
            assertThat(httpsInstance.getUri()).isEqualTo("https://api.example.com:443");
        }
    }
}
