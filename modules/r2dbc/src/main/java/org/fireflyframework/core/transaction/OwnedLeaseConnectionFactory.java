/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;

import io.r2dbc.spi.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.springframework.r2dbc.connection.DelegatingConnectionFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.*;

/** Stable qualified identity with exact invocation-created lease ownership; never closes a pool. */
public final class OwnedLeaseConnectionFactory extends DelegatingConnectionFactory {
    private static final Object INVOCATION=new Object();
    public OwnedLeaseConnectionFactory(ConnectionFactory delegate){super(Objects.requireNonNull(delegate));}
    @Override public Mono<? extends Connection> create(){return Mono.deferContextual(context->{
        var value=context.getOrDefault(INVOCATION,null);
        if(!(value instanceof Invocation scope)||scope.factory!=this)return Mono.error(new IllegalStateException("Exact bounded invocation required"));
        scope.remaining();scope.stage.set(RootTransactionException.Stage.ACQUIRE);scope.nativeAttempted.set(true);
        return Mono.from(super.create()).map(scope::own);
    });}

    /** Proves an append participates in this exact, still-active owner invocation and resource graph. */
    public Mono<Void> requireBoundScope(BoundedR2dbcResources resources,RootTransactionScope expected){
        return Mono.deferContextual(context->{
            Object active=context.getOrDefault(INVOCATION,null);
            if(!(active instanceof Invocation scope)||active!=expected||scope.factory!=this||resources==null
                    ||!resources.sameGraph(scope.resources)||!resources.current()||!scope.resources.current()
                    ||scope.transaction.get()==null||scope.transaction.get().isCompleted())
                return Mono.error(new IllegalStateException("Exact active bounded audit transaction required"));
            scope.remaining();
            return TransactionSynchronizationManager.forCurrentTransaction()
                .filter(manager->manager.isActualTransactionActive()&&manager.hasResource(this))
                .switchIfEmpty(Mono.error(new IllegalStateException("Exact active bounded audit transaction required")))
                .onErrorMap(NoTransactionException.class,error->new IllegalStateException("Exact active bounded audit transaction required"))
                .then();
        });
    }

    /** Mandatory append failure cannot be converted into a committing business result by error recovery. */
    public Mono<Void> markBoundScopeRollbackOnly(BoundedR2dbcResources resources,RootTransactionScope expected){
        return requireBoundScope(resources,expected).then(Mono.deferContextual(context->{
            ((Invocation)context.get(INVOCATION)).transaction.get().setRollbackOnly();return Mono.empty();
        }));
    }

    /** Execution bridge used by the opt-in service; the verified graph is required on every subscription. */
    public <T> Mono<RootCompletion<T>> executeBounded(BoundedR2dbcResources resources,R2dbcOperationBudget original,
            TransactionDefinition definition,CleanupPolicy cleanup,Function<RootTransactionScope,Mono<T>> work){
        Objects.requireNonNull(resources);Objects.requireNonNull(original);Objects.requireNonNull(definition);Objects.requireNonNull(cleanup);Objects.requireNonNull(work);
        return Mono.deferContextual(context->{
            if(resources.factory()!=this||!resources.current()||context.hasKey(INVOCATION)||definition.getPropagationBehavior()!=TransactionDefinition.PROPAGATION_REQUIRED)return Mono.error(gateFailure());
            return TransactionSynchronizationManager.forCurrentTransaction().hasElement().onErrorResume(NoTransactionException.class,e->Mono.just(false)).flatMap(active->{
                if(active)return Mono.error(gateFailure());
                final Invocation scope;
                try {original.remaining();scope=new Invocation(this,resources,original,cleanup);}catch(RuntimeException expired){return Mono.error(gateFailure());}
                Mono<T> bounded=Mono.usingWhen(Mono.just(scope),owned->{
                    Mono<T> transaction=TransactionalOperator.create(owned.manager(),new DefaultTransactionDefinition(definition))
                        .execute(status->TransactionSynchronizationManager.forCurrentTransaction().flatMap(sync->{
                            sync.registerSynchronization(new TransactionSynchronization(){
                                @Override public Mono<Void> afterCommit(){return Mono.fromRunnable(()->owned.commitAcknowledged.set(true));}
                                @Override public Mono<Void> afterCompletion(int state){return Mono.fromRunnable(()->{if(state==STATUS_ROLLED_BACK)owned.rollbackAcknowledged.set(true);});}
                            });
                            owned.stage.set(RootTransactionException.Stage.WORK);
                            return Mono.defer(()->work.apply(owned)).switchIfEmpty(Mono.error(new IllegalStateException("One bounded root result required")));
                        }).flux()).collectList().doOnSuccess(values->owned.boundaryCompleted.set(true)).flatMap(values->
                            values.size()==1?Mono.just(values.getFirst()):Mono.error(new IllegalStateException("One bounded root result required")));
                    return transaction.timeout(owned.deadline()).doOnError(error->owned.captureFailure());
                },Invocation::cleanup,(owned,error)->owned.cleanup(),Invocation::cleanup);
                return bounded.flatMap(value->{
                    scope.stage.set(RootTransactionException.Stage.FINAL_BUDGET);
                    scope.remaining();
                    return Mono.just(new RootCompletion<>(value,scope.facts()));
                }).onErrorMap(error->scope.failure()).contextWrite(c->c.put(INVOCATION,scope));
            });
        });
    }
    private static RootTransactionException gateFailure(){return new RootTransactionException(new TransactionFacts(false,false,false,false),CleanupFact.COMPLETE,RootTransactionException.Stage.GATE);}

    private static final class Invocation implements RootTransactionScope {
        final OwnedLeaseConnectionFactory factory;
        final BoundedR2dbcResources resources;
        final R2dbcOperationBudget original;
        final AtomicReference<R2dbcOperationBudget> cap;
        final CleanupPolicy policy;
        final List<Lease> leases=Collections.synchronizedList(new ArrayList<>());
        final AtomicBoolean nativeAttempted=new AtomicBoolean(),commitAcknowledged=new AtomicBoolean(),boundaryCompleted=new AtomicBoolean(),rollbackAcknowledged=new AtomicBoolean();
        final AtomicReference<RootTransactionException.Stage> stage=new AtomicReference<>(RootTransactionException.Stage.GATE),failureStage=new AtomicReference<>();
        final AtomicReference<ReactiveTransaction> transaction=new AtomicReference<>();
        final AtomicReference<Mono<Void>> rollback=new AtomicReference<>();
        final AtomicBoolean incompleteCleanup=new AtomicBoolean();
        final AtomicReference<CleanupFact> cleanupFact=new AtomicReference<>(CleanupFact.UNKNOWN);
        final Sinks.Many<Boolean> narrowed=Sinks.many().replay().latest();
        reactor.util.context.Context transactionContext;
        Invocation(OwnedLeaseConnectionFactory factory,BoundedR2dbcResources resources,R2dbcOperationBudget original,CleanupPolicy policy){this.factory=factory;this.resources=resources;this.original=original;this.cap=new AtomicReference<>(original);this.policy=policy;}
        @Override public Duration remaining(){var a=original.remaining();var b=cap.get().remaining();return a.compareTo(b)<0?a:b;}
        @Override public void narrow(R2dbcOperationBudget observedCap){cap.updateAndGet(current->current.intersect(observedCap));remaining();if(narrowed.tryEmitNext(true).isFailure())throw new IllegalStateException("Bounded deadline observation unavailable");}
        Mono<Long> deadline(){return narrowed.asFlux().startWith(false).switchMap(signal->Mono.defer(()->Mono.delay(remaining()))).next();}
        Connection own(Connection raw){var lease=new Lease(raw,this);leases.add(lease);return lease;}
        void captureFailure(){failureStage.compareAndSet(null,stage.get());}
        TransactionFacts facts(){return new TransactionFacts(nativeAttempted.get(),commitAcknowledged.get(),boundaryCompleted.get(),rollbackAcknowledged.get());}
        RootTransactionException failure(){return new RootTransactionException(facts(),cleanupFact.get(),Optional.ofNullable(failureStage.get()).orElse(stage.get()));}
        ReactiveTransactionManager manager(){return new ReactiveTransactionManager(){
            @Override public Mono<ReactiveTransaction> getReactiveTransaction(TransactionDefinition d){return Mono.deferContextual(context->{
                if(!resources.current())return Mono.error(gateFailure());transactionContext=reactor.util.context.Context.of(context);var limit=remaining();return resources.manager().getReactiveTransaction(d).timeout(limit);
            }).doOnNext(transaction::set);}
            @Override public Mono<Void> commit(ReactiveTransaction status){return Mono.defer(()->{stage.set(RootTransactionException.Stage.COMMIT);if(!resources.current())return Mono.error(failure());var limit=remaining();return resources.manager().commit(status).timeout(limit);});}
            @Override public Mono<Void> rollback(ReactiveTransaction status){return rollbackOnce(status);}
        };}
        Mono<Void> rollbackOnce(ReactiveTransaction status){
            Mono<Void> existing=rollback.get();if(existing!=null)return existing;
            Mono<Void> action=Mono.defer(()->{stage.set(RootTransactionException.Stage.ROLLBACK);if(!resources.current())return Mono.error(failure());return status.isCompleted()?Mono.empty():resources.manager().rollback(status);})
                .contextWrite(c->transactionContext==null?c:c.putAll(transactionContext)).timeout(policy.rollback()).doOnError(error->incompleteCleanup.set(true)).cache();
            return rollback.compareAndSet(null,action)?action:rollback.get();
        }
        Mono<Void> cleanup(){return Mono.defer(()->{
            var incomplete=incompleteCleanup;var status=transaction.get();
            Mono<Void> rolled=status!=null&&!status.isCompleted()&&!commitAcknowledged.get()?rollbackOnce(status):Mono.empty();
            return rolled.onErrorResume(error->{incomplete.set(true);return Mono.empty();}).then(Mono.defer(()->{
                stage.set(RootTransactionException.Stage.CLOSE);List<Lease> owned;synchronized(leases){owned=List.copyOf(leases);}
                return Mono.whenDelayError(owned.stream().map(Lease::close).toList()).timeout(policy.close());
            })).doOnSuccess(ignored->cleanupFact.set(incomplete.get()?CleanupFact.INCOMPLETE:CleanupFact.COMPLETE))
              .onErrorResume(error->{cleanupFact.set(CleanupFact.INCOMPLETE);captureFailure();return Mono.error(failure());})
              .then(Mono.defer(()->incomplete.get()?Mono.error(failure()):Mono.empty()));
        });}
    }
    private static final class Lease implements Connection {
        final Connection raw;final Invocation scope;final Mono<Void> close;
        Lease(Connection raw,Invocation scope){this.raw=Objects.requireNonNull(raw);this.scope=scope;close=Mono.defer(()->Mono.from(raw.close())).timeout(scope.policy.close()).cache();}
        public Publisher<Void> beginTransaction(){return Mono.defer(()->{scope.stage.set(RootTransactionException.Stage.BEGIN);var limit=scope.remaining();return Mono.from(raw.beginTransaction()).timeout(limit);});}
        public Publisher<Void> beginTransaction(io.r2dbc.spi.TransactionDefinition d){return Mono.defer(()->{scope.stage.set(RootTransactionException.Stage.BEGIN);var limit=scope.remaining();return Mono.from(raw.beginTransaction(d)).timeout(limit);});}
        public Publisher<Void> commitTransaction(){return Mono.defer(()->{scope.stage.set(RootTransactionException.Stage.COMMIT);var limit=scope.remaining();return Mono.from(raw.commitTransaction()).timeout(limit);});}
        public Publisher<Void> rollbackTransaction(){return Mono.defer(()->Mono.from(raw.rollbackTransaction())).timeout(scope.policy.rollback());}
        public Mono<Void> close(){return close;}
        public Batch createBatch(){return raw.createBatch();}
        public Publisher<Void> createSavepoint(String name){return raw.createSavepoint(name);}
        public Statement createStatement(String sql){return raw.createStatement(sql);}
        public boolean isAutoCommit(){return raw.isAutoCommit();}
        public ConnectionMetadata getMetadata(){return raw.getMetadata();}
        public IsolationLevel getTransactionIsolationLevel(){return raw.getTransactionIsolationLevel();}
        public Publisher<Void> releaseSavepoint(String name){return raw.releaseSavepoint(name);}
        public Publisher<Void> rollbackTransactionToSavepoint(String name){return raw.rollbackTransactionToSavepoint(name);}
        public Publisher<Void> setAutoCommit(boolean value){return raw.setAutoCommit(value);}
        public Publisher<Void> setLockWaitTimeout(Duration value){return raw.setLockWaitTimeout(value);}
        public Publisher<Void> setStatementTimeout(Duration value){return raw.setStatementTimeout(value);}
        public Publisher<Void> setTransactionIsolationLevel(IsolationLevel value){return raw.setTransactionIsolationLevel(value);}
        public Publisher<Boolean> validate(ValidationDepth depth){return raw.validate(depth);}
    }
}
