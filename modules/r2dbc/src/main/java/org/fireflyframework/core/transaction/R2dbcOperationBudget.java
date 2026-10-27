/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;
import java.time.*;
import java.util.Objects;
import java.util.function.LongSupplier;
/** Original technical deadline; it conveys no authentication or business authority. */
public final class R2dbcOperationBudget {
    private final Instant observedAt,until;
    private final long beforeNanos,budgetNanos;
    private final Clock clock;
    private final LongSupplier nanos;
    private R2dbcOperationBudget(Instant observedAt,Instant until,long beforeNanos,long budgetNanos,Clock clock,LongSupplier nanos){
        this.observedAt=observedAt;this.until=until;this.beforeNanos=beforeNanos;this.budgetNanos=budgetNanos;this.clock=clock;this.nanos=nanos;
    }
    public static R2dbcOperationBudget observed(Instant observedAt,Instant expiresAt,long beforeObservationNanos,
            Duration explicitMaximum,Clock clock,LongSupplier monotonicNanos){
        Objects.requireNonNull(observedAt);Objects.requireNonNull(expiresAt);Objects.requireNonNull(explicitMaximum);Objects.requireNonNull(clock);Objects.requireNonNull(monotonicNanos);
        var duration=Duration.between(observedAt,expiresAt);
        if(duration.isNegative()||duration.isZero()||explicitMaximum.isNegative()||explicitMaximum.isZero()||duration.compareTo(explicitMaximum)>0)throw new IllegalArgumentException("Finite original operation budget required");
        explicitMaximum.toNanos();return new R2dbcOperationBudget(observedAt,expiresAt,beforeObservationNanos,duration.toNanos(),clock,monotonicNanos);
    }
    public Duration remaining(){
        long elapsed=nanos.getAsLong()-beforeNanos;var now=clock.instant();
        if(elapsed<0||elapsed>=budgetNanos||now.isBefore(observedAt)||!now.isBefore(until))throw new IllegalStateException("Original operation budget exhausted");
        var absolute=Duration.between(now,until);var monotonic=Duration.ofNanos(budgetNanos-elapsed);return absolute.compareTo(monotonic)<0?absolute:monotonic;
    }
    R2dbcOperationBudget intersect(R2dbcOperationBudget other){
        Objects.requireNonNull(other);
        if(clock!=other.clock||nanos!=other.nanos)throw new IllegalArgumentException("Compatible original time sources required");
        long before=nanos.getAsLong();var a=remaining();var b=other.remaining();var remaining=a.compareTo(b)<0?a:b;var absolute=until.isBefore(other.until)?until:other.until;
        // The earlier of both original limits is retained; this observation cannot grant more time.
        return new R2dbcOperationBudget(clock.instant(),absolute,before,remaining.toNanos(),clock,nanos);
    }
    @Override public String toString(){return "R2dbcOperationBudget";}
}
