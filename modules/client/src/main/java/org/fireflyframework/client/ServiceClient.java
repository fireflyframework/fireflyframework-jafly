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

package org.fireflyframework.client;

import com.fasterxml.jackson.core.type.TypeReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Core interface for all service clients providing common lifecycle and health operations.
 *
 * <p>This interface defines the minimal contract that all service clients must implement,
 * regardless of protocol (REST, gRPC, SOAP). Protocol-specific operations are defined in
 * extending interfaces:
 * <ul>
 *   <li>{@link RestClient} - HTTP-based REST services</li>
 *   <li>{@link GrpcClient} - gRPC services with Protocol Buffers</li>
 *   <li>{@link SoapClient} - SOAP/WSDL services</li>
 * </ul>
 *
 * <p>All service clients provide:
 * <ul>
 *   <li>Reactive, non-blocking operations with Mono/Flux</li>
 *   <li>Built-in circuit breaker and resilience patterns</li>
 *   <li>Health checks and readiness probes</li>
 *   <li>Lifecycle management</li>
 *   <li>Metrics and observability</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // REST client
 * RestClient restClient = ServiceClient.rest("user-service")
 *     .baseUrl("http://localhost:8080")
 *     .build();
 *
 * // gRPC client
 * GrpcClient<UserServiceGrpc.UserServiceBlockingStub> grpcClient =
 *     ServiceClient.grpc("user-service", UserServiceGrpc.UserServiceBlockingStub.class)
 *         .address("localhost:9090")
 *         .build();
 *
 * // SOAP client
 * SoapClient soapClient = ServiceClient.soap("weather-service")
 *     .wsdlUrl("http://example.com/service?WSDL")
 *     .build();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public interface ServiceClient {

    // ========================================
    // Static Factory Methods
    // ========================================

    /**
     * Creates a REST service client builder.
     *
     * @param serviceName the name of the service
     * @return a REST client builder
     */
    static org.fireflyframework.client.builder.RestClientBuilder rest(String serviceName) {
        return new org.fireflyframework.client.builder.RestClientBuilder(serviceName);
    }

    /**
     * Creates a gRPC service client builder.
     *
     * @param serviceName the name of the service
     * @param stubType the gRPC stub type
     * @param <T> the stub type
     * @return a gRPC client builder
     */
    static <T> org.fireflyframework.client.builder.GrpcClientBuilder<T> grpc(String serviceName, Class<T> stubType) {
        return new org.fireflyframework.client.builder.GrpcClientBuilder<>(serviceName, stubType);
    }

    /**
     * Creates a SOAP service client builder.
     *
     * <p>SOAP clients provide a modern reactive API over traditional SOAP/WSDL services
     * with automatic service discovery, WS-Security support, and built-in resilience.
     *
     * @param serviceName the name of the service
     * @return a SOAP client builder
     */
    static org.fireflyframework.client.builder.SoapClientBuilder soap(String serviceName) {
        return new org.fireflyframework.client.builder.SoapClientBuilder(serviceName);
    }


    // ========================================
    // Core Lifecycle and Health Operations
    // ========================================

    /**
     * Returns the service name for this client.
     *
     * @return the service name
     */
    String getServiceName();

    /**
     * Checks if the service client is ready to handle requests.
     *
     * @return true if the client is ready, false otherwise
     */
    boolean isReady();

    /**
     * Performs a health check on the service.
     *
     * @return a Mono that completes successfully if the service is healthy
     */
    Mono<Void> healthCheck();

    /**
     * Returns the client type (REST, GRPC, SOAP).
     *
     * @return the client type
     */
    ClientType getClientType();

    /**
     * Shuts down the service client and releases resources.
     *
     * <p>After shutdown, the client should not be used for any operations.
     * Implementations should clean up connections, channels, and other resources.
     */
    void shutdown();
}