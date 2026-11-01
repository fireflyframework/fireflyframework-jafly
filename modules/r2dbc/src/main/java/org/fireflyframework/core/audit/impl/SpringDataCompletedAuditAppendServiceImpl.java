/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.core.audit.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.AuditRecord;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

/** Read-only mapped verification on the retained graph, with no automatic bean or repository fallback. */
public final class SpringDataCompletedAuditAppendServiceImpl implements CompletedAuditAppendService {
    private final BoundedR2dbcResources original;
    private final BoundedR2dbcResources resources;
    private final AuditCompletedAppendRepository repository;
    private final Clock clock;
    private final LongSupplier nanos;
    public SpringDataCompletedAuditAppendServiceImpl(BoundedR2dbcResources original,DatabaseClient client,
            R2dbcEntityTemplate template,R2dbcTransactionManager manager,Clock clock,LongSupplier nanos) {
        this.original=Objects.requireNonNull(original);
        this.resources=BoundedR2dbcResources.verify(original.factory(),client,template,manager);
        this.repository=new R2dbcRepositoryFactory(template).getRepository(AuditCompletedAppendRepository.class);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    @Override public Mono<AuditRecord> requireCompleted(RootTransactionScope scope,UUID nonce,
            TransactionalAuditAppender.ProvisionalAppend expected) {
        return Mono.defer(()->bound(scope).then(Mono.defer(()->{
            if(nonce==null||nonce.equals(new UUID(0,0))||expected==null||expected.record()==null)throw failure();
            scope.remaining();long before=nanos.getAsLong();var record=expected.record();
            return repository.requireCompleted(nonce,record.event().eventId(),record.sequence(),record.recordHash(),expected.replay())
                .take(2).collectList().timeout(scope.remaining()).flatMap(rows->{
                    scope.remaining();if(rows.size()!=1)throw failure();var row=rows.getFirst();
                    if(!nonce.equals(row.contextNonce())||row.replay()==null||row.replay()!=expected.replay()
                            ||row.dbNowMillis()==null||row.untilMillis()==null||row.dbNowMillis()<=0
                            ||row.untilMillis()<=row.dbNowMillis()||row.untilMillis()-1<=row.dbNowMillis())throw failure();
                    scope.narrow(R2dbcOperationBudget.observed(Instant.ofEpochMilli(row.dbNowMillis()),Instant.ofEpochMilli(row.untilMillis()-1),
                            before,Duration.ofSeconds(5),clock,nanos));
                    var actual=row.record();if(!record.equals(actual))throw failure();
                    return bound(scope).then(Mono.fromSupplier(()->{scope.remaining();return actual;}));
                });
        })).onErrorResume(error->original.factory().markBoundScopeRollbackOnly(original,scope)
                .onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error))));
    }
    private Mono<Void> bound(RootTransactionScope scope) {
        return original.factory().requireBoundScope(original,scope).then(resources.factory().requireBoundScope(resources,scope));
    }
    private static IllegalStateException failure(){return new IllegalStateException("Exact completed original audit append required");}
}
