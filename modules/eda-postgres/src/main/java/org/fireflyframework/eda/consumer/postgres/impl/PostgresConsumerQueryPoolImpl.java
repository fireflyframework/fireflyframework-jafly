/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres.impl;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.eda.properties.EdaProperties.Consumer.PostgresConfig;

/** Container-owned query connections; the supplied raw factory remains external. */
public final class PostgresConsumerQueryPoolImpl implements AutoCloseable {
    private final ConnectionPool pool;
    private final Duration shutdownTimeout;
    private final AtomicBoolean closed=new AtomicBoolean();

    public PostgresConsumerQueryPoolImpl(ConnectionFactory raw,PostgresConfig config) {
        Objects.requireNonNull(raw);
        if(config.getMaxPoolSize()<1||config.getMaxPoolSize()>100)throw new IllegalArgumentException("Invalid PostgreSQL consumer pool bounds");
        requireTimeout(config.getConnectTimeout());requireTimeout(config.getAcquireTimeout());requireTimeout(config.getShutdownTimeout());
        shutdownTimeout=config.getShutdownTimeout();
        var settings=ConnectionPoolConfiguration.builder(raw).initialSize(0).minIdle(0).maxSize(config.getMaxPoolSize())
                .maxAcquireTime(config.getAcquireTimeout()).maxCreateConnectionTime(config.getConnectTimeout())
                .maxValidationTime(config.getAcquireTimeout()).acquireRetry(0).maxIdleTime(Duration.ofMinutes(5)).build();
        pool=new ConnectionPool(settings);
    }
    public ConnectionFactory connectionFactory(){return pool;}
    public static void requireTimeout(Duration timeout){
        if(timeout==null||timeout.isZero()||timeout.isNegative()||timeout.compareTo(Duration.ofSeconds(60))>0||timeout.getNano()%1_000_000!=0)throw new IllegalArgumentException("PostgreSQL consumer timeouts require positive whole milliseconds up to sixty seconds");
    }
    @Override public void close(){
        if(closed.compareAndSet(false,true)){
            try {pool.disposeLater().block(shutdownTimeout);}
            catch(RuntimeException failure){throw new IllegalStateException("PostgreSQL consumer query pool shutdown failed");}
        }
    }
}
