/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.core.transaction;
import java.time.Duration;
import java.util.Objects;
public record CleanupPolicy(Duration rollback, Duration close) {
    public CleanupPolicy {
        finite(rollback);finite(close);
    }
    private static void finite(Duration value) {
        Objects.requireNonNull(value);
        if(value.isZero()||value.isNegative())throw new IllegalArgumentException("Finite cleanup duration required");
        value.toNanos();
    }
}
