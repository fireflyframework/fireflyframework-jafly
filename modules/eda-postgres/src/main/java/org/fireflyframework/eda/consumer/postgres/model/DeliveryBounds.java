/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class DeliveryBounds {
    public static final int MAX_BATCH = 1024;
    public static final int MAX_CHANNELS = 256;
    public static final Duration MAX_DELAY = Duration.ofDays(30);

    private DeliveryBounds() { }

    public static long milliseconds(Duration value, boolean allowZero) {
        if (value == null || value.isNegative() || (!allowZero && value.isZero()) || value.compareTo(MAX_DELAY) > 0) {
            throw new IllegalArgumentException("Delivery duration exceeds supported bounds");
        }
        long millis = value.toMillis();
        if (!Duration.ofMillis(millis).equals(value)) {
            throw new IllegalArgumentException("Delivery duration must use whole milliseconds");
        }
        return millis;
    }

    public static void text(String value, int maximumBytes) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Delivery name is missing or invalid");
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    throw new IllegalArgumentException("Delivery name must contain well-formed Unicode");
                }
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("Delivery name must contain well-formed Unicode");
            }
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw new IllegalArgumentException("Delivery name exceeds its UTF-8 bound");
        }
    }
}
