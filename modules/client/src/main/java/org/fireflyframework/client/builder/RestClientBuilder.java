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

package org.fireflyframework.client.builder;

import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.impl.RestServiceClientImpl;
import org.fireflyframework.resilience.CircuitBreakerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Simplified builder for REST service clients.
 * 
 * <p>This builder provides a fluent API for creating REST service clients with
 * sensible defaults and simplified configuration options. It reduces the complexity
 * of the original builder while maintaining all essential functionality.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Simple REST client
 * ServiceClient client = ServiceClient.rest("user-service")
 *     .baseUrl("http://user-service:8080")
 *     .build();
 *
 * // REST client with custom configuration
 * ServiceClient client = ServiceClient.rest("payment-service")
 *     .baseUrl("https://payment-service:8443")
 *     .timeout(Duration.ofSeconds(30))
 *     .maxConnections(50)
 *     .defaultHeader("Accept", "application/json")
 *     .build();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class RestClientBuilder {

    private final String serviceName;
    private String baseUrl;
    private Duration timeout = Duration.ofSeconds(30);
    private int maxConnections = 100;
    private Map<String, String> defaultHeaders = new HashMap<>();
    private WebClient webClient;
    private CircuitBreakerManager circuitBreakerManager;

    // Retry configuration
    private boolean retryEnabled = true;
    private int retryMaxAttempts = 3;
    private Duration retryInitialBackoff = Duration.ofMillis(500);
    private Duration retryMaxBackoff = Duration.ofSeconds(10);
    private boolean retryJitterEnabled = true;

    /**
     * Creates a new REST client builder.
     *
     * @param serviceName the service name
     */
    public RestClientBuilder(String serviceName) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }
        this.serviceName = serviceName.trim();
        
        // Set sensible defaults
        this.defaultHeaders.put("Content-Type", "application/json");
        this.defaultHeaders.put("Accept", "application/json");
        
        log.debug("Created REST client builder for service '{}'", this.serviceName);
    }

    public RestClientBuilder baseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        this.baseUrl = baseUrl.trim();
        // Remove trailing slash for consistency
        if (this.baseUrl.endsWith("/")) {
            this.baseUrl = this.baseUrl.substring(0, this.baseUrl.length() - 1);
        }
        return this;
    }

    public RestClientBuilder timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    public RestClientBuilder maxConnections(int maxConnections) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }
        this.maxConnections = maxConnections;
        return this;
    }

    public RestClientBuilder defaultHeader(String name, String value) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Header name cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Header value cannot be null");
        }
        this.defaultHeaders.put(name.trim(), value);
        return this;
    }

    /**
     * Sets a custom WebClient instance.
     * 
     * <p>When provided, this WebClient will be used instead of creating a new one.
     * The builder will still apply timeout and header configurations to this client.
     *
     * @param webClient the custom WebClient
     * @return this builder
     */
    public RestClientBuilder webClient(WebClient webClient) {
        this.webClient = webClient;
        return this;
    }

    /**
     * Sets the circuit breaker manager.
     *
     * @param circuitBreakerManager the circuit breaker manager
     * @return this builder
     */
    public RestClientBuilder circuitBreakerManager(CircuitBreakerManager circuitBreakerManager) {
        this.circuitBreakerManager = circuitBreakerManager;
        return this;
    }

    /**
     * Configures retry behavior for the client.
     *
     * @param enabled whether retry is enabled
     * @param maxAttempts maximum number of retry attempts
     * @param initialBackoff initial wait duration before first retry
     * @param maxBackoff maximum wait duration between retries
     * @param jitterEnabled whether to add randomized jitter to backoff
     * @return this builder
     */
    public RestClientBuilder retry(boolean enabled, int maxAttempts, Duration initialBackoff,
                                   Duration maxBackoff, boolean jitterEnabled) {
        this.retryEnabled = enabled;
        this.retryMaxAttempts = maxAttempts;
        this.retryInitialBackoff = initialBackoff;
        this.retryMaxBackoff = maxBackoff;
        this.retryJitterEnabled = jitterEnabled;
        return this;
    }

    /**
     * Disables retry for the client.
     *
     * @return this builder
     */
    public RestClientBuilder noRetry() {
        this.retryEnabled = false;
        return this;
    }

    /**
     * Convenience method to set JSON content type headers.
     *
     * @return this builder
     */
    public RestClientBuilder jsonContentType() {
        return defaultHeader("Content-Type", "application/json")
               .defaultHeader("Accept", "application/json");
    }

    /**
     * Convenience method to set XML content type headers.
     *
     * @return this builder
     */
    public RestClientBuilder xmlContentType() {
        return defaultHeader("Content-Type", "application/xml")
               .defaultHeader("Accept", "application/xml");
    }

    public RestClient build() {
        validateConfiguration();
        
        log.info("Building REST service client for service '{}' with base URL '{}'", 
                serviceName, baseUrl);
        
        return new RestServiceClientImpl(
            serviceName,
            baseUrl,
            timeout,
            maxConnections,
            defaultHeaders,
            webClient,
            circuitBreakerManager,
            retryEnabled,
            retryMaxAttempts,
            retryInitialBackoff,
            retryMaxBackoff,
            retryJitterEnabled
        );
    }

    private void validateConfiguration() {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("Base URL must be configured for REST clients");
        }
        
        try {
            java.net.URI.create(baseUrl).toURL();
        } catch (IllegalArgumentException | java.net.MalformedURLException e) {
            throw new IllegalStateException("Invalid base URL: " + baseUrl, e);
        }
    }
}
