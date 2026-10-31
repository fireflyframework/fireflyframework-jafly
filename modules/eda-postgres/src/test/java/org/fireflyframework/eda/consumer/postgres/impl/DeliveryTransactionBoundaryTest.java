/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.r2dbc.spi.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.InfrastructureProxy;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class DeliveryTransactionBoundaryTest {
    final ConnectionFactory factory=mock(ConnectionFactory.class);
    final List<Session> sessions=new ArrayList<>();
    final AtomicInteger acquired=new AtomicInteger();
    Mono<Void> queryGate=Mono.empty();

    DeliveryTransactionBoundaryTest() {
        when(factory.getMetadata()).thenReturn(()->"PostgreSQL");
        doReturn(Mono.defer(()->Mono.just(sessions.get(acquired.getAndIncrement()).connection))).when(factory).create();
    }
    Session session() {var session=new Session();sessions.add(session);return session;}
    DeliveryTransactionBoundary boundary(ConnectionFactory provided) {return new DeliveryTransactionBoundary(provided,Duration.ofSeconds(10));}
    Mono<String> query(ConnectionFactory provided) {
        var template=new R2dbcEntityTemplate(DatabaseClient.builder().connectionFactory(provided)
                .bindMarkers(PostgresDialect.INSTANCE.getBindMarkersFactory()).build(),PostgresDialect.INSTANCE);
        return template.getDatabaseClient().inConnection(connection->Mono.from(connection.validate(ValidationDepth.LOCAL))
                .then(queryGate).thenReturn("batch"));
    }
    static final class Session {
        final Connection connection=mock(Connection.class);
        final AtomicInteger began=new AtomicInteger(),committed=new AtomicInteger(),rolledBack=new AtomicInteger(),closed=new AtomicInteger();
        Mono<Void> commit=Mono.empty(),close=Mono.empty();
        Session() {
            when(connection.isAutoCommit()).thenReturn(true);
            when(connection.setAutoCommit(anyBoolean())).thenReturn(Mono.empty());
            when(connection.beginTransaction(any(TransactionDefinition.class))).thenReturn(Mono.fromRunnable(began::incrementAndGet));
            when(connection.commitTransaction()).thenAnswer(call->Mono.defer(()->{committed.incrementAndGet();return commit;}));
            when(connection.rollbackTransaction()).thenReturn(Mono.fromRunnable(rolledBack::incrementAndGet));
            when(connection.close()).thenAnswer(call->Mono.defer(()->{closed.incrementAndGet();return close;}));
            when(connection.validate(ValidationDepth.LOCAL)).thenReturn(Mono.just(true));
        }
    }

    @Test void managedLibraryAloneSuppressesReleaseFailureAfterCommit() {
        var session=session();session.close=Mono.error(new IllegalStateException("private release failure"));
        assertThat(TransactionalOperator.create(new R2dbcTransactionManager(factory)).transactional(query(factory)).block()).isEqualTo("batch");
        assertThat(session.committed.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);
    }

    @Test void exactOriginalTemplateUsesOneOwnedTransactionConnection() {
        var session=session();assertThat(boundary(factory).execute(()->query(factory)).block()).isEqualTo("batch");
        assertThat(acquired.get()).isEqualTo(1);assertThat(session.began.get()).isEqualTo(1);
        assertThat(session.committed.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);
    }

    @Test void batchIsWithheldUntilCommitAndCloseComplete() {
        var session=session();var commit=Sinks.<Void>empty();var close=Sinks.<Void>empty();session.commit=commit.asMono();session.close=close.asMono();
        var seen=new AtomicReference<String>();var subscription=boundary(factory).execute(()->query(factory)).doOnNext(seen::set).subscribe();
        try {
            assertThat(session.committed.get()).isEqualTo(1);assertThat(seen.get()).isNull();
            commit.tryEmitEmpty();assertThat(session.closed.get()).isEqualTo(1);assertThat(seen.get()).isNull();
            close.tryEmitEmpty();assertThat(seen.get()).isEqualTo("batch");
        } finally {subscription.dispose();}
    }

    @Test void swallowedReleaseFailureCannotPublishCommittedBatch() {
        var session=session();var failure=new IllegalStateException("private release failure");session.close=Mono.error(failure);
        assertThatThrownBy(()->boundary(factory).execute(()->query(factory)).block()).isInstanceOf(IllegalStateException.class);
        assertThat(session.committed.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);assertThat(acquired.get()).isEqualTo(1);
        assertThat(session.rolledBack.get()).isZero();
    }

    @Test void missingClosePublisherCannotPublishCommittedBatch() {
        var session=session();when(session.connection.close()).thenReturn(null);
        assertThatThrownBy(()->boundary(factory).execute(()->query(factory)).block()).isInstanceOf(IllegalStateException.class);
        assertThat(session.committed.get()).isEqualTo(1);assertThat(acquired.get()).isEqualTo(1);
    }

    @Test void failedCommitCannotPublishOrAutomaticallyRetryBatch() {
        var session=session();session.commit=Mono.error(new IllegalStateException("commit failure"));
        assertThatThrownBy(()->boundary(factory).execute(()->query(factory)).block()).isInstanceOf(RuntimeException.class);
        assertThat(session.committed.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);assertThat(acquired.get()).isEqualTo(1);
    }

    @Test void outerSameFactoryRollbackDoesNotUndoCompletedInnerClaim() {
        var outer=session();var inner=session();var boundary=boundary(factory);
        var expected=new IllegalStateException("intentional outer rollback");
        assertThatThrownBy(()->TransactionalOperator.create(new R2dbcTransactionManager(factory)).transactional(
                boundary.execute(()->query(factory)).doOnNext(value->{
                    assertThat(acquired.get()).isEqualTo(2);assertThat(inner.closed.get()).isEqualTo(1);assertThat(inner.committed.get()).isEqualTo(1);
                    assertThat(outer.closed.get()).isZero();assertThat(outer.committed.get()).isZero();
                }).then(Mono.error(expected))).block()).isSameAs(expected);
        assertThat(inner.committed.get()).isEqualTo(1);assertThat(inner.closed.get()).isEqualTo(1);
        assertThat(inner.rolledBack.get()).isZero();assertThat(outer.rolledBack.get()).isEqualTo(1);assertThat(outer.closed.get()).isEqualTo(1);
    }

    @Test void cancellationRollsBackAndClosesWithoutEmitting() {
        var session=session();queryGate=Mono.never();var seen=new AtomicReference<String>();
        var subscription=boundary(factory).execute(()->query(factory)).doOnNext(seen::set).subscribe();
        assertThat(session.began.get()).isEqualTo(1);subscription.dispose();
        assertThat(session.rolledBack.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);assertThat(session.committed.get()).isZero();assertThat(seen.get()).isNull();
    }

    @Test void concurrentSubscriptionsCannotShareReleaseObservations() {
        var first=session();var second=session();var close=Sinks.<Void>empty();first.close=close.asMono();
        var boundary=boundary(factory);var one=new AtomicReference<String>();var two=new AtomicReference<String>();
        var a=boundary.execute(()->query(factory)).doOnNext(one::set).subscribe();
        var b=boundary.execute(()->query(factory)).doOnNext(two::set).subscribe();
        try {
            assertThat(one.get()).isNull();assertThat(two.get()).isEqualTo("batch");assertThat(first.committed.get()).isEqualTo(1);assertThat(second.closed.get()).isEqualTo(1);
            close.tryEmitEmpty();assertThat(one.get()).isEqualTo("batch");assertThat(first.closed.get()).isEqualTo(1);
        } finally {a.dispose();b.dispose();}
    }

    static final class ExistingInfrastructureFactory implements ConnectionFactory,InfrastructureProxy {
        final ConnectionFactory target;
        ExistingInfrastructureFactory(ConnectionFactory target) {this.target=target;}
        public Object getWrappedObject() {return target;}
        public org.reactivestreams.Publisher<? extends Connection> create() {return target.create();}
        public ConnectionFactoryMetadata getMetadata() {return target.getMetadata();}
    }

    @Test void existingInfrastructureAliasStillUsesOriginalSpringResourceKey() {
        var outer=session();var inner=session();var provided=new ExistingInfrastructureFactory(factory);var boundary=boundary(provided);
        TransactionalOperator.create(new R2dbcTransactionManager(provided)).transactional(boundary.execute(()->query(provided))).block();
        assertThat(acquired.get()).isEqualTo(2);assertThat(outer.committed.get()).isEqualTo(1);assertThat(inner.committed.get()).isEqualTo(1);
        assertThat(outer.closed.get()).isEqualTo(1);assertThat(inner.closed.get()).isEqualTo(1);
    }

    @Test void workFailureRollsBackTheOnlyAcquiredConnection() {
        var session=session();queryGate=Mono.error(new IllegalStateException("query"));
        assertThatThrownBy(()->boundary(factory).execute(()->query(factory)).block()).isInstanceOf(IllegalStateException.class);
        assertThat(session.began.get()).isEqualTo(1);assertThat(session.rolledBack.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);assertThat(session.committed.get()).isZero();
    }

    @Test void emptyWorkStillWaitsForSuccessfulCommitAndRelease() {
        var session=session();boundary(factory).execute(()->query(factory).then()).block();
        assertThat(session.committed.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);
    }

    @Test void emptyWorkCannotHideAFailedRelease() {
        var session=session();session.close=Mono.error(new IllegalStateException("close"));
        assertThatThrownBy(()->boundary(factory).execute(()->query(factory).then()).block()).isInstanceOf(IllegalStateException.class);
        assertThat(session.committed.get()).isEqualTo(1);assertThat(session.closed.get()).isEqualTo(1);assertThat(session.rolledBack.get()).isZero();
    }

    @Test void transactionAwareProviderCannotSilentlyAdoptAnAmbientConnection() {
        var aware=new org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy(factory);
        assertThatThrownBy(()->boundary(aware)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->boundary(new ExistingInfrastructureFactory(aware))).isInstanceOf(IllegalArgumentException.class);
        assertThat(acquired.get()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false,true})
    void knownSingleConnectionProviderCannotPretendToOwnIndependentTransactions(boolean suppressClose) {
        var single=new org.springframework.r2dbc.connection.SingleConnectionFactory(factory);single.setSuppressClose(suppressClose);
        assertThatThrownBy(()->boundary(single)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->boundary(new ExistingInfrastructureFactory(single))).isInstanceOf(IllegalArgumentException.class);
        assertThat(acquired.get()).isZero();
    }

    @Test void resubscribingToOneColdOperationOwnsFreshIndependentTransactions() {
        var first=session();var second=session();var operation=boundary(factory).execute(()->query(factory));
        assertThat(operation.block()).isEqualTo("batch");assertThat(operation.block()).isEqualTo("batch");
        assertThat(acquired.get()).isEqualTo(2);assertThat(first.began.get()).isEqualTo(1);assertThat(second.began.get()).isEqualTo(1);
        assertThat(first.closed.get()).isEqualTo(1);assertThat(second.closed.get()).isEqualTo(1);
    }

    ConnectionFactory scoped(ConnectionFactory target,GenericApplicationContext context,AtomicInteger lookups) {
        context.getBeanFactory().registerScope("delivery-test",new SimpleThreadScope() {
            @Override public Object get(String name,ObjectFactory<?> objectFactory) {
                lookups.incrementAndGet();return super.get(name,objectFactory);
            }
        });
        context.registerBean("scopedTarget.delivery",ConnectionFactory.class,()->target,
                definition->definition.setScope("delivery-test"));
        context.refresh();
        var scoped=new ScopedProxyFactoryBean();scoped.setTargetBeanName("scopedTarget.delivery");
        scoped.setProxyTargetClass(false);scoped.setBeanFactory(context.getBeanFactory());
        var provided=(ConnectionFactory)scoped.getObject();assertThat(provided).isInstanceOf(ScopedObject.class);
        assertThat(lookups.get()).isZero();return provided;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false,true})
    void scopedSingleConnectionProviderIsRejectedWithoutResolvingItsTarget(boolean suppressClose) {
        var single=new org.springframework.r2dbc.connection.SingleConnectionFactory(factory);single.setSuppressClose(suppressClose);
        var lookups=new AtomicInteger();
        try(var context=new GenericApplicationContext()) {
            var provided=scoped(single,context,lookups);
            assertThatThrownBy(()->boundary(provided)).isInstanceOf(IllegalArgumentException.class);
            assertThat(lookups.get()).isZero();assertThat(acquired.get()).isZero();
        }
    }

    @Test void scopedTransactionAwareProviderIsRejectedWithoutResolvingItsTarget() {
        var aware=new org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy(factory);
        var lookups=new AtomicInteger();
        try(var context=new GenericApplicationContext()) {
            var provided=scoped(aware,context,lookups);
            assertThatThrownBy(()->boundary(provided)).isInstanceOf(IllegalArgumentException.class);
            assertThat(lookups.get()).isZero();assertThat(acquired.get()).isZero();
        }
    }

    @Test void firstInfrastructureTargetCannotHideAScopedProvider() {
        var single=new org.springframework.r2dbc.connection.SingleConnectionFactory(factory);single.setSuppressClose(true);
        var lookups=new AtomicInteger();
        try(var context=new GenericApplicationContext()) {
            var provided=new ExistingInfrastructureFactory(scoped(single,context,lookups));
            assertThatThrownBy(()->boundary(provided)).isInstanceOf(IllegalArgumentException.class);
            assertThat(lookups.get()).isZero();assertThat(acquired.get()).isZero();
        }
    }

    @Test void plainIndependentPoolRemainsAdmissibleWithoutAcquiringAConnection() {
        var pool=new io.r2dbc.pool.ConnectionPool(io.r2dbc.pool.ConnectionPoolConfiguration.builder(factory)
                .initialSize(0).maxSize(1).build());
        try {assertThatCode(()->boundary(pool)).doesNotThrowAnyException();assertThat(acquired.get()).isZero();}
        finally {pool.dispose();}
    }
}
