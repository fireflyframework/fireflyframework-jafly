/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.publisher.postgres.model;

import io.r2dbc.postgresql.codec.Json;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("firefly_eda_outbox")
public record PostgresOutboxAppend(
        @Id Long id,
        String destination,
        String channel,
        String eventType,
        byte[] payload,
        Json headers,
        String transactionId,
        String publisherType,
        String connectionId,
        String eventClass) {
}
