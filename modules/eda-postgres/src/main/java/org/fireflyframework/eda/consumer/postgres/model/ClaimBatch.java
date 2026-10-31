/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;

import java.util.List;

public record ClaimBatch(List<DeliveryClaim> claims) {
    public ClaimBatch {
        if (claims == null || claims.size() > DeliveryBounds.MAX_BATCH) {
            throw new IllegalArgumentException("Delivery batch exceeds supported bounds");
        }
        claims = List.copyOf(claims);
    }
}
