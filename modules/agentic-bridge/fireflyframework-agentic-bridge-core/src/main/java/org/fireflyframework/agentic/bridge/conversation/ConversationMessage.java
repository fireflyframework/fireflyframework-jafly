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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One message from a conversation history, returned by
 * {@code GET /agents/conversations/{conversationId}}.
 *
 * <p>Pydantic-AI emits message structures that vary by message kind, so the
 * bridge keeps the payload as a generic map and exposes a {@code role}/
 * {@code content} convenience read for the common case.</p>
 */
public final class ConversationMessage {

    private final Map<String, Object> raw;

    public ConversationMessage(Map<String, Object> raw) {
        Objects.requireNonNull(raw, "raw");
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    public Map<String, Object> raw() {
        return raw;
    }

    public String role() {
        Object r = raw.get("role");
        return r == null ? null : r.toString();
    }

    public String content() {
        Object c = raw.get("content");
        return c == null ? null : c.toString();
    }
}
