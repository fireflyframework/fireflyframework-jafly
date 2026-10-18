/*
 * Copyright (c) 2024 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.adapter.logalty;

import org.fireflyframework.ecm.adapter.AdapterFeature;
import org.fireflyframework.ecm.adapter.EcmAdapter;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.domain.enums.esignature.EnvelopeStatus;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.reactor.retry.RetryOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logalty eSignature envelope adapter implementation.
 *
 * <p>This adapter integrates with Logalty's eSignature platform, providing eIDAS-compliant
 * electronic signature capabilities. Logalty is a qualified Trust Service Provider (TSP)
 * that offers simple, advanced, and qualified electronic signatures.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>eIDAS-compliant electronic signatures (simple, advanced, qualified)</li>
 *   <li>Biometric signature capture</li>
 *   <li>SMS and video-based signer authentication</li>
 *   <li>Long-term document archival and preservation</li>
 *   <li>EU-wide legal validity for electronic signatures</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This is a placeholder implementation. Full integration requires
 * access to Logalty's API documentation and credentials.</p>
 *
 * @author Firefly Software Foundation.
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@EcmAdapter(
    type = "logalty",
    description = "Logalty eSignature Envelope Adapter - eIDAS compliant signatures",
    supportedFeatures = {
        AdapterFeature.ESIGNATURE_ENVELOPES,
        AdapterFeature.ESIGNATURE_REQUESTS,
        AdapterFeature.SIGNATURE_VALIDATION
    },
    requiredProperties = {"client-id", "client-secret"},
    optionalProperties = {"base-url", "api-version", "webhook-url", "webhook-secret", "return-url", "sandbox-mode"}
)
@ConditionalOnProperty(name = "firefly.ecm.esignature.provider", havingValue = "logalty")
public class LogaltySignatureEnvelopeAdapter implements SignatureEnvelopePort {

    private final WebClient webClient;
    private final LogaltyAdapterProperties properties;
    private final ObjectMapper objectMapper;
    private final DocumentContentPort documentContentPort;
    private final DocumentPort documentPort;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    // In-memory mappings (replace with persistent storage in production)
    private final Map<UUID, String> envelopeIdMapping = new ConcurrentHashMap<>();
    private final Map<String, UUID> externalIdMapping = new ConcurrentHashMap<>();

    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;

    public LogaltySignatureEnvelopeAdapter(WebClient webClient,
                                         LogaltyAdapterProperties properties,
                                         ObjectMapper objectMapper,
                                         DocumentContentPort documentContentPort,
                                         DocumentPort documentPort,
                                         @Qualifier("logaltyCircuitBreaker") CircuitBreaker circuitBreaker,
                                         @Qualifier("logaltyRetry") Retry retry) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.documentContentPort = documentContentPort;
        this.documentPort = documentPort;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        
        log.info("Logalty eSignature adapter initialized with base URL: {}", properties.getBaseUrl());
    }

    @Override
    public Mono<SignatureEnvelope> createEnvelope(SignatureEnvelope envelope) {
        log.debug("Creating Logalty signature envelope: {}", envelope.getTitle());
        
        return ensureValidAccessToken()
            .flatMap(token -> buildSignatureRequest(envelope)
                .flatMap(request -> webClient.post()
                    .uri("/api/{apiVersion}/signature-requests", properties.getApiVersion())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                )
                .map(response -> {
                    String logaltyRequestId = response.get("id").asText();
                    UUID id = envelope.getId() != null ? envelope.getId() : UUID.randomUUID();
                    
                    envelopeIdMapping.put(id, logaltyRequestId);
                    externalIdMapping.put(logaltyRequestId, id);
                    
                    log.info("Logalty signature envelope created successfully: {} -> {}", id, logaltyRequestId);
                    
                    return envelope.toBuilder()
                        .id(id)
                        .provider(SignatureProvider.LOGALTY)
                        .status(EnvelopeStatus.DRAFT)
                        .externalEnvelopeId(logaltyRequestId)
                        .createdAt(Instant.now())
                        .build();
                })
            )
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .doOnError(error -> log.error("Failed to create Logalty envelope: {}", error.getMessage(), error));
    }

    @Override
    public Mono<SignatureEnvelope> getEnvelope(UUID envelopeId) {
        log.debug("Retrieving Logalty signature envelope: {}", envelopeId);
        
        return ensureValidAccessToken()
            .flatMap(token -> {
                String logaltyRequestId = envelopeIdMapping.get(envelopeId);
                if (logaltyRequestId == null) {
                    return Mono.error(new RuntimeException("Envelope not found: " + envelopeId));
                }
                
                return webClient.get()
                    .uri("/api/{apiVersion}/signature-requests/{id}", 
                         properties.getApiVersion(), logaltyRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(node -> mapResponseToEnvelope(node, envelopeId, logaltyRequestId));
            })
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .doOnError(error -> log.error("Failed to retrieve Logalty envelope {}: {}", 
                                         envelopeId, error.getMessage(), error));
    }

    @Override
    public Mono<SignatureEnvelope> updateEnvelope(SignatureEnvelope envelope) {
        log.debug("Updating Logalty signature envelope: {}", envelope.getId());
        // Placeholder: implement update logic
        return getEnvelope(envelope.getId());
    }

    @Override
    public Mono<Void> deleteEnvelope(UUID envelopeId) {
        log.debug("Deleting Logalty signature envelope: {}", envelopeId);
        // Placeholder: implement delete logic
        envelopeIdMapping.remove(envelopeId);
        return Mono.empty();
    }

    @Override
    public Mono<SignatureEnvelope> sendEnvelope(UUID envelopeId, UUID sentBy) {
        log.debug("Sending Logalty signature envelope: {}", envelopeId);
        
        return ensureValidAccessToken()
            .flatMap(token -> {
                String logaltyRequestId = envelopeIdMapping.get(envelopeId);
                if (logaltyRequestId == null) {
                    return Mono.error(new RuntimeException("Envelope not found: " + envelopeId));
                }
                
                return webClient.post()
                    .uri("/api/{apiVersion}/signature-requests/{id}/send",
                         properties.getApiVersion(), logaltyRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .flatMap(response -> getEnvelope(envelopeId));
            })
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry));
    }

    @Override
    public Mono<SignatureEnvelope> voidEnvelope(UUID envelopeId, String voidReason, UUID voidedBy) {
        log.debug("Voiding Logalty signature envelope: {} with reason: {}", envelopeId, voidReason);
        // Placeholder: implement void logic
        return getEnvelope(envelopeId);
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByStatus(EnvelopeStatus status, Integer limit) {
        log.debug("Retrieving Logalty envelopes by status: {}", status);
        // Placeholder: implement query logic
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByCreator(UUID createdBy, Integer limit) {
        log.debug("Retrieving Logalty envelopes by creator: {}", createdBy);
        // Placeholder: implement query logic
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesBySender(UUID sentBy, Integer limit) {
        log.debug("Retrieving Logalty envelopes by sender: {}", sentBy);
        // Placeholder: implement query logic
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByProvider(SignatureProvider provider, Integer limit) {
        log.debug("Retrieving Logalty envelopes by provider: {}", provider);
        // Placeholder: implement query logic
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getExpiringEnvelopes(Instant fromTime, Instant toTime) {
        log.debug("Retrieving Logalty envelopes expiring between {} and {}", fromTime, toTime);
        // Placeholder: implement query logic
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getCompletedEnvelopes(Instant fromTime, Instant toTime) {
        log.debug("Retrieving Logalty envelopes completed between {} and {}", fromTime, toTime);
        // Placeholder: implement query logic
        return Flux.empty();
    }

    @Override
    public Mono<Boolean> existsEnvelope(UUID envelopeId) {
        log.debug("Checking if Logalty envelope exists: {}", envelopeId);
        return Mono.just(envelopeIdMapping.containsKey(envelopeId));
    }

    @Override
    public Mono<SignatureEnvelope> getEnvelopeByExternalId(String externalEnvelopeId, SignatureProvider provider) {
        log.debug("Retrieving Logalty envelope by external ID: {}", externalEnvelopeId);
        UUID envelopeId = externalIdMapping.get(externalEnvelopeId);
        if (envelopeId == null) {
            return Mono.empty();
        }
        return getEnvelope(envelopeId);
    }

    @Override
    public Mono<SignatureEnvelope> syncEnvelopeStatus(UUID envelopeId) {
        log.debug("Syncing Logalty envelope status: {}", envelopeId);
        // Retrieve fresh status from Logalty API
        return getEnvelope(envelopeId);
    }

    @Override
    public Mono<String> getSigningUrl(UUID envelopeId, String signerEmail, String signerName, String clientUserId) {
        log.debug("Getting signing URL for envelope: {}, signer: {}", envelopeId, signerEmail);
        
        return ensureValidAccessToken()
            .flatMap(token -> {
                String logaltyRequestId = envelopeIdMapping.get(envelopeId);
                if (logaltyRequestId == null) {
                    return Mono.error(new RuntimeException("Envelope not found: " + envelopeId));
                }
                
                // Placeholder: construct signing URL from Logalty API
                return webClient.get()
                    .uri("/api/{apiVersion}/signature-requests/{id}/signing-url?email={email}",
                         properties.getApiVersion(), logaltyRequestId, signerEmail)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(response -> response.path("signingUrl").asText());
            })
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry));
    }

    @Override
    public Mono<Void> resendEnvelope(UUID envelopeId) {
        log.debug("Resending Logalty envelope: {}", envelopeId);
        
        return ensureValidAccessToken()
            .flatMap(token -> {
                String logaltyRequestId = envelopeIdMapping.get(envelopeId);
                if (logaltyRequestId == null) {
                    return Mono.error(new RuntimeException("Envelope not found: " + envelopeId));
                }
                
                return webClient.post()
                    .uri("/api/{apiVersion}/signature-requests/{id}/resend",
                         properties.getApiVersion(), logaltyRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Void.class);
            })
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry));
    }

    @Override
    public Mono<SignatureEnvelope> archiveEnvelope(UUID envelopeId) {
        log.debug("Archiving Logalty envelope: {}", envelopeId);
        
        return ensureValidAccessToken()
            .flatMap(token -> {
                String logaltyRequestId = envelopeIdMapping.get(envelopeId);
                if (logaltyRequestId == null) {
                    return Mono.error(new RuntimeException("Envelope not found: " + envelopeId));
                }
                
                return webClient.post()
                    .uri("/api/{apiVersion}/signature-requests/{id}/archive",
                         properties.getApiVersion(), logaltyRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .flatMap(response -> getEnvelope(envelopeId));
            })
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry));
    }

    /**
     * Ensures a valid OAuth access token is available.
     * Refreshes the token if expired or not present.
     */
    private Mono<String> ensureValidAccessToken() {
        if (accessToken != null && tokenExpiresAt != null && 
            Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return Mono.just(accessToken);
        }
        
        log.debug("Refreshing Logalty access token");
        
        return webClient.post()
            .uri("/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue("grant_type=client_credentials" +
                       "&client_id=" + properties.getClientId() +
                       "&client_secret=" + properties.getClientSecret())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(response -> {
                this.accessToken = response.get("access_token").asText();
                long expiresIn = response.path("expires_in").asLong(properties.getTokenExpiration());
                this.tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
                
                log.debug("Logalty access token refreshed, expires at: {}", tokenExpiresAt);
                
                return this.accessToken;
            });
    }

    /**
     * Builds a signature request payload for Logalty API.
     */
    private Mono<Map<String, Object>> buildSignatureRequest(SignatureEnvelope envelope) {
        Map<String, Object> request = new java.util.HashMap<>();
        
        request.put("title", Optional.ofNullable(envelope.getTitle()).orElse("Signature Request"));
        request.put("message", Optional.ofNullable(envelope.getDescription())
                    .orElse(properties.getDefaultEmailMessage()));
        request.put("signatureType", properties.getDefaultSignatureType());
        request.put("biometricEnabled", properties.getEnableBiometricSignature());
        request.put("smsVerificationEnabled", properties.getEnableSmsVerification());
        request.put("videoIdentificationEnabled", properties.getEnableVideoIdentification());
        
        // Add documents and signers (placeholder)
        // TODO: Map envelope documents and signature requests to Logalty format
        
        return Mono.just(request);
    }

    /**
     * Maps Logalty API response to SignatureEnvelope domain model.
     */
    private SignatureEnvelope mapResponseToEnvelope(JsonNode node, UUID id, String logaltyRequestId) {
        return SignatureEnvelope.builder()
            .id(id)
            .provider(SignatureProvider.LOGALTY)
            .title(node.path("title").asText(null))
            .description(node.path("message").asText(null))
            .status(mapLogaltyStatusToEnvelopeStatus(node.path("status").asText("DRAFT")))
            .externalEnvelopeId(logaltyRequestId)
            .createdAt(Instant.parse(node.path("createdAt").asText(Instant.now().toString())))
            .build();
    }

    /**
     * Maps Logalty-specific status values to ECM EnvelopeStatus enum.
     */
    private EnvelopeStatus mapLogaltyStatusToEnvelopeStatus(String logaltyStatus) {
        return switch (logaltyStatus.toUpperCase()) {
            case "PENDING", "DRAFT" -> EnvelopeStatus.DRAFT;
            case "SENT", "IN_PROGRESS" -> EnvelopeStatus.SENT;
            case "COMPLETED", "SIGNED" -> EnvelopeStatus.COMPLETED;
            case "CANCELLED", "VOIDED" -> EnvelopeStatus.VOIDED;
            case "EXPIRED" -> EnvelopeStatus.EXPIRED;
            default -> EnvelopeStatus.DRAFT;
        };
    }
}
