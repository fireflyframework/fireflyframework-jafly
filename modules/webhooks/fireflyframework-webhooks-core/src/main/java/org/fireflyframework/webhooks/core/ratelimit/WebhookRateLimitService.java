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

package org.fireflyframework.webhooks.core.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Service for rate limiting webhook requests using Resilience4j.
 * Provides per-provider and per-IP rate limiting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookRateLimitService {

    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Applies rate limiting to a webhook request by provider.
     *
     * @param providerName the provider name
     * @param operation the operation to rate limit
     * @param <T> the return type
     * @return Mono with rate limiting applied
     */
    public <T> Mono<T> executeWithRateLimit(String providerName, Mono<T> operation) {
        RateLimiter rateLimiter = getRateLimiterForProvider(providerName);
        
        return operation
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorMap(RequestNotPermitted.class, e -> {
                    log.warn("Rate limit exceeded for provider: {}", providerName);
                    return new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limit exceeded for provider: " + providerName + 
                            ". Please retry after some time."
                    );
                });
    }

    /**
     * Applies rate limiting by IP address.
     *
     * @param sourceIp the source IP address
     * @param operation the operation to rate limit
     * @param <T> the return type
     * @return Mono with rate limiting applied
     */
    public <T> Mono<T> executeWithIpRateLimit(String sourceIp, Mono<T> operation) {
        RateLimiter rateLimiter = getRateLimiterForIp(sourceIp);
        
        return operation
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorMap(RequestNotPermitted.class, e -> {
                    log.warn("Rate limit exceeded for IP: {}", sourceIp);
                    return new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limit exceeded for your IP address. Please retry after some time."
                    );
                });
    }

    /**
     * Gets or creates a rate limiter for a specific provider.
     *
     * @param providerName the provider name
     * @return the rate limiter instance
     */
    private RateLimiter getRateLimiterForProvider(String providerName) {
        String rateLimiterName = "webhook-provider-" + providerName;
        return rateLimiterRegistry.rateLimiter(rateLimiterName);
    }

    /**
     * Gets or creates a rate limiter for a specific IP address.
     *
     * @param sourceIp the source IP
     * @return the rate limiter instance
     */
    private RateLimiter getRateLimiterForIp(String sourceIp) {
        String rateLimiterName = "webhook-ip-" + sourceIp;
        return rateLimiterRegistry.rateLimiter(rateLimiterName);
    }

    /**
     * Checks if a provider is currently rate limited without executing an operation.
     *
     * @param providerName the provider name
     * @return true if rate limit would be exceeded
     */
    public boolean isRateLimited(String providerName) {
        RateLimiter rateLimiter = getRateLimiterForProvider(providerName);
        return !rateLimiter.acquirePermission();
    }
}

