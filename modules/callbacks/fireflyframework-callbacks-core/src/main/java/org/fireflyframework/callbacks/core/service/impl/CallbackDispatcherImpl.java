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

package org.fireflyframework.callbacks.core.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.callbacks.core.mapper.CallbackExecutionMapper;
import org.fireflyframework.callbacks.core.service.CallbackConfigurationService;
import org.fireflyframework.callbacks.core.service.CallbackDispatcher;
import org.fireflyframework.callbacks.core.service.DomainAuthorizationService;
import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import org.fireflyframework.callbacks.interfaces.enums.CallbackExecutionStatus;
import org.fireflyframework.callbacks.models.entity.CallbackExecution;
import org.fireflyframework.callbacks.models.repository.CallbackExecutionRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetryBackoffSpec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of callback dispatcher.
 * Handles HTTP callbacks with retry logic, circuit breaker, and HMAC signing.
 */
@Service
@Slf4j
public class CallbackDispatcherImpl implements CallbackDispatcher {

    private final WebClient webClient;
    private final CallbackExecutionRepository executionRepository;
    private final CallbackExecutionMapper executionMapper;
    private final CallbackConfigurationService configurationService;
    private final DomainAuthorizationService domainAuthorizationService;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public CallbackDispatcherImpl(
            WebClient.Builder webClientBuilder,
            CallbackExecutionRepository executionRepository,
            CallbackExecutionMapper executionMapper,
            CallbackConfigurationService configurationService,
            DomainAuthorizationService domainAuthorizationService,
            ObjectMapper objectMapper) {
        
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        this.executionRepository = executionRepository;
        this.executionMapper = executionMapper;
        this.configurationService = configurationService;
        this.domainAuthorizationService = domainAuthorizationService;
        this.objectMapper = objectMapper;
        
        // Configure circuit breaker registry
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        
        // Configure retry registry
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .build();
        this.retryRegistry = RetryRegistry.of(retryConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> dispatch(
            CallbackConfigurationDTO configuration,
            String eventType,
            UUID eventId,
            JsonNode payload) {
        
        log.debug("Dispatching callback: config={}, eventType={}, eventId={}", 
                configuration.getId(), eventType, eventId);

        // Check domain authorization first
        return domainAuthorizationService.isAuthorized(configuration.getUrl())
                .flatMap(authorized -> {
                    if (!authorized) {
                        log.warn("Callback URL not authorized: {}", configuration.getUrl());
                        return recordFailedExecution(
                                configuration,
                                eventType,
                                eventId,
                                payload,
                                "Domain not authorized",
                                null,
                                1,
                                configuration.getMaxRetries()
                        ).then(Mono.error(new SecurityException("Domain not authorized: " + configuration.getUrl())));
                    }
                    
                    // Execute callback with retry and circuit breaker
                    return executeCallback(configuration, eventType, eventId, payload)
                            .doOnSuccess(v -> domainAuthorizationService.recordCallback(configuration.getUrl(), true).subscribe())
                            .doOnError(e -> domainAuthorizationService.recordCallback(configuration.getUrl(), false).subscribe());
                });
    }

    /**
     * Executes the HTTP callback with retry and circuit breaker.
     */
    private Mono<Void> executeCallback(
            CallbackConfigurationDTO configuration,
            String eventType,
            UUID eventId,
            JsonNode payload) {
        
        // Get or create circuit breaker for this configuration
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                "callback-" + configuration.getId());
        
        // Build retry spec from configuration
        RetryBackoffSpec retrySpec = reactor.util.retry.Retry
                .backoff(
                        configuration.getMaxRetries() != null ? configuration.getMaxRetries() : 3,
                        Duration.ofMillis(configuration.getRetryDelayMs() != null ? configuration.getRetryDelayMs() : 1000)
                )
                .maxBackoff(Duration.ofSeconds(30))
                .filter(throwable -> shouldRetry(throwable))
                .doBeforeRetry(retrySignal -> 
                        log.warn("Retrying callback: attempt={}, error={}", 
                                retrySignal.totalRetries() + 1, retrySignal.failure().getMessage()));
        
        // Execute with circuit breaker and retry
        return makeHttpRequest(configuration, eventType, eventId, payload, 1)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .retryWhen(retrySpec)
                .onErrorResume(error -> {
                    log.error("Callback failed after retries: config={}, error={}", 
                            configuration.getId(), error.getMessage());
                    return Mono.empty(); // Already recorded in makeHttpRequest
                });
    }

    /**
     * Makes the actual HTTP request.
     */
    private Mono<Void> makeHttpRequest(
            CallbackConfigurationDTO configuration,
            String eventType,
            UUID eventId,
            JsonNode payload,
            int attemptNumber) {
        
        Instant startTime = Instant.now();
        
        try {
            // Prepare request body
            String requestBody = objectMapper.writeValueAsString(payload);
            
            // Prepare headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-Event-Type", eventType);
            headers.put("X-Event-Id", eventId.toString());
            headers.put("X-Timestamp", Instant.now().toString());
            
            // Add custom headers
            if (configuration.getCustomHeaders() != null) {
                headers.putAll(configuration.getCustomHeaders());
            }
            
            // Add HMAC signature if enabled
            if (Boolean.TRUE.equals(configuration.getSignatureEnabled()) && configuration.getSecret() != null) {
                String signature = generateHmacSignature(requestBody, configuration.getSecret());
                String signatureHeader = configuration.getSignatureHeader() != null ? 
                        configuration.getSignatureHeader() : "X-Signature";
                headers.put(signatureHeader, signature);
            }
            
            // Build and execute request
            WebClient.RequestBodySpec requestSpec = webClient
                    .method(mapHttpMethod(configuration.getHttpMethod()))
                    .uri(configuration.getUrl())
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set));
            
            return requestSpec
                    .bodyValue(requestBody)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(configuration.getTimeoutMs() != null ? 
                            configuration.getTimeoutMs() : 30000))
                    .flatMap(response -> {
                        long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                        
                        log.info("Callback successful: config={}, status={}, duration={}ms", 
                                configuration.getId(), response.getStatusCode().value(), durationMs);
                        
                        // Record successful execution
                        return recordSuccessfulExecution(
                                configuration,
                                eventType,
                                eventId,
                                payload,
                                headers,
                                response.getStatusCode().value(),
                                null, // No response body from toBodilessEntity
                                durationMs,
                                attemptNumber,
                                configuration.getMaxRetries()
                        ).then(configurationService.recordSuccess(configuration.getId()));
                    })
                    .onErrorResume(error -> {
                        long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                        
                        log.error("Callback failed: config={}, attempt={}, error={}", 
                                configuration.getId(), attemptNumber, error.getMessage());
                        
                        Integer statusCode = null;
                        String responseBody = null;
                        
                        if (error instanceof WebClientResponseException webClientError) {
                            statusCode = webClientError.getStatusCode().value();
                            responseBody = webClientError.getResponseBodyAsString();
                        }
                        
                        // Record failed execution
                        return recordFailedExecution(
                                configuration,
                                eventType,
                                eventId,
                                payload,
                                error.getMessage(),
                                statusCode,
                                attemptNumber,
                                configuration.getMaxRetries()
                        )
                        .then(configurationService.recordFailure(configuration.getId()))
                        .then(Mono.error(error));
                    });
                    
        } catch (JsonProcessingException e) {
            log.error("Error serializing payload for callback: config={}", configuration.getId(), e);
            return Mono.error(e);
        }
    }

    /**
     * Records a successful callback execution.
     */
    private Mono<CallbackExecution> recordSuccessfulExecution(
            CallbackConfigurationDTO configuration,
            String eventType,
            UUID eventId,
            JsonNode payload,
            Map<String, String> requestHeaders,
            Integer statusCode,
            String responseBody,
            Long durationMs,
            int attemptNumber,
            Integer maxAttempts) {

        CallbackExecution execution = CallbackExecution.builder()
                // Don't set ID - let R2DBC/PostgreSQL generate it
                .configurationId(configuration.getId())
                .eventType(eventType)
                .sourceEventId(eventId)
                .status(CallbackExecutionStatus.SUCCESS)
                .requestPayload(jsonNodeToString(payload))
                .requestHeaders(mapToJson(requestHeaders))
                .responseStatusCode(statusCode)
                .responseBody(responseBody)
                .attemptNumber(attemptNumber)
                .maxAttempts(maxAttempts)
                .requestDurationMs(durationMs)
                .executedAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        return executionRepository.save(execution)
                .doOnSuccess(saved -> log.debug("Recorded successful execution: id={}", saved.getId()))
                .doOnError(error -> log.error("Error recording execution", error));
    }

    /**
     * Records a failed callback execution.
     */
    private Mono<CallbackExecution> recordFailedExecution(
            CallbackConfigurationDTO configuration,
            String eventType,
            UUID eventId,
            JsonNode payload,
            String errorMessage,
            Integer statusCode,
            int attemptNumber,
            Integer maxAttempts) {

        CallbackExecutionStatus status = attemptNumber < maxAttempts ?
                CallbackExecutionStatus.FAILED_RETRYING :
                CallbackExecutionStatus.FAILED_PERMANENT;

        CallbackExecution execution = CallbackExecution.builder()
                // Don't set ID - let R2DBC/PostgreSQL generate it
                .configurationId(configuration.getId())
                .eventType(eventType)
                .sourceEventId(eventId)
                .status(status)
                .requestPayload(jsonNodeToString(payload))
                .responseStatusCode(statusCode)
                .attemptNumber(attemptNumber)
                .maxAttempts(maxAttempts)
                .errorMessage(errorMessage)
                .executedAt(Instant.now())
                .build();

        return executionRepository.save(execution)
                .doOnSuccess(saved -> log.debug("Recorded failed execution: id={}, status={}",
                        saved.getId(), saved.getStatus()))
                .doOnError(error -> log.error("Error recording execution", error));
    }

    /**
     * Generates HMAC-SHA256 signature for the payload.
     */
    private String generateHmacSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            log.error("Error generating HMAC signature", e);
            throw new RuntimeException("Error generating HMAC signature", e);
        }
    }

    /**
     * Determines if an error should trigger a retry.
     */
    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientError) {
            int statusCode = webClientError.getStatusCode().value();
            // Retry on 5xx errors and certain 4xx errors
            return statusCode >= 500 || statusCode == 408 || statusCode == 429;
        }
        // Retry on network errors
        return true;
    }

    /**
     * Maps HTTP method enum to Spring HttpMethod.
     */
    private org.springframework.http.HttpMethod mapHttpMethod(org.fireflyframework.callbacks.interfaces.enums.HttpMethod method) {
        return switch (method) {
            case POST -> org.springframework.http.HttpMethod.POST;
            case PUT -> org.springframework.http.HttpMethod.PUT;
            case PATCH -> org.springframework.http.HttpMethod.PATCH;
        };
    }

    /**
     * Converts JsonNode to JSON String.
     */
    private String jsonNodeToString(JsonNode jsonNode) {
        if (jsonNode == null) return null;
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            log.error("Error converting JsonNode to String", e);
            return null;
        }
    }

    /**
     * Converts Map to JSON String.
     */
    private String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Error converting Map to JSON", e);
            return null;
        }
    }
}
