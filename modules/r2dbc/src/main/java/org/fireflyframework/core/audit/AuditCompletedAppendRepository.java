/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.core.audit;

import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditRecord;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;

/** Explicit read-only native completion projection; construction or a row is not admission. */
@NoRepositoryBean
public interface AuditCompletedAppendRepository extends Repository<AuditRows.Event,UUID> {
    record CompletedRow(UUID contextNonce,Long dbNowMillis,Long untilMillis,Boolean replay,
            UUID resourceTenantId,String service,String resourceType,UUID resourceId,UUID eventId,
            Long sequence,String previousHash,String recordHash,Long recordedSeconds,Integer recordedNanos,
            String actorIssuer,String actorSubject,String actorClientId,String actorPlatformTenantId,
            String actorStaffRealm,UUID actorStaffId,String action,String outcome,UUID processId,
            UUID correlationId,UUID causationId,Long occurredSeconds,Integer occurredNanos,String evidenceDigest,String policyRevision) {
        public AuditRecord record() {
            Objects.requireNonNull(sequence);Objects.requireNonNull(recordedSeconds);Objects.requireNonNull(recordedNanos);
            Objects.requireNonNull(occurredSeconds);Objects.requireNonNull(occurredNanos);
            if(sequence<1||recordedNanos<0||recordedNanos>999999999||occurredNanos<0||occurredNanos>999999999)
                throw new IllegalStateException("Exact native audit record fields required");
            return new AuditRows.Event(resourceTenantId,service,resourceType,resourceId,eventId,sequence,previousHash,recordHash,
                    recordedSeconds,recordedNanos,actorIssuer,actorSubject,actorClientId,actorPlatformTenantId,actorStaffRealm,actorStaffId,
                    action,outcome,processId,correlationId,causationId,occurredSeconds,occurredNanos,evidenceDigest,policyRevision).record();
        }
    }
    @Query("SELECT * FROM public.firefly_audit_require_completed(:nonce,:event,:sequence,:hash,:replay)")
    Flux<CompletedRow> requireCompleted(@Param("nonce") UUID nonce,@Param("event") UUID event,
            @Param("sequence") long sequence,@Param("hash") String hash,@Param("replay") boolean replay);
}
