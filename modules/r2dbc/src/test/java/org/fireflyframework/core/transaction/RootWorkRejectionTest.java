package org.fireflyframework.core.transaction;

import io.r2dbc.spi.*;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import reactor.util.context.ContextView;
import static org.junit.jupiter.api.Assertions.*;

class RootWorkRejectionTest {
    static final class ExpectedRejection extends RuntimeException {
        private static final long serialVersionUID=1L;
        ExpectedRejection(){super("PRIVATE-REJECTION-CONTENT");}
    }
    static final class Driver implements ConnectionFactory {
        final BoundedTransactionBoundaryTest.Driver original=new BoundedTransactionBoundaryTest.Driver();
        final Sinks.Empty<Void> rollbackGate=Sinks.empty(),closeGate=Sinks.empty();
        boolean holdRollback,holdClose;
        final Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("rollbackTransaction"))return Mono.from(original.connection.rollbackTransaction()).then(holdRollback?rollbackGate.asMono():Mono.empty());
            if(method.getName().equals("close"))return Mono.from(original.connection.close()).then(holdClose?closeGate.asMono():Mono.empty());
            try{return method.invoke(original.connection,args);}catch(InvocationTargetException error){throw error.getCause();}
        });
        public Publisher<? extends Connection> create(){return Mono.from(original.create()).map(ignored->connection);}
        public ConnectionFactoryMetadata getMetadata(){return original.getMetadata();}
    }
    static final class Fixture {
        final BoundedTransactionBoundaryTest.Time time=new BoundedTransactionBoundaryTest.Time();final Driver driver=new Driver();
        final OwnedLeaseConnectionFactory factory=new OwnedLeaseConnectionFactory(driver);
        final DatabaseClient client=DatabaseClient.create(factory);final R2dbcEntityTemplate template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);
        final R2dbcTransactionManager manager=new R2dbcTransactionManager(factory);
        final BoundedRootTransaction root=new DefaultBoundedRootTransaction(BoundedR2dbcResources.verify(factory,client,template,manager),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
        final R2dbcOperationBudget budget=time.budget(5000);
        Mono<RootCompletion<String>> execute(Function<RootTransactionScope,Mono<String>> work){return root.execute(budget,new DefaultTransactionDefinition(),work);}
    }
    record Failed(Fixture fixture,RootTransactionException error) { }
    static Failed failure(Consumer<Fixture> configure,BiFunction<Fixture,RootTransactionScope,Mono<String>> work,long seconds){
        var fixture=new AtomicReference<Fixture>();var result=new AtomicReference<RootTransactionException>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();fixture.set(f);configure.accept(f);return f.execute(scope->work.apply(f,scope));})
                .thenAwait(Duration.ofSeconds(seconds)).expectErrorSatisfies(error->{var root=assertInstanceOf(RootTransactionException.class,error);result.set(root);assertEquals("BOUNDED_ROOT_TRANSACTION_UNAVAILABLE",root.getMessage());assertNull(root.getCause());assertFalse(root.toString().contains("PRIVATE"));}).verify(Duration.ofSeconds(1));
        return new Failed(fixture.get(),result.get());
    }
    @Test void exactTypedRejectionIsCertifiedOnlyAfterCleanRollbackAndClose(){
        var key=RootWorkRejectionKey.create();var r=failure(f->{},(f,s)->Mono.<String>error(new ExpectedRejection()).onErrorResume(ExpectedRejection.class,error->s.reject(key)),1);
        assertTrue(r.error.workRejectedBy(key));assertFalse(r.error.workRejectedBy(RootWorkRejectionKey.create()));assertFalse(r.error.workRejectedBy(null));
        assertEquals(RootTransactionException.Stage.ROLLBACK,r.error.stage());assertTrue(r.error.transactionFacts().rollbackAcknowledged());assertFalse(r.error.transactionFacts().commitAcknowledged());assertFalse(r.error.transactionFacts().boundaryCompleted());assertEquals(CleanupFact.COMPLETE,r.error.cleanupFact());assertEquals(1,r.fixture.driver.original.closes);
        assertEquals("RootWorkRejectionKey",key.toString());
    }
    @Test void defaultScopeImplementationCannotCreateARejectionCertificate(){
        RootTransactionScope unsupported=new RootTransactionScope(){public Duration remaining(){return Duration.ofSeconds(1);}public void narrow(R2dbcOperationBudget cap){}};
        StepVerifier.create(unsupported.reject(RootWorkRejectionKey.create())).expectError(IllegalStateException.class).verify();
    }
    @Test void oldConstructorsAndUnclassifiedErrorsNeverAcquireAKey(){
        var key=RootWorkRejectionKey.create();var r=failure(f->{},(f,s)->Mono.error(new ExpectedRejection()),1);assertFalse(r.error.workRejectedBy(key));
        var old=new RootTransactionException(r.error.transactionFacts(),r.error.cleanupFact(),r.error.stage());assertFalse(old.workRejectedBy(key));assertEquals(r.error.getMessage(),old.getMessage());assertNull(old.getCause());
    }
    @Test void replacingThePrivateWorkSignalRefusesClassification(){
        var key=RootWorkRejectionKey.create();var r=failure(f->{},(f,s)->s.<String>reject(key).onErrorMap(error->new IllegalStateException("PRIVATE-REPLACEMENT")),1);assertFalse(r.error.workRejectedBy(key));
    }
    @Test void anEarlierTypedMarkerCannotClassifyTheLaterOwnedDeadline(){
        var key=RootWorkRejectionKey.create();var marker=new AtomicReference<Throwable>();var r=failure(f->{},(f,s)->Mono.<String>error(new ExpectedRejection()).doOnError(marker::set).onErrorResume(error->Mono.never()),8);
        assertInstanceOf(ExpectedRejection.class,marker.get());assertFalse(r.error.workRejectedBy(key));assertTrue(r.error.transactionFacts().rollbackAcknowledged());assertEquals(CleanupFact.COMPLETE,r.error.cleanupFact());
    }
    @Test void aRecoveredPrivateSignalCannotClassifyTheLaterOwnedDeadline(){
        var key=RootWorkRejectionKey.create();var r=failure(f->{},(f,s)->s.<String>reject(key).onErrorResume(error->Mono.never()),8);assertFalse(r.error.workRejectedBy(key));assertEquals(0,r.fixture.driver.original.commits);
    }
    @Test void rollbackFailureAndCloseTimeoutWithholdTheKey(){
        for(boolean rollback:List.of(true,false)){
            var key=RootWorkRejectionKey.create();var r=failure(f->{f.driver.original.failRollback=rollback;f.driver.holdClose=!rollback;},(f,s)->s.reject(key),8);
            assertFalse(r.error.workRejectedBy(key));assertEquals(CleanupFact.INCOMPLETE,r.error.cleanupFact());assertEquals(1,r.fixture.driver.original.closes);
        }
    }
    @Test void pendingRollbackWithholdsTheErrorUntilItsAcknowledgment(){
        var key=RootWorkRejectionKey.create();var fixture=new AtomicReference<Fixture>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();fixture.set(f);f.driver.holdRollback=true;return f.execute(s->s.reject(key));})
                .expectSubscription().expectNoEvent(Duration.ofMillis(100)).then(()->{assertEquals(1,fixture.get().driver.original.rollbacks);assertEquals(0,fixture.get().driver.original.closes);fixture.get().driver.rollbackGate.tryEmitEmpty();})
                .expectErrorSatisfies(error->assertTrue(assertInstanceOf(RootTransactionException.class,error).workRejectedBy(key))).verify(Duration.ofSeconds(1));
    }
    @Test void pendingCloseWithholdsTheCertificateUntilTheOriginalCloseCompletes(){
        var key=RootWorkRejectionKey.create();var fixture=new AtomicReference<Fixture>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();fixture.set(f);f.driver.holdClose=true;return f.execute(s->s.reject(key));})
                .expectSubscription().expectNoEvent(Duration.ofMillis(100)).then(()->{assertEquals(1,fixture.get().driver.original.closes);fixture.get().driver.closeGate.tryEmitEmpty();})
                .expectErrorSatisfies(error->assertTrue(assertInstanceOf(RootTransactionException.class,error).workRejectedBy(key))).verify(Duration.ofSeconds(1));
    }
    @Test void finalOriginalBudgetExpiryDuringCloseCannotRetainClassification(){
        var key=RootWorkRejectionKey.create();var r=failure(f->f.driver.original.afterClose=()->f.time.wallOffset.addAndGet(6000),(f,s)->s.reject(key),1);
        assertFalse(r.error.workRejectedBy(key));assertEquals(CleanupFact.COMPLETE,r.error.cleanupFact());assertTrue(r.error.transactionFacts().rollbackAcknowledged());
    }
    @Test void commitAcknowledgmentOrCommitTimeoutCannotBecomeAWorkRejection(){
        for(boolean stall:List.of(false,true)){
            var key=RootWorkRejectionKey.create();var r=failure(f->f.driver.original.stallCommit=stall,(f,s)->stall?Mono.just("value"):TransactionSynchronizationManager.forCurrentTransaction().flatMap(sync->{sync.registerSynchronization(new TransactionSynchronization(){@Override public Mono<Void> afterCommit(){return s.reject(key);}});return Mono.just("value");}),8);
            assertFalse(r.error.workRejectedBy(key));assertEquals(!stall,r.error.transactionFacts().commitAcknowledged());
        }
    }
    @Test void aConsumedKeyCannotBeUsedByAnotherInvocation(){
        var key=RootWorkRejectionKey.create();var outcomes=new ArrayList<RootTransactionException>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();return f.execute(s->s.reject(key)).materialize().concatWith(f.execute(s->s.reject(key)).materialize());})
                .assertNext(signal->{var error=assertInstanceOf(RootTransactionException.class,signal.getThrowable());outcomes.add(error);assertTrue(error.workRejectedBy(key));})
                .assertNext(signal->{var error=assertInstanceOf(RootTransactionException.class,signal.getThrowable());outcomes.add(error);assertFalse(error.workRejectedBy(key));}).verifyComplete();assertEquals(2,outcomes.size());
    }
    @Test void oldAndForeignScopesCannotEmitAClassifiedSignal(){
        var first=RootWorkRejectionKey.create();var second=RootWorkRejectionKey.create();var saved=new AtomicReference<RootTransactionScope>();var context=new AtomicReference<ContextView>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();return f.execute(s->Mono.deferContextual(c->{saved.set(s);context.set(c);return s.reject(first);}));}).expectErrorSatisfies(error->assertTrue(((RootTransactionException)error).workRejectedBy(first))).verify();
        StepVerifier.create(saved.get().reject(second).contextWrite(context.get())).expectError(IllegalStateException.class).verify();
        var r=failure(f->{},(f,s)->saved.get().reject(second),1);assertFalse(r.error.workRejectedBy(second));
    }
    @Test void callerCancellationCannotReviveAConsumedKey(){
        var key=RootWorkRejectionKey.create();var fixture=new AtomicReference<Fixture>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();fixture.set(f);return f.execute(s->s.<String>reject(key).onErrorResume(error->Mono.never()));}).thenAwait(Duration.ofMillis(100)).thenCancel().verify(Duration.ofSeconds(1));
        assertEquals(1,fixture.get().driver.original.closes);var next=failure(f->{},(f,s)->s.reject(key),1);assertFalse(next.error.workRejectedBy(key));
    }
    @Test void swallowingTheRejectionStillCannotCommitBusinessWork(){
        var key=RootWorkRejectionKey.create();var fixture=new AtomicReference<Fixture>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();fixture.set(f);return f.execute(s->s.<String>reject(key).onErrorReturn("recovered"));})
                .assertNext(value->{assertTrue(value.transactionFacts().rollbackAcknowledged());assertFalse(value.transactionFacts().commitAcknowledged());assertTrue(value.transactionFacts().boundaryCompleted());}).verifyComplete();assertEquals(0,fixture.get().driver.original.commits);
    }
    @Test void freshSubscriberKeysDoNotShareClassificationState(){
        var keys=new ArrayList<RootWorkRejectionKey>();var errors=new ArrayList<RootTransactionException>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();return Mono.defer(()->{var key=RootWorkRejectionKey.create();keys.add(key);return f.execute(s->s.reject(key)).materialize();}).repeat(1);})
                .assertNext(signal->errors.add(assertInstanceOf(RootTransactionException.class,signal.getThrowable())))
                .assertNext(signal->errors.add(assertInstanceOf(RootTransactionException.class,signal.getThrowable()))).verifyComplete();
        assertNotSame(keys.getFirst(),keys.getLast());assertTrue(errors.getFirst().workRejectedBy(keys.getFirst()));assertTrue(errors.getLast().workRejectedBy(keys.getLast()));assertFalse(errors.getFirst().workRejectedBy(keys.getLast()));
    }
    @Test void controlledWorkAndDeadlineWinnersCannotBeConfused(){
        for(long workMillis:List.of(50L,150L)){
            var key=RootWorkRejectionKey.create();var r=failure(f->{},(f,s)->{
                s.narrow(f.time.budget(100));return Mono.delay(Duration.ofMillis(workMillis)).then(Mono.defer(()->s.reject(key)));
            },1);
            assertEquals(workMillis==50,r.error.workRejectedBy(key));assertTrue(r.error.transactionFacts().rollbackAcknowledged());assertEquals(CleanupFact.COMPLETE,r.error.cleanupFact());
        }
    }
    @Test void deadlineDuringPendingRollbackStaysUnclassifiedAfterCleanLateCompletion(){
        var key=RootWorkRejectionKey.create();var fixture=new AtomicReference<Fixture>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();fixture.set(f);f.driver.holdRollback=true;return f.execute(s->{s.narrow(f.time.budget(100));return s.reject(key);});})
                .expectSubscription().expectNoEvent(Duration.ofMillis(150)).then(()->fixture.get().driver.rollbackGate.tryEmitEmpty())
                .expectErrorSatisfies(error->{var root=assertInstanceOf(RootTransactionException.class,error);assertFalse(root.workRejectedBy(key));assertTrue(root.transactionFacts().rollbackAcknowledged());assertFalse(root.transactionFacts().commitAcknowledged());assertFalse(root.transactionFacts().boundaryCompleted());assertEquals(CleanupFact.COMPLETE,root.cleanupFact());}).verify(Duration.ofSeconds(1));
    }
}
