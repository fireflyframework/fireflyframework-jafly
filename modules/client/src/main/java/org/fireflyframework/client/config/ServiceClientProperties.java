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

package org.fireflyframework.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced configuration properties for ServiceClient framework.
 *
 * <p>This configuration class provides comprehensive settings for all ServiceClient types
 * with validation, environment-specific defaults, and improved organization.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Validated
@ConfigurationProperties(prefix = "firefly.service-client")
@Data
public class ServiceClientProperties {

    /**
     * Whether ServiceClient framework is enabled.
     */
    private boolean enabled = true;

    /**
     * Global default timeout for all client operations.
     */
    private Duration defaultTimeout = Duration.ofSeconds(30);

    /**
     * Environment-specific configuration profiles.
     */
    private Environment environment = Environment.DEVELOPMENT;

    /**
     * Global default headers applied to all REST clients.
     */
    private Map<String, String> defaultHeaders = new HashMap<>();

    /**
     * REST client configuration.
     */
    private Rest rest = new Rest();

    /**
     * gRPC client configuration.
     */
    private Grpc grpc = new Grpc();

    /**
     * SOAP client configuration.
     */
    private Soap soap = new Soap();

    /**
     * SDK client configuration.
     */
    private Sdk sdk = new Sdk();

    /**
     * Circuit breaker configuration.
     */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /**
     * Retry configuration.
     */
    private Retry retry = new Retry();

    /**
     * Metrics and monitoring configuration.
     */
    private Metrics metrics = new Metrics();

    /**
     * Security configuration.
     */
    private Security security = new Security();

    @Data
    public static class Rest {
        /**
         * Maximum number of connections in the pool.
         */
        private int maxConnections = 100;

        /**
         * Maximum idle time for connections.
         */
        private Duration maxIdleTime = Duration.ofMinutes(5);

        /**
         * Maximum lifetime for connections.
         */
        private Duration maxLifeTime = Duration.ofMinutes(30);

        /**
         * Timeout for acquiring a connection from the pool.
         */
        private Duration pendingAcquireTimeout = Duration.ofSeconds(10);

        /**
         * Response timeout for HTTP requests.
         */
        private Duration responseTimeout = Duration.ofSeconds(30);

        /**
         * Maximum in-memory size for response bodies.
         */
        private int maxInMemorySize = 1024 * 1024; // 1MB

        /**
         * Default connect timeout.
         */
        private Duration connectTimeout = Duration.ofSeconds(10);

        /**
         * Default read timeout.
         */
        private Duration readTimeout = Duration.ofSeconds(30);

        /**
         * Whether to follow redirects automatically.
         */
        private boolean followRedirects = true;

        /**
         * Whether to enable compression.
         */
        private boolean compressionEnabled = true;

        /**
         * Default content type for requests.
         */
        private String defaultContentType = "application/json";

        /**
         * Default accept type for requests.
         */
        private String defaultAcceptType = "application/json";

        /**
         * Whether to enable request/response logging.
         */
        private boolean loggingEnabled = false;

        /**
         * Maximum number of retries for connection failures.
         */
        private int maxRetries = 3;

        /**
         * Environment-specific settings.
         * Only applies defaults if values haven't been explicitly configured.
         */
        public void applyEnvironmentDefaults(Environment environment) {
            switch (environment) {
                case DEVELOPMENT:
                    if (this.maxConnections == 100) { // Only if still at default
                        this.maxConnections = 50;
                    }
                    if (this.responseTimeout.equals(Duration.ofSeconds(30))) { // Only if still at default
                        this.responseTimeout = Duration.ofSeconds(60);
                    }
                    this.loggingEnabled = true;
                    break;
                case TESTING:
                    if (this.maxConnections == 100) { // Only if still at default
                        this.maxConnections = 20;
                    }
                    if (this.responseTimeout.equals(Duration.ofSeconds(30))) { // Only if still at default
                        this.responseTimeout = Duration.ofSeconds(30);
                    }
                    this.loggingEnabled = true;
                    break;
                case PRODUCTION:
                    if (this.maxConnections == 100) { // Only if still at default
                        this.maxConnections = 200;
                    }
                    if (this.responseTimeout.equals(Duration.ofSeconds(30))) { // Only if still at default
                        this.responseTimeout = Duration.ofSeconds(30);
                    }
                    this.loggingEnabled = false;
                    this.compressionEnabled = true;
                    break;
            }
        }
    }

    @Data
    public static class Grpc {
        /**
         * Keep alive time for gRPC connections.
         */
        private Duration keepAliveTime = Duration.ofMinutes(5);

        /**
         * Keep alive timeout for gRPC connections.
         */
        private Duration keepAliveTimeout = Duration.ofSeconds(30);

        /**
         * Whether to keep alive without calls.
         */
        private boolean keepAliveWithoutCalls = true;

        /**
         * Maximum inbound message size.
         */
        private int maxInboundMessageSize = 4 * 1024 * 1024; // 4MB

        /**
         * Maximum inbound metadata size.
         */
        private int maxInboundMetadataSize = 8 * 1024; // 8KB

        /**
         * Default deadline for gRPC calls.
         */
        private Duration callTimeout = Duration.ofSeconds(30);

        /**
         * Whether to enable gRPC retry.
         */
        private boolean retryEnabled = true;

        /**
         * Whether to use plaintext connections by default.
         */
        private boolean usePlaintextByDefault = true;

        /**
         * Whether to enable gRPC compression.
         */
        private boolean compressionEnabled = true;

        /**
         * Maximum number of concurrent streams per connection.
         */
        private int maxConcurrentStreams = 100;

        /**
         * Environment-specific settings.
         * Only applies defaults if values haven't been explicitly configured.
         */
        public void applyEnvironmentDefaults(Environment environment) {
            switch (environment) {
                case DEVELOPMENT:
                    this.usePlaintextByDefault = true;
                    if (this.maxInboundMessageSize == 4 * 1024 * 1024) { // Only if still at default
                        this.maxInboundMessageSize = 8 * 1024 * 1024; // 8MB for dev
                    }
                    if (this.maxConcurrentStreams == 100) { // Only if still at default
                        this.maxConcurrentStreams = 50;
                    }
                    break;
                case TESTING:
                    this.usePlaintextByDefault = true;
                    if (this.maxConcurrentStreams == 100) { // Only if still at default
                        this.maxConcurrentStreams = 25;
                    }
                    break;
                case PRODUCTION:
                    this.usePlaintextByDefault = false;
                    this.compressionEnabled = true;
                    if (this.maxConcurrentStreams == 100) { // Only if still at default
                        this.maxConcurrentStreams = 200;
                    }
                    break;
            }
        }
    }

    @Data
    public static class Soap {
        /**
         * Default timeout for SOAP operations.
         */
        private Duration defaultTimeout = Duration.ofSeconds(30);

        /**
         * Connection timeout for SOAP services.
         */
        private Duration connectionTimeout = Duration.ofSeconds(10);

        /**
         * Receive timeout for SOAP responses.
         */
        private Duration receiveTimeout = Duration.ofSeconds(30);

        /**
         * Whether to enable MTOM (Message Transmission Optimization Mechanism) by default.
         */
        private boolean mtomEnabled = false;

        /**
         * Whether to enable XML schema validation.
         */
        private boolean schemaValidationEnabled = true;

        /**
         * Whether to enable SOAP message logging.
         */
        private boolean messageLoggingEnabled = false;

        /**
         * Maximum size for SOAP messages in bytes.
         */
        private int maxMessageSize = 10 * 1024 * 1024; // 10MB

        /**
         * Whether to enable WS-Addressing.
         */
        private boolean wsAddressingEnabled = false;

        /**
         * Whether to enable WS-Security by default.
         */
        private boolean wsSecurityEnabled = false;

        /**
         * Default SOAP version (1.1 or 1.2).
         */
        private String soapVersion = "1.1";

        /**
         * Whether to cache WSDL definitions.
         */
        private boolean wsdlCacheEnabled = true;

        /**
         * WSDL cache expiration time.
         */
        private Duration wsdlCacheExpiration = Duration.ofHours(1);

        /**
         * Whether to follow HTTP redirects when fetching WSDL.
         */
        private boolean followRedirects = true;

        /**
         * Custom SOAP properties.
         */
        private Map<String, Object> properties = new HashMap<>();

        /**
         * Environment-specific settings.
         */
        public void applyEnvironmentDefaults(Environment environment) {
            switch (environment) {
                case DEVELOPMENT:
                    if (this.defaultTimeout.equals(Duration.ofSeconds(30))) {
                        this.defaultTimeout = Duration.ofSeconds(60);
                    }
                    this.messageLoggingEnabled = true;
                    this.schemaValidationEnabled = true;
                    break;
                case TESTING:
                    if (this.defaultTimeout.equals(Duration.ofSeconds(30))) {
                        this.defaultTimeout = Duration.ofSeconds(30);
                    }
                    this.messageLoggingEnabled = true;
                    this.schemaValidationEnabled = false; // Faster tests
                    break;
                case PRODUCTION:
                    if (this.defaultTimeout.equals(Duration.ofSeconds(30))) {
                        this.defaultTimeout = Duration.ofSeconds(30);
                    }
                    this.messageLoggingEnabled = false;
                    this.schemaValidationEnabled = true;
                    this.wsdlCacheEnabled = true;
                    break;
            }
        }
    }

    @Data

    public static class Sdk {
        /**
         * Default timeout for SDK operations.
         */
        
        private Duration defaultTimeout = Duration.ofSeconds(45);

        /**
         * Whether to enable automatic SDK shutdown.
         */
        private boolean autoShutdownEnabled = true;

        /**
         * Maximum number of concurrent SDK operations.
         */
        
        private int maxConcurrentOperations = 50;

        /**
         * Whether to enable SDK operation logging.
         */
        private boolean loggingEnabled = false;

        /**
         * SDK-specific configuration properties.
         */
        private Map<String, Object> properties = new HashMap<>();

        /**
         * Environment-specific settings.
         * Only applies defaults if values haven't been explicitly configured.
         */
        public void applyEnvironmentDefaults(Environment environment) {
            switch (environment) {
                case DEVELOPMENT:
                    if (this.defaultTimeout.equals(Duration.ofSeconds(45))) { // Only if still at default
                        this.defaultTimeout = Duration.ofMinutes(2);
                    }
                    this.loggingEnabled = true;
                    if (this.maxConcurrentOperations == 50) { // Only if still at default
                        this.maxConcurrentOperations = 25;
                    }
                    break;
                case TESTING:
                    if (this.defaultTimeout.equals(Duration.ofSeconds(45))) { // Only if still at default
                        this.defaultTimeout = Duration.ofSeconds(30);
                    }
                    this.loggingEnabled = true;
                    if (this.maxConcurrentOperations == 50) { // Only if still at default
                        this.maxConcurrentOperations = 10;
                    }
                    break;
                case PRODUCTION:
                    if (this.defaultTimeout.equals(Duration.ofSeconds(45))) { // Only if still at default
                        this.defaultTimeout = Duration.ofSeconds(45);
                    }
                    this.loggingEnabled = false;
                    if (this.maxConcurrentOperations == 50) { // Only if still at default
                        this.maxConcurrentOperations = 100;
                    }
                    break;
            }
        }
    }

    @Data
    
    public static class CircuitBreaker {
        /**
         * Whether circuit breaker is enabled globally.
         */
        private boolean enabled = true;

        /**
         * Failure rate threshold (percentage).
         */
        
        private float failureRateThreshold = 50.0f;

        /**
         * Wait duration in open state.
         */
        
        private Duration waitDurationInOpenState = Duration.ofSeconds(60);

        /**
         * Sliding window size for failure rate calculation.
         */
        
        private int slidingWindowSize = 10;

        /**
         * Minimum number of calls before circuit breaker can calculate failure rate.
         */
        
        private int minimumNumberOfCalls = 5;

        /**
         * Slow call rate threshold (percentage).
         */
        
        private float slowCallRateThreshold = 100.0f;

        /**
         * Slow call duration threshold.
         */
        
        private Duration slowCallDurationThreshold = Duration.ofSeconds(60);

        /**
         * Number of permitted calls in half-open state.
         */
        
        private int permittedNumberOfCallsInHalfOpenState = 3;

        /**
         * Maximum wait duration in half-open state.
         */

        private Duration maxWaitDurationInHalfOpenState = Duration.ofSeconds(0);

        /**
         * Call timeout duration.
         */

        private Duration callTimeout = Duration.ofSeconds(30);

        /**
         * Whether automatic transition from open to half-open state is enabled.
         */

        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;

        /**
         * Environment-specific settings.
         * Only applies defaults if values haven't been explicitly configured.
         */
        public void applyEnvironmentDefaults(Environment environment) {
            switch (environment) {
                case DEVELOPMENT:
                    if (this.failureRateThreshold == 50.0f) { // Only if still at default
                        this.failureRateThreshold = 70.0f; // More lenient in dev
                    }
                    if (this.waitDurationInOpenState.equals(Duration.ofSeconds(60))) { // Only if still at default
                        this.waitDurationInOpenState = Duration.ofSeconds(30);
                    }
                    break;
                case TESTING:
                    if (this.failureRateThreshold == 50.0f) { // Only if still at default
                        this.failureRateThreshold = 60.0f;
                    }
                    if (this.waitDurationInOpenState.equals(Duration.ofSeconds(60))) { // Only if still at default
                        this.waitDurationInOpenState = Duration.ofSeconds(15);
                    }
                    break;
                case PRODUCTION:
                    if (this.failureRateThreshold == 50.0f) { // Only if still at default
                        this.failureRateThreshold = 50.0f;
                    }
                    if (this.waitDurationInOpenState.equals(Duration.ofSeconds(60))) { // Only if still at default
                        this.waitDurationInOpenState = Duration.ofSeconds(60);
                    }
                    break;
            }
        }
    }

    @Data
    
    public static class Retry {
        /**
         * Whether retry is enabled globally.
         */
        private boolean enabled = true;

        /**
         * Maximum number of retry attempts.
         */
        
        private int maxAttempts = 3;

        /**
         * Wait duration between retries.
         */
        
        private Duration waitDuration = Duration.ofMillis(500);

        /**
         * Exponential backoff multiplier.
         */
        
        private double exponentialBackoffMultiplier = 2.0;

        /**
         * Maximum wait duration for exponential backoff.
         */
        
        private Duration maxWaitDuration = Duration.ofSeconds(10);

        /**
         * Whether to use jitter in wait duration.
         */
        private boolean jitterEnabled = true;

        /**
         * Environment-specific settings.
         * Only applies defaults if values haven't been explicitly configured.
         */
        public void applyEnvironmentDefaults(Environment environment) {
            switch (environment) {
                case DEVELOPMENT:
                    if (this.maxAttempts == 3) { // Only if still at default
                        this.maxAttempts = 5; // More retries in dev
                    }
                    if (this.waitDuration.equals(Duration.ofMillis(500))) { // Only if still at default
                        this.waitDuration = Duration.ofSeconds(1);
                    }
                    break;
                case TESTING:
                    if (this.maxAttempts == 3) { // Only if still at default
                        this.maxAttempts = 2; // Fewer retries in tests
                    }
                    if (this.waitDuration.equals(Duration.ofMillis(500))) { // Only if still at default
                        this.waitDuration = Duration.ofMillis(100);
                    }
                    break;
                case PRODUCTION:
                    if (this.maxAttempts == 3) { // Only if still at default
                        this.maxAttempts = 3;
                    }
                    if (this.waitDuration.equals(Duration.ofMillis(500))) { // Only if still at default
                        this.waitDuration = Duration.ofMillis(500);
                    }
                    break;
            }
        }
    }

    @Data
    
    public static class Metrics {
        /**
         * Whether metrics collection is enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to include detailed request/response metrics.
         */
        private boolean detailedMetrics = false;

        /**
         * Metrics export interval.
         */
        
        private Duration exportInterval = Duration.ofMinutes(1);

        /**
         * Whether to include histogram metrics.
         */
        private boolean histogramEnabled = true;

        /**
         * Custom metric tags.
         */
        private Map<String, String> tags = new HashMap<>();

        /**
         * Environment-specific settings.
         * Only applies defaults if values haven't been explicitly configured.
         */
        public void applyEnvironmentDefaults(Environment environment) {
            switch (environment) {
                case DEVELOPMENT:
                    this.detailedMetrics = true;
                    if (this.exportInterval.equals(Duration.ofMinutes(1))) { // Only if still at default
                        this.exportInterval = Duration.ofSeconds(30);
                    }
                    break;
                case TESTING:
                    this.enabled = false; // Disable metrics in tests
                    break;
                case PRODUCTION:
                    this.detailedMetrics = false;
                    if (this.exportInterval.equals(Duration.ofMinutes(1))) { // Only if still at default
                        this.exportInterval = Duration.ofMinutes(5);
                    }
                    break;
            }
        }
    }

    @Data
    
    public static class Security {
        /**
         * Whether security features are enabled.
         */
        private boolean enabled = true;

        /**
         * Default authentication type.
         */
        private AuthenticationType defaultAuthType = AuthenticationType.NONE;

        /**
         * Whether to validate SSL certificates.
         */
        private boolean sslValidationEnabled = true;

        /**
         * Trusted certificate authorities.
         */
        private String trustedCertificates;

        /**
         * Whether to enable request signing.
         */
        private boolean requestSigningEnabled = false;

        /**
         * Environment-specific settings.
         * Only applies defaults if values haven't been explicitly configured.
         */
        public void applyEnvironmentDefaults(Environment environment) {
            switch (environment) {
                case DEVELOPMENT:
                    this.sslValidationEnabled = false; // Relaxed SSL in dev
                    break;
                case TESTING:
                    this.sslValidationEnabled = false;
                    break;
                case PRODUCTION:
                    this.sslValidationEnabled = true;
                    this.requestSigningEnabled = true;
                    break;
            }
        }
    }

    /**
     * Environment types for configuration profiles.
     */
    public enum Environment {
        DEVELOPMENT,
        TESTING,
        PRODUCTION
    }

    /**
     * Authentication types.
     */
    public enum AuthenticationType {
        NONE,
        BASIC,
        BEARER,
        OAUTH2,
        CUSTOM
    }

    /**
     * Applies environment-specific defaults to all configuration sections.
     */
    public void applyEnvironmentDefaults() {
        rest.applyEnvironmentDefaults(environment);
        grpc.applyEnvironmentDefaults(environment);
        soap.applyEnvironmentDefaults(environment);
        sdk.applyEnvironmentDefaults(environment);
        circuitBreaker.applyEnvironmentDefaults(environment);
        retry.applyEnvironmentDefaults(environment);
        metrics.applyEnvironmentDefaults(environment);
        security.applyEnvironmentDefaults(environment);
    }
}