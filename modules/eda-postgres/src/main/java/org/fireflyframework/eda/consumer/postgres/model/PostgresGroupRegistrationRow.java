/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table(schema="public",name="firefly_eda_group")
public record PostgresGroupRegistrationRow(@Id String groupId, String[] channels, int maxAttempts,
        String replayPolicy, OffsetDateTime registeredAt) {
    public PostgresGroupRegistrationRow {
        new ConsumerGroupId(groupId);
        if (channels == null || channels.length < 1 || channels.length > DeliveryBounds.MAX_CHANNELS
                || maxAttempts < 1 || !RetainedReplayPolicy.ALL_RETAINED.name().equals(replayPolicy) || registeredAt == null) {
            throw new IllegalArgumentException("Persisted group registration is incompatible");
        }
        var unique = new java.util.HashSet<String>();
        for (String channel : channels) {
            DeliveryBounds.text(channel, 63);
            if (!unique.add(channel)) throw new IllegalArgumentException("Persisted group channels contain duplicates");
        }
        channels = channels.clone();
    }
    @Override public String[] channels() { return channels.clone(); }
    public GroupSubscription subscription() { return new GroupSubscription(java.util.Set.of(channels), maxAttempts); }
}
