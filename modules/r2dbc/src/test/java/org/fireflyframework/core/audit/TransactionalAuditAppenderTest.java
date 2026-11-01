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
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.core.audit.impl.SpringDataTransactionalAuditAppenderImpl;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class TransactionalAuditAppenderTest {
    AuditEvent event(){return LocalAuditRepositoryTest.event();}
    AuditAppendAuthority authority(AtomicInteger calls){return e->{calls.incrementAndGet();return Mono.just(new AuditAppendAuthority.Permit(e.stream(),e.actor(),Instant.now().plusSeconds(60)));};}
    SpringDataTransactionalAuditAppenderImpl appender(AuditTestFixture f,AuditAppendAuthority authority){return new SpringDataTransactionalAuditAppenderImpl(f.resources,f.client,f.template,f.manager,authority,f.clock,f.nanos,UUID::randomUUID);}
    @org.springframework.data.relational.core.mapping.Table("audit_test_business") record Business(@org.springframework.data.annotation.Id UUID id,long version) { }
    @Test void mappedBusinessAndAuditOutboxCommitOnTheSameExistingLease(){var f=new AuditTestFixture();var service=appender(f,authority(new AtomicInteger()));var done=f.execute(scope->f.template.insert(new Business(UUID.randomUUID(),1)).then(service.append(scope,event()))).block();assertTrue(done.transactionFacts().commitAcknowledged());assertEquals(1,f.driver.creates);for(String table:List.of("audit_test_business","audit_event","audit_outbox"))assertEquals(1,f.driver.tables.get(table).size());}
    @Test void caughtZeroTwoOrMissingMappedCountStillCannotCommitBusinessEffect(){
        for(int scenario:new int[]{0,1,2}){var f=new AuditTestFixture();var service=appender(f,authority(new AtomicInteger()));var done=f.execute(scope->f.template.insert(new Business(UUID.randomUUID(),1)).then(Mono.defer(()->{f.driver.writeCount=scenario==1?2:scenario==0?0:1;f.driver.emptyCount=scenario==2;return service.append(scope,event());})).thenReturn("business").onErrorReturn("caught")).block();assertFalse(done.transactionFacts().commitAcknowledged());assertTrue(done.transactionFacts().rollbackAcknowledged());assertEquals(1,f.driver.rollbacks);assertTrue(f.driver.tables.isEmpty());assertEquals(0,f.driver.nativeCompletions);}
    }
    @Test void caughtMandatoryAppendRefusalStillRollsBackTheOwningBusinessTransaction(){var f=new AuditTestFixture();var service=appender(f,e->Mono.empty());var done=f.execute(scope->f.template.insert(new Business(UUID.randomUUID(),1)).then(service.append(scope,event())).thenReturn("business-value").onErrorReturn("caught-refusal")).block();assertFalse(done.transactionFacts().commitAcknowledged());assertTrue(done.transactionFacts().rollbackAcknowledged());assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);assertTrue(f.driver.tables.isEmpty());}
    @Test void mandatoryAuthorityHasNoSuccessfulFallbackOrAutomaticBean(){var f=new AuditTestFixture();assertThrows(NullPointerException.class,()->appender(f,null));assertEquals(0,f.driver.creates);}
    @Test void actualMappedNativeAndAuditOperationsUseOnlyTheOriginalLeaseAndReturnProvisionalEvidence(){
        var f=new AuditTestFixture();var calls=new AtomicInteger();var service=appender(f,authority(calls));var done=assertDoesNotThrow(()->f.execute(scope->service.append(scope,event()).doOnNext(provisional->{assertEquals(0,f.driver.commits);assertEquals(event(),provisional.record().event());assertFalse(provisional.replay());})).block(),()->String.valueOf(f.workFailure)+f.driver.sql);
        assertTrue(done.transactionFacts().commitAcknowledged());assertTrue(done.transactionFacts().boundaryCompleted());assertEquals(1,f.driver.creates);assertEquals(1,f.driver.closes);assertEquals(1,f.driver.nativeEnters);assertEquals(1,f.driver.nativeCompletions);assertEquals(2,calls.get());
    }
    @Test void sequentialEventsUseTheSameOriginalLeaseAndAdvanceTheStreamHead(){var f=new AuditTestFixture();var service=appender(f,authority(new AtomicInteger()));var e=event();var next=new AuditEvent(UUID.randomUUID(),e.stream(),e.actor(),e.action(),e.outcome(),e.processId(),e.correlationId(),e.causationId(),e.occurredAt(),e.evidenceDigest(),e.policyRevision());var done=f.execute(scope->service.append(scope,e).then(service.append(scope,next))).block();assertEquals(2,done.value().record().sequence());assertTrue(done.transactionFacts().commitAcknowledged());assertEquals(1,f.driver.creates);assertEquals(2,f.driver.nativeEnters);assertEquals(2,f.driver.nativeCompletions);assertEquals(2,f.driver.tables.get("audit_event").size());assertEquals(2,f.driver.tables.get("audit_outbox").size());}
    @Test void repeatedFullContentEventReturnsOriginalRecordWithoutNewMutation(){var f=new AuditTestFixture();var service=appender(f,authority(new AtomicInteger()));var first=f.execute(scope->service.append(scope,event())).block().value();int writes=f.driver.writes;var second=f.execute(scope->service.append(scope,event())).block().value();assertTrue(second.replay());assertEquals(first.record(),second.record());assertEquals(writes,f.driver.writes);}
    @Test void eventIdentityWithDifferentFullContentRefusesWithoutMutation(){var f=new AuditTestFixture();var service=appender(f,authority(new AtomicInteger()));f.execute(scope->service.append(scope,event())).block();int writes=f.driver.writes;var e=event();var changed=new AuditEvent(e.eventId(),e.stream(),e.actor(),"TRANSFER_FAILED",e.outcome(),e.processId(),e.correlationId(),e.causationId(),e.occurredAt(),e.evidenceDigest(),e.policyRevision());assertThrows(RuntimeException.class,()->f.execute(scope->service.append(scope,changed)).block());assertEquals(writes,f.driver.writes);}
    @Test void unavailableCurrentAuthorityNeverEntersNativeOrWrites(){var f=new AuditTestFixture();var service=appender(f,e->Mono.empty());assertThrows(RuntimeException.class,()->f.execute(scope->service.append(scope,event())).block());assertEquals(0,f.driver.nativeEnters);assertEquals(0,f.driver.writes);assertEquals(1,f.driver.rollbacks);}
    @Test void changedCurrentActorWithholdsAppendAndRollsBackAllAuditRows(){var f=new AuditTestFixture();var calls=new AtomicInteger();var service=appender(f,e->{var actor=calls.incrementAndGet()==1?e.actor():new AuditActor("https://issuer.test","other-staff","staff-app",e.actor().platformTenantId(),null,null);return Mono.just(new AuditAppendAuthority.Permit(e.stream(),actor,Instant.now().plusSeconds(60)));});assertThrows(RuntimeException.class,()->f.execute(scope->service.append(scope,event())).block());assertEquals(1,f.driver.rollbacks);assertTrue(f.driver.tables.isEmpty());assertEquals(0,f.driver.nativeCompletions);}
    @Test void ownerFailureAfterProvisionalAppendRollsBackEvidenceAndOutbox(){var f=new AuditTestFixture();var service=appender(f,authority(new AtomicInteger()));assertThrows(RuntimeException.class,()->f.execute(scope->service.append(scope,event()).then(Mono.error(new IllegalStateException("ordinary business rollback")))).block());assertTrue(f.driver.tables.isEmpty());assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);}
    @Test void shorterLiveCurrentPermitKeepsTheOriginalCapAndEarlierExpiry(){
        for(var validity:List.of(Duration.ofSeconds(60),Duration.ofMillis(500))){
            var f=new AuditTestFixture();var calls=new AtomicInteger();
            AuditAppendAuthority current=e->Mono.just(new AuditAppendAuthority.Permit(e.stream(),e.actor(),f.clock.instant().plus(calls.incrementAndGet()==1?Duration.ofSeconds(180):validity)));
            var service=appender(f,current);var original=new java.util.concurrent.atomic.AtomicReference<Duration>();
            var done=assertDoesNotThrow(()->f.execute(scope->{original.set(scope.remaining());return service.append(scope,event()).doOnNext(provisional->{
                assertTrue(scope.remaining().compareTo(original.get())<=0);assertTrue(scope.remaining().compareTo(validity)<=0);
            });}).block());
            assertTrue(done.transactionFacts().commitAcknowledged());assertTrue(done.transactionFacts().boundaryCompleted());
            assertEquals(2,calls.get());assertEquals(1,f.driver.commits);assertEquals(1,f.driver.nativeCompletions);
        }
    }
    @Test void originalExpiredDeadlineRefusesBeforeAuditStatements(){var f=new AuditTestFixture();var service=appender(f,authority(new AtomicInteger()));assertThrows(RuntimeException.class,()->f.execute(scope->{scope.narrow(org.fireflyframework.core.transaction.R2dbcOperationBudget.observed(Instant.now().minusSeconds(2),Instant.now().minusSeconds(1),System.nanoTime(),Duration.ofSeconds(1),Clock.systemUTC(),System::nanoTime));return service.append(scope,event());}).block());assertEquals(0,f.driver.writes);assertEquals(0,f.driver.nativeEnters);}
    @Test void aDifferentMonotonicSupplierCannotReplaceTheOriginalBudgetPair(){var f=new AuditTestFixture();var service=new SpringDataTransactionalAuditAppenderImpl(f.resources,f.client,f.template,f.manager,authority(new AtomicInteger()),f.clock,System::nanoTime,UUID::randomUUID);assertThrows(RuntimeException.class,()->f.execute(scope->service.append(scope,event())).block());assertEquals(0,f.driver.writes);assertEquals(1,f.driver.rollbacks);}
    @Test void missingScopeOutsideOwnerRootRefusesBeforeCurrentAuthorityOrAcquisition(){var f=new AuditTestFixture();var calls=new AtomicInteger();var service=appender(f,authority(calls));assertThrows(RuntimeException.class,()->service.append(null,event()).block());assertEquals(0,calls.get());assertEquals(0,f.driver.creates);}
}
