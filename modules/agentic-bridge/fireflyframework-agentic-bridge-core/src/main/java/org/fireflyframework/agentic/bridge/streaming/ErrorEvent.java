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
 * Error frame emitted by the agentic side either as an SSE
 * {@code event: error} or as a WebSocket {@code {"type":"error",...}} frame.
 */
public record ErrorEvent(String message) implements StreamEvent {
}
