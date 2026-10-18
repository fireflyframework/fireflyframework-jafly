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

import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.builder.GrpcClientBuilder;
import org.fireflyframework.client.builder.RestClientBuilder;
import org.fireflyframework.client.metrics.ServiceClientMetrics;
import org.fireflyframework.resilience.CircuitBreakerConfig;
import org.fireflyframework.resilience.CircuitBreakerManager;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Auto-configuration for ServiceClient framework components.
 *
 * <p>Provides automatic setup of WebClient, circuit breakers, and service client builders
 * with enhanced circuit breaker functionality as the default.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ServiceClient.class)
@EnableConfigurationProperties(ServiceClientProperties.class)
@ConditionalOnProperty(prefix = "firefly.service-client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ServiceClientAutoConfiguration {

    private final ServiceClientProperties properties;

    public ServiceClientAutoConfiguration(ServiceClientProperties properties) {
        this.properties = properties;

        // Apply environment-specific defaults
        properties.applyEnvironmentDefaults();

        log.info("Initializing ServiceClient framework with environment: {}",
                properties.getEnvironment());
        log.debug("ServiceClient configuration: REST max connections={}, gRPC plaintext={}",
                properties.getRest().getMaxConnections(),
                properties.getGrpc().isUsePlaintextByDefault());
    }

    @Bean
    @ConditionalOnMissingBean(WebClient.Builder.class)
    public WebClient.Builder fireflyServiceWebClientBuilder() {
        log.info("Configuring enhanced WebClient builder for REST service clients");

        ServiceClientProperties.Rest restConfig = properties.getRest();

        // Configure connection pool with enhanced settings
        ConnectionProvider connectionProvider = ConnectionProvider.builder("rest-service-client")
                .maxConnections(restConfig.getMaxConnections())
                .maxIdleTime(restConfig.getMaxIdleTime())
                .maxLifeTime(restConfig.getMaxLifeTime())
                .pendingAcquireTimeout(restConfig.getPendingAcquireTimeout())
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        // Configure HTTP client with enhanced features
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(restConfig.getResponseTimeout())
                .followRedirect(restConfig.isFollowRedirects());

        // Enable compression if configured
        if (restConfig.isCompressionEnabled()) {
            httpClient = httpClient.compress(true);
        }

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(restConfig.getMaxInMemorySize());
                });

        // Add default headers
        if (restConfig.getDefaultContentType() != null) {
            builder.defaultHeader("Content-Type", restConfig.getDefaultContentType());
        }
        if (restConfig.getDefaultAcceptType() != null) {
            builder.defaultHeader("Accept", restConfig.getDefaultAcceptType());
        }

        // Add global default headers
        properties.getDefaultHeaders().forEach(builder::defaultHeader);

        log.debug("WebClient configured with {} max connections, compression={}, follow redirects={}",
                restConfig.getMaxConnections(),
                restConfig.isCompressionEnabled(),
                restConfig.isFollowRedirects());

        return builder;
    }

    /**
     * Creates a default circuit breaker configuration if none is provided.
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerConfig circuitBreakerConfig() {
        log.info("Configuring enhanced circuit breaker configuration");

        var circuitBreakerProps = properties.getCircuitBreaker();

        return CircuitBreakerConfig.builder()
            .failureRateThreshold(circuitBreakerProps.getFailureRateThreshold())
            .minimumNumberOfCalls(circuitBreakerProps.getMinimumNumberOfCalls())
            .slidingWindowSize(circuitBreakerProps.getSlidingWindowSize())
            .waitDurationInOpenState(circuitBreakerProps.getWaitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(circuitBreakerProps.getPermittedNumberOfCallsInHalfOpenState())
            .maxWaitDurationInHalfOpenState(circuitBreakerProps.getMaxWaitDurationInHalfOpenState())
            .callTimeout(circuitBreakerProps.getCallTimeout())
            .slowCallDurationThreshold(circuitBreakerProps.getSlowCallDurationThreshold())
            .slowCallRateThreshold(circuitBreakerProps.getSlowCallRateThreshold())
            .automaticTransitionFromOpenToHalfOpenEnabled(circuitBreakerProps.isAutomaticTransitionFromOpenToHalfOpenEnabled())
            .build();
    }

    /**
     * Creates a default circuit breaker manager if none is provided.
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerManager circuitBreakerManager(CircuitBreakerConfig config) {
        log.info("Configuring enhanced circuit breaker manager");
        return new CircuitBreakerManager(config);
    }

    /**
     * Creates a default REST client builder if none is provided.
     */
    @Bean
    @ConditionalOnMissingBean(RestClientBuilder.class)
    public RestClientBuilder fireflyRestClientBuilder(CircuitBreakerManager circuitBreakerManager) {
        log.info("Configuring default REST client builder with enhanced circuit breaker and retry");

        var retryProps = properties.getRetry();
        return new RestClientBuilder("default")
            .circuitBreakerManager(circuitBreakerManager)
            .retry(
                retryProps.isEnabled(),
                retryProps.getMaxAttempts(),
                retryProps.getWaitDuration(),
                retryProps.getMaxWaitDuration(),
                retryProps.isJitterEnabled()
            );
    }

    /**
     * Creates a default gRPC client builder factory if none is provided.
     * Note: gRPC builders are created per service, so this provides a factory method.
     */
    @Bean
    @ConditionalOnMissingBean(name = "grpcClientBuilderFactory")
    public GrpcClientBuilderFactory grpcClientBuilderFactory(CircuitBreakerManager circuitBreakerManager) {
        log.info("Configuring gRPC client builder factory with enhanced circuit breaker");
        return new GrpcClientBuilderFactory(circuitBreakerManager);
    }

    /**
     * Configures ServiceClient metrics integration with Micrometer.
     *
     * @param meterRegistry the Micrometer meter registry
     * @return the ServiceClientMetrics bean
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "firefly.service-client.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ServiceClientMetrics serviceClientMetrics(MeterRegistry meterRegistry) {
        log.info("Configuring ServiceClient metrics integration with Micrometer");
        return new ServiceClientMetrics(meterRegistry);
    }

    /**
     * Factory for creating gRPC client builders with auto-configured circuit breaker.
     */
    public static class GrpcClientBuilderFactory {
        private final CircuitBreakerManager circuitBreakerManager;

        public GrpcClientBuilderFactory(CircuitBreakerManager circuitBreakerManager) {
            this.circuitBreakerManager = circuitBreakerManager;
        }

        /**
         * Creates a new gRPC client builder with auto-configured circuit breaker.
         *
         * @param serviceName the service name
         * @param stubType the gRPC stub type
         * @param <T> the stub type
         * @return a configured gRPC client builder
         */
        public <T> GrpcClientBuilder<T> create(String serviceName, Class<T> stubType) {
            return new GrpcClientBuilder<>(serviceName, stubType)
                .circuitBreakerManager(circuitBreakerManager);
        }
    }
}