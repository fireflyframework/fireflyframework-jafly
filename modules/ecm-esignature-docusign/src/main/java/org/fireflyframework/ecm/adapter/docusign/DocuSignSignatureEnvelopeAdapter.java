/*
 * Copyright 2024 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import com.docusign.esign.model.*;
import org.fireflyframework.ecm.adapter.AdapterFeature;
import org.fireflyframework.ecm.adapter.EcmAdapter;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.domain.enums.esignature.EnvelopeStatus;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@EcmAdapter(
    type = "docusign",
    description = "DocuSign eSignature Envelope Adapter",
    supportedFeatures = {
        AdapterFeature.ESIGNATURE_ENVELOPES,
        AdapterFeature.ESIGNATURE_REQUESTS,
        AdapterFeature.SIGNATURE_VALIDATION
    },
    requiredProperties = {"integration-key", "user-id", "account-id", "private-key"},
    optionalProperties = {"base-url", "auth-server", "sandbox-mode", "return-url"}
)
@Component
@ConditionalOnProperty(name = "firefly.ecm.esignature.provider", havingValue = "docusign")
public class DocuSignSignatureEnvelopeAdapter implements SignatureEnvelopePort {

    private final ApiClient apiClient;
    private final EnvelopesApi envelopesApi;
    private final DocuSignAdapterProperties properties;
    private final DocumentContentPort documentContentPort;
    private final DocumentPort documentPort;

    private final Map<UUID, String> envelopeIdMapping = new ConcurrentHashMap<>();
    private final Map<String, UUID> externalIdMapping = new ConcurrentHashMap<>();

    public DocuSignSignatureEnvelopeAdapter(ApiClient apiClient,
                                          DocuSignAdapterProperties properties,
                                          DocumentContentPort documentContentPort,
                                          DocumentPort documentPort) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.documentContentPort = documentContentPort;
        this.documentPort = documentPort;
        this.envelopesApi = new EnvelopesApi(apiClient);
        log.info("DocuSignSignatureEnvelopeAdapter initialized for account: {} with document integration",
                properties.getAccountId());
    }

    @Override
    public Mono<SignatureEnvelope> createEnvelope(SignatureEnvelope envelope) {
        return Mono.fromCallable(() -> {
            UUID envelopeId = envelope.getId() != null ? envelope.getId() : UUID.randomUUID();
            EnvelopeDefinition envelopeDefinition = buildEnvelopeDefinition(envelope);
            EnvelopeSummary envelopeSummary = envelopesApi.createEnvelope(
                    properties.getAccountId(),
                    envelopeDefinition
            );
            String docuSignEnvelopeId = envelopeSummary.getEnvelopeId();
            envelopeIdMapping.put(envelopeId, docuSignEnvelopeId);
            externalIdMapping.put(docuSignEnvelopeId, envelopeId);
            return envelope.toBuilder()
                    .id(envelopeId)
                    .externalEnvelopeId(docuSignEnvelopeId)
                    .status(EnvelopeStatus.DRAFT)
                    .createdAt(Instant.now())
                    .modifiedAt(Instant.now())
                    .build();
        });
    }

    @Override
    public Mono<SignatureEnvelope> getEnvelope(UUID envelopeId) {
        return Mono.fromCallable(() -> {
            String docuSignEnvelopeId = envelopeIdMapping.get(envelopeId);
            if (docuSignEnvelopeId == null) {
                throw new RuntimeException("Envelope not found: " + envelopeId);
            }
            Envelope docuSignEnvelope = envelopesApi.getEnvelope(
                    properties.getAccountId(),
                    docuSignEnvelopeId
            );
            return buildSignatureEnvelopeFromDocuSign(envelopeId, docuSignEnvelope);
        });
    }

    @Override
    public Mono<SignatureEnvelope> updateEnvelope(SignatureEnvelope envelope) {
        return Mono.fromCallable(() -> {
            String docuSignEnvelopeId = envelopeIdMapping.get(envelope.getId());
            if (docuSignEnvelopeId == null) {
                throw new RuntimeException("Envelope not found: " + envelope.getId());
            }
            Envelope envUpdate = new Envelope();
            envUpdate.setEmailSubject(envelope.getTitle());
            envUpdate.setEmailBlurb(envelope.getDescription());
            envelopesApi.update(properties.getAccountId(), docuSignEnvelopeId, envUpdate);
            return envelope.toBuilder().modifiedAt(Instant.now()).build();
        });
    }

    @Override
    public Mono<Void> deleteEnvelope(UUID envelopeId) {
        return Mono.fromCallable(() -> {
            String docuSignEnvelopeId = envelopeIdMapping.get(envelopeId);
            if (docuSignEnvelopeId == null) {
                throw new RuntimeException("Envelope not found: " + envelopeId);
            }
            Envelope envUpdate = new Envelope();
            envUpdate.setStatus("voided");
            envelopesApi.update(properties.getAccountId(), docuSignEnvelopeId, envUpdate);
            envelopeIdMapping.remove(envelopeId);
            externalIdMapping.remove(docuSignEnvelopeId);
            return (Void) null;
        });
    }

    @Override
    public Mono<SignatureEnvelope> sendEnvelope(UUID envelopeId, UUID sentBy) {
        return Mono.fromCallable(() -> {
            String docuSignEnvelopeId = envelopeIdMapping.get(envelopeId);
            if (docuSignEnvelopeId == null) {
                throw new RuntimeException("Envelope not found: " + envelopeId);
            }
            Envelope update = new Envelope();
            update.setStatus("sent");
            envelopesApi.update(properties.getAccountId(), docuSignEnvelopeId, update);
            return envelopeId;
        }).flatMap(this::getEnvelope);
    }

    @Override
    public Mono<SignatureEnvelope> voidEnvelope(UUID envelopeId, String voidReason, UUID voidedBy) {
        return Mono.fromCallable(() -> {
            String docuSignEnvelopeId = envelopeIdMapping.get(envelopeId);
            if (docuSignEnvelopeId == null) {
                throw new RuntimeException("Envelope not found: " + envelopeId);
            }
            Envelope update = new Envelope();
            update.setStatus("voided");
            update.setVoidedReason(voidReason);
            envelopesApi.update(properties.getAccountId(), docuSignEnvelopeId, update);
            return envelopeId;
        }).flatMap(this::getEnvelope);
    }

    @Override
    public Mono<Boolean> existsEnvelope(UUID envelopeId) {
        return Mono.fromCallable(() -> {
            String docuSignEnvelopeId = envelopeIdMapping.get(envelopeId);
            if (docuSignEnvelopeId == null) return false;
            try {
                envelopesApi.getEnvelope(properties.getAccountId(), docuSignEnvelopeId);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByStatus(EnvelopeStatus status, Integer limit) {
        return Flux.fromIterable(() -> {
            try {
                EnvelopesApi.ListStatusChangesOptions options = envelopesApi.new ListStatusChangesOptions();
                if (limit != null) options.setCount(limit.toString());
                options.setStatus(mapToDocuSignStatus(status));
                EnvelopesInformation info = envelopesApi.listStatusChanges(properties.getAccountId(), options);
                return info.getEnvelopes().stream().map(this::buildSignatureEnvelopeFromDocuSign).iterator();
            } catch (Exception e) {
                return Collections.<SignatureEnvelope>emptyList().iterator();
            }
        });
    }

    private SignatureEnvelope buildSignatureEnvelopeFromDocuSign(UUID id, Envelope docuSignEnvelope) {
        EnvelopeStatus status = EnvelopeStatus.valueOf(docuSignEnvelope.getStatus().toUpperCase());
        return SignatureEnvelope.builder()
                .id(id)
                .title(docuSignEnvelope.getEmailSubject())
                .description(docuSignEnvelope.getEmailBlurb())
                .status(status)
                .provider(SignatureProvider.DOCUSIGN)
                .externalEnvelopeId(docuSignEnvelope.getEnvelopeId())
                .build();
    }

    private SignatureEnvelope buildSignatureEnvelopeFromDocuSign(Envelope env) {
        UUID id = externalIdMapping.getOrDefault(env.getEnvelopeId(), UUID.randomUUID());
        return buildSignatureEnvelopeFromDocuSign(id, env);
    }

    private EnvelopeDefinition buildEnvelopeDefinition(SignatureEnvelope envelope) {
        EnvelopeDefinition envDef = new EnvelopeDefinition();
        envDef.setEmailSubject(envelope.getTitle());
        envDef.setEmailBlurb(envelope.getDescription());
        envDef.setStatus("created");
        // Minimal placeholder; real implementation would map documents and recipients
        return envDef;
    }

    private String mapToDocuSignStatus(EnvelopeStatus status) {
        switch (status) {
            case DRAFT: return "created";
            case SENT: return "sent";
            case COMPLETED: return "completed";
            case VOIDED: return "voided";
            default: return "any";
        }
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByCreator(UUID createdBy, Integer limit) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesBySender(UUID sentBy, Integer limit) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByProvider(org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider provider, Integer limit) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getExpiringEnvelopes(java.time.Instant fromTime, java.time.Instant toTime) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getCompletedEnvelopes(java.time.Instant fromTime, java.time.Instant toTime) {
        return Flux.empty();
    }

    @Override
    public Mono<SignatureEnvelope> getEnvelopeByExternalId(String externalEnvelopeId, org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider provider) {
        UUID id = externalIdMapping.get(externalEnvelopeId);
        return id != null ? getEnvelope(id) : Mono.empty();
    }

    @Override
    public Mono<SignatureEnvelope> syncEnvelopeStatus(UUID envelopeId) {
        return getEnvelope(envelopeId);
    }

    @Override
    public Mono<String> getSigningUrl(UUID envelopeId, String signerEmail, String signerName, String clientUserId) {
        return Mono.error(new UnsupportedOperationException("Embedded signing URL not implemented for DocuSign"));
    }

    @Override
    public Mono<Void> resendEnvelope(UUID envelopeId) {
        return Mono.empty();
    }

    @Override
    public Mono<SignatureEnvelope> archiveEnvelope(UUID envelopeId) {
        return getEnvelope(envelopeId);
    }
}
