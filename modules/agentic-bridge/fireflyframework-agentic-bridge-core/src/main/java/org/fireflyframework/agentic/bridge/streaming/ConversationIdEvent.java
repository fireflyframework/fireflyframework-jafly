/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.streaming;

/**
 * Server-issued conversation identifier sent at the start of a WebSocket
 * exchange when the client did not supply one ({@code {"type":"conversation_id",...}}).
 */
public record ConversationIdEvent(String conversationId) implements StreamEvent {
}
