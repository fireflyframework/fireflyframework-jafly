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

package org.fireflyframework.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import org.fireflyframework.client.ClientType;
import org.fireflyframework.client.GrpcClient;
import org.fireflyframework.client.exception.GrpcErrorMapper;
import org.fireflyframework.client.exception.ServiceClientException;
import org.fireflyframework.client.exception.ServiceUnavailableException;
import org.fireflyframework.resilience.CircuitBreakerManager;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * gRPC implementation of ServiceClient.
 * 
 * <p>This implementation provides a unified interface for gRPC service communication
 * while maintaining protocol-specific optimizations for Protocol Buffer serialization
 * and streaming operations.
 *
 * @param <T> the gRPC stub type
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class GrpcServiceClientImpl<T> implements GrpcClient<T> {

    private final String serviceName;
    private final Class<T> stubType;
    private final String address;
    private final Duration timeout;
    private final ManagedChannel channel;
    private final T stub;
    private final CircuitBreakerManager circuitBreakerManager;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Creates a new gRPC service client implementation.
     */
    public GrpcServiceClientImpl(String serviceName,
                                Class<T> stubType,
                                String address,
                                Duration timeout,
                                ManagedChannel channel,
                                T stub,
                                CircuitBreakerManager circuitBreakerManager) {
        this.serviceName = serviceName;
        this.stubType = stubType;
        this.address = address;
        this.timeout = timeout;
        this.channel = channel;
        this.stub = stub;
        this.circuitBreakerManager = circuitBreakerManager;

        log.info("Initialized gRPC service client for service '{}' with enhanced circuit breaker and address '{}'",
                serviceName, address);
    }

    // ========================================
    // gRPC-Specific Methods
    // ========================================

    /**
     * Executes a unary gRPC call with circuit breaker protection.
     */
    @Override
    public <R> Mono<R> unary(Function<T, R> operation) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        return Mono.fromCallable(() -> operation.apply(stub))
            .onErrorMap(throwable -> GrpcErrorMapper.mapGrpcError(
                throwable,
                serviceName,
                "unary", // method name - could be enhanced to extract actual method
                requestId,
                startTime
            ))
            .transform(this::applyCircuitBreakerProtection);
    }

    /**
     * Executes any gRPC operation with circuit breaker protection.
     */
    @Override
    public <R> Mono<R> execute(Function<T, R> operation) {
        return unary(operation);
    }

    /**
     * Executes a server-streaming gRPC call.
     */
    @Override
    public <R> Flux<R> serverStream(Function<T, Iterator<R>> operation) {
        return Flux.defer(() -> {
            try {
                Iterator<R> iterator = operation.apply(stub);
                return Flux.fromIterable(() -> iterator);
            } catch (Exception e) {
                return Flux.error(new ServiceClientException(
                    "Failed to execute gRPC server streaming operation", e));
            }
        }).transform(this::applyCircuitBreakerProtectionFlux);
    }

    /**
     * Executes a client-streaming gRPC call.
     */
    @Override
    public <Req, Res> Mono<Res> clientStream(
            Function<T, StreamObserver<Res>> operation,
            Publisher<Req> requests) {
        return Mono.error(new UnsupportedOperationException(
            "Client streaming requires custom implementation based on your gRPC service definition. " +
            "Use getStub() for direct access to streaming methods."));
    }

    /**
     * Executes a bidirectional-streaming gRPC call.
     */
    @Override
    public <Req, Res> Flux<Res> bidiStream(
            Function<T, StreamObserver<Res>> operation,
            Publisher<Req> requests) {
        return Flux.error(new UnsupportedOperationException(
            "Bidirectional streaming requires custom implementation based on your gRPC service definition. " +
            "Use getStub() for direct access to streaming methods."));
    }

    /**
     * Executes any gRPC streaming operation.
     */
    @Override
    public <R> Flux<R> executeStream(Function<T, Publisher<R>> operation) {
        return Flux.defer(() -> {
            try {
                Publisher<R> publisher = operation.apply(stub);
                return Flux.from(publisher);
            } catch (Exception e) {
                return Flux.error(new ServiceClientException(
                    "Failed to execute gRPC streaming operation", e));
            }
        }).transform(this::applyCircuitBreakerProtectionFlux);
    }

    // ========================================
    // gRPC Metadata and Lifecycle
    // ========================================

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public ManagedChannel getChannel() {
        return channel;
    }

    @Override
    public boolean isReady() {
        return !isShutdown.get() && !channel.isShutdown();
    }

    @Override
    public Mono<Void> healthCheck() {
        if (isShutdown.get()) {
            return Mono.error(new IllegalStateException("Client has been shut down"));
        }

        // For gRPC, we check if the channel is ready with circuit breaker protection
        Mono<Void> healthCheckOperation = Mono.<Void>fromCallable(() -> {
            if (channel.isShutdown() || channel.isTerminated()) {
                throw new ServiceUnavailableException("gRPC channel is not available for service: " + serviceName);
            }
            return null;
        })
        .timeout(Duration.ofSeconds(5))
        .onErrorMap(throwable -> new ServiceUnavailableException("Health check failed for gRPC service: " + serviceName, throwable));

        return applyCircuitBreakerProtection(healthCheckOperation);
    }

    @Override
    public ClientType getClientType() {
        return ClientType.GRPC;
    }

    @Override
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            log.info("Shutting down gRPC service client for service '{}'", serviceName);
            if (!channel.isShutdown()) {
                channel.shutdown();
            }
        }
    }

    /**
     * Gets the gRPC stub for direct access.
     *
     * @return the gRPC stub
     */
    public T getStub() {
        if (isShutdown.get()) {
            throw new IllegalStateException("Client has been shut down");
        }
        return stub;
    }

    /**
     * Executes a gRPC operation with circuit breaker protection.
     *
     * @param operation the gRPC operation to execute
     * @param <R> the response type
     * @return the result wrapped in a Mono with circuit breaker protection
     */
    public <R> Mono<R> executeWithCircuitBreaker(Mono<R> operation) {
        return applyCircuitBreakerProtection(operation);
    }

    /**
     * Executes a streaming gRPC operation with circuit breaker protection.
     *
     * @param operation the gRPC streaming operation to execute
     * @param <R> the response type
     * @return the result wrapped in a Flux with circuit breaker protection
     */
    public <R> Flux<R> executeStreamWithCircuitBreaker(Flux<R> operation) {
        return applyCircuitBreakerProtectionFlux(operation);
    }

    // ========================================
    // Circuit Breaker Protection
    // ========================================

    private <R> Mono<R> applyCircuitBreakerProtection(Mono<R> operation) {
        // Use enhanced circuit breaker
        if (circuitBreakerManager != null) {
            return circuitBreakerManager.executeWithCircuitBreaker(serviceName, () -> operation)
                .doOnError(error -> log.warn("Circuit breaker detected failure for gRPC service '{}': {}",
                    serviceName, error.getMessage()))
                .doOnSuccess(result -> log.debug("Circuit breaker allowed successful gRPC request for service '{}'",
                    serviceName));
        } else {
            // No circuit breaker protection (should not happen with auto-configuration)
            log.warn("No circuit breaker configured for gRPC service '{}'", serviceName);
            return operation;
        }
    }

    private <R> Flux<R> applyCircuitBreakerProtectionFlux(Flux<R> operation) {
        // For streaming operations, we apply circuit breaker protection to the entire stream
        if (circuitBreakerManager != null) {
            return circuitBreakerManager.executeWithCircuitBreaker(serviceName, () -> operation.collectList())
                .flatMapMany(Flux::fromIterable)
                .doOnError(error -> log.warn("Circuit breaker detected failure for gRPC streaming service '{}': {}",
                    serviceName, error.getMessage()))
                .doOnComplete(() -> log.debug("Circuit breaker allowed successful gRPC streaming request for service '{}'",
                    serviceName));
        } else {
            // No circuit breaker protection (should not happen with auto-configuration)
            log.warn("No circuit breaker configured for gRPC streaming service '{}'", serviceName);
            return operation;
        }
    }

}