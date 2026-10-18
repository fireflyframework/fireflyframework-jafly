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
 * A single token emitted by an incremental SSE stream
 * ({@code POST /agents/{name}/stream/incremental}) or by a WebSocket
 * {@code {"type":"token",...}} frame.
 */
public record TokenEvent(String text) implements StreamEvent {
}
