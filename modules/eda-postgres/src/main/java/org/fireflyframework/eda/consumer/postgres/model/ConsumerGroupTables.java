/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;
public record ConsumerGroupTables(String schema, String outbox, String registry, String deliveries, String redrives) {
    public ConsumerGroupTables {
        for (String table : new String[]{outbox, registry, deliveries, redrives}) {
            org.fireflyframework.eda.consumer.postgres.PostgresDeliverySchema.qualified(schema, table);
        }
        if (java.util.Set.of(outbox, registry, deliveries, redrives).size() != 4) {
            throw new IllegalArgumentException("Group protocol relations must be distinct");
        }
    }
}
