/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres.model;
import java.util.UUID;
public record DeadLetterRedriveCommand(UUID commandId, long eventId, UUID terminalToken, long terminalGeneration,
        int additionalAttempts, String actor, String reason) {
    public DeadLetterRedriveCommand {
        if (commandId == null || terminalToken == null || terminalGeneration < 1 || additionalAttempts < 1) {
            throw new IllegalArgumentException("Redrive command identity and budget are required");
        }
        DeliveryBounds.text(actor, 255);
        DeliveryBounds.text(reason, 1024);
    }
}
