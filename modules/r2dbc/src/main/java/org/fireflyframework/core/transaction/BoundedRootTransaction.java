/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;
import java.util.function.Function;
import org.springframework.transaction.TransactionDefinition;
import reactor.core.publisher.Mono;
public interface BoundedRootTransaction {
    <T> Mono<RootCompletion<T>> execute(R2dbcOperationBudget original, TransactionDefinition definition,
            Function<RootTransactionScope, Mono<T>> work);
}
