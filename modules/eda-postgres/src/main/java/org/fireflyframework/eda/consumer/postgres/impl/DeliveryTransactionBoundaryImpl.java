/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.impl;

import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionMetadata;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.ValidationDepth;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.core.InfrastructureProxy;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

/** Owns a short query transaction while preserving Spring's original resource key. */
final class DeliveryTransactionBoundaryImpl {
    private final Object observationKey = new Object();
    private final TransactionalOperator transactions;
    private final Duration timeout;

    DeliveryTransactionBoundaryImpl(ConnectionFactory factory, Duration timeout) {
        Objects.requireNonNull(factory, "factory");
        if (factory instanceof ScopedObject) {
            throw new IllegalArgumentException("Delivery requires the underlying independent ConnectionFactory");
        }
        Object key = factory instanceof InfrastructureProxy proxy ? proxy.getWrappedObject() : factory;
        if (key instanceof ScopedObject || reusesConnections(factory) || reusesConnections(key)) {
            throw new IllegalArgumentException("Delivery requires the underlying independent ConnectionFactory");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(60)) > 0
                || !timeout.equals(Duration.ofSeconds(timeout.toSeconds()))) {
            throw new IllegalArgumentException("Delivery transaction timeout must be 1 to 60 whole seconds");
        }
        this.timeout = timeout;
        var definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        definition.setTimeout(Math.toIntExact(timeout.toSeconds()));
        definition.setName("firefly.eda.postgres.delivery");
        transactions = TransactionalOperator.create(new R2dbcTransactionManager(new ObservedFactoryImpl(factory)), definition);
    }

    private static boolean reusesConnections(Object factory) {
        return factory instanceof org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy
                || factory instanceof org.springframework.r2dbc.connection.SingleConnectionFactory;
    }

    <T> Mono<T> execute(Supplier<Mono<T>> work) {
        return Mono.defer(() -> {
            var observation = new Observation();
            Mono<Optional<T>> materialized = Mono.defer(work).timeout(timeout).map(Optional::of).defaultIfEmpty(Optional.empty());
            return transactions.transactional(materialized).flatMap(value -> {
                observation.verifyReleased();
                return value.map(Mono::just).orElseGet(Mono::empty);
            }).contextWrite(context -> context.put(observationKey, observation));
        });
    }

    private final class ObservedFactoryImpl implements ConnectionFactory, InfrastructureProxy {
        private final ConnectionFactory target;

        private ObservedFactoryImpl(ConnectionFactory target) { this.target = target; }

        @Override
        public Object getWrappedObject() {
            // Spring unwraps InfrastructureProxy once; preserve an existing proxy's identical key.
            return target instanceof InfrastructureProxy proxy ? proxy.getWrappedObject() : target;
        }

        @Override
        public Publisher<? extends Connection> create() {
            return Mono.deferContextual(context -> {
                Observation observation = context.get(observationKey);
                return Mono.from(target.create()).flatMap(connection -> {
                    if (observation.acquired.incrementAndGet() != 1) {
                        return Mono.from(connection.close()).then(Mono.error(
                                new IllegalStateException("Delivery transaction acquired multiple connections")));
                    }
                    return Mono.just(new ObservedConnectionImpl(connection, observation));
                });
            });
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() { return target.getMetadata(); }
    }

    private static final class Observation {
        private final AtomicInteger acquired = new AtomicInteger();
        private final AtomicInteger closeAttempts = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private void verifyReleased() {
            if (acquired.get() != 1 || closeAttempts.get() != 1 || !closed.get() || failure.get() != null) {
                throw new IllegalStateException("Delivery transaction resource release did not complete", failure.get());
            }
        }
    }

    private static final class ObservedConnectionImpl implements Connection {
        private final Connection target;
        private final Observation observation;

        private ObservedConnectionImpl(Connection target, Observation observation) {
            this.target = target;
            this.observation = observation;
        }

        @Override
        public Publisher<Void> close() {
            return Mono.defer(() -> {
                if (observation.closeAttempts.incrementAndGet() != 1) {
                    return Mono.<Void>error(new IllegalStateException("Delivery connection released multiple times"));
                }
                return Mono.from(target.close());
            }).doOnSuccess(unused -> observation.closed.set(true))
                    .doOnError(error -> observation.failure.compareAndSet(null, error))
                    .doOnCancel(() -> observation.failure.compareAndSet(null,
                            new IllegalStateException("Delivery connection release was cancelled")));
        }

        @Override public Publisher<Void> beginTransaction() { return target.beginTransaction(); }
        @Override public Publisher<Void> beginTransaction(io.r2dbc.spi.TransactionDefinition definition) { return target.beginTransaction(definition); }
        @Override public Publisher<Void> commitTransaction() { return target.commitTransaction(); }
        @Override public Batch createBatch() { return target.createBatch(); }
        @Override public Publisher<Void> createSavepoint(String name) { return target.createSavepoint(name); }
        @Override public Statement createStatement(String sql) { return target.createStatement(sql); }
        @Override public boolean isAutoCommit() { return target.isAutoCommit(); }
        @Override public ConnectionMetadata getMetadata() { return target.getMetadata(); }
        @Override public IsolationLevel getTransactionIsolationLevel() { return target.getTransactionIsolationLevel(); }
        @Override public Publisher<Void> releaseSavepoint(String name) { return target.releaseSavepoint(name); }
        @Override public Publisher<Void> rollbackTransaction() { return target.rollbackTransaction(); }
        @Override public Publisher<Void> rollbackTransactionToSavepoint(String name) { return target.rollbackTransactionToSavepoint(name); }
        @Override public Publisher<Void> setAutoCommit(boolean autoCommit) { return target.setAutoCommit(autoCommit); }
        @Override public Publisher<Void> setLockWaitTimeout(Duration timeout) { return target.setLockWaitTimeout(timeout); }
        @Override public Publisher<Void> setStatementTimeout(Duration timeout) { return target.setStatementTimeout(timeout); }
        @Override public Publisher<Void> setTransactionIsolationLevel(IsolationLevel level) { return target.setTransactionIsolationLevel(level); }
        @Override public Publisher<Boolean> validate(ValidationDepth depth) { return target.validate(depth); }
    }
}
