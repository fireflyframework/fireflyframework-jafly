/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;
import java.time.Duration;
import reactor.core.publisher.Mono;
public interface RootTransactionScope {
    Duration remaining();
    void narrow(R2dbcOperationBudget observedCap);
    /** Explicit opt-in; custom scopes cannot manufacture a successful rejection correlation. */
    default <T> Mono<T> reject(RootWorkRejectionKey key){
        return Mono.error(new IllegalStateException("Safe owned work rejection unavailable"));
    }
}
