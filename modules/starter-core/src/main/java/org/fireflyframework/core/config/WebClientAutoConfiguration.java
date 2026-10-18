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


package org.fireflyframework.core.config;

import org.fireflyframework.core.web.client.WebClientTemplate;
import org.fireflyframework.core.web.resilience.ResilientWebClient;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.fireflyframework.core.web.resilience.WebClientResilienceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.fireflyframework.core.config.TransactionFilter.TRANSACTION_ID_HEADER;

/**
 * Configuration class for creating and customizing a {@link WebClient} bean.
 *
 * This class defines the setup for a {@link WebClient} instance with advanced configuration
 * options and resilience capabilities. It provides a highly configurable WebClient with:
 *
 * Key functionality provided by this configuration:
 * - Intercepts requests, adding a transaction ID from the reactive context to the request headers.
 * - The transaction ID is propagated using the `X-Transaction-Id` HTTP header.
 * - Applies resilience patterns (circuit breaker, retry, timeout, bulkhead) to the WebClient.
 * - Supports advanced configuration options like SSL/TLS, proxy, connection pooling, and HTTP/2.
 * - Provides customizable timeout settings for connect, read, and write operations.
 * - Allows for codec customization.
 *
 * Integration details:
 * - Works in conjunction with {@link TransactionFilter}, which generates and manages
 *   the transaction ID within the reactive context.
 * - Uses {@link WebClientBuilderCustomizer} to apply advanced configuration options.
 * - Uses Resilience4j for implementing resilience patterns.
 * - All configuration options can be customized through application properties with the
 *   `webclient` prefix.
 *
 * Annotations:
 * - {@link Configuration}: Marks the class as a configuration component in the Spring context.
 * - {@link Bean}: Declares a {@link WebClient} bean to be managed by the Spring container.
 * - {@link ConditionalOnProperty}: Conditionally enables the configuration based on the
 *   `webclient.enabled` property.
 */
@Slf4j
@AutoConfiguration(after = WebClientResilienceAutoConfiguration.class)
@ConditionalOnProperty(name = "firefly.webclient.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({WebClientProperties.class, HeaderPropagationProperties.class})
public class WebClientAutoConfiguration {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final WebClientBuilderCustomizer webClientBuilderCustomizer;

    public WebClientAutoConfiguration(
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Qualifier("webClientRetryRegistry") RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            WebClientBuilderCustomizer webClientBuilderCustomizer) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.meterRegistryProvider = meterRegistryProvider;
        this.webClientBuilderCustomizer = webClientBuilderCustomizer;
    }

    /**
     * WebFilter that captures the allow-listed inbound headers into the reactive context.
     */
    @Bean
    @ConditionalOnMissingBean
    HeaderPropagationWebFilter headerPropagationWebFilter(HeaderPropagationProperties properties) {
        return new HeaderPropagationWebFilter(properties);
    }

    /**
     * Reusable outbound filter that re-applies the captured inbound headers. Exposed as a bean
     * so SDK-generated {@code ApiClient} WebClients can attach it too.
     */
    @Bean
    @ConditionalOnMissingBean
    HeaderPropagationExchangeFilter headerPropagationExchangeFilter() {
        return new HeaderPropagationExchangeFilter();
    }

    /**
     * Creates a WebClient bean with transaction ID propagation, advanced configuration options,
     * and resilience capabilities.
     *
     * @return the WebClient bean
     */
    @Bean
    @ConditionalOnMissingBean
    WebClient webClient(HeaderPropagationExchangeFilter headerPropagationExchangeFilter) {
        // Create a WebClient.Builder with transaction ID + allow-listed header propagation
        WebClient.Builder builder = WebClient.builder()
                .filter((request, next) ->
                        Mono.deferContextual(ctx -> {
                            String transactionId = ctx.getOrDefault(TRANSACTION_ID_HEADER, null);
                            if (transactionId != null) {
                                ClientRequest newRequest = ClientRequest.from(request)
                                        .header(TRANSACTION_ID_HEADER, transactionId)
                                        .build();
                                return next.exchange(newRequest);
                            }
                            return next.exchange(request);
                        }))
                .filter(headerPropagationExchangeFilter);

        // Apply advanced configuration options
        try {
            builder = webClientBuilderCustomizer.customize(builder);
        } catch (Exception e) {
            log.error("Failed to apply advanced WebClient configuration", e);
        }

        // Build the base WebClient
        WebClient baseWebClient = builder.build();

        // Enhance the WebClient with resilience capabilities
        return new ResilientWebClient(
                baseWebClient,
                circuitBreakerRegistry,
                retryRegistry,
                timeLimiterRegistry,
                bulkheadRegistry,
                meterRegistryProvider,
                "webclient")
                .build();
    }

    /**
     * Creates a WebClient.Builder bean that can be used to create custom WebClient instances
     * with the same advanced configuration options and transaction ID propagation.
     *
     * @return the WebClient.Builder bean
     */
    @Bean
    @ConditionalOnMissingBean
    WebClient.Builder webClientBuilder(HeaderPropagationExchangeFilter headerPropagationExchangeFilter) {
        // Create a WebClient.Builder with transaction ID + allow-listed header propagation
        WebClient.Builder builder = WebClient.builder()
                .filter((request, next) ->
                        Mono.deferContextual(ctx -> {
                            String transactionId = ctx.getOrDefault(TRANSACTION_ID_HEADER, null);
                            if (transactionId != null) {
                                ClientRequest newRequest = ClientRequest.from(request)
                                        .header(TRANSACTION_ID_HEADER, transactionId)
                                        .build();
                                return next.exchange(newRequest);
                            }
                            return next.exchange(request);
                        }))
                .filter(headerPropagationExchangeFilter);

        // Apply advanced configuration options
        try {
            builder = webClientBuilderCustomizer.customize(builder);
        } catch (Exception e) {
            log.error("Failed to apply advanced WebClient configuration", e);
        }

        return builder;
    }

    @Bean
    @ConditionalOnMissingBean
    WebClientTemplate webClientTemplate(WebClient webClient, WebClientProperties properties) {
        return new WebClientTemplate(webClient, properties);
    }
}
