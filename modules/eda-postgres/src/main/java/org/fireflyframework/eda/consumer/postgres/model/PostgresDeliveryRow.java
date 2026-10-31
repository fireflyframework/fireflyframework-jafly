/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;

import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(schema = "public", name = "firefly_eda_outbox")
public record PostgresDeliveryRow(@Id Long id, String destination, String channel, String eventType,
        byte[] payload, Json headers, String transactionId, String publisherType, String connectionId,
        String eventClass, String status, int attempts, String errorMessage, OffsetDateTime createdAt,
        OffsetDateTime processedAt, OffsetDateTime failedAt, UUID leaseToken, long leaseGeneration,
        OffsetDateTime leaseUntil, OffsetDateTime nextAttemptAt) {
    public PostgresDeliveryRow {
        payload = payload == null ? null : payload.clone();
        headers = headers == null ? null : Json.of(headers.asString());
    }

    @Override
    public byte[] payload() { return payload == null ? null : payload.clone(); }
}
