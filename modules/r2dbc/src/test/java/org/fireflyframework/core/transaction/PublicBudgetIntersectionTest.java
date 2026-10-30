/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class PublicBudgetIntersectionTest {
    private static final Instant START=Instant.parse("2026-10-27T00:00:00Z");
    private final Clock clock=Clock.fixed(START,ZoneOffset.UTC);
    private final AtomicLong elapsed=new AtomicLong();
    private final LongSupplier nanos=elapsed::get;

    private R2dbcOperationBudget budget(long seconds){return R2dbcOperationBudget.observed(START,START.plusSeconds(seconds),0,Duration.ofSeconds(5),clock,nanos);}
    private static R2dbcOperationBudget compose(R2dbcOperationBudget first,R2dbcOperationBudget second){
        var method=assertDoesNotThrow(()->R2dbcOperationBudget.class.getMethod("intersect",R2dbcOperationBudget.class),"Owners require public budget composition before acquisition starts");
        try{return (R2dbcOperationBudget)method.invoke(first,second);}
        catch(InvocationTargetException error){if(error.getCause() instanceof RuntimeException cause)throw cause;throw new AssertionError(error.getCause());}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    @Test void composedBudgetKeepsTheEarlierOriginalMonotonicDeadline(){
        var original=budget(5);var inherited=budget(1);elapsed.set(250_000_000);
        var combined=compose(original,inherited);assertEquals(Duration.ofMillis(750),combined.remaining());
        elapsed.set(1_000_000_000);assertThrows(IllegalStateException.class,combined::remaining);
        assertEquals(Duration.ofSeconds(4),original.remaining());
    }
    @Test void expiredInheritedBudgetRefusesCompositionBeforeAnyTransactionCanStart(){
        var original=budget(5);var inherited=budget(1);elapsed.set(1_000_000_000);
        assertThrows(IllegalStateException.class,()->compose(original,inherited));
    }
    @Test void differentTimeSourceObjectsCannotBeComposed(){
        var other=R2dbcOperationBudget.observed(START,START.plusSeconds(1),0,Duration.ofSeconds(5),Clock.fixed(START,ZoneOffset.UTC),nanos);
        assertThrows(IllegalArgumentException.class,()->compose(budget(5),other));
    }
}
