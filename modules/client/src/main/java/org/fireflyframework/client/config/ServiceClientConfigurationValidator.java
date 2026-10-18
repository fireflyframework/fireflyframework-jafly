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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Configuration validator for ServiceClient properties.
 * 
 * <p>This validator ensures that all ServiceClient configuration properties are valid
 * and provides helpful error messages for common configuration mistakes.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class ServiceClientConfigurationValidator {

    private final ServiceClientProperties properties;

    public ServiceClientConfigurationValidator(ServiceClientProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates configuration when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        log.info("Validating ServiceClient configuration for environment: {}", 
                properties.getEnvironment());
        
        try {
            validateConfiguration();
            log.info("ServiceClient configuration validation completed successfully");
        } catch (Exception e) {
            log.error("ServiceClient configuration validation failed: {}", e.getMessage());
            throw new IllegalStateException("Invalid ServiceClient configuration", e);
        }
    }

    /**
     * Performs comprehensive configuration validation.
     */
    public void validateConfiguration() {
        validateGlobalConfiguration();
        validateRestConfiguration();
        validateGrpcConfiguration();
        validateSdkConfiguration();
        validateCircuitBreakerConfiguration();
        validateRetryConfiguration();
        validateMetricsConfiguration();
        validateSecurityConfiguration();
    }

    private void validateGlobalConfiguration() {
        if (properties.getDefaultTimeout() == null || properties.getDefaultTimeout().isNegative()) {
            throw new IllegalArgumentException("Default timeout must be positive");
        }

        if (properties.getEnvironment() == null) {
            throw new IllegalArgumentException("Environment must be specified");
        }

        log.debug("Global configuration validation passed");
    }

    private void validateRestConfiguration() {
        ServiceClientProperties.Rest rest = properties.getRest();

        if (rest.getMaxConnections() <= 0) {
            throw new IllegalArgumentException("REST max connections must be positive, got: " + rest.getMaxConnections());
        }

        if (rest.getMaxConnections() > 1000) {
            log.warn("REST max connections is very high ({}), consider if this is intentional", rest.getMaxConnections());
        }

        if (rest.getResponseTimeout() == null || rest.getResponseTimeout().isNegative()) {
            throw new IllegalArgumentException("REST response timeout must be positive");
        }

        if (rest.getMaxInMemorySize() <= 0) {
            throw new IllegalArgumentException("REST max in-memory size must be positive");
        }

        if (rest.getMaxInMemorySize() > 100 * 1024 * 1024) { // 100MB
            log.warn("REST max in-memory size is very high ({}), this may cause memory issues", rest.getMaxInMemorySize());
        }

        log.debug("REST configuration validation passed");
    }

    private void validateGrpcConfiguration() {
        ServiceClientProperties.Grpc grpc = properties.getGrpc();

        if (grpc.getMaxInboundMessageSize() <= 0) {
            throw new IllegalArgumentException("gRPC max inbound message size must be positive");
        }

        if (grpc.getMaxInboundMetadataSize() <= 0) {
            throw new IllegalArgumentException("gRPC max inbound metadata size must be positive");
        }

        if (grpc.getKeepAliveTime() == null || grpc.getKeepAliveTime().isNegative()) {
            throw new IllegalArgumentException("gRPC keep alive time must be positive");
        }

        if (grpc.getMaxConcurrentStreams() <= 0) {
            throw new IllegalArgumentException("gRPC max concurrent streams must be positive");
        }

        log.debug("gRPC configuration validation passed");
    }

    private void validateSdkConfiguration() {
        ServiceClientProperties.Sdk sdk = properties.getSdk();

        if (sdk.getDefaultTimeout() == null || sdk.getDefaultTimeout().isNegative()) {
            throw new IllegalArgumentException("SDK default timeout must be positive");
        }

        if (sdk.getMaxConcurrentOperations() <= 0) {
            throw new IllegalArgumentException("SDK max concurrent operations must be positive");
        }

        log.debug("SDK configuration validation passed");
    }

    private void validateCircuitBreakerConfiguration() {
        ServiceClientProperties.CircuitBreaker cb = properties.getCircuitBreaker();

        if (cb.getFailureRateThreshold() <= 0 || cb.getFailureRateThreshold() > 100) {
            throw new IllegalArgumentException(
                "Circuit breaker failure rate threshold must be between 1 and 100, got: " + cb.getFailureRateThreshold());
        }

        if (cb.getSlidingWindowSize() <= 0) {
            throw new IllegalArgumentException("Circuit breaker sliding window size must be positive");
        }

        if (cb.getMinimumNumberOfCalls() <= 0) {
            throw new IllegalArgumentException("Circuit breaker minimum number of calls must be positive");
        }

        if (cb.getWaitDurationInOpenState() == null || cb.getWaitDurationInOpenState().isNegative()) {
            throw new IllegalArgumentException("Circuit breaker wait duration in open state must be positive");
        }

        log.debug("Circuit breaker configuration validation passed");
    }

    private void validateRetryConfiguration() {
        ServiceClientProperties.Retry retry = properties.getRetry();

        if (retry.getMaxAttempts() <= 0) {
            throw new IllegalArgumentException("Retry max attempts must be positive, got: " + retry.getMaxAttempts());
        }

        if (retry.getMaxAttempts() > 10) {
            log.warn("Retry max attempts is very high ({}), this may cause long delays", retry.getMaxAttempts());
        }

        if (retry.getWaitDuration() == null || retry.getWaitDuration().isNegative()) {
            throw new IllegalArgumentException("Retry wait duration must be positive");
        }

        if (retry.getExponentialBackoffMultiplier() < 1.0) {
            throw new IllegalArgumentException("Retry exponential backoff multiplier must be >= 1.0");
        }

        log.debug("Retry configuration validation passed");
    }

    private void validateMetricsConfiguration() {
        ServiceClientProperties.Metrics metrics = properties.getMetrics();

        if (metrics.getExportInterval() == null || metrics.getExportInterval().isNegative()) {
            throw new IllegalArgumentException("Metrics export interval must be positive");
        }

        log.debug("Metrics configuration validation passed");
    }

    private void validateSecurityConfiguration() {
        ServiceClientProperties.Security security = properties.getSecurity();

        if (security.getDefaultAuthType() == null) {
            throw new IllegalArgumentException("Default authentication type must be specified");
        }

        // Validate environment-specific security settings
        if (properties.getEnvironment() == ServiceClientProperties.Environment.PRODUCTION) {
            if (!security.isSslValidationEnabled()) {
                log.warn("SSL validation is disabled in production environment - this is not recommended");
            }
        }

        log.debug("Security configuration validation passed");
    }

    /**
     * Validates a specific service configuration.
     *
     * @param serviceName the service name
     * @param clientType the client type
     */
    public void validateServiceConfiguration(String serviceName, String clientType) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }

        if (clientType == null || clientType.trim().isEmpty()) {
            throw new IllegalArgumentException("Client type cannot be null or empty");
        }

        log.debug("Service configuration validation passed for service '{}' with type '{}'", serviceName, clientType);
    }

    /**
     * Provides configuration recommendations based on environment.
     */
    public void logConfigurationRecommendations() {
        ServiceClientProperties.Environment env = properties.getEnvironment();
        
        log.info("Configuration recommendations for {} environment:", env);
        
        switch (env) {
            case DEVELOPMENT:
                log.info("- Consider enabling detailed logging for debugging");
                log.info("- SSL validation is relaxed for easier development");
                log.info("- Connection limits are reduced for resource conservation");
                break;
            case TESTING:
                log.info("- Metrics collection is disabled to reduce test overhead");
                log.info("- Retry attempts are minimized for faster test execution");
                log.info("- Connection limits are minimal for test isolation");
                break;
            case PRODUCTION:
                log.info("- SSL validation is enforced for security");
                log.info("- Connection limits are optimized for high throughput");
                log.info("- Detailed metrics are disabled for performance");
                log.info("- Circuit breaker thresholds are conservative");
                break;
        }
    }
}
