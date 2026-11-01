/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;
import java.util.Objects;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
/** Verifies caller-built resources without cloning or changing the caller's client or template. */
public final class BoundedR2dbcResources {
    private final OwnedLeaseConnectionFactoryImpl factory;
    private final R2dbcTransactionManager manager;
    private final DatabaseClient client;
    private final R2dbcEntityTemplate template;
    private BoundedR2dbcResources(OwnedLeaseConnectionFactoryImpl factory,DatabaseClient client,R2dbcEntityTemplate template,R2dbcTransactionManager manager){this.factory=factory;this.client=client;this.template=template;this.manager=manager;}
    public static BoundedR2dbcResources verify(OwnedLeaseConnectionFactoryImpl factory,DatabaseClient client,
            R2dbcEntityTemplate template,R2dbcTransactionManager manager){
        Objects.requireNonNull(factory);Objects.requireNonNull(client);Objects.requireNonNull(template);Objects.requireNonNull(manager);
        if(client.getConnectionFactory()!=factory||template.getDatabaseClient()!=client||manager.getConnectionFactory()!=factory)throw new IllegalArgumentException("One qualified factory/client/template/manager graph required");
        return new BoundedR2dbcResources(factory,client,template,manager);
    }
    boolean current(){return client.getConnectionFactory()==factory&&template.getDatabaseClient()==client&&manager.getConnectionFactory()==factory;}
    boolean sameGraph(BoundedR2dbcResources other){return other!=null&&factory==other.factory&&client==other.client&&template==other.template&&manager==other.manager;}
    public OwnedLeaseConnectionFactoryImpl factory(){return factory;}
    public R2dbcTransactionManager manager(){return manager;}
}
