/*
 * Copyright (c) 2024 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.adapter.logalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Spring Boot auto-configuration for the Logalty eSignature adapter.
 *
 * <p>This configuration class automatically sets up all required beans for the Logalty
 * adapter when the appropriate properties are configured. It includes:</p>
 * <ul>
 *   <li>WebClient for HTTP communication with Logalty API</li>
 *   <li>CircuitBreaker for fault tolerance</li>
 *   <li>Retry mechanism for transient failures</li>
 *   <li>ObjectMapper for JSON serialization</li>
 * </ul>
 *
 * <p>The adapter is only activated when {@code firefly.ecm.esignature.provider=logalty}
 * is configured in application properties.</p>
 *
 * @author Firefly Software Foundation.
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({WebClient.class, CircuitBreaker.class})
@EnableConfigurationProperties(LogaltyAdapterProperties.class)
@ConditionalOnProperty(name = "firefly.ecm.esignature.provider", havingValue = "logalty")
public class LogaltyAdapterAutoConfiguration {

    /**
     * Configures the WebClient for Logalty API communication.
     *
     * @param properties the Logalty adapter properties
     * @return configured WebClient instance
     */
    @ConditionalOnMissingBean
    @Bean
    public WebClient logaltyWebClient(LogaltyAdapterProperties properties) {
        log.info("Configuring Logalty WebClient with base URL: {}", properties.getBaseUrl());

        HttpClient httpClient = HttpClient.create()
            .responseTimeout(properties.getReadTimeout())
            .doOnConnected(conn -> {
                log.debug("Logalty HTTP connection established");
            });

        return WebClient.builder()
            .baseUrl(properties.getBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    /**
     * Configures the CircuitBreaker for Logalty API calls.
     *
     * @param properties the Logalty adapter properties
     * @return configured CircuitBreaker instance
     */
    @ConditionalOnMissingBean
    @Bean(name = "logaltyCircuitBreaker")
    public CircuitBreaker logaltyCircuitBreaker(LogaltyAdapterProperties properties) {
        log.info("Configuring Logalty CircuitBreaker with max retries: {}", properties.getMaxRetries());

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("logalty", config);

        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Logalty CircuitBreaker state transition: {} -> {}", 
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
            .onError(event -> 
                log.error("Logalty CircuitBreaker error: {}", event.getThrowable().getMessage()));

        return circuitBreaker;
    }

    /**
     * Configures the Retry mechanism for Logalty API calls.
     *
     * @param properties the Logalty adapter properties
     * @return configured Retry instance
     */
    @ConditionalOnMissingBean
    @Bean(name = "logaltyRetry")
    public Retry logaltyRetry(LogaltyAdapterProperties properties) {
        log.info("Configuring Logalty Retry mechanism with max attempts: {}", properties.getMaxRetries());

        RetryConfig config = RetryConfig.custom()
            .maxAttempts(properties.getMaxRetries())
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(
                java.net.SocketTimeoutException.class,
                java.io.IOException.class,
                org.springframework.web.reactive.function.client.WebClientRequestException.class
            )
            .build();

        Retry retry = Retry.of("logalty", config);

        retry.getEventPublisher()
            .onRetry(event -> 
                log.warn("Logalty API call retry attempt {} due to: {}", 
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()));

        return retry;
    }

    /**
     * Provides ObjectMapper for JSON serialization/deserialization.
     *
     * @return ObjectMapper instance
     */
    @ConditionalOnMissingBean
    @Bean
    public ObjectMapper logaltyObjectMapper() {
        return new ObjectMapper()
            .findAndRegisterModules();
    }

    /**
     * Creates the Logalty signature envelope adapter.
     *
     * @param webClient the WebClient for Logalty API communication
     * @param properties the Logalty adapter properties
     * @param objectMapper the ObjectMapper for JSON serialization
     * @param documentContentPort the document content port
     * @param documentPort the document port
     * @param circuitBreaker the circuit breaker for fault tolerance
     * @param retry the retry mechanism for transient failures
     * @return configured LogaltySignatureEnvelopeAdapter instance
     */
    @Bean
    @ConditionalOnMissingBean
    public LogaltySignatureEnvelopeAdapter logaltySignatureEnvelopeAdapter(
            WebClient webClient,
            LogaltyAdapterProperties properties,
            ObjectMapper objectMapper,
            DocumentContentPort documentContentPort,
            DocumentPort documentPort,
            @Qualifier("logaltyCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("logaltyRetry") Retry retry) {
        return new LogaltySignatureEnvelopeAdapter(
                webClient, properties, objectMapper,
                documentContentPort, documentPort,
                circuitBreaker, retry);
    }
}
