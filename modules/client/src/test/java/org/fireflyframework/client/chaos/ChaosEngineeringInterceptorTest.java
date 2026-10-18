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

package org.fireflyframework.client.chaos;

import org.fireflyframework.client.chaos.ChaosEngineeringInterceptor.ChaosStatistics;
import org.fireflyframework.client.exception.ServiceConnectionException;
import org.fireflyframework.client.exception.ServiceTimeoutException;
import org.fireflyframework.client.interceptor.InterceptorChain;
import org.fireflyframework.client.interceptor.InterceptorRequest;
import org.fireflyframework.client.interceptor.InterceptorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for ChaosEngineeringInterceptor.
 */
class ChaosEngineeringInterceptorTest {

    @Mock
    private InterceptorRequest request;

    @Mock
    private InterceptorResponse response;

    @Mock
    private InterceptorChain chain;

    private ChaosEngineeringInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(request.getServiceName()).thenReturn("test-service");
        when(request.getEndpoint()).thenReturn("/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeaders()).thenReturn(new HashMap<>());
        when(request.getAttributes()).thenReturn(new HashMap<>());

        when(chain.proceed(any())).thenReturn(Mono.just(response));
    }

    @Test
    void shouldNotInjectFaultsWhenDisabled() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.builder()
            .enabled(false)
            .build();

        interceptor = new ChaosEngineeringInterceptor(config);

        // When
        Mono<InterceptorResponse> result = interceptor.intercept(request, chain);

        // Then
        StepVerifier.create(result)
            .expectNext(response)
            .verifyComplete();
    }

    @Test
    void shouldInjectLatency() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.builder()
            .enabled(true)
            .latencyInjectionEnabled(true)
            .latencyProbability(1.0) // Always inject
            .minLatency(Duration.ofMillis(100))
            .maxLatency(Duration.ofMillis(100))
            .build();

        interceptor = new ChaosEngineeringInterceptor(config);

        // When
        long startTime = System.currentTimeMillis();
        Mono<InterceptorResponse> result = interceptor.intercept(request, chain);

        // Then
        StepVerifier.create(result)
            .expectNext(response)
            .verifyComplete();

        long duration = System.currentTimeMillis() - startTime;
        assertThat(duration).isGreaterThanOrEqualTo(100);

        ChaosStatistics stats = interceptor.getStatistics();
        assertThat(stats.latencyInjections()).isEqualTo(1);
    }

    @Test
    void shouldInjectNetworkFailure() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.builder()
            .enabled(true)
            .networkFailureInjectionEnabled(true)
            .networkFailureProbability(1.0) // Always inject
            .build();

        interceptor = new ChaosEngineeringInterceptor(config);

        // When
        Mono<InterceptorResponse> result = interceptor.intercept(request, chain);

        // Then
        StepVerifier.create(result)
            .expectError(ServiceConnectionException.class)
            .verify();

        ChaosStatistics stats = interceptor.getStatistics();
        assertThat(stats.networkFailureInjections()).isEqualTo(1);
    }

    @Test
    void shouldInjectTimeout() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.builder()
            .enabled(true)
            .timeoutInjectionEnabled(true)
            .timeoutProbability(1.0) // Always inject
            .timeoutDuration(Duration.ofMillis(10))
            .build();

        interceptor = new ChaosEngineeringInterceptor(config);

        // When
        Mono<InterceptorResponse> result = interceptor.intercept(request, chain);

        // Then
        StepVerifier.create(result)
            .expectError(ServiceTimeoutException.class)
            .verify(Duration.ofSeconds(5));

        ChaosStatistics stats = interceptor.getStatistics();
        assertThat(stats.timeoutInjections()).isEqualTo(1);
    }

    @Test
    void shouldInjectError() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.builder()
            .enabled(true)
            .errorInjectionEnabled(true)
            .errorProbability(1.0) // Always inject
            .errorType(FaultInjectionConfig.ErrorType.SERVICE_UNAVAILABLE)
            .build();

        interceptor = new ChaosEngineeringInterceptor(config);

        // When
        Mono<InterceptorResponse> result = interceptor.intercept(request, chain);

        // Then
        StepVerifier.create(result)
            .expectError()
            .verify();

        ChaosStatistics stats = interceptor.getStatistics();
        assertThat(stats.errorInjections()).isEqualTo(1);
    }

    @Test
    void shouldCalculateStatistics() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.builder()
            .enabled(true)
            .latencyInjectionEnabled(true)
            .latencyProbability(1.0)
            .minLatency(Duration.ofMillis(10))
            .maxLatency(Duration.ofMillis(10))
            .build();

        interceptor = new ChaosEngineeringInterceptor(config);

        // When
        for (int i = 0; i < 10; i++) {
            interceptor.intercept(request, chain).block();
        }

        // Then
        ChaosStatistics stats = interceptor.getStatistics();
        assertThat(stats.getTotalInjections()).isEqualTo(10);
        assertThat(stats.latencyInjections()).isEqualTo(10);
        assertThat(stats.getLatencyInjectionRate()).isEqualTo(1.0);
    }

    @Test
    void shouldResetStatistics() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.builder()
            .enabled(true)
            .latencyInjectionEnabled(true)
            .latencyProbability(1.0)
            .minLatency(Duration.ofMillis(10))
            .maxLatency(Duration.ofMillis(10))
            .build();

        interceptor = new ChaosEngineeringInterceptor(config);
        interceptor.intercept(request, chain).block();

        // When
        interceptor.resetStatistics();

        // Then
        ChaosStatistics stats = interceptor.getStatistics();
        assertThat(stats.getTotalInjections()).isZero();
        assertThat(stats.latencyInjections()).isZero();
    }

    @Test
    void shouldUseLatencyTestingConfig() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.latencyTesting();

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.isLatencyInjectionEnabled()).isTrue();
        assertThat(config.getLatencyProbability()).isEqualTo(0.3);
    }

    @Test
    void shouldUseErrorTestingConfig() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.errorTesting();

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.isErrorInjectionEnabled()).isTrue();
        assertThat(config.getErrorProbability()).isEqualTo(0.2);
    }

    @Test
    void shouldBuildCustomConfig() {
        // Given
        FaultInjectionConfig config = FaultInjectionConfig.builder()
            .enabled(true)
            .latencyInjectionEnabled(true)
            .errorInjectionEnabled(true)
            .timeoutInjectionEnabled(true)
            .networkFailureInjectionEnabled(true)
            .build();

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.isLatencyInjectionEnabled()).isTrue();
        assertThat(config.isErrorInjectionEnabled()).isTrue();
        assertThat(config.isTimeoutInjectionEnabled()).isTrue();
        assertThat(config.isNetworkFailureInjectionEnabled()).isTrue();
    }
}

