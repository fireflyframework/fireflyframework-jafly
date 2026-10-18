package org.fireflyframework.client.impl;

import org.fireflyframework.client.ClientType;
import org.fireflyframework.resilience.CircuitBreakerConfig;
import org.fireflyframework.resilience.CircuitBreakerManager;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for GrpcServiceClientImpl to verify circuit breaker integration.
 */
class GrpcServiceClientImplTest {

    @Mock
    private ManagedChannel mockChannel;

    @Mock
    private Object mockStub;

    private CircuitBreakerManager circuitBreakerManager;
    private GrpcServiceClientImpl<Object> grpcClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create circuit breaker manager with test configuration
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureRateThreshold(50.0)
            .minimumNumberOfCalls(2)
            .slidingWindowSize(3)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(1)
            .callTimeout(Duration.ofSeconds(1))
            .build();

        circuitBreakerManager = new CircuitBreakerManager(config);

        // Mock channel behavior
        when(mockChannel.isShutdown()).thenReturn(false);
        when(mockChannel.isTerminated()).thenReturn(false);

        // Create gRPC client
        grpcClient = new GrpcServiceClientImpl<>(
            "test-grpc-service",
            Object.class,
            "localhost:9090",
            Duration.ofSeconds(30),
            mockChannel,
            mockStub,
            circuitBreakerManager
        );
    }

    @Test
    void testBasicProperties() {
        assertThat(grpcClient.getServiceName()).isEqualTo("test-grpc-service");
        assertThat(grpcClient.getAddress()).isEqualTo("localhost:9090");
        assertThat(grpcClient.getClientType()).isEqualTo(ClientType.GRPC);
        assertThat(grpcClient.isReady()).isTrue();
        assertThat(grpcClient.getStub()).isEqualTo(mockStub);
    }

    @Test
    void testHealthCheckWithCircuitBreaker() {
        // When - Execute health check
        StepVerifier.create(grpcClient.healthCheck())
            .verifyComplete();

        // Verify circuit breaker state
        assertThat(circuitBreakerManager.getState("test-grpc-service")).isNotNull();
    }

    @Test
    void testHealthCheckFailureWithCircuitBreaker() {
        // Given - Mock channel as shutdown
        when(mockChannel.isShutdown()).thenReturn(true);

        // When - Execute health check
        StepVerifier.create(grpcClient.healthCheck())
            .expectError(RuntimeException.class)
            .verify();
    }

    @Test
    void testExecuteWithCircuitBreakerSuccess() {
        // Given
        Mono<String> operation = Mono.just("success");

        // When
        StepVerifier.create(grpcClient.executeWithCircuitBreaker(operation))
            .expectNext("success")
            .verifyComplete();

        // Verify circuit breaker metrics
        var metrics = circuitBreakerManager.getMetrics("test-grpc-service");
        assertThat(metrics.getSuccessfulCalls()).isGreaterThan(0);
    }

    @Test
    void testExecuteWithCircuitBreakerFailure() {
        // Given
        Mono<String> operation = Mono.error(new RuntimeException("gRPC call failed"));

        // When - Execute failing operation twice to trigger circuit breaker
        StepVerifier.create(grpcClient.executeWithCircuitBreaker(operation))
            .expectError(RuntimeException.class)
            .verify();

        StepVerifier.create(grpcClient.executeWithCircuitBreaker(operation))
            .expectError(RuntimeException.class)
            .verify();

        // Verify circuit breaker metrics
        var metrics = circuitBreakerManager.getMetrics("test-grpc-service");
        assertThat(metrics.getFailedCalls()).isEqualTo(2);
    }

    @Test
    void testExecuteStreamWithCircuitBreakerSuccess() {
        // Given
        Flux<String> operation = Flux.just("item1", "item2", "item3");

        // When
        StepVerifier.create(grpcClient.executeStreamWithCircuitBreaker(operation))
            .expectNext("item1", "item2", "item3")
            .verifyComplete();

        // Verify circuit breaker metrics
        var metrics = circuitBreakerManager.getMetrics("test-grpc-service");
        assertThat(metrics.getSuccessfulCalls()).isGreaterThan(0);
    }

    @Test
    void testExecuteStreamWithCircuitBreakerFailure() {
        // Given
        Flux<String> operation = Flux.error(new RuntimeException("gRPC stream failed"));

        // When
        StepVerifier.create(grpcClient.executeStreamWithCircuitBreaker(operation))
            .expectError(RuntimeException.class)
            .verify();

        // Verify circuit breaker metrics
        var metrics = circuitBreakerManager.getMetrics("test-grpc-service");
        assertThat(metrics.getFailedCalls()).isGreaterThan(0);
    }

    @Test
    void testHttpToGrpcMethodMapping() {
        // Create a fresh client to avoid circuit breaker interference
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureRateThreshold(50.0)
            .minimumNumberOfCalls(10) // Higher threshold to avoid opening
            .slidingWindowSize(20)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(1)
            .callTimeout(Duration.ofSeconds(1))
            .build();
        CircuitBreakerManager freshCircuitBreakerManager = new CircuitBreakerManager(config);

        GrpcServiceClientImpl<Object> freshClient = new GrpcServiceClientImpl<>(
            "fresh-test-service",
            Object.class,
            "localhost:9090",
            Duration.ofSeconds(1),
            mockChannel,
            mockStub,
            freshCircuitBreakerManager
        );

        // Verify that the gRPC client provides native gRPC API
        // Test unary operation with circuit breaker
        StepVerifier.create(freshClient.unary(stub -> "test-response"))
            .expectNext("test-response")
            .verifyComplete();

        // Verify stub access
        assertThat(freshClient.getStub()).isNotNull();
        assertThat(freshClient.getAddress()).isEqualTo("localhost:9090");
        assertThat(freshClient.getChannel()).isNotNull();
    }

    @Test
    void testShutdown() {
        // When
        grpcClient.shutdown();

        // Then
        assertThat(grpcClient.isReady()).isFalse();
        verify(mockChannel).shutdown();

        // Verify that operations fail after shutdown
        assertThatThrownBy(() -> grpcClient.getStub())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Client has been shut down");

        StepVerifier.create(grpcClient.healthCheck())
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void testCircuitBreakerWithoutManager() {
        // Given - Create client without circuit breaker manager
        GrpcServiceClientImpl<Object> clientWithoutCB = new GrpcServiceClientImpl<>(
            "test-service-no-cb",
            Object.class,
            "localhost:9090",
            Duration.ofSeconds(30),
            mockChannel,
            mockStub,
            null
        );

        // When - Execute operation
        Mono<String> operation = Mono.just("success");
        
        StepVerifier.create(clientWithoutCB.executeWithCircuitBreaker(operation))
            .expectNext("success")
            .verifyComplete();

        // Clean up
        clientWithoutCB.shutdown();
    }

    @Test
    void testMultipleOperationsWithSameCircuitBreaker() {
        // Given - Create a new circuit breaker manager for this test to avoid interference
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureRateThreshold(50.0)
            .minimumNumberOfCalls(5) // Higher threshold to avoid opening too quickly
            .slidingWindowSize(10)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(1)
            .callTimeout(Duration.ofSeconds(1))
            .build();

        CircuitBreakerManager testManager = new CircuitBreakerManager(config);

        GrpcServiceClientImpl<Object> testClient = new GrpcServiceClientImpl<>(
            "multi-ops-service",
            Object.class,
            "localhost:9090",
            Duration.ofSeconds(30),
            mockChannel,
            mockStub,
            testManager
        );

        Mono<String> successOperation = Mono.just("success");
        Mono<String> failureOperation = Mono.error(new RuntimeException("failure"));

        // When - Execute mixed operations
        StepVerifier.create(testClient.executeWithCircuitBreaker(successOperation))
            .expectNext("success")
            .verifyComplete();

        StepVerifier.create(testClient.executeWithCircuitBreaker(failureOperation))
            .expectError(RuntimeException.class)
            .verify();

        StepVerifier.create(testClient.executeWithCircuitBreaker(successOperation))
            .expectNext("success")
            .verifyComplete();

        // Verify circuit breaker metrics
        var metrics = testManager.getMetrics("multi-ops-service");
        assertThat(metrics.getTotalCalls()).isEqualTo(3);
        assertThat(metrics.getSuccessfulCalls()).isEqualTo(2);
        assertThat(metrics.getFailedCalls()).isEqualTo(1);

        // Clean up
        testClient.shutdown();
    }
}
