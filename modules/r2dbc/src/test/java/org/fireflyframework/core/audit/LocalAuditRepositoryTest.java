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

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.fireflyframework.core.audit.impl.SpringDataLocalAuditRepository;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class LocalAuditRepositoryTest {
    static UUID id(long n){return new UUID(0,n);}
    static AuditEvent event(){return new AuditEvent(id(1),new AuditStream(id(2),"payments","transfer",id(3)),new AuditActor("https://issuer.test","staff-1","staff-app","sandbox-platform","staff",id(5)),"TRANSFER_POSTED",AuditEvent.Outcome.COMPLETED,id(6),id(7),id(8),Instant.ofEpochSecond(123456,987654321),"a".repeat(64),"policy-1");}
    static AuditRecord record(){return AuditRecordHasher.record(AuditCheckpoint.genesis(event().stream()),event(),Instant.ofEpochMilli(123456789));}
    @Test void fullKernelEventAndNanosecondFieldsRoundTripThroughMappedRows(){var row=AuditRows.Event.from(record());assertEquals(record(),row.record());assertEquals(987654321,row.occurredNanos());}
    @Test void firstAppendUsesExactlyThreeAffectedRowCountsAndReadback(){
        var f=new AuditTestFixture();var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);var r=record();
        var done=f.execute(scope->store.append(scope,AuditCheckpoint.genesis(r.event().stream()),r)).block();assertEquals(r,done.value());assertEquals(3,f.driver.writes);assertEquals(3,f.driver.affectedObservations);assertEquals(1,f.driver.commits);
        assertEquals(1,f.driver.tables.get("audit_event").size());assertEquals(1,f.driver.tables.get("audit_outbox").size());
    }
    @Test void secondAppendUsesOriginalHeadVersionAndHashForCas(){
        var f=new AuditTestFixture();var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);var first=record();var next=new AuditEvent(id(9),event().stream(),event().actor(),event().action(),event().outcome(),event().processId(),event().correlationId(),null,event().occurredAt(),event().evidenceDigest(),event().policyRevision());var prior=new AuditCheckpoint(event().stream(),1,first.recordHash());var second=AuditRecordHasher.record(prior,next,first.recordedAt().plusMillis(1));
        assertDoesNotThrow(()->f.execute(scope->store.append(scope,AuditCheckpoint.genesis(event().stream()),first).then(store.append(scope,prior,second))).block(),()->String.valueOf(f.workFailure)+f.driver.sql);assertEquals(6,f.driver.writes);int update=java.util.stream.IntStream.range(0,f.driver.sql.size()).filter(i->f.driver.sql.get(i).startsWith("UPDATE")).findFirst().orElseThrow();assertEquals(List.of(2L,second.recordHash(),event().stream().resourceTenantId(),"payments","transfer",event().stream().resourceId(),1L,first.recordHash()),new ArrayList<>(f.driver.bindings.get(update).values()));assertEquals(2,f.execute(scope->store.head(scope,event().stream())).block().value().lastSequence());
    }
    @Test void fullEventAndPermanentOutboxAreRequiredForReplayRead(){
        var f=new AuditTestFixture();var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);f.execute(scope->store.append(scope,AuditCheckpoint.genesis(event().stream()),record())).block();assertEquals(record(),f.execute(scope->store.find(scope,event().stream().resourceTenantId(),event().eventId())).block().value());
        f.driver.tables.get("audit_outbox").clear();assertThrows(RuntimeException.class,()->f.execute(scope->store.find(scope,event().stream().resourceTenantId(),event().eventId())).block());
    }
    @Test void zeroAndTwoAffectedRowsAbortBeforeLaterWrites(){
        for(long count:new long[]{0,2})for(int position:new int[]{1,2,3}){var f=new AuditTestFixture();f.driver.writeCount=count;f.driver.countAtWrite=position;var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);assertThrows(RuntimeException.class,()->f.execute(scope->store.append(scope,AuditCheckpoint.genesis(event().stream()),record())).block());assertEquals(position,f.driver.writes);assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);}
    }
    @Test void missingAffectedRowObservationIsNeverSuccess(){for(int position:new int[]{1,2,3}){var f=new AuditTestFixture();f.driver.emptyCount=true;f.driver.countAtWrite=position;var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);assertThrows(RuntimeException.class,()->f.execute(scope->store.append(scope,AuditCheckpoint.genesis(event().stream()),record())).block());assertEquals(position,f.driver.writes);assertEquals(1,f.driver.rollbacks);}}
    @Test void duplicateFailureHasNoLaterReadOrOutboxWrite(){var f=new AuditTestFixture();f.driver.failWrite=2;var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);assertThrows(RuntimeException.class,()->f.execute(scope->store.append(scope,AuditCheckpoint.genesis(event().stream()),record())).block());assertEquals(2,f.driver.writes);assertEquals(0,f.driver.selects);assertEquals(1,f.driver.rollbacks);assertTrue(f.driver.tables.isEmpty());}
    @Test void zeroOrTwoHeadCasRowsAbortWithoutTheNextEventOrOutbox(){for(long count:new long[]{0,2}){var f=new AuditTestFixture();f.driver.countAtWrite=4;f.driver.writeCount=count;var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);var first=record();var prior=new AuditCheckpoint(event().stream(),1,first.recordHash());var e=event();var next=new AuditEvent(id(9),e.stream(),e.actor(),e.action(),e.outcome(),e.processId(),e.correlationId(),null,e.occurredAt(),e.evidenceDigest(),e.policyRevision());var second=AuditRecordHasher.record(prior,next,first.recordedAt().plusMillis(1));assertThrows(RuntimeException.class,()->f.execute(scope->store.append(scope,AuditCheckpoint.genesis(e.stream()),first).then(store.append(scope,prior,second))).block());assertEquals(4,f.driver.writes);assertEquals(1,f.driver.rollbacks);assertTrue(f.driver.tables.isEmpty());}}
    @Test void lowLevelRepositoryCannotUseAnUnqualifiedOrdinaryAmbientTransaction(){var f=new AuditTestFixture();var driver=new AuditTestFixture.Driver();var client=org.springframework.r2dbc.core.DatabaseClient.create(driver);var template=new org.springframework.data.r2dbc.core.R2dbcEntityTemplate(client,org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE);assertThrows(IllegalArgumentException.class,()->new SpringDataLocalAuditRepository(f.resources,client,template,new org.springframework.r2dbc.connection.R2dbcTransactionManager(driver)));assertEquals(0,driver.writes);assertEquals(0,driver.creates);}
    @Test void noAmbientTransactionRefusesAllPublicStoreOperationsBeforeMappedStatements(){var f=new AuditTestFixture();var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);for(var operation:List.of(store.head(null,event().stream()),store.find(null,event().stream().resourceTenantId(),event().eventId()),store.append(null,AuditCheckpoint.genesis(event().stream()),record())))assertThrows(RuntimeException.class,operation::block);assertEquals(0,f.driver.creates);assertTrue(f.driver.sql.isEmpty());}
    @Test void allPublicStoreOperationsRefuseAForeignRealScopeBeforeSql(){var owner=new AuditTestFixture();var foreign=new AuditTestFixture();var store=new SpringDataLocalAuditRepository(foreign.resources,foreign.client,foreign.template,foreign.manager);for(int operation:new int[]{0,1,2})assertThrows(RuntimeException.class,()->owner.execute(scope->operation==0?store.head(scope,event().stream()).map(Object.class::cast):operation==1?store.find(scope,event().stream().resourceTenantId(),event().eventId()).map(Object.class::cast):store.append(scope,AuditCheckpoint.genesis(event().stream()),record()).map(Object.class::cast)).block());assertTrue(foreign.driver.sql.isEmpty());assertEquals(0,foreign.driver.creates);}
    @Test void allPublicStoreOperationsRefuseACompletedOriginalScopeBeforeSql(){var f=new AuditTestFixture();var retained=new java.util.concurrent.atomic.AtomicReference<org.fireflyframework.core.transaction.RootTransactionScope>();f.execute(scope->{retained.set(scope);return Mono.just("closed");}).block();var store=new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager);for(var operation:List.of(store.head(retained.get(),event().stream()),store.find(retained.get(),event().stream().resourceTenantId(),event().eventId()),store.append(retained.get(),AuditCheckpoint.genesis(event().stream()),record())))assertThrows(RuntimeException.class,operation::block);assertEquals(1,f.driver.creates);assertTrue(f.driver.sql.isEmpty());}

}
