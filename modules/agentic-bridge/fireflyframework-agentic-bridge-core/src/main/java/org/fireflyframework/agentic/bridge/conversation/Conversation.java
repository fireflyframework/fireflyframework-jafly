/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.conversation;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight handle to a conversation managed by the agentic
 * {@link ConversationManager}. The {@code id} is the value to send back in
 * {@code AgentRequest.conversationId} for subsequent turns.
 */
public final class Conversation {

    private final String id;
    private final Instant createdAt;

    public Conversation(String id, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
