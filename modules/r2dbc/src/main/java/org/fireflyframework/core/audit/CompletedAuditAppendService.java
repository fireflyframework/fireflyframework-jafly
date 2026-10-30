/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.core.audit;

import java.util.UUID;
import org.fireflyframework.core.transaction.RootTransactionScope;
import org.fireflyframework.kernel.audit.AuditRecord;
import reactor.core.publisher.Mono;

/** Verifies an actual completed append in its still-active original owner root; never grants admission. */
@FunctionalInterface
public interface CompletedAuditAppendService {
    Mono<AuditRecord> requireCompleted(RootTransactionScope scope,UUID contextNonce,
            TransactionalAuditAppender.ProvisionalAppend expected);
}
