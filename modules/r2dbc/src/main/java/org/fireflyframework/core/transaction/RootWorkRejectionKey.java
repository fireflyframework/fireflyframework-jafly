/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.core.transaction;

import java.util.concurrent.atomic.AtomicReference;

/** Fresh per subscriber; correlates an owned rejection without carrying business data or authority. */
public final class RootWorkRejectionKey {
    private final AtomicReference<Object> owner=new AtomicReference<>();
    private RootWorkRejectionKey() { }
    public static RootWorkRejectionKey create(){return new RootWorkRejectionKey();}
    boolean bind(Object invocation){return owner.compareAndSet(null,invocation);}
    boolean belongsTo(Object invocation){return owner.get()==invocation;}
    @Override public String toString(){return "RootWorkRejectionKey";}
}
