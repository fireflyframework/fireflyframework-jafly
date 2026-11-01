/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction.impl;
import java.util.Objects;
import java.util.function.Function;
import org.fireflyframework.core.transaction.*;
import org.springframework.transaction.TransactionDefinition;
import reactor.core.publisher.Mono;
/** Explicit opt-in service; existing transaction auto-configuration is unchanged. */
public final class DefaultBoundedRootTransactionImpl implements BoundedRootTransaction {
    private final BoundedR2dbcResources resources;
    private final CleanupPolicy cleanup;
    public DefaultBoundedRootTransactionImpl(BoundedR2dbcResources resources,CleanupPolicy cleanup){this.resources=Objects.requireNonNull(resources);this.cleanup=Objects.requireNonNull(cleanup);}
    @Override public <T> Mono<RootCompletion<T>> execute(R2dbcOperationBudget original,TransactionDefinition definition,
            Function<RootTransactionScope,Mono<T>> work){return resources.factory().executeBounded(resources,original,definition,cleanup,work);}
}
