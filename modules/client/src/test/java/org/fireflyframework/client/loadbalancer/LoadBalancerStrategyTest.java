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

package org.fireflyframework.client.loadbalancer;

import org.fireflyframework.client.discovery.ServiceDiscoveryClient.ServiceInstance;
import org.fireflyframework.client.discovery.ServiceDiscoveryClient.HealthStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LoadBalancerStrategy implementations.
 */
class LoadBalancerStrategyTest {

    private final List<ServiceInstance> instances = List.of(
        new ServiceInstance("instance-1", "test-service", "localhost", 8080, false, HealthStatus.UP, Map.of()),
        new ServiceInstance("instance-2", "test-service", "localhost", 8081, false, HealthStatus.UP, Map.of()),
        new ServiceInstance("instance-3", "test-service", "localhost", 8082, false, HealthStatus.UP, Map.of())
    );

    @Test
    void shouldSelectInstanceWithRoundRobin() {
        // Given
        LoadBalancerStrategy strategy = new LoadBalancerStrategy.RoundRobin();

        // When
        Optional<ServiceInstance> first = strategy.selectInstance(instances);
        Optional<ServiceInstance> second = strategy.selectInstance(instances);
        Optional<ServiceInstance> third = strategy.selectInstance(instances);
        Optional<ServiceInstance> fourth = strategy.selectInstance(instances);

        // Then
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(third).isPresent();
        assertThat(fourth).isPresent();
        assertThat(fourth.get().instanceId()).isEqualTo(first.get().instanceId());
    }

    @Test
    void shouldSelectInstanceWithRandom() {
        // Given
        LoadBalancerStrategy strategy = new LoadBalancerStrategy.Random();

        // When
        Optional<ServiceInstance> selected = strategy.selectInstance(instances);

        // Then
        assertThat(selected).isPresent();
        assertThat(instances).contains(selected.get());
    }

    @Test
    void shouldSelectInstanceWithWeightedRoundRobin() {
        // Given
        Map<String, Integer> weights = Map.of(
            "instance-1", 3,
            "instance-2", 1,
            "instance-3", 1
        );
        LoadBalancerStrategy strategy = new LoadBalancerStrategy.WeightedRoundRobin(weights);

        // When
        Optional<ServiceInstance> selected = strategy.selectInstance(instances);

        // Then
        assertThat(selected).isPresent();
    }

    @Test
    void shouldSelectInstanceWithLeastConnections() {
        // Given
        LoadBalancerStrategy.LeastConnections strategy = new LoadBalancerStrategy.LeastConnections();

        // When
        Optional<ServiceInstance> selected = strategy.selectInstance(instances);
        strategy.incrementConnections(selected.get().instanceId());

        Optional<ServiceInstance> second = strategy.selectInstance(instances);

        // Then
        assertThat(selected).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().instanceId()).isNotEqualTo(selected.get().instanceId());
    }

    @Test
    void shouldSelectInstanceWithStickySession() {
        // Given
        LoadBalancerStrategy.StickySession strategy = new LoadBalancerStrategy.StickySession(
            new LoadBalancerStrategy.RoundRobin()
        );

        // When
        Optional<ServiceInstance> first = strategy.selectInstanceWithSession("session-123", instances);
        Optional<ServiceInstance> second = strategy.selectInstanceWithSession("session-123", instances);

        // Then
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().instanceId()).isEqualTo(first.get().instanceId());
    }

    @Test
    void shouldSelectInstanceWithZoneAware() {
        // Given
        List<ServiceInstance> zoneInstances = List.of(
            new ServiceInstance("instance-1", "test-service", "localhost", 8080, false, HealthStatus.UP, Map.of("zone", "us-east-1a")),
            new ServiceInstance("instance-2", "test-service", "localhost", 8081, false, HealthStatus.UP, Map.of("zone", "us-east-1b")),
            new ServiceInstance("instance-3", "test-service", "localhost", 8082, false, HealthStatus.UP, Map.of("zone", "us-east-1a"))
        );

        LoadBalancerStrategy strategy = new LoadBalancerStrategy.ZoneAware(
            "us-east-1a",
            new LoadBalancerStrategy.RoundRobin()
        );

        // When
        Optional<ServiceInstance> selected = strategy.selectInstance(zoneInstances);

        // Then
        assertThat(selected).isPresent();
        assertThat(selected.get().metadata().get("zone")).isEqualTo("us-east-1a");
    }
}

