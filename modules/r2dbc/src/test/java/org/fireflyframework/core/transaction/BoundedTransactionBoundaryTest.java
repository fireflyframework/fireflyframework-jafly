package org.fireflyframework.core.transaction;

import io.r2dbc.spi.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransactionImpl;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import static org.junit.jupiter.api.Assertions.*;

class BoundedTransactionBoundaryTest {
    static final class Time extends Clock {
        final VirtualTimeScheduler scheduler=VirtualTimeScheduler.getOrSet();
        final LongSupplier nanos=()->scheduler.now(TimeUnit.NANOSECONDS);
        final AtomicLong wallOffset=new AtomicLong();
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){if(!zone.equals(ZoneOffset.UTC))throw new IllegalArgumentException();return this;}
        public Instant instant(){return Instant.parse("2026-10-26T00:00:00Z").plusNanos(nanos.getAsLong()).plusMillis(wallOffset.get());}
        R2dbcOperationBudget budget(long millis){var now=instant();return R2dbcOperationBudget.observed(now,now.plusMillis(millis),nanos.getAsLong(),Duration.ofMillis(millis),this,nanos);}
    }
    static final class Driver implements ConnectionFactory {
        int creates,begins,commits,rollbacks,closes,acquireCancelled,beginCancelled,commitCancelled,rollbackCancelled,closeCancelled;
        boolean stallAcquire,stallBegin,stallCommit,stallRollback,stallClose,failRollback;
        Runnable afterClose=()->{};
        final Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "isAutoCommit" -> true;
            case "getTransactionIsolationLevel" -> IsolationLevel.READ_COMMITTED;
            case "beginTransaction" -> Mono.defer(()->{begins++;return stallBegin?Mono.<Void>never().doOnCancel(()->beginCancelled++):Mono.empty();});
            case "commitTransaction" -> Mono.defer(()->{commits++;return stallCommit?Mono.<Void>never().doOnCancel(()->commitCancelled++):Mono.empty();});
            case "rollbackTransaction" -> Mono.defer(()->{rollbacks++;return failRollback?Mono.error(new IllegalStateException("source rollback unavailable")):stallRollback?Mono.<Void>never().doOnCancel(()->rollbackCancelled++):Mono.empty();});
            case "close" -> Mono.defer(()->{closes++;if(stallClose)return Mono.<Void>never().doOnCancel(()->closeCancelled++);afterClose.run();return Mono.empty();});
            case "createStatement" -> statement();
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p==a[0];
            case "toString" -> "OwnedNetworklessLease";
            default -> Mono.empty();
        });
        Statement statement(){return (Statement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->m.getName().equals("execute")?Mono.just((Result)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Result.class},(v,n,b)->n.getName().equals("getRowsUpdated")?Mono.just(1L):n.getName().equals("filter")?v:Mono.empty())):p);}
        public Publisher<? extends Connection> create(){return Mono.defer(()->{creates++;return stallAcquire?Mono.<Connection>never().doOnCancel(()->acquireCancelled++):Mono.just(connection);});}
        public ConnectionFactoryMetadata getMetadata(){return ()->"PostgreSQL";}
    }
    static final class Fixture {
        final Time time=new Time();final Driver driver=new Driver();
        final OwnedLeaseConnectionFactoryImpl factory=new OwnedLeaseConnectionFactoryImpl(driver);
        final DatabaseClient client=DatabaseClient.create(factory);
        final R2dbcEntityTemplate template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);
        final R2dbcTransactionManager manager=new R2dbcTransactionManager(factory);
        final BoundedRootTransaction service=new DefaultBoundedRootTransactionImpl(BoundedR2dbcResources.verify(factory,client,template,manager),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
        final R2dbcOperationBudget original=time.budget(5000);
        Mono<RootCompletion<String>> execute(Function<RootTransactionScope,Mono<String>> work){return service.execute(original,new DefaultTransactionDefinition(),work);}
    }
    record Failed(Fixture fixture,RootTransactionException error) {}
    static Failed fails(Consumer<Fixture> configure,BiFunction<Fixture,RootTransactionScope,Mono<String>> work,long seconds){
        var fixture=new AtomicReference<Fixture>();var error=new AtomicReference<RootTransactionException>();
        StepVerifier.withVirtualTime(()->{var f=new Fixture();fixture.set(f);configure.accept(f);return f.execute(scope->work.apply(f,scope));})
            .thenAwait(Duration.ofSeconds(seconds)).expectErrorSatisfies(e->{assertInstanceOf(RootTransactionException.class,e);error.set((RootTransactionException)e);assertFalse(e.toString().contains("protected-value"));assertNull(e.getCause());}).verify(Duration.ofSeconds(1));
        return new Failed(fixture.get(),error.get());
    }
    @Test void acquisitionTimeoutHasNoInventedLeaseCloseOrCommit(){
        var r=fails(f->f.driver.stallAcquire=true,(f,s)->Mono.just("protected-value"),8);assertEquals(1,r.fixture.driver.acquireCancelled);assertEquals(0,r.fixture.driver.closes);assertFalse(r.error.transactionFacts().commitAcknowledged());assertTrue(r.error.transactionFacts().nativeAttempted());
    }
    @Test void stalledBeginClosesExactlyItsCreatedLease(){
        var r=fails(f->f.driver.stallBegin=true,(f,s)->Mono.just("protected-value"),8);assertEquals(1,r.fixture.driver.beginCancelled);assertEquals(1,r.fixture.driver.closes);assertFalse(r.error.transactionFacts().boundaryCompleted());assertFalse(r.error.transactionFacts().commitAcknowledged());
    }
    @Test void stalledWorkIsBoundedAndObservedRollbackIsNotBusinessNoEffect(){
        var cancelled=new AtomicInteger();var r=fails(f->{},(f,s)->Mono.<String>never().doOnCancel(cancelled::incrementAndGet),8);assertEquals(1,cancelled.get());assertEquals(1,r.fixture.driver.closes);assertEquals(0,r.fixture.driver.commits);assertTrue(r.fixture.driver.rollbacks>0);
    }
    @Test void actualCommitPublisherIsCancelledWithinOriginalBudget(){
        var r=fails(f->f.driver.stallCommit=true,(f,s)->Mono.just("protected-value"),8);assertEquals(1,r.fixture.driver.commitCancelled);assertEquals(1,r.fixture.driver.closes);assertFalse(r.error.transactionFacts().commitAcknowledged());assertFalse(r.error.transactionFacts().boundaryCompleted());
    }
    @Test void stalledRollbackStillAttemptsCloseWithIndependentFiniteCleanup(){
        var r=fails(f->f.driver.stallRollback=true,(f,s)->Mono.error(new IllegalStateException("protected-value")),10);assertTrue(r.fixture.driver.rollbackCancelled>0);assertEquals(1,r.fixture.driver.closes);assertFalse(r.error.transactionFacts().commitAcknowledged());assertEquals(CleanupFact.INCOMPLETE,r.error.cleanupFact());
    }
    @Test void failedRollbackCannotSkipTheOwnedClose(){
        var r=fails(f->f.driver.failRollback=true,(f,s)->Mono.error(new IllegalStateException("protected-value")),8);assertEquals(1,r.fixture.driver.closes);assertFalse(r.error.transactionFacts().rollbackAcknowledged());assertEquals(CleanupFact.INCOMPLETE,r.error.cleanupFact());
    }
    @Test void stalledCloseAfterCommitRetainsBothCommitAndBoundaryFactsWithoutValue(){
        var r=fails(f->f.driver.stallClose=true,(f,s)->Mono.just("protected-value"),8);assertEquals(1,r.fixture.driver.closes);assertEquals(1,r.fixture.driver.closeCancelled);assertTrue(r.error.transactionFacts().commitAcknowledged());assertTrue(r.error.transactionFacts().boundaryCompleted());assertEquals(CleanupFact.INCOMPLETE,r.error.cleanupFact());
    }
    @Test void afterCommitFailureKeepsAcknowledgmentDistinctFromBoundaryCompletion(){
        var r=fails(f->{},(f,s)->TransactionSynchronizationManager.forCurrentTransaction().flatMap(sync->{sync.registerSynchronization(new TransactionSynchronization(){@Override public Mono<Void> afterCommit(){return Mono.error(new IllegalStateException("protected-value"));}});return Mono.just("protected-value");}),8);
        assertTrue(r.error.transactionFacts().commitAcknowledged());assertFalse(r.error.transactionFacts().boundaryCompleted());assertEquals(1,r.fixture.driver.closes);
    }
    @Test void stalledAfterCommitBoundaryKeepsKnownCommitAndCannotRenewItsBudget(){
        var r=fails(f->{},(f,s)->TransactionSynchronizationManager.forCurrentTransaction().flatMap(sync->{sync.registerSynchronization(new TransactionSynchronization(){@Override public Mono<Void> afterCommit(){return Mono.never();}});return Mono.just("protected-value");}),8);
        assertTrue(r.error.transactionFacts().commitAcknowledged());assertFalse(r.error.transactionFacts().boundaryCompleted());assertEquals(1,r.fixture.driver.closes);assertEquals(0,r.fixture.driver.rollbacks);
    }
    @Test void finalOriginalBudgetMustStillBeLiveAfterSuccessfulClose(){
        var r=fails(f->f.driver.afterClose=()->f.time.wallOffset.addAndGet(6000),(f,s)->Mono.just("protected-value"),1);assertTrue(r.error.transactionFacts().commitAcknowledged());assertTrue(r.error.transactionFacts().boundaryCompleted());assertEquals(CleanupFact.COMPLETE,r.error.cleanupFact());assertEquals(RootTransactionException.Stage.FINAL_BUDGET,r.error.stage());
    }
    @Test void nativeCapWakesThePendingDeadlineInsteadOfWaitingFiveSeconds(){
        var r=fails(f->{},(f,s)->Mono.delay(Duration.ofMillis(100)).flatMap(v->{s.narrow(f.time.budget(400));return Mono.never();}),2);assertEquals(1,r.fixture.driver.closes);assertEquals(0,r.fixture.driver.commits);
    }
    @Test void laterLongerCapCannotRenewOriginalBudget(){
        var r=fails(f->{},(f,s)->{s.narrow(f.time.budget(10000));return Mono.never();},8);assertEquals(0,r.fixture.driver.commits);assertEquals(1,r.fixture.driver.closes);
    }
    @Test void foreignFactoryCannotBorrowAnotherInvocationOrBeClosed(){
        var foreign=new Driver();var other=new OwnedLeaseConnectionFactoryImpl(foreign);var r=fails(f->{},(f,s)->Mono.from(other.create()).thenReturn("protected-value"),1);assertEquals(0,foreign.creates);assertEquals(0,foreign.closes);assertEquals(1,r.fixture.driver.closes);
    }
    @Test void wrapperWithoutPrivateInvocationCannotAcquire(){var d=new Driver();StepVerifier.create(Mono.from(new OwnedLeaseConnectionFactoryImpl(d).create())).expectError(IllegalStateException.class).verify();assertEquals(0,d.creates);}
    @Test void callerCancellationStillClosesItsOwnBeginLeaseOnce(){
        var ref=new AtomicReference<Fixture>();StepVerifier.withVirtualTime(()->{var f=new Fixture();ref.set(f);f.driver.stallBegin=true;return f.execute(s->Mono.just("protected-value"));}).thenAwait(Duration.ofMillis(10)).thenCancel().verify(Duration.ofSeconds(1));assertEquals(1,ref.get().driver.beginCancelled);assertEquals(1,ref.get().driver.closes);
    }
    @Test void successIsDeliveredOnlyAfterActualCommitBoundaryAndExactClose(){
        var ref=new AtomicReference<Fixture>();StepVerifier.withVirtualTime(()->{var f=new Fixture();ref.set(f);return f.execute(s->Mono.just("protected-value"));}).assertNext(value->{assertEquals("protected-value",value.value());assertEquals("RootCompletion",value.toString());assertTrue(value.transactionFacts().commitAcknowledged());assertTrue(value.transactionFacts().boundaryCompleted());assertEquals(1,ref.get().driver.closes);}).verifyComplete();
    }
    @Test void freshSubscriptionsOwnFreshHandlesButCannotResetOriginalExpiry(){
        var ref=new AtomicReference<Fixture>();StepVerifier.withVirtualTime(()->{var f=new Fixture();ref.set(f);var result=f.execute(s->Mono.just("protected-value"));return result.concatWith(result.delaySubscription(Duration.ofSeconds(6)));}).expectNextCount(1).thenAwait(Duration.ofSeconds(7)).expectErrorSatisfies(e->{var failure=(RootTransactionException)e;assertFalse(failure.transactionFacts().nativeAttempted());assertEquals(RootTransactionException.Stage.GATE,failure.stage());}).verify(Duration.ofSeconds(1));assertEquals(1,ref.get().driver.creates);assertEquals(1,ref.get().driver.closes);
    }
    @Test void freshSubscriptionsInsideBudgetDoNotShareLeaseCloseState(){
        var ref=new AtomicReference<Fixture>();StepVerifier.withVirtualTime(()->{var f=new Fixture();ref.set(f);return f.execute(s->Mono.just("protected-value")).repeat(1);}).expectNextCount(2).verifyComplete();assertEquals(2,ref.get().driver.creates);assertEquals(2,ref.get().driver.closes);
    }
    @Test void ambientRootIsRefusedBeforeNestedAcquisition(){
        var nested=new AtomicInteger();var r=fails(f->{},(f,s)->f.execute(inner->{nested.incrementAndGet();return Mono.just("protected-value");}).thenReturn("protected-value"),1);assertEquals(0,nested.get());assertEquals(1,r.fixture.driver.creates);assertEquals(1,r.fixture.driver.closes);
    }
    @Test void genuineAmbientSpringTransactionIsNotJoinedOrClosed(){
        var outer=new Driver();var ref=new AtomicReference<Fixture>();StepVerifier.withVirtualTime(()->{var f=new Fixture();ref.set(f);return TransactionalOperator.create(new R2dbcTransactionManager(outer)).transactional(f.execute(s->Mono.just("protected-value")));}).expectError(RootTransactionException.class).verify();assertEquals(0,ref.get().driver.creates);assertEquals(0,ref.get().driver.closes);assertEquals(1,outer.closes);
    }
    @Test void emptyWorkResultCannotCommitOrEmitCompletion(){var r=fails(f->{},(f,s)->Mono.empty(),1);assertEquals(0,r.fixture.driver.commits);assertEquals(1,r.fixture.driver.closes);assertTrue(r.error.transactionFacts().rollbackAcknowledged());assertFalse(r.error.transactionFacts().boundaryCompleted());}
}
