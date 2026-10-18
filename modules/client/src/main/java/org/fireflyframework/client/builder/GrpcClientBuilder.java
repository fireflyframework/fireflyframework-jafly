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

package org.fireflyframework.client.builder;

import org.fireflyframework.client.GrpcClient;
import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.impl.GrpcServiceClientImpl;
import org.fireflyframework.resilience.CircuitBreakerManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Function;

/**
 * Simplified builder for gRPC service clients.
 * 
 * <p>This builder provides a fluent API for creating gRPC service clients with
 * sensible defaults and simplified configuration options.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Simple gRPC client
 * ServiceClient client = ServiceClient.grpc("user-service", UserServiceStub.class)
 *     .address("user-service:9090")
 *     .stubFactory(channel -> UserServiceGrpc.newStub(channel))
 *     .build();
 *
 * // Secure gRPC client
 * ServiceClient client = ServiceClient.grpc("payment-service", PaymentServiceStub.class)
 *     .address("payment-service:9443")
 *     .useTransportSecurity()
 *     .stubFactory(channel -> PaymentServiceGrpc.newStub(channel))
 *     .build();
 * }</pre>
 *
 * @param <T> the gRPC stub type
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class GrpcClientBuilder<T> {

    private final String serviceName;
    private final Class<T> stubType;
    private String address;
    private Duration timeout = Duration.ofSeconds(30);
    private boolean usePlaintext = true;
    private boolean useTransportSecurity = false;
    private Function<Object, T> stubFactory;
    private ManagedChannel channel;
    private CircuitBreakerManager circuitBreakerManager;

    /**
     * Creates a new gRPC client builder.
     *
     * @param serviceName the service name
     * @param stubType the gRPC stub type
     */
    public GrpcClientBuilder(String serviceName, Class<T> stubType) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }
        if (stubType == null) {
            throw new IllegalArgumentException("Stub type cannot be null");
        }
        
        this.serviceName = serviceName.trim();
        this.stubType = stubType;
        
        log.debug("Created gRPC client builder for service '{}' with stub type '{}'", 
                this.serviceName, stubType.getSimpleName());
    }

    public GrpcClientBuilder<T> address(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address cannot be null or empty");
        }
        this.address = address.trim();
        return this;
    }

    public GrpcClientBuilder<T> timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    public GrpcClientBuilder<T> usePlaintext() {
        this.usePlaintext = true;
        this.useTransportSecurity = false;
        return this;
    }

    public GrpcClientBuilder<T> useTransportSecurity() {
        this.useTransportSecurity = true;
        this.usePlaintext = false;
        return this;
    }

    public GrpcClientBuilder<T> stubFactory(Function<Object, T> stubFactory) {
        if (stubFactory == null) {
            throw new IllegalArgumentException("Stub factory cannot be null");
        }
        this.stubFactory = stubFactory;
        return this;
    }

    /**
     * Sets a custom managed channel.
     * 
     * <p>When provided, this channel will be used instead of creating a new one.
     *
     * @param channel the custom managed channel
     * @return this builder
     */
    public GrpcClientBuilder<T> channel(ManagedChannel channel) {
        this.channel = channel;
        return this;
    }

    /**
     * Sets the circuit breaker manager.
     *
     * @param circuitBreakerManager the circuit breaker manager
     * @return this builder
     */
    public GrpcClientBuilder<T> circuitBreakerManager(CircuitBreakerManager circuitBreakerManager) {
        this.circuitBreakerManager = circuitBreakerManager;
        return this;
    }

    public GrpcClient<T> build() {
        validateConfiguration();
        
        log.info("Building gRPC service client for service '{}' with address '{}'", 
                serviceName, address);
        
        ManagedChannel finalChannel = channel != null ? channel : createChannel();
        T stub = createStub(finalChannel);
        
        return new GrpcServiceClientImpl<>(
            serviceName,
            stubType,
            address,
            timeout,
            finalChannel,
            stub,
            circuitBreakerManager
        );
    }

    private void validateConfiguration() {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalStateException("Address must be configured for gRPC clients");
        }
        
        if (stubFactory == null) {
            throw new IllegalStateException("Stub factory must be configured for gRPC clients");
        }
    }

    private ManagedChannel createChannel() {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(address);
        
        if (usePlaintext) {
            builder.usePlaintext();
        }
        
        return builder.build();
    }

    private T createStub(ManagedChannel channel) {
        return stubFactory.apply(channel);
    }
}
