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

package org.fireflyframework.client.metrics;

import org.fireflyframework.client.ClientType;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.fireflyframework.resilience.CircuitBreakerState;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics integration for ServiceClient.
 *
 * <p>This class provides comprehensive metrics collection for service client operations
 * using the Firefly observability naming convention ({@code firefly.client.*}).
 * <ul>
 *   <li>Request counters (total, success, failure)</li>
 *   <li>Request duration timers</li>
 *   <li>Circuit breaker state gauges</li>
 *   <li>Error rate tracking</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class ServiceClientMetrics extends FireflyMetricsSupport {

    private final ConcurrentHashMap<String, ClientType> serviceClientTypes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> circuitBreakerStates = new ConcurrentHashMap<>();

    /**
     * Creates a new ServiceClientMetrics instance.
     *
     * @param meterRegistry the Micrometer meter registry
     */
    public ServiceClientMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "client");
        log.info("Initialized ServiceClient metrics with Micrometer");
    }

    /**
     * Records a successful request.
     *
     * @param serviceName the service name
     * @param clientType the client type (REST or gRPC)
     * @param endpoint the endpoint path
     * @param duration the request duration
     */
    public void recordSuccess(String serviceName, ClientType clientType, String endpoint, Duration duration) {
        ensureServiceRegistered(serviceName, clientType);
        counter("requests.success", "service", serviceName, "client.type", clientType.name()).increment();
        timer("requests.duration", "service", serviceName, "client.type", clientType.name()).record(duration);

        log.debug("Recorded successful request for service '{}' to endpoint '{}' in {}ms",
            serviceName, endpoint, duration.toMillis());
    }

    /**
     * Records a failed request.
     *
     * @param serviceName the service name
     * @param clientType the client type (REST or gRPC)
     * @param endpoint the endpoint path
     * @param duration the request duration
     * @param errorType the error type/class name
     */
    public void recordFailure(String serviceName, ClientType clientType, String endpoint,
                             Duration duration, String errorType) {
        ensureServiceRegistered(serviceName, clientType);
        counter("requests.failure", "service", serviceName, "client.type", clientType.name()).increment();

        counter("errors", "service", serviceName, "client.type", clientType.name(),
                "error.type", errorType).increment();

        if (duration != null) {
            timer("requests.duration", "service", serviceName, "client.type", clientType.name()).record(duration);
        }

        log.debug("Recorded failed request for service '{}' to endpoint '{}': {}",
            serviceName, endpoint, errorType);
    }

    /**
     * Records circuit breaker state.
     *
     * @param serviceName the service name
     * @param state the circuit breaker state
     */
    public void recordCircuitBreakerState(String serviceName, CircuitBreakerState state) {
        AtomicInteger stateValue = circuitBreakerStates.get(serviceName);
        if (stateValue != null) {
            stateValue.set(state.ordinal());
            log.debug("Updated circuit breaker state for service '{}' to {}", serviceName, state);
        }
    }

    /**
     * Records a circuit breaker state transition.
     *
     * @param serviceName the service name
     * @param fromState the previous state
     * @param toState the new state
     */
    public void recordCircuitBreakerTransition(String serviceName, CircuitBreakerState fromState,
                                              CircuitBreakerState toState) {
        counter("circuit.breaker.transitions", "service", serviceName,
                "from.state", fromState.name(), "to.state", toState.name()).increment();

        recordCircuitBreakerState(serviceName, toState);

        log.info("Circuit breaker transition for service '{}': {} -> {}", serviceName, fromState, toState);
    }

    /**
     * Ensures gauge and state tracking are initialized for a service.
     */
    private void ensureServiceRegistered(String serviceName, ClientType clientType) {
        serviceClientTypes.computeIfAbsent(serviceName, name -> {
            AtomicInteger stateValue = new AtomicInteger(CircuitBreakerState.CLOSED.ordinal());
            circuitBreakerStates.put(name, stateValue);
            gauge("circuit.breaker.state", stateValue, AtomicInteger::get,
                  "service", name, "client.type", clientType.name());
            return clientType;
        });
    }

    /**
     * Gets the total number of requests for a service.
     *
     * @param serviceName the service name
     * @return the total request count, or 0 if no metrics exist
     */
    public long getTotalRequests(String serviceName) {
        ClientType clientType = serviceClientTypes.get(serviceName);
        if (clientType == null) {
            return 0;
        }
        return (long) (counter("requests.success", "service", serviceName, "client.type", clientType.name()).count()
                + counter("requests.failure", "service", serviceName, "client.type", clientType.name()).count());
    }

    /**
     * Gets the success rate for a service.
     *
     * @param serviceName the service name
     * @return the success rate (0.0 to 1.0), or 0.0 if no metrics exist
     */
    public double getSuccessRate(String serviceName) {
        ClientType clientType = serviceClientTypes.get(serviceName);
        if (clientType == null) {
            return 0.0;
        }

        double success = counter("requests.success", "service", serviceName, "client.type", clientType.name()).count();
        double total = success + counter("requests.failure", "service", serviceName, "client.type", clientType.name()).count();
        if (total == 0) {
            return 0.0;
        }
        return success / total;
    }

    /**
     * Gets the current circuit breaker state for a service.
     *
     * @param serviceName the service name
     * @return the circuit breaker state, or CLOSED if no metrics exist
     */
    public CircuitBreakerState getCircuitBreakerState(String serviceName) {
        AtomicInteger stateValue = circuitBreakerStates.get(serviceName);
        if (stateValue == null) {
            return CircuitBreakerState.CLOSED;
        }
        int stateOrdinal = stateValue.get();
        return CircuitBreakerState.values()[stateOrdinal];
    }
}
