/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;

public record DeliveryClaim(PostgresDeliveryRow row, int maxAttempts) {
    public DeliveryClaim {
        if (row == null || row.id() == null || row.payload() == null || !"IN_FLIGHT".equals(row.status())
                || row.attempts() < 1 || maxAttempts < row.attempts() || row.leaseGeneration() < 1
                || row.leaseToken() == null || row.leaseUntil() == null) {
            throw new IllegalArgumentException("Delivery claim has no valid fenced owner");
        }
    }
}
