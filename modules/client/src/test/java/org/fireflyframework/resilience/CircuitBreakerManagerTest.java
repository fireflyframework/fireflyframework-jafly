package org.fireflyframework.resilience;

import org.fireflyframework.client.exception.CircuitBreakerOpenException;
import org.fireflyframework.client.exception.CircuitBreakerTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for enhanced CircuitBreakerManager functionality.
 */
class CircuitBreakerManagerTest {

    private CircuitBreakerManager circuitBreakerManager;
    private CircuitBreakerConfig config;

    @BeforeEach
    void setUp() {
        config = CircuitBreakerConfig.builder()
            .failureRateThreshold(50.0)
            .minimumNumberOfCalls(3)
            .slidingWindowSize(5)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(2)
            .callTimeout(Duration.ofSeconds(1))
            .build();
            
        circuitBreakerManager = new CircuitBreakerManager(config);
    }

    @Test
    void testCircuitBreakerInitialState() {
        // When
        CircuitBreakerState state = circuitBreakerManager.getState("test-service");
        
        // Then
        assertThat(state).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void testSuccessfulExecution() {
        // Given
        String serviceName = "success-service";
        
        // When & Then
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName, 
                () -> Mono.just("SUCCESS"))
        )
        .expectNext("SUCCESS")
        .verifyComplete();
        
        // Verify state remains closed
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void testCircuitOpensOnFailures() {
        // Given
        String serviceName = "failing-service";
        
        // When - Execute enough failures to open the circuit
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(
                circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                    () -> Mono.error(new RuntimeException("Service failure")))
            )
            .expectError(RuntimeException.class)
            .verify();
        }
        
        // Then - Circuit should be open
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.OPEN);
        
        // And subsequent calls should be rejected
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("Should not execute"))
        )
        .expectError(CircuitBreakerOpenException.class)
        .verify();
    }

    @Test
    void testCircuitTransitionsToHalfOpen() throws InterruptedException {
        // Given
        String serviceName = "recovery-service";
        
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(
                circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                    () -> Mono.error(new RuntimeException("Service failure")))
            )
            .expectError(RuntimeException.class)
            .verify();
        }
        
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.OPEN);
        
        // Wait for transition to half-open
        Thread.sleep(150); // Wait longer than waitDurationInOpenState
        
        // When - Execute a successful call
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("RECOVERY"))
        )
        .expectNext("RECOVERY")
        .verifyComplete();
        
        // Then - Circuit should transition to half-open, then potentially to closed
        CircuitBreakerState finalState = circuitBreakerManager.getState(serviceName);
        assertThat(finalState).isIn(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED);
    }

    @Test
    void testMetricsCollection() {
        // Given
        String serviceName = "metrics-service";
        
        // When - Execute some successful and failed calls
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("SUCCESS"))
        ).expectNext("SUCCESS").verifyComplete();
        
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.error(new RuntimeException("Failure")))
        ).expectError(RuntimeException.class).verify();
        
        // Then
        CircuitBreakerMetrics metrics = circuitBreakerManager.getMetrics(serviceName);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getName()).isEqualTo(serviceName);
        assertThat(metrics.getTotalCalls()).isEqualTo(2);
        assertThat(metrics.getSuccessfulCalls()).isEqualTo(1);
        assertThat(metrics.getFailedCalls()).isEqualTo(1);
        assertThat(metrics.getFailureRate()).isEqualTo(50.0);
    }

    @Test
    void testCircuitBreakerReset() {
        // Given
        String serviceName = "reset-service";
        
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(
                circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                    () -> Mono.error(new RuntimeException("Service failure")))
            )
            .expectError(RuntimeException.class)
            .verify();
        }
        
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.OPEN);
        
        // When - Reset the circuit breaker
        circuitBreakerManager.reset(serviceName);
        
        // Then
        assertThat(circuitBreakerManager.getState(serviceName)).isEqualTo(CircuitBreakerState.CLOSED);
        
        // And it should accept calls again
        StepVerifier.create(
            circuitBreakerManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.just("RESET SUCCESS"))
        )
        .expectNext("RESET SUCCESS")
        .verifyComplete();
    }

    @Test
    void testCallTimeout() {
        // Given
        String serviceName = "timeout-service";
        CircuitBreakerConfig timeoutConfig = CircuitBreakerConfig.builder()
            .callTimeout(Duration.ofMillis(50))
            .build();
        CircuitBreakerManager timeoutManager = new CircuitBreakerManager(timeoutConfig);
        
        // When & Then
        StepVerifier.create(
            timeoutManager.executeWithCircuitBreaker(serviceName,
                () -> Mono.delay(Duration.ofMillis(100)).then(Mono.just("DELAYED")))
        )
        .expectError(CircuitBreakerTimeoutException.class)
        .verify();
    }

    @Test
    void testConfigValidation() {
        // When & Then
        assertThatThrownBy(() -> 
            CircuitBreakerConfig.builder()
                .failureRateThreshold(-10.0)
                .build()
                .validate()
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failure rate threshold must be between 0 and 100");
    }
}
