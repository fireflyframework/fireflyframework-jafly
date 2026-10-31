/*
 * Copyright 2024 Firefly Software Foundation.
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
package org.fireflyframework.ecm.domain.model.esignature;

import org.fireflyframework.ecm.domain.enums.esignature.EnvelopePriority;
import org.fireflyframework.ecm.domain.enums.esignature.EnvelopeStatus;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.domain.enums.esignature.SigningOrder;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Signature envelope entity representing a collection of documents for signing.
 * Uses UUID envelope/actor IDs and preserves the legacy numeric sender field.
 */
@Data
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PACKAGE)
@Builder(toBuilder = true)
@Jacksonized
public class SignatureEnvelope {
    
    /**
     * Unique envelope identifier (UUID)
     */
    private final UUID id;
    
    /**
     * Envelope title/subject
     */
    private final String title;
    
    /**
     * Envelope description/message
     */
    private final String description;
    
    /**
     * Current envelope status
     */
    private final EnvelopeStatus status;
    
    /**
     * List of document IDs included in this envelope
     */
    private final List<UUID> documentIds;
    
    /**
     * List of signature requests in this envelope
     */
    private final List<SignatureRequest> signatureRequests;
    
    /**
     * User who created the envelope (UUID)
     */
    private final UUID createdBy;

    /**
     * Envelope creation timestamp
     */
    private final Instant createdAt;

    /**
     * Legacy numeric sender ID; UUID send operations populate sentByActorId separately.
     */
    private final Long sentBy;

    /** Exact UUID actor supplied to sendEnvelope; legacy numeric sentBy is preserved separately. */
    private final UUID sentByActorId;
    
    /**
     * Envelope sent timestamp
     */
    private final Instant sentAt;
    
    /**
     * Envelope completion timestamp
     */
    private final Instant completedAt;
    
    /**
     * Envelope expiration timestamp
     */
    private final Instant expiresAt;
    
    /**
     * Last modification timestamp
     */
    private final Instant modifiedAt;
    
    /**
     * Envelope metadata
     */
    private final Map<String, Object> metadata;
    
    /**
     * External provider envelope ID (DocuSign, Logalty, etc.)
     */
    private final String externalEnvelopeId;
    
    /**
     * Signature provider type
     */
    private final SignatureProvider provider;
    
    /**
     * Envelope priority
     */
    private final EnvelopePriority priority;
    
    /**
     * Whether envelope requires all signers to complete
     */
    private final Boolean requireAllSigners;
    
    /**
     * Signing order enforcement
     */
    private final SigningOrder signingOrder;
    
    /**
     * Reminder settings
     */
    private final ReminderSettings reminderSettings;
    
    /**
     * Authentication requirements
     */
    private final AuthenticationRequirements authRequirements;
    
    /**
     * Envelope callback URL for notifications
     */
    private final String callbackUrl;
    
    /**
     * Whether envelope is voided
     */
    private final Boolean voided;
    
    /**
     * Void reason if envelope is voided
     */
    private final String voidReason;
    /** Retains the original constructor descriptor for existing compiled callers. */
    SignatureEnvelope(UUID id,
            String title,
            String description,
            EnvelopeStatus status,
            List<UUID> documentIds,
            List<SignatureRequest> signatureRequests,
            UUID createdBy,
            Instant createdAt,
            Long sentBy,
            Instant sentAt,
            Instant completedAt,
            Instant expiresAt,
            Instant modifiedAt,
            Map<String, Object> metadata,
            String externalEnvelopeId,
            SignatureProvider provider,
            EnvelopePriority priority,
            Boolean requireAllSigners,
            SigningOrder signingOrder,
            ReminderSettings reminderSettings,
            AuthenticationRequirements authRequirements,
            String callbackUrl,
            Boolean voided,
            String voidReason) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.documentIds = documentIds;
        this.signatureRequests = signatureRequests;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.sentBy = sentBy;
        this.sentAt = sentAt;
        this.completedAt = completedAt;
        this.expiresAt = expiresAt;
        this.modifiedAt = modifiedAt;
        this.metadata = metadata;
        this.externalEnvelopeId = externalEnvelopeId;
        this.provider = provider;
        this.priority = priority;
        this.requireAllSigners = requireAllSigners;
        this.signingOrder = signingOrder;
        this.reminderSettings = reminderSettings;
        this.authRequirements = authRequirements;
        this.callbackUrl = callbackUrl;
        this.voided = voided;
        this.voidReason = voidReason;
        this.sentByActorId = null;
    }
}
