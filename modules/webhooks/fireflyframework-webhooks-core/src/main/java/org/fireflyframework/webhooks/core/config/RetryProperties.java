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

package org.fireflyframework.webhooks.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for retry behavior.
 * <p>
 * Supports global defaults and per-provider overrides.
 */
@Configuration
@Validated
@ConfigurationProperties(prefix = "firefly.webhooks.retry")
@Data
public class RetryProperties {

    /**
     * Global retry configuration (default for all providers)
     */
    private RetryConfig defaults = new RetryConfig();

    /**
     * Per-provider retry configuration overrides
     * <p>
     * Example:
     * <pre>
     * firefly:
     *   webhooks:
     *     retry:
     *       providers:
     *         stripe:
     *           max-attempts: 5
     *           initial-delay: PT2S
     *         paypal:
     *           max-attempts: 3
     *           initial-delay: PT1S
     * </pre>
     */
    private Map<String, RetryConfig> providers = new HashMap<>();

    /**
     * Gets the retry configuration for a specific provider.
     * Falls back to defaults if no provider-specific config exists.
     *
     * @param providerName the provider name
     * @return the retry configuration
     */
    public RetryConfig getConfigForProvider(String providerName) {
        return providers.getOrDefault(providerName, defaults);
    }

    /**
     * Retry configuration for a provider or global defaults
     */
    @Data
    public static class RetryConfig {
        /**
         * Maximum number of retry attempts
         */
        private int maxAttempts = 3;

        /**
         * Initial delay before first retry
         */
        private Duration initialDelay = Duration.ofSeconds(1);

        /**
         * Maximum delay between retries
         */
        private Duration maxDelay = Duration.ofSeconds(30);

        /**
         * Multiplier for exponential backoff
         * <p>
         * Delay calculation: min(initialDelay * (multiplier ^ attemptNumber), maxDelay)
         */
        private double multiplier = 2.0;

        /**
         * Enable jitter to randomize retry delays
         * <p>
         * Jitter helps prevent thundering herd problem when many requests retry simultaneously.
         * When enabled, actual delay = baseDelay * (1 + random(0, jitterFactor))
         */
        private boolean enableJitter = true;

        /**
         * Jitter factor (0.0 to 1.0)
         * <p>
         * 0.0 = no jitter, 1.0 = up to 100% additional delay
         */
        private double jitterFactor = 0.5;

        /**
         * Whether to retry on timeout errors
         */
        private boolean retryOnTimeout = true;

        /**
         * Whether to retry on connection errors
         */
        private boolean retryOnConnectionError = true;

        /**
         * Whether to retry on server errors (5xx)
         */
        private boolean retryOnServerError = true;

        /**
         * Whether to retry on client errors (4xx)
         * <p>
         * Usually false, as 4xx errors are typically not transient
         */
        private boolean retryOnClientError = false;
    }
}

