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

import org.fireflyframework.client.exception.*;
import org.fireflyframework.client.interceptor.InterceptorChain;
import org.fireflyframework.client.interceptor.InterceptorRequest;
import org.fireflyframework.client.interceptor.InterceptorResponse;
import org.fireflyframework.client.interceptor.ServiceClientInterceptor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chaos Engineering interceptor for fault injection and resilience testing.
 * 
 * <p>This interceptor implements various fault injection strategies to test
 * the resilience of service clients and downstream systems. It's designed for
 * testing environments to validate circuit breakers, retries, timeouts, and
 * error handling.
 *
 * <p><strong>WARNING:</strong> This interceptor should NEVER be enabled in production
 * unless you're running controlled chaos experiments with proper monitoring.
 *
 * <p>Features:
 * <ul>
 *   <li>Latency injection - Add artificial delays</li>
 *   <li>Error injection - Throw exceptions or return errors</li>
 *   <li>Timeout injection - Force requests to timeout</li>
 *   <li>Network failure simulation - Simulate connection failures</li>
 *   <li>Response corruption - Corrupt response data</li>
 *   <li>Circuit breaker manipulation - Force circuit states</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * FaultInjectionConfig config = FaultInjectionConfig.builder()
 *     .enabled(true)
 *     .latencyInjectionEnabled(true)
 *     .latencyProbability(0.2)
 *     .errorInjectionEnabled(true)
 *     .errorProbability(0.1)
 *     .build();
 *
 * ChaosEngineeringInterceptor interceptor = new ChaosEngineeringInterceptor(config);
 *
 * RestClient client = ServiceClient.rest("user-service")
 *     .baseUrl("http://localhost:8080")
 *     .interceptor(interceptor)
 *     .build();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class ChaosEngineeringInterceptor implements ServiceClientInterceptor {

    private final FaultInjectionConfig config;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong latencyInjections = new AtomicLong(0);
    private final AtomicLong errorInjections = new AtomicLong(0);
    private final AtomicLong timeoutInjections = new AtomicLong(0);
    private final AtomicLong networkFailureInjections = new AtomicLong(0);
    private final AtomicLong responseCorruptions = new AtomicLong(0);

    public ChaosEngineeringInterceptor(FaultInjectionConfig config) {
        this.config = config;
        
        if (config.isEnabled()) {
            log.warn("⚠️  CHAOS ENGINEERING ENABLED - Fault injection is active!");
            log.warn("Configuration: latency={}, errors={}, timeouts={}, network={}, corruption={}",
                config.isLatencyInjectionEnabled(),
                config.isErrorInjectionEnabled(),
                config.isTimeoutInjectionEnabled(),
                config.isNetworkFailureInjectionEnabled(),
                config.isResponseCorruptionEnabled());
        }
    }

    @Override
    public Mono<InterceptorResponse> intercept(InterceptorRequest request, InterceptorChain chain) {
        if (!config.isEnabled()) {
            return chain.proceed(request);
        }

        String serviceName = request.getServiceName();
        
        if (!config.shouldInjectFault(serviceName)) {
            return chain.proceed(request);
        }

        totalRequests.incrementAndGet();

        // Priority order: Network failure > Timeout > Error > Latency > Response corruption
        
        // 1. Network failure injection (highest priority - prevents request)
        if (config.shouldInjectNetworkFailure()) {
            return injectNetworkFailure(request);
        }

        // 2. Timeout injection
        if (config.shouldInjectTimeout()) {
            return injectTimeout(request, chain);
        }

        // 3. Error injection
        if (config.shouldInjectError()) {
            return injectError(request);
        }

        // 4. Latency injection
        if (config.shouldInjectLatency()) {
            return injectLatency(request, chain);
        }

        // 5. Normal execution with possible response corruption
        return chain.proceed(request)
            .flatMap(response -> {
                if (config.shouldCorruptResponse()) {
                    return corruptResponse(response);
                }
                return Mono.just(response);
            });
    }

    /**
     * Injects artificial latency into the request.
     */
    private Mono<InterceptorResponse> injectLatency(InterceptorRequest request, InterceptorChain chain) {
        Duration latency = config.getRandomLatency();
        latencyInjections.incrementAndGet();
        
        log.debug("🐌 Chaos: Injecting {}ms latency for {} {}",
            latency.toMillis(), request.getMethod(), request.getEndpoint());

        return Mono.delay(latency)
            .then(chain.proceed(request))
            .doOnSuccess(response -> 
                log.debug("✅ Chaos: Latency injection completed for {} {}", 
                    request.getMethod(), request.getEndpoint()));
    }

    /**
     * Injects errors into the request.
     */
    private Mono<InterceptorResponse> injectError(InterceptorRequest request) {
        errorInjections.incrementAndGet();
        
        FaultInjectionConfig.ErrorType errorType = config.getErrorType();
        if (errorType == FaultInjectionConfig.ErrorType.RANDOM) {
            errorType = getRandomErrorType();
        }

        RuntimeException exception = createException(errorType, request);
        
        log.warn("💥 Chaos: Injecting {} error for {} {}",
            errorType, request.getMethod(), request.getEndpoint());

        return Mono.error(exception);
    }

    /**
     * Injects timeout into the request.
     */
    private Mono<InterceptorResponse> injectTimeout(InterceptorRequest request, InterceptorChain chain) {
        timeoutInjections.incrementAndGet();

        log.warn("⏱️  Chaos: Injecting timeout for {} {}",
            request.getMethod(), request.getEndpoint());

        ErrorContext errorContext = ErrorContext.builder()
            .serviceName(request.getServiceName())
            .endpoint(request.getEndpoint())
            .method(request.getMethod())
            .build();

        // Delay longer than the timeout, then throw timeout exception
        return Mono.delay(config.getTimeoutDuration().plusSeconds(1))
            .then(Mono.error(new ServiceTimeoutException(
                "Chaos Engineering: Injected timeout",
                errorContext
            )));
    }

    /**
     * Injects network failure into the request.
     */
    private Mono<InterceptorResponse> injectNetworkFailure(InterceptorRequest request) {
        networkFailureInjections.incrementAndGet();

        log.warn("🔌 Chaos: Injecting network failure for {} {}",
            request.getMethod(), request.getEndpoint());

        ErrorContext errorContext = ErrorContext.builder()
            .serviceName(request.getServiceName())
            .endpoint(request.getEndpoint())
            .method(request.getMethod())
            .build();

        return Mono.error(new ServiceConnectionException(
            "Chaos Engineering: Injected network failure - Connection refused",
            errorContext,
            new java.net.ConnectException("Connection refused (chaos injection)")
        ));
    }

    /**
     * Corrupts the response data.
     */
    private Mono<InterceptorResponse> corruptResponse(InterceptorResponse response) {
        responseCorruptions.incrementAndGet();
        
        log.warn("🔀 Chaos: Corrupting response data");

        // Corrupt response by returning null body or invalid data
        return Mono.just(response.withBody(null));
    }

    /**
     * Gets a random error type.
     */
    private FaultInjectionConfig.ErrorType getRandomErrorType() {
        FaultInjectionConfig.ErrorType[] types = FaultInjectionConfig.ErrorType.values();
        int randomIndex = ThreadLocalRandom.current().nextInt(1, types.length); // Skip RANDOM
        return types[randomIndex];
    }

    /**
     * Creates an exception based on error type.
     */
    private RuntimeException createException(FaultInjectionConfig.ErrorType errorType, InterceptorRequest request) {
        String serviceName = request.getServiceName();
        String endpoint = request.getEndpoint();
        String message = "Chaos Engineering: Injected " + errorType.name().toLowerCase().replace('_', ' ');

        ErrorContext errorContext = ErrorContext.builder()
            .serviceName(serviceName)
            .endpoint(endpoint)
            .method(request.getMethod())
            .build();

        return switch (errorType) {
            case TIMEOUT -> new ServiceTimeoutException(message, errorContext);
            case CONNECTION_REFUSED -> new ServiceConnectionException(
                message,
                errorContext,
                new java.net.ConnectException("Connection refused")
            );
            case SERVICE_UNAVAILABLE -> new ServiceUnavailableException(message, errorContext);
            case INTERNAL_ERROR -> new ServiceInternalErrorException(message, errorContext);
            case BAD_GATEWAY -> new ServiceInternalErrorException(message, errorContext);
            case RATE_LIMIT -> new ServiceRateLimitException(message, 60, errorContext);
            default -> new ServiceClientException(message, errorContext);
        };
    }

    @Override
    public boolean shouldIntercept(InterceptorRequest request) {
        return config.isEnabled() && config.shouldInjectFault(request.getServiceName());
    }

    @Override
    public int getOrder() {
        return 10; // Execute very early in the chain
    }

    /**
     * Gets chaos engineering statistics.
     */
    public ChaosStatistics getStatistics() {
        return new ChaosStatistics(
            totalRequests.get(),
            latencyInjections.get(),
            errorInjections.get(),
            timeoutInjections.get(),
            networkFailureInjections.get(),
            responseCorruptions.get()
        );
    }

    /**
     * Resets statistics counters.
     */
    public void resetStatistics() {
        totalRequests.set(0);
        latencyInjections.set(0);
        errorInjections.set(0);
        timeoutInjections.set(0);
        networkFailureInjections.set(0);
        responseCorruptions.set(0);
    }

    /**
     * Statistics for chaos engineering operations.
     */
    public record ChaosStatistics(
        long totalRequests,
        long latencyInjections,
        long errorInjections,
        long timeoutInjections,
        long networkFailureInjections,
        long responseCorruptions
    ) {
        public double getLatencyInjectionRate() {
            return totalRequests > 0 ? (double) latencyInjections / totalRequests : 0.0;
        }

        public double getErrorInjectionRate() {
            return totalRequests > 0 ? (double) errorInjections / totalRequests : 0.0;
        }

        public double getTimeoutInjectionRate() {
            return totalRequests > 0 ? (double) timeoutInjections / totalRequests : 0.0;
        }

        public double getNetworkFailureInjectionRate() {
            return totalRequests > 0 ? (double) networkFailureInjections / totalRequests : 0.0;
        }

        public double getResponseCorruptionRate() {
            return totalRequests > 0 ? (double) responseCorruptions / totalRequests : 0.0;
        }

        public long getTotalInjections() {
            return latencyInjections + errorInjections + timeoutInjections + 
                   networkFailureInjections + responseCorruptions;
        }
    }
}

