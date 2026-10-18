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

package org.fireflyframework.data.resiliency;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.fireflyframework.data.config.JobOrchestrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProviderResiliencyRegistry}.
 */
class ProviderResiliencyRegistryTest {

    private ResiliencyDecoratorService defaultService;

    @BeforeEach
    void setUp() {
        JobOrchestrationProperties properties = new JobOrchestrationProperties();
        defaultService = new ResiliencyDecoratorService(
                properties,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    @Test
    void decorate_shouldUseProviderSpecificTimeout_whenConfigured() {
        // Given
        ProviderResiliencyConfig config = new ProviderResiliencyConfig();
        config.setTimeoutMs(100);
        config.setCircuitBreakerEnabled(false);
        config.setRetryEnabled(false);

        Map<String, ProviderResiliencyConfig> providerConfigs = Map.of("slow-provider", config);
        ProviderResiliencyRegistry registry = new ProviderResiliencyRegistry(providerConfigs, defaultService);

        Mono<String> slowOperation = Mono.delay(Duration.ofMillis(500)).thenReturn("too late");

        // When & Then
        StepVerifier.create(registry.decorate("slow-provider", slowOperation))
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void decorate_shouldFallbackToDefault_whenProviderNotConfigured() {
        // Given
        ResiliencyDecoratorService mockDefaultService = mock(ResiliencyDecoratorService.class);
        Mono<String> operation = Mono.just("result");
        when(mockDefaultService.decorate(any(Mono.class))).thenReturn(operation);

        Map<String, ProviderResiliencyConfig> providerConfigs = new HashMap<>();
        ProviderResiliencyRegistry registry = new ProviderResiliencyRegistry(providerConfigs, mockDefaultService);

        // When
        registry.decorate("unknown-provider", operation);

        // Then
        verify(mockDefaultService).decorate(any(Mono.class));
    }

    @Test
    void decorate_shouldApplyRateLimiter_whenEnabled() {
        // Given
        ProviderResiliencyConfig config = new ProviderResiliencyConfig();
        config.setRateLimiterEnabled(true);
        config.setRateLimitForPeriod(1);
        config.setRateLimitRefreshPeriodMs(1000);
        config.setCircuitBreakerEnabled(false);
        config.setRetryEnabled(false);
        config.setTimeoutMs(5000);

        Map<String, ProviderResiliencyConfig> providerConfigs = Map.of("limited-provider", config);
        ProviderResiliencyRegistry registry = new ProviderResiliencyRegistry(providerConfigs, defaultService);

        AtomicInteger rejectedCount = new AtomicInteger(0);

        // When - fire 5 calls; only 1 permit per second so at least some should be rejected
        for (int i = 0; i < 5; i++) {
            try {
                registry.decorate("limited-provider", Mono.just("ok")).block();
            } catch (Exception e) {
                if (e instanceof RequestNotPermitted || e.getCause() instanceof RequestNotPermitted) {
                    rejectedCount.incrementAndGet();
                }
            }
        }

        // Then - with 1 permit per second and 5 rapid-fire calls, at least some must be rejected
        assertThat(rejectedCount.get()).isGreaterThan(0);
    }

    @Test
    void hasProviderConfig_shouldReturnTrueForConfiguredProvider() {
        // Given
        ProviderResiliencyConfig config = new ProviderResiliencyConfig();
        Map<String, ProviderResiliencyConfig> providerConfigs = Map.of("my-provider", config);
        ProviderResiliencyRegistry registry = new ProviderResiliencyRegistry(providerConfigs, defaultService);

        // When & Then
        assertThat(registry.hasProviderConfig("my-provider")).isTrue();
        assertThat(registry.hasProviderConfig("other-provider")).isFalse();
    }
}
