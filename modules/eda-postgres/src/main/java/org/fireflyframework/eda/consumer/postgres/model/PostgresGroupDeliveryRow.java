/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table(schema="public",name="firefly_eda_group_delivery")
public record PostgresGroupDeliveryRow(@Id UUID deliveryId, String groupId, long eventId, String status,
        int attempts, int attemptCeiling, String errorMessage, OffsetDateTime processedAt, OffsetDateTime failedAt,
        UUID leaseToken, long leaseGeneration, OffsetDateTime leaseUntil, OffsetDateTime nextAttemptAt) { }
