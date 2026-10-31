package org.fireflyframework.core.audit;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.*;
import org.fireflyframework.core.audit.impl.SpringDataLocalAuditRepository;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** Controlled mapped driver only; these tests do not execute native SQL or create authority. */
public class CompletedAuditAppendServiceTest {
    static final UUID NONCE=new UUID(0,81);
    static final String FUNCTION="firefly_audit_require_completed";
    static Map<String,Object> row(Object value)throws Exception {
        var result=new LinkedHashMap<String,Object>();
        for(var component:value.getClass().getRecordComponents())result.put(component.getName().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),component.getAccessor().invoke(value));
        return result;
    }
    static AuditTestFixture fixture()throws Exception {
        var f=new AuditTestFixture();var record=LocalAuditRepositoryTest.record();
        f.driver.tables.put("audit_event",new ArrayList<>(List.of(row(AuditRows.Event.from(record)))));
        f.driver.tables.put("audit_outbox",new ArrayList<>(List.of(row(AuditRows.Outbox.from(record)))));
        return f;
    }
    @SuppressWarnings("unchecked")
    static Mono<AuditRecord> read(AuditTestFixture f,RootTransactionScope scope,UUID nonce,TransactionalAuditAppender.ProvisionalAppend expected) {
        try {
            for(var row:f.driver.tables.getOrDefault(FUNCTION,List.of())){
                if(!row.containsKey("context_nonce"))row.put("context_nonce",NONCE);
                if(!row.containsKey("db_now_millis"))row.put("db_now_millis",f.clock.instant().toEpochMilli());
                if(!row.containsKey("until_millis"))row.put("until_millis",f.clock.instant().plusSeconds(5).toEpochMilli());
                if(!row.containsKey("replay"))row.put("replay",expected.replay());
            }
            var type=Class.forName("org.fireflyframework.core.audit.impl.SpringDataCompletedAuditAppendService");
            var service=type.getConstructor(BoundedR2dbcResources.class,org.springframework.r2dbc.core.DatabaseClient.class,
                    org.springframework.data.r2dbc.core.R2dbcEntityTemplate.class,org.springframework.r2dbc.connection.R2dbcTransactionManager.class,java.time.Clock.class,java.util.function.LongSupplier.class)
                    .newInstance(f.resources,f.client,f.template,f.manager,f.clock,f.nanos);
            return (Mono<AuditRecord>)type.getMethod("requireCompleted",RootTransactionScope.class,UUID.class,TransactionalAuditAppender.ProvisionalAppend.class).invoke(service,scope,nonce,expected);
        }catch(ClassNotFoundException predecessor){
            return new SpringDataLocalAuditRepository(f.resources,f.client,f.template,f.manager).find(scope,expected.record().event().stream().resourceTenantId(),expected.record().event().eventId());
        }catch(InvocationTargetException error){return Mono.error(error.getCause());}
        catch(Exception error){return Mono.error(error);}
    }
    static TransactionalAuditAppender.ProvisionalAppend expected(){return new TransactionalAuditAppender.ProvisionalAppend(LocalAuditRepositoryTest.record(),false);}
    @Test void anOrdinaryRetainedRowIsNotProofOfCompletedNativeContextEvenWhenFailureIsCaught()throws Exception {
        var f=fixture();var done=f.execute(scope->read(f,scope,NONCE,expected()).onErrorReturn(LocalAuditRepositoryTest.record())).block();
        assertTrue(done.transactionFacts().rollbackAcknowledged());assertFalse(done.transactionFacts().commitAcknowledged());
    }
    @Test void typedReadUsesExactOriginalNonceEventSequenceHashAndReplayOnTheOriginalLease()throws Exception {
        var f=fixture();f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(LocalAuditRepositoryTest.record())))));
        var done=f.execute(scope->read(f,scope,NONCE,expected())).block();assertEquals(LocalAuditRepositoryTest.record(),done.value());assertTrue(done.transactionFacts().commitAcknowledged());
        int at=java.util.stream.IntStream.range(0,f.driver.sql.size()).filter(i->f.driver.sql.get(i).contains(FUNCTION)).findFirst().orElse(-1);assertTrue(at>=0,"A completed-context function is required, not an ordinary retained-row read");
        assertEquals(List.of(NONCE,LocalAuditRepositoryTest.event().eventId(),1L,LocalAuditRepositoryTest.record().recordHash(),false),new ArrayList<>(f.driver.bindings.get(at).values()));assertEquals(1,f.driver.creates);assertEquals(0,f.driver.writes);
    }
    @Test void callerExpectedFullRecordCannotBeReplacedWithAnotherRetainedRecord()throws Exception {
        var f=fixture();f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(LocalAuditRepositoryTest.record())))));
        var record=LocalAuditRepositoryTest.record();var event=record.event();var actor=new AuditActor(event.actor().issuer(),event.actor().subject(),"other-client",event.actor().platformTenantId(),event.actor().staffRealm(),event.actor().staffId());
        var changed=new AuditEvent(event.eventId(),event.stream(),actor,event.action(),event.outcome(),event.processId(),event.correlationId(),event.causationId(),event.occurredAt(),event.evidenceDigest(),event.policyRevision());
        var want=AuditRecordHasher.record(AuditCheckpoint.genesis(changed.stream()),changed,record.recordedAt());
        var done=f.execute(scope->read(f,scope,NONCE,new TransactionalAuditAppender.ProvisionalAppend(want,false)).onErrorReturn(record)).block();assertTrue(done.transactionFacts().rollbackAcknowledged());assertEquals(0,f.driver.commits);
    }
    @Test void missingOwningRootCannotReadAnyMappedOrNativeRow()throws Exception {var f=fixture();assertThrows(RuntimeException.class,()->read(f,null,NONCE,expected()).block());assertEquals(0,f.driver.creates);assertTrue(f.driver.sql.isEmpty());}
    static void caughtRefuses(AuditTestFixture f,UUID nonce,TransactionalAuditAppender.ProvisionalAppend expected){
        var done=f.execute(scope->Mono.defer(()->read(f,scope,nonce,expected)).onErrorReturn(LocalAuditRepositoryTest.record())).block();
        assertTrue(done.transactionFacts().rollbackAcknowledged());assertEquals(0,f.driver.commits);
    }
    @Test void twoCompletedRowsCannotBeSilentlySelected()throws Exception {var f=fixture();var row=row(AuditRows.Event.from(LocalAuditRepositoryTest.record()));f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row,row)));caughtRefuses(f,NONCE,expected());}
    @Test void nativeReadErrorMarksTheOriginalRootRollbackOnlyEvenIfCallerCatchesIt()throws Exception {var f=fixture();f.driver.readFailure=new IllegalStateException("Controlled native read refusal");caughtRefuses(f,NONCE,expected());}
    @Test void invalidNativeRecordHashIsNotAnImmutableAppendWitness()throws Exception {var f=fixture();var row=row(AuditRows.Event.from(LocalAuditRepositoryTest.record()));row.put("record_hash","b".repeat(64));f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row)));caughtRefuses(f,NONCE,expected());}
    @Test void zeroNonceAndMissingExpectedRecordRefuseBeforeNativeOrMappedRead()throws Exception {
        for(var nonce:Arrays.asList(null,new UUID(0,0))){var f=fixture();caughtRefuses(f,nonce,expected());assertTrue(f.driver.sql.isEmpty());}
        var f=fixture();caughtRefuses(f,NONCE,new TransactionalAuditAppender.ProvisionalAppend(null,false));assertTrue(f.driver.sql.isEmpty());
    }
    @Test void nullPrimitiveProjectionCannotBecomeAValidEpochZeroRecord()throws Exception {
        var e=LocalAuditRepositoryTest.event();var epoch=new AuditEvent(e.eventId(),e.stream(),e.actor(),e.action(),e.outcome(),e.processId(),e.correlationId(),e.causationId(),Instant.EPOCH,e.evidenceDigest(),e.policyRevision());
        var expected=AuditRecordHasher.record(AuditCheckpoint.genesis(epoch.stream()),epoch,Instant.EPOCH);
        for(var field:List.of("sequence","recorded_seconds","recorded_nanos","occurred_seconds","occurred_nanos")){
            var f=fixture();var row=row(AuditRows.Event.from(expected));row.put(field,null);f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row)));caughtRefuses(f,NONCE,new TransactionalAuditAppender.ProvisionalAppend(expected,false));
        }
    }
    @Test void sameFunctionCannotReadUsingAForeignOwningScope()throws Exception {var f=fixture();var foreign=fixture();assertThrows(RuntimeException.class,()->f.execute(scope->read(foreign,scope,NONCE,expected())).block());assertEquals(0,foreign.driver.creates);assertTrue(foreign.driver.sql.isEmpty());}
    @Test void originalReplayFlagIsPassedWithoutChangingTheRecord()throws Exception {
        var f=fixture();f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(LocalAuditRepositoryTest.record())))));
        var done=f.execute(scope->read(f,scope,NONCE,new TransactionalAuditAppender.ProvisionalAppend(LocalAuditRepositoryTest.record(),true))).block();assertEquals(LocalAuditRepositoryTest.record(),done.value());
        var at=java.util.stream.IntStream.range(0,f.driver.sql.size()).filter(i->f.driver.sql.get(i).contains(FUNCTION)).findFirst().orElse(-1);assertTrue(at>=0);assertEquals(true,f.driver.bindings.get(at).get(4));
    }
    static final class Time extends java.time.Clock {
        Instant instant=Instant.parse("2026-01-01T00:00:00Z");long nanos=10;
        public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}public java.time.Clock withZone(java.time.ZoneId zone){return this;}public Instant instant(){return instant;}
        void advance(){instant=instant.plusSeconds(6);nanos+=java.time.Duration.ofSeconds(6).toNanos();}
    }
    @Test void elapsedReadCannotEmitAWitnessAfterOriginalBudgetExpiry()throws Exception {
        var time=new Time();var f=new AuditTestFixture(time,()->time.nanos);f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(LocalAuditRepositoryTest.record())))));
        f.driver.tables.put("audit_event",new ArrayList<>(List.of(row(AuditRows.Event.from(LocalAuditRepositoryTest.record())))));f.driver.tables.put("audit_outbox",new ArrayList<>(List.of(row(AuditRows.Outbox.from(LocalAuditRepositoryTest.record())))));
        f.driver.beforeRead=time::advance;var delivered=new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(RuntimeException.class,()->f.execute(scope->read(f,scope,NONCE,expected()).doOnNext(value->delivered.set(true))).block());assertFalse(delivered.get());assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);
    }

    @Test void nullableNativeContextFieldsAndChangedNonceOrReplayCannotBecomeProof()throws Exception {
        for(var field:List.of("context_nonce","db_now_millis","until_millis","replay")){
            var f=fixture();var row=row(AuditRows.Event.from(LocalAuditRepositoryTest.record()));row.put(field,null);f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row)));caughtRefuses(f,NONCE,expected());
        }
        for(var field:List.of("context_nonce","replay")){
            var f=fixture();var row=row(AuditRows.Event.from(LocalAuditRepositoryTest.record()));row.put(field,field.equals("replay")?true:new UUID(0,82));f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row)));caughtRefuses(f,NONCE,expected());
        }
    }
    @Test void rawNanosecondFieldsCannotBeNormalizedIntoDifferentNativeValues()throws Exception {
        for(var field:List.of("recorded_nanos","occurred_nanos"))for(int value:new int[]{-1,1000000000}){
            var f=fixture();var row=row(AuditRows.Event.from(LocalAuditRepositoryTest.record()));row.put(field,value);f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row)));caughtRefuses(f,NONCE,expected());
        }
    }
    @Test void nativeWindowOnlyNarrowsTheSameOriginalScope()throws Exception {
        var f=fixture();var row=row(AuditRows.Event.from(LocalAuditRepositoryTest.record()));row.put("until_millis",f.clock.instant().plusSeconds(2).toEpochMilli());f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row)));
        var done=f.execute(scope->read(f,scope,NONCE,expected()).doOnNext(value->assertTrue(scope.remaining().compareTo(java.time.Duration.ofSeconds(2))<0))).block();assertEquals(LocalAuditRepositoryTest.record(),done.value());
    }
    @Test void aDifferentTimeSupplierCannotSubstituteForTheOriginalBudget()throws Exception {
        var f=fixture();f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(LocalAuditRepositoryTest.record())))));
        var type=Class.forName("org.fireflyframework.core.audit.impl.SpringDataCompletedAuditAppendService");
        var service=type.getConstructor(BoundedR2dbcResources.class,org.springframework.r2dbc.core.DatabaseClient.class,org.springframework.data.r2dbc.core.R2dbcEntityTemplate.class,org.springframework.r2dbc.connection.R2dbcTransactionManager.class,java.time.Clock.class,java.util.function.LongSupplier.class)
            .newInstance(f.resources,f.client,f.template,f.manager,f.clock,(java.util.function.LongSupplier)()->f.nanos.getAsLong());
        var done=f.execute(scope->{var row=f.driver.tables.get(FUNCTION).getFirst();row.put("context_nonce",NONCE);row.put("replay",false);row.put("db_now_millis",f.clock.instant().toEpochMilli());row.put("until_millis",f.clock.instant().plusSeconds(5).toEpochMilli());
            try{@SuppressWarnings("unchecked")var result=(Mono<AuditRecord>)type.getMethod("requireCompleted",RootTransactionScope.class,UUID.class,TransactionalAuditAppender.ProvisionalAppend.class).invoke(service,scope,NONCE,expected());return result.onErrorReturn(LocalAuditRepositoryTest.record());}catch(Exception error){return Mono.error(error);}}).block();
        assertTrue(done.transactionFacts().rollbackAcknowledged());assertEquals(0,f.driver.commits);
    }
    @Test void anotherTemplateOnTheSameFactoryCannotReplaceTheRetainedRootGraph()throws Exception {
        var f=fixture();var client=org.springframework.r2dbc.core.DatabaseClient.create(f.factory);var template=new org.springframework.data.r2dbc.core.R2dbcEntityTemplate(client,org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE);
        var type=Class.forName("org.fireflyframework.core.audit.impl.SpringDataCompletedAuditAppendService");var service=type.getConstructor(BoundedR2dbcResources.class,org.springframework.r2dbc.core.DatabaseClient.class,org.springframework.data.r2dbc.core.R2dbcEntityTemplate.class,org.springframework.r2dbc.connection.R2dbcTransactionManager.class,java.time.Clock.class,java.util.function.LongSupplier.class).newInstance(f.resources,client,template,f.manager,f.clock,f.nanos);
        assertThrows(RuntimeException.class,()->f.execute(scope->{try{@SuppressWarnings("unchecked")var result=(Mono<AuditRecord>)type.getMethod("requireCompleted",RootTransactionScope.class,UUID.class,TransactionalAuditAppender.ProvisionalAppend.class).invoke(service,scope,NONCE,expected());return result;}catch(Exception error){return Mono.error(error);}}).block());assertTrue(f.driver.sql.isEmpty());
    }
    public static final class HashingDelay extends java.security.MessageDigestSpi {
        static java.security.Provider delegate;static Runnable duringHash;
        private final java.security.MessageDigest digest;
        public HashingDelay(){try{digest=java.security.MessageDigest.getInstance("SHA-256",delegate);}catch(java.security.GeneralSecurityException error){throw new IllegalStateException(error);}}
        @Override protected void engineUpdate(byte value){digest.update(value);}
        @Override protected void engineUpdate(byte[] value,int offset,int length){digest.update(value,offset,length);}
        @Override protected byte[] engineDigest(){var bytes=digest.digest();if(StackWalker.getInstance().walk(frames->frames.anyMatch(frame->frame.getClassName().equals(AuditRecordHasher.class.getName()))))duringHash.run();return bytes;}
        @Override protected void engineReset(){digest.reset();}
    }
    @Test void timeSpentInTheActualCanonicalHasherCannotEscapeTheOriginalWindow()throws Exception {
        var time=new Time();var f=new AuditTestFixture(time,()->time.nanos);var wanted=expected();f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(wanted.record())))));var emitted=new java.util.concurrent.atomic.AtomicBoolean();
        HashingDelay.delegate=java.security.MessageDigest.getInstance("SHA-256").getProvider();HashingDelay.duringHash=time::advance;
        var provider=new java.security.Provider("ControlledAuditHashDelay","1","Controlled source-only genuine SHA-256 delegation"){private static final long serialVersionUID=1L;{put("MessageDigest.SHA-256",HashingDelay.class.getName());}};
        java.security.Security.insertProviderAt(provider,1);
        try{assertThrows(RuntimeException.class,()->f.execute(scope->read(f,scope,NONCE,wanted).doOnNext(value->emitted.set(true))).block());assertFalse(emitted.get());assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);}
        finally{java.security.Security.removeProvider(provider.getName());HashingDelay.duringHash=()->{};}
    }

    static AuditEvent changed(AuditEvent value,String name,Object replacement)throws Exception {
        var components=AuditEvent.class.getRecordComponents();var arguments=new Object[components.length];var types=new Class<?>[components.length];
        for(int i=0;i<components.length;i++){types[i]=components[i].getType();arguments[i]=components[i].getName().equals(name)?replacement:components[i].getAccessor().invoke(value);}
        return AuditEvent.class.getConstructor(types).newInstance(arguments);
    }
    @Test void canonicalButDifferentFullEventOrRecordCannotSatisfyExpectedEquality()throws Exception {
        var original=LocalAuditRepositoryTest.record();var event=original.event();var variants=new ArrayList<AuditRecord>();
        for(var change:Map.<String,Object>of("action","TRANSFER_FAILED","outcome",AuditEvent.Outcome.REJECTED,"processId",new UUID(0,101),
                "correlationId",new UUID(0,102),"causationId",new UUID(0,103),"occurredAt",event.occurredAt().plusNanos(1),
                "evidenceDigest","b".repeat(64),"policyRevision","policy-2","stream",new AuditStream(new UUID(0,104),"another-owner","transfer",event.stream().resourceId())).entrySet()){
            var altered=changed(event,change.getKey(),change.getValue());variants.add(AuditRecordHasher.record(AuditCheckpoint.genesis(altered.stream()),altered,original.recordedAt()));
        }
        variants.add(AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,original.recordedAt().plusNanos(1)));
        variants.add(AuditRecordHasher.record(new AuditCheckpoint(event.stream(),1,"b".repeat(64)),event,original.recordedAt()));
        for(var actual:variants){var f=fixture();f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(actual)))));caughtRefuses(f,NONCE,expected());}
    }

    @Test void zeroOrNegativeSequenceIsNotACompletedAppend()throws Exception {
        for(long sequence:new long[]{0,-1}){var f=fixture();var row=row(AuditRows.Event.from(LocalAuditRepositoryTest.record()));row.put("sequence",sequence);f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row)));caughtRefuses(f,NONCE,expected());}
    }
    @Test void aCompletedOriginalRootCannotReadAnotherNativeContext()throws Exception {
        var f=fixture();var retained=new java.util.concurrent.atomic.AtomicReference<RootTransactionScope>();f.execute(scope->{retained.set(scope);return Mono.just("closed");}).block();
        assertThrows(RuntimeException.class,()->read(f,retained.get(),NONCE,expected()).block());assertEquals(1,f.driver.creates);assertTrue(f.driver.sql.isEmpty());
    }

    @Test void genericFiniteNativeWindowDoesNotInheritAnOwnerYear9999Limit()throws Exception {
        var time=new Time();time.instant=Instant.parse("+10000-01-01T00:00:00Z");var f=new AuditTestFixture(time,()->time.nanos);f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(LocalAuditRepositoryTest.record())))));
        var done=f.execute(scope->read(f,scope,NONCE,new TransactionalAuditAppender.ProvisionalAppend(LocalAuditRepositoryTest.record(),true))).block();assertEquals(LocalAuditRepositoryTest.record(),done.value());assertTrue(done.transactionFacts().commitAcknowledged());
    }

    @Test void existingNonnullZeroEventIdentityRemainsReadableAfterCompletion()throws Exception {
        var original=LocalAuditRepositoryTest.record();var e=original.event();var event=new AuditEvent(new UUID(0,0),e.stream(),e.actor(),e.action(),e.outcome(),e.processId(),e.correlationId(),e.causationId(),e.occurredAt(),e.evidenceDigest(),e.policyRevision());
        var record=AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,original.recordedAt());assertEquals(record,AuditRows.Event.from(record).record());
        var f=fixture();f.driver.tables.put(FUNCTION,new ArrayList<>(List.of(row(AuditRows.Event.from(record)))));
        var done=f.execute(scope->read(f,scope,NONCE,new TransactionalAuditAppender.ProvisionalAppend(record,false))).block();assertEquals(record,done.value());assertEquals(new UUID(0,0),done.value().event().eventId());assertTrue(done.transactionFacts().commitAcknowledged());
        var index=java.util.stream.IntStream.range(0,f.driver.sql.size()).filter(i->f.driver.sql.get(i).contains(FUNCTION)).findFirst().orElseThrow();assertEquals(new UUID(0,0),f.driver.bindings.get(index).get(1));assertEquals(0,f.driver.writes);
    }

}
