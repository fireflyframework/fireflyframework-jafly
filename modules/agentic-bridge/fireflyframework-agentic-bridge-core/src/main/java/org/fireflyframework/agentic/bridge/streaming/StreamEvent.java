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
 * Sealed marker for every event that can flow through a streaming agent
 * invocation, regardless of transport (SSE or WebSocket).
 *
 * <p>Pattern-match on the concrete subtype with a {@code switch} expression
 * to handle each event class without instanceof chains.</p>
 */
public sealed interface StreamEvent permits TokenEvent, ChunkEvent, ResultEvent, ErrorEvent, DoneEvent, ConversationIdEvent {
}
