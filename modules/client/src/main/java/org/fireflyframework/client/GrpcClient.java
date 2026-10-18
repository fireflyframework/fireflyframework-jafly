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

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.function.Function;

/**
 * gRPC-specific service client interface with native gRPC semantics.
 * 
 * <p>Provides direct access to gRPC stub and streaming operations with full
 * support for all gRPC communication patterns (unary, server streaming, client
 * streaming, and bidirectional streaming).
 *
 * <p>Example usage:
 * <pre>{@code
 * GrpcClient<UserServiceGrpc.UserServiceBlockingStub> client = 
 *     ServiceClient.grpc("user-service", UserServiceGrpc.UserServiceBlockingStub.class)
 *         .address("localhost:9090")
 *         .usePlaintext()
 *         .build();
 *
 * // Direct stub access (most type-safe)
 * UserResponse response = client.getStub().getUserById(
 *     UserRequest.newBuilder().setId(123).build()
 * );
 *
 * // With circuit breaker protection
 * Mono<UserResponse> response = client.unary(stub -> 
 *     stub.getUserById(UserRequest.newBuilder().setId(123).build())
 * );
 *
 * // Server streaming
 * Flux<UserEvent> events = client.serverStream(stub ->
 *     stub.streamUserEvents(UserEventRequest.newBuilder().build())
 * );
 * }</pre>
 *
 * @param <T> the gRPC stub type
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public interface GrpcClient<T> extends ServiceClient {

    // ========================================
    // Stub Access
    // ========================================

    /**
     * Gets the underlying gRPC stub for direct method invocation.
     * 
     * <p>Use this for type-safe, generated stub methods when you don't need
     * circuit breaker protection or when you want to handle errors yourself.
     *
     * <p>Example:
     * <pre>{@code
     * UserServiceGrpc.UserServiceBlockingStub stub = client.getStub();
     * UserResponse response = stub.getUserById(request);
     * }</pre>
     *
     * @return the gRPC stub
     * @throws IllegalStateException if the client has been shut down
     */
    T getStub();

    // ========================================
    // Unary Operations
    // ========================================

    /**
     * Executes a unary gRPC call with circuit breaker protection.
     * 
     * <p>This method wraps the gRPC call with circuit breaker logic, providing
     * automatic failure detection and recovery.
     *
     * <p>Example:
     * <pre>{@code
     * Mono<UserResponse> response = client.unary(stub -> 
     *     stub.getUserById(UserRequest.newBuilder().setId(123).build())
     * );
     * }</pre>
     *
     * @param operation the gRPC operation to execute
     * @param <R> the response type
     * @return a Mono containing the response with circuit breaker protection
     */
    <R> Mono<R> unary(Function<T, R> operation);

    /**
     * Executes any gRPC operation with circuit breaker protection.
     * 
     * <p>This is an alias for {@link #unary(Function)} for consistency with
     * other client types.
     *
     * @param operation the gRPC operation to execute
     * @param <R> the response type
     * @return a Mono containing the response with circuit breaker protection
     */
    <R> Mono<R> execute(Function<T, R> operation);

    // ========================================
    // Streaming Operations
    // ========================================

    /**
     * Executes a server-streaming gRPC call with circuit breaker protection.
     * 
     * <p>Use this for gRPC methods that return a stream of responses from the server.
     *
     * <p>Example:
     * <pre>{@code
     * Flux<UserEvent> events = client.serverStream(stub ->
     *     stub.streamUserEvents(UserEventRequest.newBuilder().build())
     * );
     * }</pre>
     *
     * @param operation the gRPC streaming operation to execute
     * @param <R> the response type
     * @return a Flux containing the streaming response
     */
    <R> Flux<R> serverStream(Function<T, Iterator<R>> operation);

    /**
     * Executes a client-streaming gRPC call with circuit breaker protection.
     * 
     * <p>Use this for gRPC methods that accept a stream of requests and return
     * a single response.
     *
     * @param operation the gRPC operation that returns a StreamObserver
     * @param requests the stream of requests to send
     * @param <Req> the request type
     * @param <Res> the response type
     * @return a Mono containing the response
     */
    <Req, Res> Mono<Res> clientStream(
        Function<T, StreamObserver<Res>> operation,
        Publisher<Req> requests
    );

    /**
     * Executes a bidirectional-streaming gRPC call with circuit breaker protection.
     * 
     * <p>Use this for gRPC methods that accept a stream of requests and return
     * a stream of responses.
     *
     * <p>Example:
     * <pre>{@code
     * Flux<ChatMessage> messages = client.bidiStream(
     *     stub -> stub.chat(),
     *     Flux.just(message1, message2, message3)
     * );
     * }</pre>
     *
     * @param operation the gRPC operation that returns a StreamObserver
     * @param requests the stream of requests to send
     * @param <Req> the request type
     * @param <Res> the response type
     * @return a Flux containing the streaming response
     */
    <Req, Res> Flux<Res> bidiStream(
        Function<T, StreamObserver<Res>> operation,
        Publisher<Req> requests
    );

    /**
     * Executes any gRPC streaming operation with circuit breaker protection.
     * 
     * <p>This is the most flexible method for custom gRPC streaming operations.
     *
     * @param operation the gRPC streaming operation to execute
     * @param <R> the response type
     * @return a Flux containing the streaming response
     */
    <R> Flux<R> executeStream(Function<T, Publisher<R>> operation);

    // ========================================
    // gRPC-Specific Metadata
    // ========================================

    /**
     * Returns the gRPC server address.
     *
     * @return the server address (e.g., "localhost:9090")
     */
    String getAddress();

    /**
     * Returns the underlying gRPC ManagedChannel.
     * 
     * <p>Use this for advanced channel configuration or monitoring.
     *
     * @return the ManagedChannel
     */
    ManagedChannel getChannel();
}

