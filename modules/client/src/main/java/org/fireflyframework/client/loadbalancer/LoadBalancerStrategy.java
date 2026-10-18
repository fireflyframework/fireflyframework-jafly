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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load balancer strategy for distributing requests across service instances.
 * 
 * <p>Supported strategies:
 * <ul>
 *   <li>Round Robin - Distributes requests evenly</li>
 *   <li>Weighted Round Robin - Distributes based on weights</li>
 *   <li>Random - Selects random instance</li>
 *   <li>Least Connections - Selects instance with fewest active connections</li>
 *   <li>Sticky Session - Routes to same instance based on session ID</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
public interface LoadBalancerStrategy {

    /**
     * Selects a service instance from the available instances.
     *
     * @param instances available service instances
     * @return selected service instance
     */
    Optional<ServiceInstance> selectInstance(List<ServiceInstance> instances);

    /**
     * Round Robin load balancer.
     */
    class RoundRobin implements LoadBalancerStrategy {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Optional<ServiceInstance> selectInstance(List<ServiceInstance> instances) {
            if (instances == null || instances.isEmpty()) {
                return Optional.empty();
            }

            int index = Math.abs(counter.getAndIncrement() % instances.size());
            return Optional.of(instances.get(index));
        }
    }

    /**
     * Weighted Round Robin load balancer.
     */
    class WeightedRoundRobin implements LoadBalancerStrategy {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final Map<String, Integer> weights;

        public WeightedRoundRobin(Map<String, Integer> weights) {
            this.weights = weights;
        }

        @Override
        public Optional<ServiceInstance> selectInstance(List<ServiceInstance> instances) {
            if (instances == null || instances.isEmpty()) {
                return Optional.empty();
            }

            // Build weighted list
            List<ServiceInstance> weightedList = instances.stream()
                .flatMap(instance -> {
                    int weight = weights.getOrDefault(instance.instanceId(), 1);
                    return java.util.stream.Stream.generate(() -> instance).limit(weight);
                })
                .toList();

            int index = Math.abs(counter.getAndIncrement() % weightedList.size());
            return Optional.of(weightedList.get(index));
        }
    }

    /**
     * Random load balancer.
     */
    class Random implements LoadBalancerStrategy {
        @Override
        public Optional<ServiceInstance> selectInstance(List<ServiceInstance> instances) {
            if (instances == null || instances.isEmpty()) {
                return Optional.empty();
            }

            int index = ThreadLocalRandom.current().nextInt(instances.size());
            return Optional.of(instances.get(index));
        }
    }

    /**
     * Least Connections load balancer.
     */
    class LeastConnections implements LoadBalancerStrategy {
        private final Map<String, AtomicInteger> connections = new ConcurrentHashMap<>();

        @Override
        public Optional<ServiceInstance> selectInstance(List<ServiceInstance> instances) {
            if (instances == null || instances.isEmpty()) {
                return Optional.empty();
            }

            return instances.stream()
                .min((i1, i2) -> {
                    int conn1 = connections.computeIfAbsent(i1.instanceId(), k -> new AtomicInteger(0)).get();
                    int conn2 = connections.computeIfAbsent(i2.instanceId(), k -> new AtomicInteger(0)).get();
                    return Integer.compare(conn1, conn2);
                });
        }

        public void incrementConnections(String instanceId) {
            connections.computeIfAbsent(instanceId, k -> new AtomicInteger(0)).incrementAndGet();
        }

        public void decrementConnections(String instanceId) {
            connections.computeIfAbsent(instanceId, k -> new AtomicInteger(0)).decrementAndGet();
        }
    }

    /**
     * Sticky Session load balancer.
     */
    class StickySession implements LoadBalancerStrategy {
        private final Map<String, ServiceInstance> sessionMap = new ConcurrentHashMap<>();
        private final LoadBalancerStrategy fallbackStrategy;

        public StickySession(LoadBalancerStrategy fallbackStrategy) {
            this.fallbackStrategy = fallbackStrategy;
        }

        @Override
        public Optional<ServiceInstance> selectInstance(List<ServiceInstance> instances) {
            // This method is not used for sticky sessions
            // Use selectInstanceWithSession instead
            return fallbackStrategy.selectInstance(instances);
        }

        public Optional<ServiceInstance> selectInstanceWithSession(String sessionId, List<ServiceInstance> instances) {
            if (instances == null || instances.isEmpty()) {
                return Optional.empty();
            }

            // Check if session already has an instance
            ServiceInstance existing = sessionMap.get(sessionId);
            if (existing != null && instances.contains(existing)) {
                return Optional.of(existing);
            }

            // Select new instance and store in session
            Optional<ServiceInstance> selected = fallbackStrategy.selectInstance(instances);
            selected.ifPresent(instance -> sessionMap.put(sessionId, instance));
            return selected;
        }

        public void clearSession(String sessionId) {
            sessionMap.remove(sessionId);
        }
    }

    /**
     * Zone-aware load balancer (prefers instances in same zone).
     */
    class ZoneAware implements LoadBalancerStrategy {
        private final String preferredZone;
        private final LoadBalancerStrategy fallbackStrategy;

        public ZoneAware(String preferredZone, LoadBalancerStrategy fallbackStrategy) {
            this.preferredZone = preferredZone;
            this.fallbackStrategy = fallbackStrategy;
        }

        @Override
        public Optional<ServiceInstance> selectInstance(List<ServiceInstance> instances) {
            if (instances == null || instances.isEmpty()) {
                return Optional.empty();
            }

            // Filter instances in preferred zone
            List<ServiceInstance> zoneInstances = instances.stream()
                .filter(instance -> preferredZone.equals(instance.metadata().get("zone")))
                .toList();

            // Use zone instances if available, otherwise use all instances
            List<ServiceInstance> candidates = zoneInstances.isEmpty() ? instances : zoneInstances;
            return fallbackStrategy.selectInstance(candidates);
        }
    }
}

