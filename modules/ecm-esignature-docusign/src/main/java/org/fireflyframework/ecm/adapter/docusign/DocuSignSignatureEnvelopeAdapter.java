/*
 * Copyright 2024 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.model.*;
import org.fireflyframework.ecm.adapter.AdapterFeature;
import org.fireflyframework.ecm.adapter.EcmAdapter;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.domain.enums.esignature.EnvelopeStatus;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopeReferenceResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.Callable;

@Slf4j
@EcmAdapter(
    type = "docusign",
    description = "DocuSign eSignature Envelope Adapter",
    supportedFeatures = {
        AdapterFeature.ESIGNATURE_ENVELOPES
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
    private final @Nullable SignatureEnvelopeReferenceResolver references;

    public DocuSignSignatureEnvelopeAdapter(ApiClient apiClient, DocuSignAdapterProperties properties,
                                           DocumentContentPort documentContentPort, DocumentPort documentPort) {
        this(apiClient, properties, documentContentPort, documentPort, null);
    }

    @Autowired
    public DocuSignSignatureEnvelopeAdapter(ApiClient apiClient, DocuSignAdapterProperties properties,
                                           DocumentContentPort documentContentPort, DocumentPort documentPort,
                                           @Nullable SignatureEnvelopeReferenceResolver references) {
        this.apiClient = Objects.requireNonNull(apiClient);
        this.properties = Objects.requireNonNull(properties);
        this.documentContentPort = Objects.requireNonNull(documentContentPort);
        this.documentPort = Objects.requireNonNull(documentPort);
        this.references = references;
        this.envelopesApi = new EnvelopesApi(apiClient);
    }

    @Override
    public Mono<SignatureEnvelope> createEnvelope(SignatureEnvelope envelope) {
        return Mono.defer(() -> {
            EnvelopeDefinition definition = DocuSignEnvelopeMapping.draft(envelope);
            var total = new java.util.concurrent.atomic.AtomicLong();
            return Flux.fromIterable(envelope.getDocumentIds()).concatMap(id ->
                    documentPort.getDocument(id).switchIfEmpty(Mono.error(DocuSignEnvelopeMapping.invalid()))
                            .flatMap(document -> documentContentPort.getContent(id)
                                    .switchIfEmpty(Mono.error(DocuSignEnvelopeMapping.invalid()))
                                    .map(bytes -> {
                                        if (total.addAndGet(bytes.length) > DocuSignEnvelopeMapping.MAX_TOTAL_BYTES) throw DocuSignEnvelopeMapping.invalid();
                                        return document(document, id, bytes, envelope.getDocumentIds().indexOf(id) + 1);
                                    })))
                    .collectList().flatMap(documents -> {
                        definition.setDocuments(documents);
                        return sdkCallable(() -> {
                            EnvelopeSummary summary = envelopesApi.createEnvelope(properties.getAccountId(), definition);
                            if (summary == null || summary.getErrorDetails() != null || summary.getEnvelopeId() == null
                                    || !summary.getEnvelopeId().matches("[A-Za-z0-9_-]{1,128}")
                                    || !"created".equals(summary.getStatus())) throw DocuSignEnvelopeMapping.response();
                            return envelope.toBuilder().id(envelope.getId() == null ? UUID.randomUUID() : envelope.getId())
                                    .externalEnvelopeId(summary.getEnvelopeId()).provider(SignatureProvider.DOCUSIGN)
                                    .status(EnvelopeStatus.DRAFT).createdAt(DocuSignEnvelopeMapping.instant(summary.getStatusDateTime())).build();
                        });
                    });
        }).onErrorMap(error -> DocuSignAdapterException.safe(error, DocuSignAdapterException.Operation.ENVELOPE));
    }

    private static com.docusign.esign.model.Document document(
            org.fireflyframework.ecm.domain.model.document.Document metadata, UUID id, byte[] bytes, int index) {
        if (!id.equals(metadata.getId()) || !"application/pdf".equalsIgnoreCase(metadata.getMimeType())
                || (metadata.getExtension() != null && !"pdf".equalsIgnoreCase(metadata.getExtension()))
                || bytes.length == 0 || bytes.length > DocuSignEnvelopeMapping.MAX_DOCUMENT_BYTES
                || (metadata.getSize() != null && metadata.getSize() != bytes.length)) throw DocuSignEnvelopeMapping.invalid();
        var document = new com.docusign.esign.model.Document();
        document.setDocumentId(Integer.toString(index)); document.setName(DocuSignEnvelopeMapping.text(metadata.getName(), 255));
        document.setFileExtension("pdf"); document.setDocumentBase64(Base64.getEncoder().encodeToString(bytes));
        return document;
    }

    private Mono<String> reference(UUID id) {
        return Mono.defer(() -> {
            if (id == null) throw DocuSignEnvelopeMapping.invalid();
            if (references == null) throw DocuSignEnvelopeMapping.unsupported();
            return references.resolveExternalId(id, SignatureProvider.DOCUSIGN).map(DocuSignEnvelopeMapping::externalId);
        }).onErrorMap(error -> DocuSignAdapterException.safe(error, DocuSignAdapterException.Operation.ENVELOPE));
    }

    private Mono<String> requiredReference(UUID id) {
        return reference(id).switchIfEmpty(Mono.error(new DocuSignAdapterException(
                DocuSignAdapterException.Code.NOT_FOUND, DocuSignAdapterException.Operation.ENVELOPE)));
    }

    private Mono<SignatureEnvelope> fetch(UUID id, String externalId) {
        return sdkCallable(() -> {
            try {
                return DocuSignEnvelopeMapping.fromProvider(id, externalId, envelopesApi.getEnvelope(properties.getAccountId(), externalId));
            } catch (ApiException error) {
                if (error.getCode() == 404) return null;
                throw error;
            }
        });
    }

    @Override public Mono<SignatureEnvelope> getEnvelope(UUID id) {
        return reference(id).flatMap(externalId -> fetch(id, externalId));
    }

    @Override public Mono<SignatureEnvelope> getEnvelopeByExternalId(String externalId, SignatureProvider provider) {
        return Mono.defer(() -> {
            if (provider != SignatureProvider.DOCUSIGN) throw DocuSignEnvelopeMapping.invalid();
            return fetch(null, DocuSignEnvelopeMapping.externalId(externalId));
        });
    }

    private Mono<SignatureEnvelope> requiredFetch(UUID id, String externalId) {
        return fetch(id, externalId).switchIfEmpty(Mono.error(new DocuSignAdapterException(
                DocuSignAdapterException.Code.NOT_FOUND, DocuSignAdapterException.Operation.ENVELOPE)));
    }

    private String update(String externalId, Envelope update) throws ApiException {
        EnvelopeUpdateSummary result = envelopesApi.update(properties.getAccountId(), externalId, update);
        if (result == null || result.getErrorDetails() != null
                || (result.getEnvelopeId() != null && !externalId.equals(result.getEnvelopeId()))
                || (result.getRecipientUpdateResults() != null && result.getRecipientUpdateResults().stream()
                    .anyMatch(recipient -> recipient == null || recipient.getErrorDetails() != null))) throw DocuSignEnvelopeMapping.response();
        return externalId;
    }

    @Override public Mono<SignatureEnvelope> updateEnvelope(SignatureEnvelope envelope) {
        return Mono.defer(() -> {
            DocuSignEnvelopeMapping.metadataUpdate(envelope);
            Mono<String> selected = envelope.getExternalEnvelopeId() == null ? requiredReference(envelope.getId())
                    : Mono.just(DocuSignEnvelopeMapping.externalId(envelope.getExternalEnvelopeId()));
            return selected.flatMap(externalId -> sdkCallable(() -> {
                var update = new Envelope(); update.setEmailSubject(envelope.getTitle()); update.setEmailBlurb(envelope.getDescription());
                return update(externalId, update);
            }).flatMap(external -> requiredFetch(envelope.getId(), external)));
        });
    }

    @Override public Mono<SignatureEnvelope> sendEnvelope(UUID id, UUID sentBy) {
        return Mono.defer(() -> {
            if (sentBy == null) throw DocuSignEnvelopeMapping.invalid();
            return requiredReference(id).flatMap(external -> sdkCallable(() -> {
                var update = new Envelope(); update.setStatus("sent");
                return update(external, update);
            }).flatMap(externalId -> requiredFetch(id, externalId)).map(envelope -> envelope.toBuilder().sentByActorId(sentBy).build()));
        });
    }

    @Override public Mono<SignatureEnvelope> voidEnvelope(UUID id, String reason, UUID voidedBy) {
        return Mono.defer(() -> {
            if (voidedBy == null) throw DocuSignEnvelopeMapping.invalid();
            DocuSignEnvelopeMapping.text(reason, 200);
            return requiredReference(id).flatMap(external -> sdkCallable(() -> {
                var update = new Envelope(); update.setStatus("voided"); update.setVoidedReason(reason);
                return update(external, update);
            }).flatMap(externalId -> requiredFetch(id, externalId)));
        });
    }

    @Override public Mono<Boolean> existsEnvelope(UUID id) {
        return reference(id).flatMap(external -> sdkCallable(() -> {
            try {
                var response = envelopesApi.getEnvelope(properties.getAccountId(), external);
                if (response == null || !external.equals(response.getEnvelopeId())) throw DocuSignEnvelopeMapping.response();
                return true;
            } catch (ApiException error) {
                if (error.getCode() == 404) return false;
                throw error;
            }
        })).defaultIfEmpty(false);
    }

    @Override public Flux<SignatureEnvelope> getEnvelopesByStatus(EnvelopeStatus status, Instant from, Instant to, Integer limit) {
        return Flux.defer(() -> {
            String providerStatus = DocuSignEnvelopeMapping.status(status);
            if (from == null || to == null || from.isAfter(to) || limit == null || limit < 1 || limit > 1000) throw DocuSignEnvelopeMapping.invalid();
            return sdkCallable(() -> {
                var options = envelopesApi.new ListStatusChangesOptions(); options.setStatus(providerStatus);
                options.setFromDate(from.toString()); options.setToDate(to.toString()); options.setCount(limit.toString());
                EnvelopesInformation info = envelopesApi.listStatusChanges(properties.getAccountId(), options);
                if (info == null || info.getEnvelopes() == null || info.getEnvelopes().size() > limit) throw DocuSignEnvelopeMapping.response();
                return info.getEnvelopes().stream().map(envelope -> {
                    String externalId;
                    try { externalId = DocuSignEnvelopeMapping.externalId(envelope.getEnvelopeId()); }
                    catch (RuntimeException invalid) { throw DocuSignEnvelopeMapping.response(); }
                    return DocuSignEnvelopeMapping.fromProvider(null, externalId, envelope);
                }).toList();
            }).flatMapMany(Flux::fromIterable);
        });
    }

    private <T> Mono<T> sdkCallable(Callable<T> operation) {
        if (apiClient instanceof ManagedDocuSignApiClient managed) return managed.execute(operation);
        return Mono.fromCallable(operation).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(error -> DocuSignAdapterException.safe(error, DocuSignAdapterException.Operation.ENVELOPE));
    }

    @Override public Mono<Void> deleteEnvelope(UUID id) { return Mono.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Flux<SignatureEnvelope> getEnvelopesByStatus(EnvelopeStatus status, Integer limit) { return Flux.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Flux<SignatureEnvelope> getEnvelopesByCreator(UUID creator, Integer limit) { return Flux.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Flux<SignatureEnvelope> getEnvelopesBySender(UUID sender, Integer limit) { return Flux.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Flux<SignatureEnvelope> getEnvelopesByProvider(SignatureProvider provider, Integer limit) { return Flux.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Flux<SignatureEnvelope> getExpiringEnvelopes(Instant from, Instant to) { return Flux.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Flux<SignatureEnvelope> getCompletedEnvelopes(Instant from, Instant to) { return Flux.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Mono<SignatureEnvelope> syncEnvelopeStatus(UUID id) { return getEnvelope(id); }
    @Override public Mono<String> getSigningUrl(UUID id, String email, String name, String clientUserId) { return Mono.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Mono<Void> resendEnvelope(UUID id) { return Mono.error(DocuSignEnvelopeMapping.unsupported()); }
    @Override public Mono<SignatureEnvelope> archiveEnvelope(UUID id) { return Mono.error(DocuSignEnvelopeMapping.unsupported()); }
}
