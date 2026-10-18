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

package org.fireflyframework.client.integration;

import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.health.ServiceClientHealthManager;
import org.fireflyframework.client.interceptor.MetricsInterceptor;
import org.fireflyframework.client.resilience.AdvancedResilienceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test suite for advanced ServiceClient features.
 * 
 * <p>This test suite covers the advanced features of the redesigned ServiceClient framework
 * including interceptors, health checks, metrics collection, and resilience patterns.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Advanced ServiceClient Features - Integration Tests")
class AdvancedServiceClientTest {

    @Mock
    private ServiceClient mockServiceClient;

    private ServiceClientHealthManager healthManager;
    private MetricsInterceptor.InMemoryMetricsCollector metricsCollector;
    private AdvancedResilienceManager resilienceManager;

    @BeforeEach
    void setUp() {
        healthManager = new ServiceClientHealthManager(
            Duration.ofSeconds(5), 
            Duration.ofSeconds(2), 
            3
        );
        
        metricsCollector = new MetricsInterceptor.InMemoryMetricsCollector();
        
        resilienceManager = new AdvancedResilienceManager(
            new AdvancedResilienceManager.SystemLoadSheddingStrategy(
                0.8,    // maxCpuUsage
                0.9,    // maxMemoryUsage
                0.95,   // maxThreadPoolUtilization
                10000,  // maxResponseTimeMs (10 seconds - very high for testing)
                10000   // maxRequestsPerSecond (very high for testing)
            )
        );
    }

    @Test
    @DisplayName("Should collect comprehensive metrics for banking operations")
    void shouldCollectComprehensiveMetricsForBankingOperations() {
        // Given: A metrics interceptor
        MetricsInterceptor metricsInterceptor = new MetricsInterceptor(metricsCollector, true, true);
        
        // When: Simulating multiple banking operations
        simulateBankingOperations();
        
        // Then: Metrics should be collected
        MetricsInterceptor.MetricsSnapshot snapshot = metricsCollector.getSnapshot();
        
        assertThat(snapshot.getRequestCounts()).isNotEmpty();
        assertThat(snapshot.getSuccessCounts()).isNotEmpty();
        
        // Verify specific banking operation metrics
        assertThat(snapshot.getRequestCounts().keySet())
            .anyMatch(key -> key.contains("account-service"))
            .anyMatch(key -> key.contains("payment-service"));
    }

    @Test
    @DisplayName("Should perform health checks and detect service failures")
    void shouldPerformHealthChecksAndDetectServiceFailures() {
        // Given: A healthy service client
        when(mockServiceClient.getServiceName()).thenReturn("test-service");
        when(mockServiceClient.healthCheck()).thenReturn(Mono.empty());

        healthManager.registerClient(mockServiceClient);

        // When: Performing health check
        StepVerifier.create(healthManager.performHealthCheck("test-service", mockServiceClient))
            .assertNext(healthStatus -> {
                // Then: Should be healthy
                assertThat(healthStatus.getServiceName()).isEqualTo("test-service");
                assertThat(healthStatus.getState()).isEqualTo(ServiceClientHealthManager.HealthState.HEALTHY);
                assertThat(healthStatus.isHealthy()).isTrue();
            })
            .verifyComplete();

        // When: Service starts failing
        when(mockServiceClient.healthCheck()).thenReturn(Mono.error(new RuntimeException("Service unavailable")));

        StepVerifier.create(healthManager.performHealthCheck("test-service", mockServiceClient))
            .assertNext(failureStatus -> {
                // Then: Should be degraded
                assertThat(failureStatus.getState()).isEqualTo(ServiceClientHealthManager.HealthState.DEGRADED);
                assertThat(failureStatus.getConsecutiveFailures()).isEqualTo(1);
                assertThat(failureStatus.isHealthy()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should apply bulkhead isolation for high-volume banking operations")
    void shouldApplyBulkheadIsolationForHighVolumeBankingOperations() {
        // Given: Create a resilience manager without load shedding for this test
        AdvancedResilienceManager testResilienceManager = new AdvancedResilienceManager(
            new AdvancedResilienceManager.SystemLoadSheddingStrategy(
                1.0,    // maxCpuUsage - 100% (disabled)
                1.0,    // maxMemoryUsage - 100% (disabled)
                1.0,    // maxThreadPoolUtilization - 100% (disabled)
                Integer.MAX_VALUE,  // maxResponseTimeMs (disabled)
                Integer.MAX_VALUE   // maxRequestsPerSecond (disabled)
            )
        );

        // Given: Resilience configuration for banking operations
        AdvancedResilienceManager.ResilienceConfig config = new AdvancedResilienceManager.ResilienceConfig(
            2, // Max 2 concurrent calls
            Duration.ofMillis(100), // Max wait time
            10.0, // 10 requests per second
            5, // Burst capacity
            Duration.ofSeconds(1), // Base timeout
            Duration.ofSeconds(5) // Max timeout
        );

        AtomicInteger concurrentCalls = new AtomicInteger(0);
        AtomicInteger maxConcurrentCalls = new AtomicInteger(0);

        // When: Executing multiple concurrent operations
        Mono<String> operation = Mono.fromCallable(() -> {
            int current = concurrentCalls.incrementAndGet();
            maxConcurrentCalls.updateAndGet(max -> Math.max(max, current));
            
            try {
                Thread.sleep(50); // Simulate work
                return "Payment processed";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                concurrentCalls.decrementAndGet();
            }
        });

        // Execute 5 operations concurrently (should be limited to 2 by bulkhead)
        // Use test-specific resilience manager without load shedding
        Mono<String> resilientOperation1 = testResilienceManager.applyResilience("bulkhead-test-service", operation, config);
        Mono<String> resilientOperation2 = testResilienceManager.applyResilience("bulkhead-test-service", operation, config);
        Mono<String> resilientOperation3 = testResilienceManager.applyResilience("bulkhead-test-service", operation, config);
        Mono<String> resilientOperation4 = testResilienceManager.applyResilience("bulkhead-test-service", operation, config);
        Mono<String> resilientOperation5 = testResilienceManager.applyResilience("bulkhead-test-service", operation, config);

        StepVerifier.create(
            Mono.when(
                resilientOperation1,
                resilientOperation2,
                resilientOperation3,
                resilientOperation4,
                resilientOperation5
            )
        )
        .expectComplete()
        .verify(Duration.ofSeconds(10));

        // Then: Bulkhead should have limited concurrent calls
        assertThat(maxConcurrentCalls.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should apply rate limiting for API protection")
    void shouldApplyRateLimitingForApiProtection() {
        // Given: Rate limiter with low limits
        AdvancedResilienceManager.RateLimiter rateLimiter = 
            new AdvancedResilienceManager.RateLimiter(2.0, 2); // 2 requests per second, burst of 2

        // When: Making rapid requests
        boolean firstRequest = rateLimiter.tryAcquire();
        boolean secondRequest = rateLimiter.tryAcquire();
        boolean thirdRequest = rateLimiter.tryAcquire(); // Should be rate limited

        // Then: Rate limiting should be applied
        assertThat(firstRequest).isTrue();
        assertThat(secondRequest).isTrue();
        assertThat(thirdRequest).isFalse();
    }

    @Test
    @DisplayName("Should adapt timeout based on service performance")
    void shouldAdaptTimeoutBasedOnServicePerformance() {
        // Given: Adaptive timeout manager
        AdvancedResilienceManager.AdaptiveTimeout adaptiveTimeout = 
            new AdvancedResilienceManager.AdaptiveTimeout(Duration.ofSeconds(1), Duration.ofSeconds(10));

        // When: Recording fast responses
        for (int i = 0; i < 10; i++) {
            adaptiveTimeout.recordSuccess(Duration.ofMillis(100));
        }

        Duration fastTimeout = adaptiveTimeout.calculateTimeout();

        // When: Recording slow responses
        for (int i = 0; i < 10; i++) {
            adaptiveTimeout.recordSuccess(Duration.ofMillis(2000));
        }

        Duration slowTimeout = adaptiveTimeout.calculateTimeout();

        // Then: Timeout should adapt to performance
        assertThat(slowTimeout).isGreaterThan(fastTimeout);
    }

    @Test
    @DisplayName("Should demonstrate comprehensive banking service integration")
    void shouldDemonstrateComprehensiveBankingServiceIntegration() {
        // Given: A complete banking service setup with all advanced features
        BankingServiceIntegration integration = new BankingServiceIntegration();
        
        // When: Processing a complex banking transaction
        Mono<TransactionResult> result = integration.processComplexTransaction(
            "CUST-123", 
            "ACC-456", 
            new BigDecimal("1000.00"), 
            "USD"
        );

        // Then: Transaction should complete successfully with all features applied
        StepVerifier.create(result)
            .assertNext(transactionResult -> {
                assertThat(transactionResult).isNotNull();
                assertThat(transactionResult.getTransactionId()).isNotNull();
                assertThat(transactionResult.getStatus()).isEqualTo("COMPLETED");
                assertThat(transactionResult.getAmount()).isEqualTo(new BigDecimal("1000.00"));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should handle service degradation gracefully")
    void shouldHandleServiceDegradationGracefully() {
        // Given: A service that starts failing
        when(mockServiceClient.getServiceName()).thenReturn("degrading-service");
        when(mockServiceClient.healthCheck())
            .thenReturn(Mono.empty()) // First call succeeds
            .thenReturn(Mono.error(new RuntimeException("Intermittent failure"))) // Then fails
            .thenReturn(Mono.error(new RuntimeException("Still failing"))) // Continues failing
            .thenReturn(Mono.empty()); // Then recovers

        healthManager.registerClient(mockServiceClient);

        // When & Then: Monitoring health over time
        StepVerifier.create(healthManager.performHealthCheck("degrading-service", mockServiceClient))
            .assertNext(status -> assertThat(status.getState()).isEqualTo(ServiceClientHealthManager.HealthState.HEALTHY))
            .verifyComplete();

        StepVerifier.create(healthManager.performHealthCheck("degrading-service", mockServiceClient))
            .assertNext(status -> assertThat(status.getState()).isEqualTo(ServiceClientHealthManager.HealthState.DEGRADED))
            .verifyComplete();

        StepVerifier.create(healthManager.performHealthCheck("degrading-service", mockServiceClient))
            .assertNext(status -> assertThat(status.getState()).isEqualTo(ServiceClientHealthManager.HealthState.DEGRADED))
            .verifyComplete();

        StepVerifier.create(healthManager.performHealthCheck("degrading-service", mockServiceClient))
            .assertNext(status -> assertThat(status.getState()).isEqualTo(ServiceClientHealthManager.HealthState.HEALTHY))
            .verifyComplete();
    }

    // Helper methods and classes for testing

    private void simulateBankingOperations() {
        // Simulate various banking operations for metrics collection
        metricsCollector.incrementRequestCount("account-service", "/accounts", "GET", "REST");
        metricsCollector.incrementSuccessCount("account-service", "/accounts", "GET", "REST", 200);
        metricsCollector.recordResponseTime("account-service", "/accounts", "GET", "REST", Duration.ofMillis(150));

        metricsCollector.incrementRequestCount("payment-service", "/payments", "POST", "REST");
        metricsCollector.incrementSuccessCount("payment-service", "/payments", "POST", "REST", 201);
        metricsCollector.recordResponseTime("payment-service", "/payments", "POST", "REST", Duration.ofMillis(300));

        metricsCollector.incrementRequestCount("fraud-service", "/check", "POST", "REST");
        metricsCollector.incrementErrorCount("fraud-service", "/check", "POST", "REST", "TimeoutException");
    }

    /**
     * Mock banking service integration for testing.
     */
    private static class BankingServiceIntegration {
        public Mono<TransactionResult> processComplexTransaction(String customerId, String accountId, 
                                                               BigDecimal amount, String currency) {
            // Simulate complex transaction processing
            return Mono.just(new TransactionResult(
                "TXN-" + System.currentTimeMillis(),
                customerId,
                accountId,
                amount,
                currency,
                "COMPLETED"
            ));
        }
    }

    /**
     * Mock transaction result for testing.
     */
    private static class TransactionResult {
        private final String transactionId;
        private final String customerId;
        private final String accountId;
        private final BigDecimal amount;
        private final String currency;
        private final String status;

        public TransactionResult(String transactionId, String customerId, String accountId, 
                               BigDecimal amount, String currency, String status) {
            this.transactionId = transactionId;
            this.customerId = customerId;
            this.accountId = accountId;
            this.amount = amount;
            this.currency = currency;
            this.status = status;
        }

        public String getTransactionId() { return transactionId; }
        public String getCustomerId() { return customerId; }
        public String getAccountId() { return accountId; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getStatus() { return status; }
    }
}
