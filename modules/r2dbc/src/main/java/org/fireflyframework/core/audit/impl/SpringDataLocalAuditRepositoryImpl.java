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

import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.transaction.*;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.StatementMapper;
import org.springframework.data.relational.core.query.*;
import reactor.core.publisher.Mono;

/** Actual mapped statements/counts. Failures propagate; never continue on an aborted duplicate. */
public final class SpringDataLocalAuditRepositoryImpl implements LocalAuditRepository {
    private final R2dbcEntityTemplate template;
    private final BoundedR2dbcResources resources;
    public SpringDataLocalAuditRepositoryImpl(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,R2dbcTransactionManager manager){this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);this.template=template;}
    @Override public Mono<AuditCheckpoint> head(RootTransactionScope owner,AuditStream stream){return transaction(owner,()->template.select(AuditRows.Head.class).matching(Query.query(scope(stream))).one().map(AuditRows.Head::checkpoint).defaultIfEmpty(AuditCheckpoint.genesis(stream)));}
    @Override public Mono<AuditRecord> find(RootTransactionScope owner,UUID tenant,UUID event){return transaction(owner,()->{
        var query=Query.query(Criteria.where("resourceTenantId").is(tenant).and("eventId").is(event));
        return template.select(AuditRows.Event.class).matching(query).one().map(AuditRows.Event::record).flatMap(record->template.select(AuditRows.Outbox.class).matching(query).one()
                .filter(AuditRows.Outbox.from(record)::equals).switchIfEmpty(Mono.error(failure())).thenReturn(record));
    });}
    @Override public Mono<AuditRecord> append(RootTransactionScope owner,AuditCheckpoint expected,AuditRecord record){return transaction(owner,()->{
        if(!AuditRecordHasher.record(expected,record.event(),record.recordedAt()).equals(record))return Mono.error(failure());
        var next=new AuditCheckpoint(record.event().stream(),record.sequence(),record.recordHash());
        Mono<Void> head;
        if(expected.lastSequence()==0)head=insert(AuditRows.Head.from(next));
        else{
            var strategy=template.getDataAccessStrategy();var mapper=strategy.getStatementMapper().forType(AuditRows.Head.class);
            var criteria=scope(expected.stream()).and("lastSequence").is(expected.lastSequence()).and("lastHash").is(expected.lastHash());
            var update=mapper.createUpdate(strategy.getTableName(AuditRows.Head.class),Update.update("lastSequence",next.lastSequence()).set("lastHash",next.lastHash())).withCriteria(criteria);
            head=exactlyOne(template.getDatabaseClient().sql(mapper.getMappedObject(update)).fetch().rowsUpdated());
        }
        return head.then(insert(AuditRows.Event.from(record))).then(insert(AuditRows.Outbox.from(record)))
                .then(find(owner,record.event().stream().resourceTenantId(),record.event().eventId()))
                .filter(record::equals).switchIfEmpty(Mono.error(failure()));
    });}
    private <T> Mono<Void> insert(T row){
        var strategy=template.getDataAccessStrategy();var mapper=strategy.getStatementMapper().forType(row.getClass());
        StatementMapper.InsertSpec insert=mapper.createInsert(strategy.getTableName(row.getClass()));
        for(var entry:strategy.getOutboundRow(row).entrySet())insert=insert.withColumn(entry.getKey(),entry.getValue());
        return exactlyOne(template.getDatabaseClient().sql(mapper.getMappedObject(insert)).fetch().rowsUpdated());
    }
    private static Mono<Void> exactlyOne(Mono<Long> count){return count.switchIfEmpty(Mono.error(failure())).flatMap(n->n==1?Mono.empty():Mono.error(failure()));}
    private <T> Mono<T> transaction(RootTransactionScope scope,java.util.function.Supplier<Mono<T>> work){return resources.factory().requireBoundScope(resources,scope).then(Mono.defer(work))
            .onErrorResume(error->resources.factory().markBoundScopeRollbackOnly(resources,scope).onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error)));}
    private static Criteria scope(AuditStream s){return Criteria.where("resourceTenantId").is(s.resourceTenantId()).and("service").is(s.service()).and("resourceType").is(s.resourceType()).and("resourceId").is(s.resourceId());}
    private static IllegalStateException failure(){return new IllegalStateException("Exact local audit storage observation required");}
}
