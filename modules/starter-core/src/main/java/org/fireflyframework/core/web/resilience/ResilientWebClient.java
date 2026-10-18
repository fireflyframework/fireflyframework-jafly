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


package org.fireflyframework.core.web.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Function;

/**
 * A decorator for {@link WebClient} that adds resilience patterns like
 * circuit breaker, retry, timeout, bulkhead, and metrics.
 * <p>
 * This class implements the Decorator pattern to enhance a {@link WebClient}
 * with resilience capabilities. It wraps an existing WebClient and adds the following
 * features:
 * <ul>
 *   <li><strong>Circuit Breaker:</strong> Prevents cascading failures by stopping calls to a failing service</li>
 *   <li><strong>Retry:</strong> Automatically retries failed operations with configurable backoff</li>
 *   <li><strong>Timeout:</strong> Sets maximum duration for operations to prevent blocked threads</li>
 *   <li><strong>Bulkhead:</strong> Limits the number of concurrent calls to a service</li>
 *   <li><strong>Metrics:</strong> Collects and exposes metrics for monitoring and alerting</li>
 * </ul>
 * <p>
 * The resilience features are implemented using the Resilience4j library, which provides
 * a comprehensive set of fault tolerance mechanisms for reactive applications.
 * <p>
 * The metrics are collected using Micrometer, which provides a vendor-neutral metrics facade
 * that can be integrated with various monitoring systems like Prometheus, Datadog, etc.
 */
@Slf4j
public class ResilientWebClient {

    private final WebClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final Bulkhead bulkhead;
    private final MeterRegistry meterRegistry;
    private final String clientName;

    /**
     * Creates a new ResilientWebClient.
     *
     * @param delegate the delegate WebClient
     * @param circuitBreakerRegistry the circuit breaker registry
     * @param retryRegistry the retry registry
     * @param timeLimiterRegistry the time limiter registry
     * @param bulkheadRegistry the bulkhead registry
     * @param meterRegistryProvider the meter registry provider
     * @param clientName the name of the client
     */
    public ResilientWebClient(
            WebClient delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            String clientName) {
        this.delegate = delegate;
        this.clientName = clientName;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(clientName + "-circuit-breaker");
        this.retry = retryRegistry.retry(clientName + "-retry");
        this.timeLimiter = timeLimiterRegistry.timeLimiter(clientName + "-time-limiter");
        this.bulkhead = bulkheadRegistry.bulkhead(clientName + "-bulkhead");
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    /**
     * Creates a new WebClient with resilience capabilities.
     *
     * @return a new WebClient with resilience capabilities
     */
    public WebClient.Builder mutate() {
        return delegate.mutate()
                .filter(createResilienceFilter());
    }

    /**
     * Creates a resilience filter that applies all resilience patterns.
     *
     * @return the resilience filter
     */
    private ExchangeFilterFunction createResilienceFilter() {
        return (request, next) -> {
            Mono<ClientResponse> responseMono = executeWithResilience(request, next);

            if (meterRegistry != null) {
                Timer timer = Timer.builder("webclient.request")
                        .tag("client", clientName)
                        .tag("method", request.method().name())
                        .tag("uri", request.url().getPath())
                        .register(meterRegistry);

                return Mono.defer(() -> {
                    long startTime = System.nanoTime();
                    return responseMono.doFinally(signal -> {
                        long endTime = System.nanoTime();
                        timer.record(Duration.ofNanos(endTime - startTime));
                    });
                });
            }

            return responseMono;
        };
    }

    /**
     * Executes a request with all resilience patterns applied.
     *
     * @param request the request
     * @param next the next exchange function
     * @return the response mono
     */
    private Mono<ClientResponse> executeWithResilience(ClientRequest request, ExchangeFunction next) {
        return Mono.defer(() -> next.exchange(request))
                .doOnError(e -> log.error("Error executing request to {}: {}", request.url(), e.getMessage()))
                .transformDeferred(BulkheadOperator.of(bulkhead))
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    /**
     * Creates a new WebClient with resilience capabilities.
     *
     * @return a new WebClient with resilience capabilities
     */
    public WebClient build() {
        return mutate().build();
    }
}