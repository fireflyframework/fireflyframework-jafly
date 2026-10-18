package org.fireflyframework.client.integration;

import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.exception.CircuitBreakerOpenException;
import org.fireflyframework.resilience.CircuitBreakerConfig;
import org.fireflyframework.resilience.CircuitBreakerManager;
import org.fireflyframework.resilience.CircuitBreakerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for enhanced circuit breaker with ServiceClient.
 */
class EnhancedCircuitBreakerIntegrationTest {

    private CircuitBreakerManager circuitBreakerManager;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void testEnhancedCircuitBreakerDirectUsage() {
        // Test the enhanced circuit breaker directly
        String serviceName = "test-service";

        // Test successful operation
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("success"))
        )
        .expectNext("success")
        .verifyComplete();

        // Verify circuit breaker state
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void testEnhancedCircuitBreakerOpensOnFailures() {
        String serviceName = "failing-service";

        // When - Execute failing requests
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.error(new RuntimeException("Service failure")))
        ).expectError(RuntimeException.class).verify();

        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.error(new RuntimeException("Service failure")))
        ).expectError(RuntimeException.class).verify();

        // Then - Circuit should be open
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.OPEN);

        // And subsequent requests should be rejected by circuit breaker
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("Should not execute"))
        ).expectError(CircuitBreakerOpenException.class).verify();
    }

    @Test
    void testEnhancedCircuitBreakerRecovery() throws InterruptedException {
        String serviceName = "recovery-service";

        // Given - Open the circuit
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.error(new RuntimeException("Service failure")))
        ).expectError().verify();

        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.error(new RuntimeException("Service failure")))
        ).expectError().verify();

        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.OPEN);

        // Wait for circuit to transition to half-open
        Thread.sleep(150);

        // When - Execute successful request
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("recovered"))
        )
        .expectNext("recovered")
        .verifyComplete();

        // Then - Circuit should be closed
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void testEnhancedCircuitBreakerMetrics() {
        String serviceName = "metrics-service";

        // When - Execute mixed requests
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("success"))
        )
        .expectNext("success")
        .verifyComplete();

        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.error(new RuntimeException("Failure")))
        )
        .expectError()
        .verify();

        // Then - Verify metrics
        var metrics = circuitBreakerManager.getMetrics(serviceName);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getName()).isEqualTo(serviceName);
        assertThat(metrics.getTotalCalls()).isEqualTo(2);
        assertThat(metrics.getSuccessfulCalls()).isEqualTo(1);
        assertThat(metrics.getFailedCalls()).isEqualTo(1);
        assertThat(metrics.getFailureRate()).isEqualTo(50.0);
    }

    @Test
    void testCircuitBreakerReset() {
        String serviceName = "reset-service";

        // Given - Open the circuit
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.error(new RuntimeException("Service failure")))
        ).expectError().verify();

        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.error(new RuntimeException("Service failure")))
        ).expectError().verify();

        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.OPEN);

        // When - Reset the circuit breaker
        circuitBreakerManager.reset(serviceName);

        // Then
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.CLOSED);

        // And it should accept calls again
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("reset success"))
        )
        .expectNext("reset success")
        .verifyComplete();
    }

    @Test
    void testHighAvailabilityConfiguration() {
        // Given - Create circuit breaker with high availability config
        CircuitBreakerConfig haConfig = CircuitBreakerConfig.highAvailabilityConfig();
        CircuitBreakerManager haManager = new CircuitBreakerManager(haConfig);
        String serviceName = "ha-service";

        // When & Then
        StepVerifier.create(
            haManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("HA OK"))
        )
        .expectNext("HA OK")
        .verifyComplete();

        // Verify circuit breaker metrics
        var metrics = haManager.getMetrics(serviceName);
        assertThat(metrics).isNotNull();
        assertThat(metrics.isHealthy()).isTrue();
        assertThat(metrics.getTotalCalls()).isEqualTo(1);
        assertThat(metrics.getSuccessfulCalls()).isEqualTo(1);
    }
}
