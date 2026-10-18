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
 * Sentinel emitted when the agentic SSE stream sends {@code data: [DONE]}.
 */
public record DoneEvent() implements StreamEvent {

    public static final DoneEvent INSTANCE = new DoneEvent();
}
