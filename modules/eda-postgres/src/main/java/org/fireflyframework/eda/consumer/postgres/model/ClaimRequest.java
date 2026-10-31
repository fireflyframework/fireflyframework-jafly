/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;

import java.time.Duration;
import java.util.Set;

public record ClaimRequest(Set<String> channels, int limit, int maxAttempts, Duration leaseDuration) {
    public ClaimRequest {
        if (channels == null || channels.isEmpty() || channels.size() > DeliveryBounds.MAX_CHANNELS
                || limit < 1 || limit > DeliveryBounds.MAX_BATCH || maxAttempts < 1) {
            throw new IllegalArgumentException("Delivery claim bounds are invalid");
        }
        channels.forEach(channel -> DeliveryBounds.text(channel, 63));
        channels = Set.copyOf(channels);
        DeliveryBounds.milliseconds(leaseDuration, false);
    }
}
