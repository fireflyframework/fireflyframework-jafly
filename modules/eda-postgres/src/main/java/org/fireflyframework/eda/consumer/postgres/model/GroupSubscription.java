/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;
import java.util.Set;
public record GroupSubscription(Set<String> channels, int maxAttempts) {
    public GroupSubscription {
        if (channels == null || channels.isEmpty() || channels.size() > DeliveryBounds.MAX_CHANNELS || maxAttempts < 1) {
            throw new IllegalArgumentException("Group subscription bounds are invalid");
        }
        channels.forEach(channel -> DeliveryBounds.text(channel, 63));
        channels = Set.copyOf(channels);
    }
}
