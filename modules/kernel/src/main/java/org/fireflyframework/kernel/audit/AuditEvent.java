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
package org.fireflyframework.kernel.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Bounded business evidence metadata, without arbitrary payloads or secrets.
 * Identity, outcome, time and evidence digest require verification by the owning service.
 * A null causation ID identifies a root event; other links remain explicit.
 */
public record AuditEvent(UUID eventId, AuditStream stream, AuditActor actor, String action, Outcome outcome,
                         UUID processId, UUID correlationId, UUID causationId, Instant occurredAt,
                         String evidenceDigest, String policyRevision) {
    public enum Outcome { ATTEMPTED, ACCEPTED, REJECTED, FAILED, CANCELLED, COMPLETED, UNKNOWN }

    public AuditEvent {
        AuditValidation.required(eventId);
        AuditValidation.required(stream);
        AuditValidation.required(actor);
        AuditValidation.token(action);
        AuditValidation.required(outcome);
        AuditValidation.required(processId);
        AuditValidation.required(correlationId);
        AuditValidation.required(occurredAt);
        AuditValidation.digest(evidenceDigest);
        AuditValidation.token(policyRevision);
    }
}
