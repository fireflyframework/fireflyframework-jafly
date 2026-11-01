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
package org.fireflyframework.core.audit.impl;

import java.time.*;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

/** Opt-in real mapped append/native clock; never creates an inner root or treats claims as grants. */
public final class SpringDataTransactionalAuditAppenderImpl implements TransactionalAuditAppender {
    private final BoundedR2dbcResources resources;
    private final LocalAuditRepository repository;
    private final AuditNativeContextRepository nativeContext;
    private final AuditAppendAuthority authority;
    private final Clock clock;private final LongSupplier nanos;private final Supplier<UUID> nonces;
    public SpringDataTransactionalAuditAppenderImpl(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,R2dbcTransactionManager manager,AuditAppendAuthority authority,Clock clock,LongSupplier nanos,Supplier<UUID> nonces){
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);
        this.authority=Objects.requireNonNull(authority);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.nonces=Objects.requireNonNull(nonces);
        repository=new SpringDataLocalAuditRepositoryImpl(this.resources,client,template,manager);nativeContext=new R2dbcRepositoryFactory(template).getRepository(AuditNativeContextRepository.class);
    }
    @Override public Mono<ProvisionalAppend> append(RootTransactionScope scope,AuditEvent event){return Mono.defer(()->resources.factory().requireBoundScope(resources,scope).then(Mono.defer(()->{
        Objects.requireNonNull(event);scope.remaining();
        return current(event,scope).flatMap(permit->{
            UUID nonce=Objects.requireNonNull(nonces.get());if(nonce.equals(new UUID(0,0)))return Mono.error(failure());
            Instant now=clock.instant();Instant until=Collections.min(List.of(permit.validUntil(),now.plus(scope.remaining()),now.plusSeconds(5)));
            long beforeNative=nanos.getAsLong();var s=permit.stream();var a=permit.actor();
            return nativeContext.enter(nonce,s.resourceTenantId(),s.service(),s.resourceType(),s.resourceId(),event.eventId(),a.issuer(),a.subject(),a.clientId(),a.platformTenantId(),a.staffRealm(),a.staffId(),OffsetDateTime.ofInstant(until,ZoneOffset.UTC))
                .switchIfEmpty(Mono.error(failure())).timeout(scope.remaining()).flatMap(stamp->{
                    if(!nonce.equals(stamp.contextNonce())||!event.eventId().equals(stamp.eventId())||stamp.dbNowMillis()==null||stamp.untilMillis()==null
                            ||stamp.dbNowMillis()<=0||stamp.untilMillis()>until.toEpochMilli()||stamp.untilMillis()-1<=stamp.dbNowMillis())return Mono.error(failure());
                    var cap=R2dbcOperationBudget.observed(Instant.ofEpochMilli(stamp.dbNowMillis()),Instant.ofEpochMilli(stamp.untilMillis()-1),beforeNative,Duration.ofSeconds(5),clock,nanos);scope.narrow(cap);
                    return repository.find(scope,s.resourceTenantId(),event.eventId()).flatMap(record->{
                        if(!record.event().equals(event))return Mono.error(failure());
                        return repository.head(scope,s).filter(head->head.lastSequence()>=record.sequence()&&(head.lastSequence()!=record.sequence()||head.lastHash().equals(record.recordHash())))
                            .switchIfEmpty(Mono.error(failure())).thenReturn(new ProvisionalAppend(record,true));
                    }).switchIfEmpty(Mono.defer(()->repository.head(scope,s).flatMap(head->repository.append(scope,head,AuditRecordHasher.record(head,event,Instant.ofEpochMilli(stamp.dbNowMillis())))).map(record->new ProvisionalAppend(record,false))))
                        .flatMap(provisional->current(event,scope).flatMap(current->{
                            if(current.validUntil().isBefore(permit.validUntil())){
                                long beforeCurrent=nanos.getAsLong();Instant observed=clock.instant();
                                Instant currentUntil=Collections.min(List.of(current.validUntil(),observed.plus(scope.remaining()),observed.plusSeconds(5)));
                                var narrowed=R2dbcOperationBudget.observed(observed,currentUntil,beforeCurrent,Duration.ofSeconds(5),clock,nanos);scope.narrow(narrowed);
                            }
                            return resources.factory().requireBoundScope(resources,scope).then(nativeContext.complete(nonce,provisional.record().sequence(),provisional.record().recordHash()))
                                .filter(nonce::equals).switchIfEmpty(Mono.error(failure())).timeout(scope.remaining()).map(done->{scope.remaining();return provisional;});
                        }));
                });
        });
    }).onErrorResume(error->resources.factory().markBoundScopeRollbackOnly(resources,scope)
        .onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error)))));}
    private Mono<AuditAppendAuthority.Permit> current(AuditEvent event,RootTransactionScope scope){return Mono.defer(()->authority.requireCurrent(event)).switchIfEmpty(Mono.error(failure())).timeout(scope.remaining()).map(permit->{
        if(!permit.stream().equals(event.stream())||!permit.actor().equals(event.actor())||!permit.validUntil().isAfter(clock.instant()))throw failure();return permit;
    });}
    private static IllegalStateException failure(){return new IllegalStateException("Current admitted local audit append required");}
}
