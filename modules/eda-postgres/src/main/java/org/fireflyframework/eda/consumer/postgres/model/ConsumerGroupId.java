/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;
public record ConsumerGroupId(String value) {
    public ConsumerGroupId { DeliveryBounds.text(value, 255); }
}
