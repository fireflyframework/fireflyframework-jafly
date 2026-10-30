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
package org.fireflyframework.core.audit;

import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.annotation.Id;

/** Exact immutable storage projection of kernel values; no authorization claims are created. */
public final class AuditRows {
    private AuditRows() { }
    @Table(name="audit_head",schema="firefly_audit")
    public record Head(UUID resourceTenantId,String service,String resourceType,UUID resourceId,long lastSequence,String lastHash) {
        public AuditCheckpoint checkpoint(){return new AuditCheckpoint(new AuditStream(resourceTenantId,service,resourceType,resourceId),lastSequence,lastHash);}
        public static Head from(AuditCheckpoint c){return new Head(c.stream().resourceTenantId(),c.stream().service(),c.stream().resourceType(),c.stream().resourceId(),c.lastSequence(),c.lastHash());}
    }
    @Table(name="audit_event",schema="firefly_audit")
    public record Event(UUID resourceTenantId,String service,String resourceType,UUID resourceId,@Id UUID eventId,
            long sequence,String previousHash,String recordHash,long recordedSeconds,int recordedNanos,
            String actorIssuer,String actorSubject,String actorClientId,String actorPlatformTenantId,String actorStaffRealm,UUID actorStaffId,
            String action,String outcome,UUID processId,UUID correlationId,UUID causationId,long occurredSeconds,int occurredNanos,String evidenceDigest,String policyRevision) {
        public static Event from(AuditRecord r){var e=r.event();var s=e.stream();var a=e.actor();return new Event(s.resourceTenantId(),s.service(),s.resourceType(),s.resourceId(),e.eventId(),r.sequence(),r.previousHash(),r.recordHash(),r.recordedAt().getEpochSecond(),r.recordedAt().getNano(),a.issuer(),a.subject(),a.clientId(),a.platformTenantId(),a.staffRealm(),a.staffId(),e.action(),e.outcome().name(),e.processId(),e.correlationId(),e.causationId(),e.occurredAt().getEpochSecond(),e.occurredAt().getNano(),e.evidenceDigest(),e.policyRevision());}
        public AuditRecord record(){var event=new AuditEvent(eventId,new AuditStream(resourceTenantId,service,resourceType,resourceId),new AuditActor(actorIssuer,actorSubject,actorClientId,actorPlatformTenantId,actorStaffRealm,actorStaffId),action,AuditEvent.Outcome.valueOf(outcome),processId,correlationId,causationId,Instant.ofEpochSecond(occurredSeconds,occurredNanos),evidenceDigest,policyRevision);var record=new AuditRecord(event,sequence,Instant.ofEpochSecond(recordedSeconds,recordedNanos),previousHash,recordHash);var expected=AuditRecordHasher.record(new AuditCheckpoint(event.stream(),sequence-1,previousHash),event,record.recordedAt());if(!expected.equals(record))throw new IllegalStateException("Audit stored record integrity mismatch");return record;}
    }
    /** Permanent export intent; mutable delivery leases/ACKs must live in separate state. */
    @Table(name="audit_outbox",schema="firefly_audit")
    public record Outbox(UUID resourceTenantId,UUID eventId,String service,String resourceType,UUID resourceId,long sequence,String recordHash,String intent) {
        public static Outbox from(AuditRecord r){var s=r.event().stream();return new Outbox(s.resourceTenantId(),r.event().eventId(),s.service(),s.resourceType(),s.resourceId(),r.sequence(),r.recordHash(),"AUDIT_EXPORT");}
    }
}
