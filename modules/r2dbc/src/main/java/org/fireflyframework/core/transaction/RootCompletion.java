/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;
import java.util.Objects;
public final class RootCompletion<T> {
    private final T value;
    private final TransactionFacts facts;
    public RootCompletion(T value, TransactionFacts facts) {this.value=Objects.requireNonNull(value);this.facts=Objects.requireNonNull(facts);}
    public T value(){return value;}
    public TransactionFacts transactionFacts(){return facts;}
    public CleanupFact cleanupFact(){return CleanupFact.COMPLETE;}
    @Override public String toString(){return "RootCompletion";}
}
