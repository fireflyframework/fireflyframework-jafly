/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;
import java.util.Objects;
public final class RootTransactionException extends IllegalStateException {
    public enum Stage { GATE, ACQUIRE, BEGIN, WORK, COMMIT, ROLLBACK, CLOSE, FINAL_BUDGET }
    private static final long serialVersionUID=1L;
    private final TransactionFacts facts;
    private final CleanupFact cleanup;
    private final Stage stage;
    private final transient RootWorkRejectionKey rejection;
    public RootTransactionException(TransactionFacts facts,CleanupFact cleanup,Stage stage){
        this(facts,cleanup,stage,null);
    }
    RootTransactionException(TransactionFacts facts,CleanupFact cleanup,Stage stage,RootWorkRejectionKey rejection){
        super("BOUNDED_ROOT_TRANSACTION_UNAVAILABLE");this.facts=Objects.requireNonNull(facts);this.cleanup=Objects.requireNonNull(cleanup);this.stage=Objects.requireNonNull(stage);this.rejection=rejection;
    }
    public TransactionFacts transactionFacts(){return facts;}
    public CleanupFact cleanupFact(){return cleanup;}
    public Stage stage(){return stage;}
    public boolean workRejectedBy(RootWorkRejectionKey key){return key!=null&&rejection==key;}
}
